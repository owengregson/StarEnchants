package bootstrap;

import bootstrap.wire.ModuleFold;
import compile.load.ContentHolder;
import compile.load.CrystalDef;
import compile.load.EnchantDef;
import engine.boot.RegistryFingerprint;
import feature.apply.ApplyResult;
import feature.apply.ItemEnchanter;
import feature.imports.ImportCode;
import feature.menu.Menu;
import feature.menu.MenuRegistry;
import feature.menu.MintIo;
import feature.menu.Mintable;
import feature.menu.ReferenceCatalog;
import org.bukkit.Bukkit;
import feature.soul.SoulService;
import item.codec.CarrierCodec;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.HeroicStat;
import item.codec.ItemKeys;
import item.codec.ItemStateStore;
import platform.item.Inventories;
import platform.lang.Messages;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import migrate.Migrator;
import pack.Pack;
import pack.PackStore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.content.ContentReloader;
import platform.content.ReloadResult;
import platform.sched.Scheduling;
import schema.diag.Diagnostic;

/**
 * The {@code /se} admin command (ADR-0014, §10). {@code /se} runs on the command thread, not a region
 * thread, so item/inventory work hops to the target player's own thread (Folia-correct).
 *
 * <p>Command replies deliberately bypass the {@link Messages#send} feedback gate (ADR-0041): {@code /se} is the
 * operator/admin-echo surface — {@code messages.feedback} must never silence a command's response. All raw
 * {@code sendMessage} in this class is that documented exemption.
 */
public final class SeCommand implements CommandExecutor, TabCompleter {

    /**
     * Every {@code /se} subcommand. The single source for {@link #SUBCOMMANDS} (completion) and the generated
     * command docs ({@code website/src/data/surface.json}, via {@code SurfaceCatalogDriftTest}). Order matches
     * the dispatch switch; adding a command here keeps completion + docs in step.
     */
    static final List<CommandInfo> COMMANDS = List.of(
            CommandInfo.of("reload", "[--dry-run]", "Rebuild the content library off-thread and hot-swap it in (or just validate)."),
            CommandInfo.of("problems", "", "Show the errors and warnings from the last content load."),
            CommandInfo.of("why", "<player> [key]",
                    "Explain a player's recent activation attempts — which gate stopped each one and why."),
            CommandInfo.of("damagedebug", "",
                    "Toggle a per-hit damage-fold readout (base, percents, flats, scale, result) for yourself."),
            CommandInfo.of("modules", "",
                    "Show every feature module — toggle state and depth, wired listeners, commands, mint types, stores."),
            CommandInfo.of("give", "<type> <player> [args]", "Give any mintable item (book, scroll, dust, gem, orb, crystal, set piece, heroic…) to a player."),
            CommandInfo.of("item", "dump", "Print the decoded StarEnchants state of the held item."),
            CommandInfo.of("enchant", "<key> [level]", "Apply an enchant to the held item (admin; bypasses apply rules)."),
            CommandInfo.of("removeenchant", "<key>", "Strip an enchant from the held item."),
            CommandInfo.alias("unenchant", "removeenchant"),
            CommandInfo.of("crystal", "<key>", "Mint a socketable crystal to yourself."),
            CommandInfo.of("heroic", "", "Mint a Heroic upgrade item to yourself."),
            CommandInfo.of("orb", "", "Mint a slot-expander orb to yourself."),
            CommandInfo.of("gem", "[amount]", "Mint a soul gem to yourself (optionally pre-loaded with that many souls)."),
            CommandInfo.of("book", "<key> [level]", "Mint an enchant book to yourself."),
            CommandInfo.of("blackscroll", "", "Mint a black scroll (extracts a random enchant from gear to a book)."),
            CommandInfo.of("randomizer", "", "Mint a randomizer scroll (rerolls a book's success chance)."),
            CommandInfo.of("transmog", "", "Mint a transmog scroll (re-skins an item's appearance)."),
            CommandInfo.of("godlytransmog", "", "Mint a godly transmog tool (reorder an item's enchant lore)."),
            CommandInfo.of("holy", "", "Mint a holy scroll (keeps items + levels on death)."),
            CommandInfo.of("nametag", "", "Mint an item nametag."),
            CommandInfo.of("dust", "[percent]", "Mint success dust (a fixed percent, or a random roll)."),
            CommandInfo.of("whitescroll", "", "Mint a white scroll (protects an item from a black scroll)."),
            CommandInfo.of("unopened", "<tier>", "Mint an unopened book of a rarity tier."),
            CommandInfo.of("soulmode", "", "Toggle your active soul gem's soul mode."),
            CommandInfo.of("split", "<amount>", "Split souls off your active gem into a new gem."),
            CommandInfo.of("migrate", "<ee|ea|ae> <path>", "Import EliteEnchantments / EliteArmor / AdvancedEnchantments configs into the unified schema."),
            CommandInfo.of("import", "<code>", "Import an enchant from an SE1 code (e.g. from the web Enchant Creator) and reload."),
            CommandInfo.of("pack", "<list|info|apply|export> [name] [--force]", "Manage config-pack ZIP snapshots of your whole setup."),
            CommandInfo.of("menu", "[name]", "Open an operator GUI (no name = the console; or mint, admin, sets, crystals, apply, browsers)."),
            CommandInfo.of("effects", "", "Browse the effect reference in chat."),
            CommandInfo.of("selectors", "", "Browse the selector reference in chat."),
            CommandInfo.of("triggers", "", "Browse the trigger reference in chat."),
            CommandInfo.of("conditions", "", "Browse the conditions reference in chat."),
            CommandInfo.of("variables", "", "Browse the condition variables in chat."),
            CommandInfo.of("list", "", "List every enchant, set, and crystal."),
            CommandInfo.of("docs", "[vocabulary]", "Umbrella for the DSL reference: no arg lists the vocabularies; an arg opens one."));

    static final List<String> SUBCOMMANDS = COMMANDS.stream().map(CommandInfo::name).toList();

    static final List<String> PACK_ACTIONS = List.of("list", "info", "apply", "export");

    static final List<String> ITEM_ACTIONS = List.of("dump");

    /** The five DSL reference vocabularies {@code /se docs} routes to (each maps to a {@link ReferenceCatalog} category). */
    static final List<String> DOC_VOCABS = List.of("effects", "conditions", "triggers", "selectors", "variables");

    /** Filename-safe stamp for an auto-backup pack name on {@code /se pack apply}. */
    private static final java.time.format.DateTimeFormatter BACKUP_STAMP =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    static final List<String> SET_MEMBERS = List.of("helmet", "chestplate", "leggings", "boots", "weapon");

    private final ContentReloader reloader;
    private final ItemEnchanter enchanter;
    private final Consumer<Player> refreshWorn;
    private final SoulService souls;
    private final Messages messages;
    private final Path migrationTarget;
    private final Path contentRoot; // ADR-0029 /se import writes content/enchants/<key>.yml here
    private final MenuRegistry menus;
    private final ContentHolder content;
    private final java.util.function.Function<String, schema.spec.ParamSpec> migrateSpecs;
    private final feature.crystal.CrystalService crystals;
    private final feature.heroic.HeroicService heroics;
    private final feature.slot.SlotService slots;
    private final feature.scroll.ScrollService scrolls;
    private final feature.book.UnopenedBookService unopenedBooks;
    private final feature.scroll.HolyScrollService holyScrolls;
    private final feature.scroll.NametagService nametags;
    private final feature.trak.TrakService traks;
    private final PackStore packs;
    private final CombatCodec codec;                       // /se item dump — decode the held item's combat state
    private final CarrierCodec carrierCodec;               // /se item dump — book/dust/white-scroll identity marker
    private final java.util.function.IntSupplier baseSlots; // /se item dump — base enchant slots for the max total
    private final ItemStateStore store;                     // /se item dump — probe raw on-item PDC/NBT key presence
    private final feature.compat.Hands hands;               // main-hand read/write for /se enchant|unenchant|item (§4 era seam)
    private final PackGate packGate;                        // /se pack apply pre-flight (ADR-0046); the live fingerprint supplier rides inside it
    private final engine.stores.WhyStore why;               // /se why — the per-player activation flight recorder (ADR-0045)
    private final feature.combat.DamageDebug damageDebug;   // /se damagedebug — the per-hit fold readout (ADR-0050 R3)
    private final java.util.function.Supplier<java.util.List<String>> quarantined; // /se why — live quarantined stable keys (§10)
    private final item.worn.WornStateStore worn;            // /se why — the target's resolved worn gear (not-worn diagnosis)
    private final java.util.function.LongSupplier nowTicks; // /se why — the current game tick, for "N s ago"
    private final MintIo io;                                 // ADR-0047 the mint-delivery handle passed to give/self bodies
    private final List<String> giveTypes;                   // ADR-0047 derived /se give <type> list (canonical then aliases)
    private final Map<String, Mintable> giveIndex;          // ADR-0047 canonical + alias → mintable (/se give dispatch)
    private final Map<String, Mintable> selfIndex;          // ADR-0047 type → mintable with a self() row (/se <type> dispatch)
    private final Supplier<ModuleFold.Report> modulesReport; // /se modules — the frozen fold report (ADR-0047)

    public SeCommand(ContentReloader reloader, ItemEnchanter enchanter, Consumer<Player> refreshWorn,
              SoulService souls, Path migrationTarget, MenuRegistry menus, ContentHolder content,
              java.util.function.Function<String, schema.spec.ParamSpec> migrateSpecs,
              feature.crystal.CrystalService crystals,
              feature.heroic.HeroicService heroics, feature.slot.SlotService slots,
              feature.scroll.ScrollService scrolls, feature.book.UnopenedBookService unopenedBooks,
              feature.scroll.HolyScrollService holyScrolls, feature.scroll.NametagService nametags,
              feature.trak.TrakService traks, PackStore packs, CombatCodec codec, CarrierCodec carrierCodec,
              java.util.function.IntSupplier baseSlots, Messages messages, Path contentRoot, ItemStateStore store,
              feature.compat.Hands hands, PackGate packGate, engine.stores.WhyStore why,
              feature.combat.DamageDebug damageDebug,
              java.util.function.Supplier<java.util.List<String>> quarantined, item.worn.WornStateStore worn,
              java.util.function.LongSupplier nowTicks, List<Mintable> mintables, MintIo io,
              Supplier<ModuleFold.Report> modulesReport) {
        this.reloader = reloader;
        this.enchanter = enchanter;
        this.refreshWorn = refreshWorn;
        this.souls = souls;
        this.messages = messages;
        this.migrationTarget = migrationTarget;
        this.contentRoot = contentRoot;
        this.menus = menus;
        this.content = content;
        this.migrateSpecs = migrateSpecs;
        this.crystals = crystals;
        this.heroics = heroics;
        this.slots = slots;
        this.scrolls = scrolls;
        this.unopenedBooks = unopenedBooks;
        this.holyScrolls = holyScrolls;
        this.nametags = nametags;
        this.traks = traks;
        this.packs = packs;
        this.codec = codec;
        this.carrierCodec = carrierCodec;
        this.baseSlots = baseSlots;
        this.store = store;
        this.hands = hands;
        this.packGate = packGate;
        this.why = why;
        this.damageDebug = damageDebug;
        this.quarantined = quarantined;
        this.worn = worn;
        this.nowTicks = nowTicks;
        this.io = io;
        this.modulesReport = modulesReport;
        // ADR-0047: the three mint surfaces derive from the ONE declaration set — the give-type list, the give
        // dispatch (canonical + aliases), and the /se <type> self-mint dispatch (types with a self() row).
        List<String> types = new ArrayList<>();
        Map<String, Mintable> byGiveKey = new HashMap<>();
        Map<String, Mintable> bySelfType = new HashMap<>();
        for (Mintable mintable : mintables) {
            types.add(mintable.type());
            byGiveKey.put(mintable.type(), mintable);
            if (mintable.self() != null) {
                bySelfType.put(mintable.type(), mintable);
            }
        }
        for (Mintable mintable : mintables) {
            for (String alias : mintable.aliases()) {
                types.add(alias);
                byGiveKey.put(alias, mintable);
            }
        }
        this.giveTypes = List.copyOf(types);
        this.giveIndex = Map.copyOf(byGiveKey);
        this.selfIndex = Map.copyOf(bySelfType);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender, args);
            case "problems" -> problems(sender);
            case "why" -> why(sender, args);
            case "damagedebug" -> toggleDamageDebug(sender);
            case "modules" -> modules(sender);
            case "give" -> give(sender, args);
            case "item" -> itemDump(sender, args);
            case "enchant" -> applyHeld(sender, args);
            case "removeenchant", "unenchant" -> removeHeld(sender, args);
            case "soulmode" -> toggleSoulMode(sender);
            case "split" -> splitSoul(sender, args);
            case "migrate" -> migrate(sender, args);
            case "import" -> importCode(sender, args);
            case "pack" -> pack(sender, args);
            case "menu" -> openMenu(sender, args);
            case "effects" -> reference(sender, ReferenceCatalog.EFFECTS);
            case "selectors" -> reference(sender, ReferenceCatalog.SELECTORS);
            case "triggers" -> reference(sender, ReferenceCatalog.TRIGGERS);
            case "conditions" -> reference(sender, ReferenceCatalog.CONDITIONS);
            case "variables" -> reference(sender, ReferenceCatalog.VARIABLES);
            case "list" -> referenceList(sender);
            case "docs" -> docs(sender, args);
            // The 14 self-mint types (ADR-0047) are no longer hand-cased — each derives from its Mintable's self().
            default -> selfMintOrUsage(sender, args);
        }
        return true;
    }

    /** {@code /se <type>} self-mint dispatch: a type with a declared {@code self()} row mints it, else usage. */
    private void selfMintOrUsage(CommandSender sender, String[] args) {
        Mintable mintable = selfIndex.get(args[0].toLowerCase(Locale.ROOT));
        if (mintable != null && mintable.self() != null) {
            mintable.self().run(sender, args, io);
        } else {
            usage(sender);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        Completions completions = Completions.none()
                .withEnchantKeys(content.library().catalog().stream().map(EnchantDef::key).toList())
                .withCrystalKeys(content.library().crystals().stream().map(CrystalDef::key).toList())
                .withTierNames(content.library().tiers().tiers().stream().map(t -> t.name()).toList())
                .withMenuNames(menus.names())
                .withPlayerNames(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList())
                .withSetKeys(content.library().sets().stream().map(compile.load.SetDef::key).toList())
                .withPackNames(packNamesQuietly())
                // enchant key → max level, so completion can offer the valid levels of a chosen enchant.
                .withEnchantMaxLevels(content.library().catalog().stream()
                        .collect(Collectors.toMap(EnchantDef::key, EnchantDef::maxLevel, (a, b) -> a)))
                .withGiveTypes(giveTypes); // ADR-0047 derived /se give <type> list
        return complete(args, completions);
    }

    /** An I/O hiccup completes to nothing rather than throwing out of tab-completion. */
    private List<String> packNamesQuietly() {
        try {
            return packs.list().stream().map(PackStore.PackInfo::name).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** The tab-completion vocabularies (§J); tests start from {@link #none()} and set only the lists they exercise. */
    record Completions(List<String> enchantKeys, List<String> crystalKeys, List<String> tierNames,
                       List<String> menuNames, List<String> playerNames, List<String> setKeys,
                       List<String> packNames, Map<String, Integer> enchantMaxLevels, List<String> giveTypes) {

        static Completions none() {
            return new Completions(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), Map.of(), List.of());
        }

        Completions withEnchantKeys(List<String> enchantKeys) {
            return new Completions(enchantKeys, crystalKeys, tierNames, menuNames, playerNames, setKeys,
                    packNames, enchantMaxLevels, giveTypes);
        }

        Completions withCrystalKeys(List<String> crystalKeys) {
            return new Completions(enchantKeys, crystalKeys, tierNames, menuNames, playerNames, setKeys,
                    packNames, enchantMaxLevels, giveTypes);
        }

        Completions withTierNames(List<String> tierNames) {
            return new Completions(enchantKeys, crystalKeys, tierNames, menuNames, playerNames, setKeys,
                    packNames, enchantMaxLevels, giveTypes);
        }

        Completions withMenuNames(List<String> menuNames) {
            return new Completions(enchantKeys, crystalKeys, tierNames, menuNames, playerNames, setKeys,
                    packNames, enchantMaxLevels, giveTypes);
        }

        Completions withPlayerNames(List<String> playerNames) {
            return new Completions(enchantKeys, crystalKeys, tierNames, menuNames, playerNames, setKeys,
                    packNames, enchantMaxLevels, giveTypes);
        }

        Completions withSetKeys(List<String> setKeys) {
            return new Completions(enchantKeys, crystalKeys, tierNames, menuNames, playerNames, setKeys,
                    packNames, enchantMaxLevels, giveTypes);
        }

        Completions withPackNames(List<String> packNames) {
            return new Completions(enchantKeys, crystalKeys, tierNames, menuNames, playerNames, setKeys,
                    packNames, enchantMaxLevels, giveTypes);
        }

        Completions withEnchantMaxLevels(Map<String, Integer> enchantMaxLevels) {
            return new Completions(enchantKeys, crystalKeys, tierNames, menuNames, playerNames, setKeys,
                    packNames, enchantMaxLevels, giveTypes);
        }

        /** The derived {@code /se give <type>} list (ADR-0047): canonical mint types then aliases, registry order. */
        Completions withGiveTypes(List<String> giveTypes) {
            return new Completions(enchantKeys, crystalKeys, tierNames, menuNames, playerNames, setKeys,
                    packNames, enchantMaxLevels, giveTypes);
        }
    }

    /**
     * Pure tab-completion (extracted from Bukkit so it is unit-tested without a server): subcommand at
     * {@code args[0]}, then the §J {@code give <type> <player> [type-arg]} tree and the §packs
     * {@code pack <action> [name]} tree. {@code enchantMaxLevels} (enchant key → max level) drives the
     * context-aware level suggestions after an enchant arg in the book/enchant flows.
     */
    static List<String> complete(String[] args, Completions c) {
        List<String> enchantKeys = c.enchantKeys();
        List<String> crystalKeys = c.crystalKeys();
        List<String> tierNames = c.tierNames();
        List<String> menuNames = c.menuNames();
        List<String> playerNames = c.playerNames();
        List<String> setKeys = c.setKeys();
        List<String> packNames = c.packNames();
        Map<String, Integer> enchantMaxLevels = c.enchantMaxLevels();
        List<String> giveTypes = c.giveTypes();
        if (args.length <= 1) {
            return filter(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "give" -> filter(giveTypes, args[1]);
                case "why" -> filter(playerNames, args[1]);
                case "enchant", "book", "removeenchant", "unenchant" -> filter(enchantKeys, args[1]);
                case "crystal" -> filter(crystalKeys, args[1]);
                case "unopened" -> filter(tierNames, args[1]);
                case "menu" -> filter(menuNames, args[1]);
                case "migrate" -> filter(List.of("ee", "ea", "ae"), args[1]);
                case "pack" -> filter(PACK_ACTIONS, args[1]);
                case "reload" -> filter(List.of("--dry-run"), args[1]);
                case "item" -> filter(ITEM_ACTIONS, args[1]);
                case "docs" -> filter(DOC_VOCABS, args[1]);
                default -> List.of();
            };
        }
        if (sub.equals("give") && args.length == 3) {
            return filter(playerNames, args[2]); // /se give <type> <player>
        }
        if (sub.equals("why") && args.length == 3) { // /se why <player> <key> — any content key filters the recorder
            List<String> keys = new ArrayList<>(enchantKeys.size() + setKeys.size() + crystalKeys.size());
            keys.addAll(enchantKeys);
            keys.addAll(setKeys);
            keys.addAll(crystalKeys);
            return filter(keys, args[2]);
        }
        if (sub.equals("pack") && args.length == 3) {
            String action = args[1].toLowerCase(Locale.ROOT); // /se pack <info|apply> <name>
            return action.equals("info") || action.equals("apply") ? filter(packNames, args[2]) : List.of();
        }
        if (sub.equals("pack") && args.length == 4 && args[1].equalsIgnoreCase("apply")) {
            return filter(List.of("--force"), args[3]); // /se pack apply <name> --force (only apply offers the flag)
        }
        // /se book <enchant> <level> and /se enchant <key> <level> — offer the chosen enchant's valid levels.
        if (args.length == 3 && (sub.equals("book") || sub.equals("enchant"))) {
            return filter(levelsUpTo(maxLevelFor(args[1], enchantMaxLevels)), args[2]);
        }
        if (sub.equals("give") && args.length == 4) {
            return switch (args[1].toLowerCase(Locale.ROOT)) { // the type-specific key
                case "crystal" -> filter(crystalKeys, args[3]);
                case "book" -> filter(concat("random", enchantKeys), args[3]); // book key, or the `random` form
                case "unopened" -> filter(tierNames, args[3]);
                case "set" -> filter(setKeys, args[3]);
                default -> List.of();
            };
        }
        if (sub.equals("give") && args.length == 5) {
            if (args[1].equalsIgnoreCase("book")) {
                return args[3].equalsIgnoreCase("random")
                        ? filter(tierNames, args[4])                                          // give book <player> random <tier>
                        : filter(levelsUpTo(maxLevelFor(args[3], enchantMaxLevels)), args[4]); // give book <player> <enchant> <level>
            }
            if (args[1].equalsIgnoreCase("set")) {
                return filter(SET_MEMBERS, args[4]); // /se give set <player> <set> <member>
            }
        }
        return List.of();
    }

    /** The candidate level numerals {@code "1".."max"} for an enchant (empty when the enchant/levels are unknown). */
    private static List<String> levelsUpTo(int max) {
        List<String> out = new ArrayList<>(Math.max(0, max));
        for (int level = 1; level <= max; level++) {
            out.add(Integer.toString(level));
        }
        return out;
    }

    /** Max level for a typed enchant arg, prefix-normalised to the {@code enchants/<x>} map keys; 0 if unknown. */
    private static int maxLevelFor(String enchantArg, Map<String, Integer> enchantMaxLevels) {
        return enchantMaxLevels.getOrDefault(normalize(enchantArg, "enchants/"), 0);
    }

    private static List<String> concat(String head, List<String> rest) {
        List<String> out = new java.util.ArrayList<>(rest.size() + 1);
        out.add(head);
        out.addAll(rest);
        return out;
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(candidate);
            }
        }
        return out;
    }

    private void toggleSoulMode(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        // SoulService sends the ENABLED/DISABLED feedback; only the no-gem case (soul.empty) is the command's.
        Scheduling.onEntity(player, () -> {
            if (souls.toggle(player) == SoulService.Toggle.NO_GEM) {
                messages.lines("soul.empty").forEach(player::sendMessage);
            }
        });
    }

    /** Toggle the per-hit damage-fold readout for the sender: {@code /se damagedebug} (ADR-0050 R3). */
    private void toggleDamageDebug(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        boolean on = damageDebug.toggle(player.getUniqueId());
        sender.sendMessage(platform.text.Colors.translate(on
                ? "&8[&6dmg&8] &7Per-hit damage readout &aENABLED&7 — every hit you land or take prints its fold."
                : "&8[&6dmg&8] &7Per-hit damage readout &cDISABLED&7."));
    }

    /** Split souls off the held gem into a new gem: {@code /se split <amount>} (never auto-split, §D). */
    private void splitSoul(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.format("command.soul.split-usage"));
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException bad) {
            sender.sendMessage(messages.format("command.error.bad-number", "ARG", args[1]));
            return;
        }
        Scheduling.onEntity(player, () -> {
            SoulService.SplitResult result = souls.split(player, amount);
            switch (result.status()) {
                case OK -> player.sendMessage(messages.format("command.soul.split-ok",
                        "MOVED", result.moved(), "REMAINING", result.remaining()));
                case NO_GEM -> player.sendMessage(messages.format("command.soul.no-gem"));
                case BAD_AMOUNT -> player.sendMessage(messages.format("command.soul.split-bad"));
                case TOO_MANY -> player.sendMessage(messages.format("command.soul.split-too-many",
                        "REMAINING", result.remaining()));
                default -> { /* unreachable */ }
            }
        });
    }

    private void reload(CommandSender sender, String[] args) {
        boolean dryRun = args.length >= 2 && args[1].equalsIgnoreCase("--dry-run");
        sender.sendMessage(messages.format("command.reload.start", "MODE", dryRun ? "checking" : "reloading"));
        if (dryRun) {
            reloader.dryRun(result -> report(sender, result));
        } else {
            reloader.reload(result -> report(sender, result));
        }
    }

    /** The menu {@code /se menu} (no name) opens — the operator console hub (ADR-0030). */
    private static final String DEFAULT_MENU = "console";

    private void openMenu(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        String name = args.length >= 2 ? args[1] : DEFAULT_MENU;
        Menu menu = menus.get(name).orElse(null);
        if (menu == null) {
            sender.sendMessage(messages.format("command.menu.unknown",
                    "NAME", name, "AVAILABLE", String.join(", ", menus.names())));
            return;
        }
        String perm = menu.permission();
        if (perm != null && !player.hasPermission(perm)) {
            sender.sendMessage(messages.format("command.menu.no-permission"));
            return;
        }
        menu.open(player);
    }

    /**
     * {@code /se <effects|selectors|triggers|conditions|variables>} — a one-line dump of one reference
     * category (§J/§M); the same live vocabularies the browser GUI and {@code docs/reference/} read.
     */
    private void reference(CommandSender sender, String category) {
        ReferenceCatalog catalog = ReferenceCatalog.build();
        List<ReferenceCatalog.Entry> entries = catalog.entries(category);
        String items = entries.stream().map(ReferenceCatalog.Entry::title).collect(Collectors.joining("&7, &f"));
        sender.sendMessage(messages.format("command.reference.header",
                "CATEGORY", category, "COUNT", entries.size(), "ITEMS", items));
    }

    /** {@code /se list} — the reference categories + counts, a directory to the per-category dumps. */
    private void referenceList(CommandSender sender) {
        ReferenceCatalog catalog = ReferenceCatalog.build();
        String cats = catalog.categories().stream()
                .map(c -> c + "&7(" + catalog.entries(c).size() + ")&f")
                .collect(Collectors.joining("&7, &f"));
        sender.sendMessage(messages.format("command.reference.list", "CATEGORIES", cats));
    }

    /** {@code /se migrate <ee|ea|ae> <sourcePath>} — import legacy configs into the migrated/ folder for review. */
    private void migrate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.lines("command.migrate.usage").forEach(sender::sendMessage);
            return;
        }
        String format = args[1].toLowerCase(Locale.ROOT);
        Path source = Path.of(args[2]);
        sender.sendMessage(messages.format("command.migrate.start", "FORMAT", format, "SOURCE", source));
        // File I/O off the command thread; migrateSpecs writes effects in the verbose v2 form.
        Scheduling.async(() -> {
            try {
                Migrator.Result result = switch (format) {
                    case "ee" -> Migrator.eliteEnchantments(Files.readString(source, StandardCharsets.UTF_8), migrateSpecs);
                    case "ea" -> migrateArmorDir(source, migrateSpecs);
                    case "ae" -> Migrator.advancedEnchantments(Files.readString(source, StandardCharsets.UTF_8), migrateSpecs);
                    default -> null;
                };
                if (result == null) {
                    tell(sender, messages.format("command.migrate.unknown-format", "FORMAT", format));
                    return;
                }
                int written = result.writeTo(migrationTarget);
                int skipped = result.files().size() - written;
                tell(sender, messages.format("command.migrate.done", "N", result.files().size(),
                        "WRITTEN", written, "SKIPPED", skipped, "NOTES", result.diagnostics().size()));
                tell(sender, messages.format("command.migrate.review", "TARGET", migrationTarget));
            } catch (IOException e) {
                tell(sender, messages.format("command.migrate.failed", "SOURCE", source, "ERROR", e.getMessage()));
            }
        });
    }

    /**
     * {@code /se import <code>} (ADR-0029) — apply an enchant built in the web creator from its {@code SE1:}
     * paste code: decode it, validate it through the SAME compiler {@code /se reload --dry-run} uses, and —
     * only if clean — write {@code content/enchants/<key>.yml} (overwriting, so editing an existing enchant
     * works) and hot-swap via the transactional reloader. Any decode/validation failure leaves disk untouched.
     */
    private void importCode(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.lines("command.import.usage").forEach(sender::sendMessage);
            return;
        }
        ImportCode.Envelope envelope;
        try {
            envelope = ImportCode.decode(args[1]);
        } catch (ImportCode.DecodeException bad) {
            sender.sendMessage(messages.format("command.import.bad-code", "ERROR", bad.getMessage()));
            return;
        }
        String key = envelope.key();
        String yaml = ImportCode.toYaml(envelope.content());
        String relative = "enchants/" + key + ".yml";
        sender.sendMessage(messages.format("command.import.start", "KEY", key));
        // Validate (off-thread, throwaway tree) BEFORE writing: a faulty content reports its diagnostics
        // and leaves the live content/ folder untouched. Only a clean candidate is written + reloaded.
        Scheduling.async(() -> {
            ReloadResult validation = reloader.validateCandidate(relative, yaml);
            if (validation.errorCount() != 0) {
                tell(sender, messages.format("command.import.invalid", "KEY", key, "N", validation.errorCount()));
                for (Diagnostic diagnostic : validation.diagnostics()) {
                    if (diagnostic.blocking()) {
                        tell(sender, messages.format("command.reload.error-line", "DIAGNOSTIC", diagnostic));
                    }
                }
                return;
            }
            try {
                Path target = contentRoot.resolve(relative);
                Files.createDirectories(target.getParent());
                Files.writeString(target, yaml, StandardCharsets.UTF_8);
            } catch (IOException io) {
                tell(sender, messages.format("command.import.write-failed", "KEY", key, "ERROR", io.getMessage()));
                return;
            }
            int levels = levelCount(envelope.content());
            // Same transactional hot-swap as /se reload — re-validates the whole tree and publishes atomically.
            reloader.reload(result -> {
                report(sender, result);
                if (result.published()) {
                    tell(sender, messages.format("command.import.done", "KEY", key, "LEVELS", levels));
                }
            });
        });
    }

    /** The number of declared levels in a decoded enchant {@code content} map (0 if none/malformed). */
    private static int levelCount(java.util.Map<String, Object> content) {
        return content.get("levels") instanceof java.util.Map<?, ?> levels ? levels.size() : 0;
    }

    /** Migrate every {@code *.yml} in an EliteArmor armour directory, merging the per-set results. */
    private static Migrator.Result migrateArmorDir(Path dir,
            java.util.function.Function<String, schema.spec.ParamSpec> specs) throws IOException {
        java.util.Map<String, String> files = new java.util.LinkedHashMap<>();
        schema.diag.Diagnostics diagnostics = new schema.diag.Diagnostics();
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path file : entries.filter(p -> p.toString().endsWith(".yml")).sorted().toList()) {
                String id = file.getFileName().toString().replaceFirst("\\.yml$", "");
                Migrator.Result one = Migrator.eliteArmorSet(id, Files.readString(file, StandardCharsets.UTF_8), specs);
                files.putAll(one.files());
                diagnostics.merge(one.diagnostics());
            }
        }
        return new Migrator.Result(files, diagnostics);
    }

    /**
     * {@code /se pack <list|info|apply|export>} (ADR-0023) — config packs are a ZIP snapshot of the whole
     * config surface (config.yml, lang.yml, content/, items/, menus/). All filesystem work is off-thread.
     */
    private void pack(CommandSender sender, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "list" -> packList(sender);
            case "info" -> packInfo(sender, args);
            case "apply" -> packApply(sender, args);
            case "export" -> packExport(sender, args);
            default -> messages.lines("command.pack.usage").forEach(sender::sendMessage);
        }
    }

    /** {@code /se pack list} — every pack in {@code packs/}, with its description + file count. */
    private void packList(CommandSender sender) {
        Scheduling.async(() -> {
            try {
                List<PackStore.PackInfo> available = packs.list();
                if (available.isEmpty()) {
                    tell(sender, messages.format("command.pack.empty"));
                    return;
                }
                tell(sender, messages.format("command.pack.list-header", "COUNT", available.size()));
                for (PackStore.PackInfo info : available) {
                    tell(sender, messages.format("command.pack.list-entry", "NAME", info.name(),
                            "DESC", info.manifest().description(), "FILES", info.manifest().fileCount()));
                }
            } catch (IOException e) {
                tell(sender, messages.format("command.pack.error", "ERROR", String.valueOf(e.getMessage())));
            }
        });
    }

    /** {@code /se pack info <name>} — the manifest of one pack. */
    private void packInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.lines("command.pack.usage").forEach(sender::sendMessage);
            return;
        }
        String name = args[2];
        Scheduling.async(() -> {
            try {
                var manifest = packs.info(name).orElse(null);
                if (manifest == null) {
                    tell(sender, messages.format("command.pack.unknown", "NAME", name));
                    return;
                }
                tell(sender, messages.format("command.pack.info", "NAME", manifest.name(),
                        "DESC", manifest.description(), "AUTHOR", manifest.author(),
                        "CREATED", manifest.created(), "FILES", manifest.fileCount()));
                if (!manifest.fingerprint().isEmpty()) {
                    // Whole-line keys (not a composed COMPAT fragment) so the two states stay translatable as wholes.
                    String key = manifest.fingerprint().equals(packGate.liveFingerprint())
                            ? "command.pack.info-surface-match" : "command.pack.info-surface-differs";
                    tell(sender, messages.format(key, "SURFACE", manifest.surface(),
                            "FP", RegistryFingerprint.shortForm(manifest.fingerprint())));
                }
            } catch (IOException e) {
                tell(sender, messages.format("command.pack.error", "ERROR", String.valueOf(e.getMessage())));
            }
        });
    }

    /**
     * {@code /se pack apply <name> [--force]} — pre-flight the pack against the live authoring surface
     * (ADR-0046): compare fingerprints then dry-run compile the WHOLE surface; abort untouched on any
     * blocking fault unless {@code --force}. On a clean/forced apply: back up the current config, swap in
     * the pack, reload transactionally. Boot-gated wiring (souls/slots/scrolls listeners, integration
     * toggles, command-trigger) still needs a restart — reported in the apply note.
     */
    private void packApply(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.lines("command.pack.usage").forEach(sender::sendMessage);
            return;
        }
        String name = args[2];
        if (!PackStore.isValidName(name)) {
            sender.sendMessage(messages.format("command.pack.bad-name", "NAME", name));
            return;
        }
        if (!packs.exists(name)) {
            sender.sendMessage(messages.format("command.pack.unknown", "NAME", name));
            return;
        }
        boolean force = Arrays.stream(args).skip(3).anyMatch(a -> a.equalsIgnoreCase("--force"));
        sender.sendMessage(messages.format("command.pack.apply-start", "NAME", name));
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String createdIso = now.toString();
        String backupLabel = "backup-" + now.format(BACKUP_STAMP);
        Scheduling.async(() -> {
            try {
                Pack pack = packs.load(name).orElseThrow(() -> new IOException("no such pack '" + name + "'"));
                // Pre-flight in the staging dir BEFORE any disk mutation; the report explains + decides.
                PackGate.Report gateReport = packGate.check(pack);
                for (String line : gateLines(gateReport, messages, name, force)) {
                    tell(sender, line);
                }
                if (!gateReport.clean() && !force) {
                    return; // disk untouched, no backup, no reload
                }
                // Backup stamps the fingerprint AND surface from the gate-time snapshot (one registry read),
                // not a second live walk — the two describe the same instant.
                PackStore.ApplyResult applied = packs.apply(name, backupLabel, createdIso,
                        gateReport.liveFingerprint(), gateReport.liveSurface());
                tell(sender, messages.format("command.pack.apply-done", "NAME", applied.manifest().name(),
                        "FILES", applied.fileCount(),
                        "BACKUP", applied.hasBackup() ? applied.backupName() : "(none)"));
                if (!applied.skipped().isEmpty()) {
                    tell(sender, messages.format("command.pack.apply-skipped", "N", applied.skipped().size()));
                }
                // Same transactional reload as /se reload: a faulty pack keeps the previous in-memory
                // state and reports its diagnostics here (so a forced erroring apply holds disk-vs-memory split).
                reloader.reload(result -> report(sender, result));
                tell(sender, messages.format("command.pack.apply-note"));
            } catch (IOException e) {
                tell(sender, messages.format("command.pack.error", "ERROR", String.valueOf(e.getMessage())));
            }
        });
    }

    /**
     * Pure gate-report rendering for {@code /se pack apply} (ADR-0046); the async caller sends the lines.
     * The fingerprint line is the fast explanation (nothing on MATCH — the compile result speaks); errors
     * render EVERY blocking diagnostic (no cap, the {@code report()} precedent) via the reused
     * {@code command.reload.error-line}, warnings a count only, then the abort/forced verdict.
     */
    static List<String> gateLines(PackGate.Report report, Messages messages, String name, boolean force) {
        List<String> lines = new ArrayList<>();
        switch (report.status()) {
            case MISMATCH -> lines.add(messages.format("command.pack.gate-mismatch",
                    "PACK", RegistryFingerprint.shortForm(report.packFingerprint()),
                    "LIVE", RegistryFingerprint.shortForm(report.liveFingerprint())));
            case UNSTAMPED -> lines.add(messages.format("command.pack.gate-unstamped"));
            case MATCH -> { /* a match needs no explanation line — the compile result speaks */ }
            default -> { }
        }
        if (report.clean()) {
            if (report.warningCount() == 0) {
                lines.add(messages.format("command.pack.gate-clean", "ABILITIES", report.abilityCount()));
            } else {
                lines.add(messages.format("command.pack.gate-warnings", "N", report.warningCount()));
            }
            return lines;
        }
        List<Diagnostic> blocking = report.blocking();
        lines.add(messages.format("command.pack.gate-errors", "N", blocking.size()));
        for (Diagnostic diagnostic : blocking) {
            lines.add(messages.format("command.reload.error-line", "DIAGNOSTIC", diagnostic.render()));
        }
        if (report.warningCount() > 0) {
            lines.add(messages.format("command.pack.gate-warnings", "N", report.warningCount()));
        }
        lines.add(force ? messages.format("command.pack.gate-forced")
                : messages.format("command.pack.gate-abort", "NAME", name));
        return lines;
    }

    /** {@code /se pack export <name> [description…]} — snapshot the live config into a new pack. */
    private void packExport(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.lines("command.pack.usage").forEach(sender::sendMessage);
            return;
        }
        String name = args[2];
        if (!PackStore.isValidName(name)) {
            sender.sendMessage(messages.format("command.pack.bad-name", "NAME", name));
            return;
        }
        String description = args.length > 3
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length))
                : "Exported config snapshot.";
        String author = sender.getName();
        String createdIso = java.time.LocalDateTime.now().toString();
        Scheduling.async(() -> {
            try {
                // Fingerprint the live registry off the command thread (hashing walks the registries).
                PackStore.ExportResult result = packs.export(name, description, author, createdIso,
                        packGate.liveFingerprint(), packGate.liveSurface());
                tell(sender, messages.format("command.pack.export-done", "NAME", result.name(),
                        "FILES", result.fileCount()));
            } catch (IOException e) {
                tell(sender, messages.format("command.pack.error", "ERROR", String.valueOf(e.getMessage())));
            }
        });
    }

    /** Message the sender, routing a {@link Player} to its own region thread (Folia-correct). */
    private static void tell(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            Scheduling.onEntity(player, () -> player.sendMessage(message));
        } else {
            sender.sendMessage(message);
        }
    }

    /** Apply an enchant to the sender's held item (admin force-give), on the sender's own thread. */
    private void applyHeld(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.format("command.enchant.usage"));
            return;
        }
        String key = normalize(args[1], "enchants/");
        int level = 1;
        if (args.length >= 3) {
            try {
                level = Integer.parseInt(args[2]);
            } catch (NumberFormatException bad) {
                sender.sendMessage(messages.format("command.error.bad-level", "ARG", args[2]));
                return;
            }
        }
        int appliedLevel = level;
        Scheduling.onEntity(player, () -> {
            ItemStack held = hands.mainHand(player);
            // /se enchant is admin force-give → bypass the §G requires/blacklist relationship gates.
            ApplyResult result = enchanter.applyEnchant(held, key, appliedLevel, false);
            if (result.ok()) {
                hands.setMainHand(player, held);
                // Mutating the held item in place fires no equip event; re-resolve or the new enchant
                // sits in PDC + lore but inert until a re-equip.
                refreshWorn.accept(player);
            }
            player.sendMessage(result.message());
        });
    }

    /**
     * {@code /se give <type> <player> [type-args…]} — resolve the target, then delegate to the type's declared
     * {@link Mintable} (ADR-0047 §7). The per-type give bodies moved verbatim into the module {@code Mints}; an
     * unknown type falls to the usage message exactly as the old {@code default} arm did.
     */
    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.format("command.give.usage"));
            return;
        }
        Player target = resolveTarget(sender, args[2]);
        if (target == null) {
            return; // resolveTarget messaged the sender
        }
        Mintable mintable = giveIndex.get(args[1].toLowerCase(Locale.ROOT));
        if (mintable == null) {
            sender.sendMessage(messages.format("command.give.usage"));
            return;
        }
        mintable.give(sender, target, args, io);
    }

    /** Resolve an online-player target by exact name, messaging the sender if none matches. */
    private Player resolveTarget(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            sender.sendMessage(messages.format("command.error.no-such-player", "PLAYER", name));
        }
        return target;
    }

    /** {@code /se removeenchant <enchant>} / {@code unenchant} — strip an enchant from the sender's held item. */
    private void removeHeld(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.format("command.removeenchant.usage"));
            return;
        }
        String key = normalize(args[1], "enchants/");
        Scheduling.onEntity(player, () -> {
            ItemStack held = hands.mainHand(player);
            ApplyResult result = enchanter.removeEnchant(held, key);
            if (result.ok()) {
                hands.setMainHand(player, held);
                refreshWorn.accept(player); // in-place mutation fires no equip event — re-resolve WornState
            }
            player.sendMessage(result.message());
        });
    }

    /** Prepend the namespace prefix when the operator typed a short key (no {@code /}). */
    private static String normalize(String key, String prefix) {
        return key.indexOf('/') >= 0 ? key : prefix + key;
    }

    /** {@code /se} / {@code /se help} — the command list, rendered from {@link #COMMANDS} so it can never drift. */
    private void usage(CommandSender sender) {
        sender.sendMessage(messages.format("command.help.header"));
        for (CommandInfo info : COMMANDS) {
            String usage = info.args().isEmpty() ? info.name() : info.name() + " " + info.args();
            sender.sendMessage(messages.format("command.help.entry", "USAGE", usage, "DESCRIPTION", info.description()));
        }
    }

    /**
     * Report a reload result. The reloader's callback fires on the GLOBAL thread; a {@link Player}
     * sender is region-owned, so its messages route to its own thread. Console is fine inline.
     */
    private void report(CommandSender sender, ReloadResult result) {
        List<String> lines = format(result);
        if (sender instanceof Player player) {
            Scheduling.onEntity(player, () -> lines.forEach(player::sendMessage));
        } else {
            lines.forEach(sender::sendMessage);
        }
    }

    /** How many non-blocking warnings {@code /se reload} lists inline before deferring the rest to {@code /se problems}. */
    private static final int RELOAD_WARNING_PREVIEW = 5;

    private List<String> format(ReloadResult result) {
        List<String> lines = new ArrayList<>();
        if (result.errorCount() == 0) {
            lines.add(messages.format("command.reload.loaded",
                    "VERB", result.dryRun() ? "would load" : "loaded",
                    "COUNT", result.abilityCount(), "GEN", result.generation()));
        } else {
            lines.add(messages.format("command.reload.errors-header", "N", result.errorCount()));
            for (Diagnostic diagnostic : result.diagnostics()) {
                if (diagnostic.blocking()) {
                    lines.add(messages.format("command.reload.error-line", "DIAGNOSTIC", diagnostic.render()));
                }
            }
        }
        // Warnings never block the swap, so they were silent before — surface a preview so a degraded-but-loaded
        // reload is visible; the full list stays on /se problems.
        List<Diagnostic> warnings = result.diagnostics().stream().filter(d -> !d.blocking()).toList();
        if (!warnings.isEmpty()) {
            lines.add(messages.format("command.reload.warnings-header", "N", warnings.size()));
            for (int i = 0; i < warnings.size() && i < RELOAD_WARNING_PREVIEW; i++) {
                lines.add(messages.format("command.reload.warning-line", "DIAGNOSTIC", warnings.get(i).render()));
            }
            if (warnings.size() > RELOAD_WARNING_PREVIEW) {
                lines.add(messages.format("command.reload.warnings-more", "N", warnings.size() - RELOAD_WARNING_PREVIEW));
            }
        }
        return lines;
    }

    /**
     * {@code /se problems} — the errors + warnings from the LAST content load, read live from the published
     * {@link compile.load.Library} (its diagnostics are retained after the swap). Sent inline like the other
     * chat dumps; the formatting is the pure, unit-tested {@link #problemLines}.
     */
    private void problems(CommandSender sender) {
        problemLines(content.library().diagnostics(), messages).forEach(sender::sendMessage);
    }

    /**
     * {@code /se modules} — render the frozen {@link ModuleFold.Report} (ADR-0047): every feature module in
     * registry order with its toggle depth/state, wired listeners, dynamic commands, mint types, menus, swept
     * player stores and disable stops. The report is captured at wire time (listeners cannot re-register mid-run);
     * LIVE toggle states are read live at render. Runs on the command thread — the pure {@link ModulesRender}.
     */
    private void modules(CommandSender sender) {
        ModulesRender.lines(modulesReport.get(), messages).forEach(sender::sendMessage);
    }

    /**
     * {@code /se why <player> [key]} — render the player's recent activation attempts from the flight recorder
     * (ADR-0045). Runs on the global/command thread: it reads only the copied ring, the immutable
     * {@link compile.model.Snapshot}, the immutable {@link item.worn.WornState}, and thread-safe name lookups —
     * no entity state is touched, so no Folia scheduling hop is needed.
     */
    private void why(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.format("command.why.usage"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]); // rings are per-session; quit sweeps them
        if (target == null) {
            sender.sendMessage(messages.format("command.why.no-player", "NAME", args[1]));
            return;
        }
        String filter = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : null;
        WhyRender.lines(why.attempts(target.getUniqueId()), content.snapshot(), nowTicks.getAsLong(),
                filter, quarantined.get(), worn.get(target.getUniqueId()), target.getName(), messages)
                .forEach(sender::sendMessage);
    }

    /** Pure {@code /se problems} rendering (server-free): a summary line, then each finding as {@code file:line severity[code] message}. */
    static List<String> problemLines(List<Diagnostic> diagnostics, Messages messages) {
        List<String> lines = new ArrayList<>();
        if (diagnostics.isEmpty()) {
            lines.add(messages.format("command.problems.none"));
            return lines;
        }
        long errors = diagnostics.stream().filter(Diagnostic::blocking).count();
        lines.add(messages.format("command.problems.summary", "ERRORS", errors, "WARNINGS", diagnostics.size() - errors));
        for (Diagnostic diagnostic : diagnostics) {
            String key = diagnostic.blocking() ? "command.problems.error-line" : "command.problems.warning-line";
            lines.add(messages.format(key, "DIAGNOSTIC", diagnostic.render()));
        }
        return lines;
    }

    /**
     * {@code /se docs [vocabulary]} — the umbrella over the five DSL reference vocabularies. No arg (or an
     * unknown one) prints the index; a known vocabulary delegates to the same per-vocabulary {@link #reference}
     * dump {@code /se effects|conditions|triggers|selectors|variables} use (pure routing, no new rendering).
     */
    private void docs(CommandSender sender, String[] args) {
        String category = args.length >= 2 ? docsCategory(args[1]) : null;
        if (category == null) {
            messages.lines("command.docs.index").forEach(sender::sendMessage);
            return;
        }
        reference(sender, category);
    }

    /** The {@link ReferenceCatalog} category a {@code /se docs <vocabulary>} arg routes to, or {@code null} if unknown. */
    static String docsCategory(String vocabulary) {
        return switch (vocabulary.toLowerCase(Locale.ROOT)) {
            case "effects" -> ReferenceCatalog.EFFECTS;
            case "conditions" -> ReferenceCatalog.CONDITIONS;
            case "triggers" -> ReferenceCatalog.TRIGGERS;
            case "selectors" -> ReferenceCatalog.SELECTORS;
            case "variables" -> ReferenceCatalog.VARIABLES;
            default -> null;
        };
    }

    /**
     * {@code /se item dump} — print the decoded StarEnchants state of the held item: combat state (enchants,
     * slots, crystals, set, heroic) plus the identity markers and versioned {@code se:*} PDC keys present.
     * The combat section is the pure, unit-tested {@link #combatLines}; markers/keys read the item directly.
     */
    private void itemDump(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("dump")) {
            sender.sendMessage(messages.format("command.item.usage"));
            return;
        }
        Scheduling.onEntity(player, () -> {
            ItemStack held = hands.mainHand(player);
            if (held == null || held.getType() == org.bukkit.Material.AIR) {
                player.sendMessage(messages.format("command.dump.empty-hand"));
                return;
            }
            CombatState combat = codec.read(held);
            List<String> markers = dumpMarkers(held);
            List<String> pdcKeys = dumpPdcKeys(held);
            if (combat.isEmpty() && markers.isEmpty() && pdcKeys.isEmpty()) {
                player.sendMessage(messages.format("command.dump.no-data"));
                return;
            }
            player.sendMessage(messages.format("command.dump.header", "ITEM", held.getType().name()));
            if (combat.isEmpty()) {
                player.sendMessage(messages.format("command.dump.combat-none"));
            } else {
                combatLines(combat, baseSlots.getAsInt(), messages).forEach(player::sendMessage);
            }
            if (!markers.isEmpty()) {
                player.sendMessage(messages.format("command.dump.markers", "MARKERS", String.join(", ", markers)));
            }
            if (!pdcKeys.isEmpty()) {
                player.sendMessage(messages.format("command.dump.pdc-keys", "KEYS", String.join(", ", pdcKeys)));
            }
        });
    }

    /** Pure combat-state dump (server-free): enchants, slot accounting ({@code base + added} = max, used = enchant count), crystals, set, heroic. */
    static List<String> combatLines(CombatState combat, int baseSlots, Messages messages) {
        List<String> lines = new ArrayList<>();
        if (combat.enchants().isEmpty()) {
            lines.add(messages.format("command.dump.enchants-none"));
        } else {
            lines.add(messages.format("command.dump.enchants-header", "COUNT", combat.enchants().size()));
            combat.enchants().forEach((key, level) ->
                    lines.add(messages.format("command.dump.enchant", "KEY", key, "LEVEL", level)));
        }
        lines.add(messages.format("command.dump.slots",
                "USED", combat.enchants().size(), "MAX", baseSlots + combat.added(), "ADDED", combat.added()));
        if (combat.crystals().isEmpty()) {
            lines.add(messages.format("command.dump.crystals-none"));
        } else {
            lines.add(messages.format("command.dump.crystals", "KEYS", String.join(", ", combat.crystals())));
        }
        if (combat.setKey() != null) {
            lines.add(messages.format("command.dump.set", "SET", combat.setKey()));
        }
        if (combat.setWeaponKey() != null) {
            lines.add(messages.format("command.dump.set-weapon", "SET", combat.setWeaponKey()));
        }
        if (combat.omni()) {
            lines.add(messages.format("command.dump.omni"));
        }
        if (!combat.heroic().isZero()) {
            HeroicStat heroic = combat.heroic();
            lines.add(messages.format("command.dump.heroic",
                    "DMG", pct(heroic.percentDamage()), "RED", pct(heroic.percentReduction()),
                    "DUR", pct(heroic.durability()), "FLATDMG", heroic.flatDamage(), "FLATRED", heroic.flatReduction()));
        }
        return lines;
    }

    /** A fraction rendered as a whole-number percent (e.g. {@code 0.10} → {@code 10}). */
    private static long pct(double fraction) {
        return Math.round(fraction * 100.0);
    }

    /** The identity/economy item kinds present on {@code held}, as structural labels (never user-facing copy). */
    private List<String> dumpMarkers(ItemStack held) {
        List<String> markers = new ArrayList<>();
        if (souls.isGem(held)) {
            markers.add("soul-gem");
        }
        if (crystals.isCrystal(held)) {
            markers.add("crystal");
        }
        if (crystals.isExtractor(held)) {
            markers.add("crystal-extractor");
        }
        if (heroics.isUpgrade(held)) {
            markers.add("heroic-upgrade");
        }
        if (slots.isSlotItem(held)) {
            markers.add("slot-orb");
        }
        if (scrolls.isScroll(held)) {
            markers.add("scroll");
        }
        if (scrolls.isGodlyTransmog(held)) {
            markers.add("godly-transmog");
        }
        if (holyScrolls.isHolyScroll(held)) {
            markers.add("holy-scroll");
        }
        if (nametags.isNametag(held)) {
            markers.add("nametag");
        }
        if (traks.isTrakGem(held)) {
            markers.add("trak-gem");
        }
        if (unopenedBooks.isUnopened(held)) {
            markers.add("unopened-book");
        }
        if (carrierCodec.read(held) != null) {
            markers.add("carrier");
        }
        return markers;
    }

    /** The logical {@code se:*} PDC keys physically present on {@code held} — probed across the blob/flag/int stores. */
    private List<String> dumpPdcKeys(ItemStack held) {
        ItemKeys keys = ItemKeys.of();
        List<String> present = new ArrayList<>();
        for (String key : List.of(keys.combat(), keys.soul(), keys.carrier(), keys.guarded(), keys.crystalItem(),
                keys.crystalExtractor(), keys.heroicUpgrade(), keys.slotItem(), keys.slotSuccess(), keys.scroll(),
                keys.scrollConvert(), keys.unopened(), keys.godlyTransmog(), keys.appliedSlot(), keys.trakGem(),
                keys.trakBlocks(), keys.trakMobs(), keys.trakSouls(), keys.trakFish())) {
            if (store.read(held, key) != null
                    || store.hasByte(held, key) || store.hasInt(held, key)) {
                present.add(key);
            }
        }
        return present;
    }
}
