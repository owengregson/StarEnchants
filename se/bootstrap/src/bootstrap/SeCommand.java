package bootstrap;

import compile.load.ContentHolder;
import compile.load.CrystalDef;
import compile.load.EnchantDef;
import feature.apply.ApplyResult;
import feature.apply.ItemEnchanter;
import feature.imports.ImportCode;
import feature.menu.Menu;
import feature.menu.MenuItems;
import feature.menu.MenuRegistry;
import feature.menu.ReferenceCatalog;
import org.bukkit.Bukkit;
import feature.soul.SoulService;
import item.lang.Messages;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import migrate.Migrator;
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
 */
public final class SeCommand implements CommandExecutor, TabCompleter {

    /**
     * Every {@code /se} subcommand. The single source for {@link #SUBCOMMANDS} (completion) and the generated
     * command docs ({@code website/src/data/surface.json}, via {@code SurfaceCatalogDriftTest}). Order matches
     * the dispatch switch; adding a command here keeps completion + docs in step.
     */
    static final List<CommandInfo> COMMANDS = List.of(
            CommandInfo.of("reload", "[--dry-run]", "Rebuild the content library off-thread and hot-swap it in (or just validate)."),
            CommandInfo.of("give", "<type> <player> [args]", "Give any mintable item (book, scroll, dust, gem, orb, crystal, set piece, heroic…) to a player."),
            CommandInfo.of("enchant", "<key> [level]", "Apply an enchant to the held item (admin; bypasses apply rules)."),
            CommandInfo.of("removeenchant", "<key>", "Strip an enchant from the held item."),
            CommandInfo.alias("unenchant", "removeenchant"),
            CommandInfo.of("crystal", "<key>", "Mint a socketable crystal to yourself."),
            CommandInfo.of("heroic", "", "Mint a Heroic upgrade item to yourself."),
            CommandInfo.of("orb", "", "Mint a slot-expander orb to yourself."),
            CommandInfo.of("gem", "", "Mint a soul gem to yourself."),
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
            CommandInfo.of("pack", "<list|info|apply|export> [name]", "Manage config-pack ZIP snapshots of your whole setup."),
            CommandInfo.of("menu", "[name]", "Open an in-game GUI (enchanter, alchemist, tinkerer, transmog, browsers)."),
            CommandInfo.of("effects", "", "Browse the effect reference in chat."),
            CommandInfo.of("selectors", "", "Browse the selector reference in chat."),
            CommandInfo.of("triggers", "", "Browse the trigger reference in chat."),
            CommandInfo.of("conditions", "", "Browse the conditions reference in chat."),
            CommandInfo.of("variables", "", "Browse the condition variables in chat."),
            CommandInfo.of("list", "", "List every enchant, set, and crystal."));

    static final List<String> SUBCOMMANDS = COMMANDS.stream().map(CommandInfo::name).toList();

    static final List<String> PACK_ACTIONS = List.of("list", "info", "apply", "export");

    /** Filename-safe stamp for an auto-backup pack name on {@code /se pack apply}. */
    private static final java.time.format.DateTimeFormatter BACKUP_STAMP =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    static final List<String> GIVE_TYPES =
            List.of("gem", "crystal", "extractor", "book", "set", "heroic", "upgrade", "orb",
                    "blackscroll", "randomizer", "transmog", "godlytransmog", "holy", "nametag",
                    "dust", "whitescroll", "unopened");

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
    private final feature.carrier.CarrierService carriers;
    private final feature.crystal.CrystalService crystals;
    private final feature.heroic.HeroicService heroics;
    private final feature.slot.SlotService slots;
    private final feature.scroll.ScrollService scrolls;
    private final feature.book.UnopenedBookService unopenedBooks;
    private final feature.scroll.HolyScrollService holyScrolls;
    private final feature.scroll.NametagService nametags;
    private final PackStore packs;

    SeCommand(ContentReloader reloader, ItemEnchanter enchanter, Consumer<Player> refreshWorn, SoulService souls,
              Path migrationTarget, MenuRegistry menus, ContentHolder content,
              java.util.function.Function<String, schema.spec.ParamSpec> migrateSpecs,
              feature.carrier.CarrierService carriers, feature.crystal.CrystalService crystals,
              feature.heroic.HeroicService heroics, feature.slot.SlotService slots,
              feature.scroll.ScrollService scrolls, feature.book.UnopenedBookService unopenedBooks,
              feature.scroll.HolyScrollService holyScrolls, feature.scroll.NametagService nametags,
              PackStore packs, Messages messages, Path contentRoot) {
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
        this.carriers = carriers;
        this.crystals = crystals;
        this.heroics = heroics;
        this.slots = slots;
        this.scrolls = scrolls;
        this.unopenedBooks = unopenedBooks;
        this.holyScrolls = holyScrolls;
        this.nametags = nametags;
        this.packs = packs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender, args);
            case "give" -> give(sender, args);
            case "enchant" -> applyHeld(sender, args);
            case "removeenchant", "unenchant" -> removeHeld(sender, args);
            case "crystal" -> giveCrystal(sender, args);
            case "heroic" -> giveHeroic(sender);
            case "orb" -> giveSlotItem(sender);
            case "gem" -> giveGem(sender);
            case "book" -> giveBook(sender, args);
            case "blackscroll" -> giveScroll(sender, true);
            case "randomizer" -> giveScroll(sender, false);
            case "transmog" -> giveSimpleItem(sender, scrolls.mintTransmog(), messages.format("command.give.transmog"));
            case "godlytransmog" -> giveSimpleItem(sender, scrolls.mintGodlyTransmog(),
                    messages.format("command.give.godlytransmog"));
            case "holy" -> giveSimpleItem(sender, holyScrolls.mint(), messages.format("command.give.holy"));
            case "nametag" -> giveSimpleItem(sender, nametags.mint(), messages.format("command.give.nametag"));
            case "dust" -> giveSimpleItem(sender, dustFor(args, 1), messages.format("command.give.dust"));
            case "whitescroll" -> giveSimpleItem(sender, carriers.mintWhiteScroll(),
                    messages.format("command.give.whitescroll"));
            case "unopened" -> giveUnopened(sender, args);
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
            default -> usage(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return complete(args,
                content.library().catalog().stream().map(EnchantDef::key).toList(),
                content.library().crystals().stream().map(CrystalDef::key).toList(),
                content.library().tiers().tiers().stream().map(t -> t.name()).toList(),
                menus.names(),
                Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),
                content.library().sets().stream().map(compile.load.SetDef::key).toList(),
                packNamesQuietly());
    }

    /** An I/O hiccup completes to nothing rather than throwing out of tab-completion. */
    private List<String> packNamesQuietly() {
        try {
            return packs.list().stream().map(PackStore.PackInfo::name).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Arity shims onto the canonical {@code complete}, each defaulting the trailing key lists to empty. */
    static List<String> complete(String[] args, List<String> enchantKeys, List<String> crystalKeys) {
        return complete(args, enchantKeys, crystalKeys, List.of());
    }

    static List<String> complete(String[] args, List<String> enchantKeys, List<String> crystalKeys,
                                 List<String> tierNames) {
        return complete(args, enchantKeys, crystalKeys, tierNames, List.of());
    }

    static List<String> complete(String[] args, List<String> enchantKeys, List<String> crystalKeys,
                                 List<String> tierNames, List<String> menuNames) {
        return complete(args, enchantKeys, crystalKeys, tierNames, menuNames, List.of());
    }

    static List<String> complete(String[] args, List<String> enchantKeys, List<String> crystalKeys,
                                 List<String> tierNames, List<String> menuNames, List<String> playerNames) {
        return complete(args, enchantKeys, crystalKeys, tierNames, menuNames, playerNames, List.of());
    }

    static List<String> complete(String[] args, List<String> enchantKeys, List<String> crystalKeys,
                                 List<String> tierNames, List<String> menuNames,
                                 List<String> playerNames, List<String> setKeys) {
        return complete(args, enchantKeys, crystalKeys, tierNames, menuNames, playerNames, setKeys, List.of());
    }

    /**
     * Pure tab-completion (extracted from Bukkit so it is unit-tested without a server): subcommand at
     * {@code args[0]}, then the §J {@code give <type> <player> [type-arg]} tree and the §packs
     * {@code pack <action> [name]} tree.
     */
    static List<String> complete(String[] args, List<String> enchantKeys, List<String> crystalKeys,
                                 List<String> tierNames, List<String> menuNames,
                                 List<String> playerNames, List<String> setKeys, List<String> packNames) {
        if (args.length <= 1) {
            return filter(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "give" -> filter(GIVE_TYPES, args[1]);
                case "enchant", "book", "removeenchant", "unenchant" -> filter(enchantKeys, args[1]);
                case "crystal" -> filter(crystalKeys, args[1]);
                case "unopened" -> filter(tierNames, args[1]);
                case "menu" -> filter(menuNames, args[1]);
                case "migrate" -> filter(List.of("ee", "ea", "ae"), args[1]);
                case "pack" -> filter(PACK_ACTIONS, args[1]);
                case "reload" -> filter(List.of("--dry-run"), args[1]);
                default -> List.of();
            };
        }
        if (sub.equals("give") && args.length == 3) {
            return filter(playerNames, args[2]); // /se give <type> <player>
        }
        if (sub.equals("pack") && args.length == 3) {
            String action = args[1].toLowerCase(Locale.ROOT); // /se pack <info|apply> <name>
            return action.equals("info") || action.equals("apply") ? filter(packNames, args[2]) : List.of();
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
            if (args[1].equalsIgnoreCase("book") && args[3].equalsIgnoreCase("random")) {
                return filter(tierNames, args[4]); // /se give book <player> random <tier>
            }
            if (args[1].equalsIgnoreCase("set")) {
                return filter(SET_MEMBERS, args[4]); // /se give set <player> <set> <member>
            }
        }
        return List.of();
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

    private void giveGem(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        Scheduling.onEntity(player, () -> {
            ItemStack gem = souls.mintGem();
            // Overflow drops at the player's feet (their own region thread) rather than being lost.
            player.getInventory().addItem(gem).values()
                    .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            player.sendMessage(messages.format("command.give.gem"));
        });
    }

    private void toggleSoulMode(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        // SoulService sends the ENABLED/DISABLED feedback; only the no-gem hint is the command's to relay.
        Scheduling.onEntity(player, () -> {
            if (souls.toggle(player) == SoulService.Toggle.NO_GEM) {
                player.sendMessage(messages.format("command.soul.no-gem"));
            }
        });
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

    /** The menu {@code /se menu} (no name) opens — the direct-apply enchant GUI. */
    private static final String DEFAULT_MENU = "apply";

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
            } catch (IOException e) {
                tell(sender, messages.format("command.pack.error", "ERROR", String.valueOf(e.getMessage())));
            }
        });
    }

    /**
     * {@code /se pack apply <name>} — back up the current config, swap in the pack, reload transactionally.
     * Boot-gated wiring (souls/slots/scrolls listeners, integration toggles, command-trigger) still needs
     * a restart — reported in the apply note.
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
        sender.sendMessage(messages.format("command.pack.apply-start", "NAME", name));
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String createdIso = now.toString();
        String backupLabel = "backup-" + now.format(BACKUP_STAMP);
        Scheduling.async(() -> {
            try {
                PackStore.ApplyResult applied = packs.apply(name, backupLabel, createdIso);
                tell(sender, messages.format("command.pack.apply-done", "NAME", applied.manifest().name(),
                        "FILES", applied.fileCount(),
                        "BACKUP", applied.hasBackup() ? applied.backupName() : "(none)"));
                if (!applied.skipped().isEmpty()) {
                    tell(sender, messages.format("command.pack.apply-skipped", "N", applied.skipped().size()));
                }
                // Same transactional reload as /se reload: a faulty pack keeps the previous in-memory
                // state and reports its diagnostics here.
                reloader.reload(result -> report(sender, result));
                tell(sender, messages.format("command.pack.apply-note"));
            } catch (IOException e) {
                tell(sender, messages.format("command.pack.error", "ERROR", String.valueOf(e.getMessage())));
            }
        });
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
                PackStore.ExportResult result = packs.export(name, description, author, createdIso);
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
            ItemStack held = player.getInventory().getItemInMainHand();
            // /se enchant is admin force-give → bypass the §G requires/blacklist relationship gates.
            ApplyResult result = enchanter.applyEnchant(held, key, appliedLevel, false);
            if (result.ok()) {
                player.getInventory().setItemInMainHand(held);
                // Mutating the held item in place fires no equip event; re-resolve or the new enchant
                // sits in PDC + lore but inert until a re-equip.
                refreshWorn.accept(player);
            }
            player.sendMessage(result.message());
        });
    }

    /** {@code /se give <type> <player> [type-args…]} — the §J give-to-player surface (delivery via {@link #deliver}). */
    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.format("command.give.usage"));
            return;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        Player target = resolveTarget(sender, args[2]);
        if (target == null) {
            return; // resolveTarget messaged the sender
        }
        switch (type) {
            case "gem" -> {
                int amount = 0;
                if (args.length >= 4) {
                    try {
                        amount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException bad) {
                        sender.sendMessage(messages.format("command.error.bad-number", "ARG", args[3]));
                        return;
                    }
                }
                deliver(sender, target, souls.mintGem(amount), "command.give.gem", "soul gem");
            }
            case "heroic", "upgrade" -> deliver(sender, target, heroics.mint(), "command.give.heroic", "heroic upgrade");
            case "orb" -> deliver(sender, target, slots.mintOrb(), "command.give.slot", "slot expander");
            case "blackscroll" -> deliver(sender, target, scrolls.mintBlack(), "command.give.blackscroll", "black scroll");
            case "randomizer" -> deliver(sender, target, scrolls.mintRandomizer(), "command.give.randomizer", "randomizer scroll");
            case "transmog" -> deliver(sender, target, scrolls.mintTransmog(), "command.give.transmog", "transmog scroll");
            case "holy" -> deliver(sender, target, holyScrolls.mint(), "command.give.holy", "holy white scroll");
            case "nametag" -> deliver(sender, target, nametags.mint(), "command.give.nametag", "item nametag");
            case "dust" -> giveDustTo(sender, target, args);
            case "whitescroll" -> deliver(sender, target, carriers.mintWhiteScroll(),
                    "command.give.whitescroll", "white scroll");
            case "crystal" -> giveCrystalTo(sender, target, args);
            case "extractor" -> deliver(sender, target, crystals.mintExtractor(), "command.give.extractor", "crystal extractor");
            case "book" -> giveBookTo(sender, target, args);
            case "unopened" -> giveUnopenedTo(sender, target, args);
            case "set" -> giveSetTo(sender, target, args);
            default -> sender.sendMessage(messages.format("command.give.usage"));
        }
    }

    /** Resolve an online-player target by exact name, messaging the sender if none matches. */
    private Player resolveTarget(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            sender.sendMessage(messages.format("command.error.no-such-player", "PLAYER", name));
        }
        return target;
    }

    /** Deliver {@code item} on the target's own region thread (overflow → feet); confirm to a distinct sender. */
    private void deliver(CommandSender sender, Player target, ItemStack item, String targetMsgKey, String itemLabel) {
        Scheduling.onEntity(target, () -> {
            MenuItems.giveOrDrop(target, item);
            target.sendMessage(messages.format(targetMsgKey, "KEY", itemLabel, "KIND", itemLabel,
                    "TIER", itemLabel, "LEVEL", "", "ID", itemLabel));
        });
        if (!(sender instanceof Player p) || !p.getUniqueId().equals(target.getUniqueId())) {
            tell(sender, messages.format("command.give.delivered", "ITEM", itemLabel, "PLAYER", target.getName()));
        }
    }

    /** {@code /se give crystal <player> <key>} — mint a crystal for the target. */
    private void giveCrystalTo(CommandSender sender, Player target, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(messages.format("command.crystal.usage"));
            return;
        }
        String key = normalize(args[3], "crystals/");
        if (content.library().crystals().stream().noneMatch(d -> d.key().equals(key))) {
            sender.sendMessage(messages.format("command.error.no-such-crystal", "KEY", key));
            return;
        }
        Scheduling.onEntity(target, () -> {
            MenuItems.giveOrDrop(target, crystals.mint(java.util.List.of(key)));
            target.sendMessage(messages.format("command.give.crystal", "KEY", key));
        });
        if (notSelf(sender, target)) {
            tell(sender, messages.format("command.give.delivered", "ITEM", key, "PLAYER", target.getName()));
        }
    }

    /** {@code /se give set <player> <set> <member>} — mint a declared set member (§6.6); undeclared fails cleanly. */
    private void giveSetTo(CommandSender sender, Player target, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(messages.format("command.set.usage"));
            return;
        }
        String key = normalize(args[3], "sets/");
        if (content.library().sets().stream().noneMatch(d -> d.key().equals(key))) {
            sender.sendMessage(messages.format("command.error.no-such-set", "KEY", key));
            return;
        }
        String piece = args[4];
        java.util.Optional<ItemStack> minted = enchanter.mintSetPiece(key, piece);
        if (minted.isEmpty()) {
            sender.sendMessage(messages.format("command.give.set-piece", "PIECE", piece, "KEY", key));
            return;
        }
        ItemStack item = minted.get();
        Scheduling.onEntity(target, () -> {
            MenuItems.giveOrDrop(target, item);
            target.sendMessage(messages.format("command.give.set", "KEY", key, "PIECE", piece));
        });
        if (notSelf(sender, target)) {
            tell(sender, messages.format("command.give.delivered", "ITEM", key + " " + piece,
                    "PLAYER", target.getName()));
        }
    }

    /**
     * {@code /se give book <player> <enchant> [level] [success]} — mint an enchant book for the target;
     * {@code /se give book <player> random <tier>} mints a CONCRETE random enchant book from that tier.
     */
    private void giveBookTo(CommandSender sender, Player target, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(messages.format("command.book.usage"));
            return;
        }
        if ("random".equalsIgnoreCase(args[3])) {
            giveRandomBookTo(sender, target, args);
            return;
        }
        String key = normalize(args[3], "enchants/");
        EnchantDef def = content.library().catalog().stream()
                .filter(d -> d.key().equals(key)).findFirst().orElse(null);
        if (def == null) {
            sender.sendMessage(messages.format("command.error.no-such-enchant", "KEY", key));
            return;
        }
        int level = 1;
        if (args.length >= 5) {
            try {
                level = Integer.parseInt(args[4]);
            } catch (NumberFormatException bad) {
                sender.sendMessage(messages.format("command.error.bad-level", "ARG", args[4]));
                return;
            }
        }
        if (level < 1 || level > def.maxLevel()) {
            sender.sendMessage(messages.format("command.error.level-range", "MAX", def.maxLevel(), "KEY", key));
            return;
        }
        Integer success = null;
        if (args.length >= 6) {
            try {
                success = Integer.parseInt(args[5]);
            } catch (NumberFormatException bad) {
                sender.sendMessage(messages.format("command.error.bad-number", "ARG", args[5]));
                return;
            }
        }
        int bookLevel = level;
        Integer bookSuccess = success;
        Scheduling.onEntity(target, () -> {
            ItemStack book = bookSuccess == null ? carriers.mintBook(key, bookLevel)
                    : carriers.mintBook(key, bookLevel, bookSuccess);
            MenuItems.giveOrDrop(target, book);
            target.sendMessage(messages.format("command.give.book", "KEY", key, "LEVEL", bookLevel));
        });
        if (notSelf(sender, target)) {
            tell(sender, messages.format("command.give.delivered", "ITEM", key, "PLAYER", target.getName()));
        }
    }

    /** {@code /se give book <player> random <tier>} — mint a CONCRETE random enchant book from that tier. */
    private void giveRandomBookTo(CommandSender sender, Player target, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(messages.format("command.book.usage"));
            return;
        }
        String tier = args[4];
        if (!content.library().tiers().isTier(tier)) {
            sender.sendMessage(messages.format("command.error.no-such-tier", "TIER", tier));
            return;
        }
        java.util.Optional<ItemStack> rolled = unopenedBooks.roll(tier);
        if (rolled.isEmpty()) {
            sender.sendMessage(messages.format("command.error.no-such-tier", "TIER", tier)); // valid tier, but empty
            return;
        }
        ItemStack book = rolled.get();
        Scheduling.onEntity(target, () -> {
            MenuItems.giveOrDrop(target, book);
            target.sendMessage(messages.format("command.give.book", "KEY", tier, "LEVEL", 0));
        });
        if (notSelf(sender, target)) {
            tell(sender, messages.format("command.give.delivered", "ITEM", "random " + tier + " book",
                    "PLAYER", target.getName()));
        }
    }

    /** {@code /se give unopened <player> <tier>} — mint a tier-scoped unopened book for the target. */
    private void giveUnopenedTo(CommandSender sender, Player target, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(messages.format("command.unopened.usage"));
            return;
        }
        String tier = args[3];
        if (!content.library().tiers().isTier(tier)) {
            sender.sendMessage(messages.format("command.error.no-such-tier", "TIER", tier));
            return;
        }
        Scheduling.onEntity(target, () -> {
            MenuItems.giveOrDrop(target, unopenedBooks.mint(tier));
            target.sendMessage(messages.format("command.give.unopened", "TIER", tier));
        });
        if (notSelf(sender, target)) {
            tell(sender, messages.format("command.give.delivered", "ITEM", tier, "PLAYER", target.getName()));
        }
    }

    /**
     * {@code /se give dust <player> [percent]} — mint a success dust for the target. With no percent it is a
     * RANDOM-bonus dust (rolls the configured {@code [min, max]} range when combined); with a percent it
     * confers exactly that fixed bonus (§I).
     */
    private void giveDustTo(CommandSender sender, Player target, String[] args) {
        deliver(sender, target, dustFor(args, 3), "command.give.dust", "success dust");
    }

    /** Mint a success dust from {@code args[idx]}: a parseable percent → a FIXED dust, else a RANDOM-range dust. */
    private ItemStack dustFor(String[] args, int idx) {
        java.util.OptionalInt percent = dustPercent(args, idx);
        return percent.isPresent() ? carriers.mintDust(percent.getAsInt()) : carriers.mintDust();
    }

    /** Parse an optional dust percent at {@code args[idx]}; absent or non-numeric ⇒ empty (a random dust). */
    private static java.util.OptionalInt dustPercent(String[] args, int idx) {
        if (args.length <= idx) {
            return java.util.OptionalInt.empty();
        }
        try {
            return java.util.OptionalInt.of(Integer.parseInt(args[idx].trim()));
        } catch (NumberFormatException e) {
            return java.util.OptionalInt.empty();
        }
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
            ItemStack held = player.getInventory().getItemInMainHand();
            ApplyResult result = enchanter.removeEnchant(held, key);
            if (result.ok()) {
                player.getInventory().setItemInMainHand(held);
                refreshWorn.accept(player); // in-place mutation fires no equip event — re-resolve WornState
            }
            player.sendMessage(result.message());
        });
    }

    private static boolean notSelf(CommandSender sender, Player target) {
        return !(sender instanceof Player p) || !p.getUniqueId().equals(target.getUniqueId());
    }

    /** {@code /se crystal <key>} — mint a physical crystal item and give it (drag it onto gear to apply). */
    private void giveCrystal(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.format("command.crystal.usage"));
            return;
        }
        String key = normalize(args[1], "crystals/");
        if (content.library().crystals().stream().noneMatch(d -> d.key().equals(key))) {
            player.sendMessage(messages.format("command.error.no-such-crystal", "KEY", key));
            return;
        }
        Scheduling.onEntity(player, () -> {
            ItemStack crystal = crystals.mint(java.util.List.of(key));
            player.getInventory().addItem(crystal).values()
                    .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            player.sendMessage(messages.format("command.give.crystal", "KEY", key));
        });
    }

    /** {@code /se heroic} — mint a heroic upgrade item and give it (drag it onto armour/weapon to attempt). */
    private void giveHeroic(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        Scheduling.onEntity(player, () -> {
            ItemStack upgrade = heroics.mint();
            player.getInventory().addItem(upgrade).values()
                    .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            player.sendMessage(messages.format("command.give.heroic"));
        });
    }

    /** {@code /se orb} — mint a slot expander (+N) and give it. */
    private void giveSlotItem(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        Scheduling.onEntity(player, () -> {
            ItemStack item = slots.mintOrb();
            player.getInventory().addItem(item).values()
                    .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            player.sendMessage(messages.format("command.give.slot", "KIND", "slot expander"));
        });
    }

    /** Give a pre-minted item to the sender on their own thread (overflow drops at feet), with a message. */
    private void giveSimpleItem(CommandSender sender, ItemStack item, String message) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        Scheduling.onEntity(player, () -> {
            player.getInventory().addItem(item).values()
                    .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            player.sendMessage(message);
        });
    }

    /** {@code /se blackscroll} / {@code /se randomizer} — mint a book-economy scroll and give it. */
    private void giveScroll(CommandSender sender, boolean black) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        Scheduling.onEntity(player, () -> {
            ItemStack scroll = black ? scrolls.mintBlack() : scrolls.mintRandomizer();
            player.getInventory().addItem(scroll).values()
                    .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            player.sendMessage(messages.format(black ? "command.give.blackscroll" : "command.give.randomizer"));
        });
    }

    /** {@code /se unopened <tier>} — mint a tier-scoped unopened book (right-click it to reveal a book). */
    private void giveUnopened(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.format("command.unopened.usage"));
            return;
        }
        String tier = args[1];
        if (!content.library().tiers().isTier(tier)) {
            player.sendMessage(messages.format("command.error.no-such-tier", "TIER", tier));
            return;
        }
        Scheduling.onEntity(player, () -> {
            ItemStack book = unopenedBooks.mint(tier);
            player.getInventory().addItem(book).values()
                    .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            player.sendMessage(messages.format("command.give.unopened", "TIER", tier));
        });
    }

    /** {@code /se book <enchant> [level]} — mint an enchant book carrier and give it to the sender. */
    private void giveBook(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.format("command.book.usage"));
            return;
        }
        String key = normalize(args[1], "enchants/");
        EnchantDef def = content.library().catalog().stream()
                .filter(d -> d.key().equals(key)).findFirst().orElse(null);
        if (def == null) {
            player.sendMessage(messages.format("command.error.no-such-enchant", "KEY", key));
            return;
        }
        int level = 1;
        if (args.length >= 3) {
            try {
                level = Integer.parseInt(args[2]);
            } catch (NumberFormatException bad) {
                sender.sendMessage(messages.format("command.error.bad-level", "ARG", args[2]));
                return;
            }
        }
        if (level < 1 || level > def.maxLevel()) {
            player.sendMessage(messages.format("command.error.level-range", "MAX", def.maxLevel(), "KEY", key));
            return;
        }
        int bookLevel = level;
        Scheduling.onEntity(player, () -> {
            org.bukkit.inventory.ItemStack book = carriers.mintBook(key, bookLevel);
            player.getInventory().addItem(book).values()
                    .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            player.sendMessage(messages.format("command.give.book", "KEY", key, "LEVEL", bookLevel));
        });
    }

    /** Prepend the namespace prefix when the operator typed a short key (no {@code /}). */
    private static String normalize(String key, String prefix) {
        return key.indexOf('/') >= 0 ? key : prefix + key;
    }

    private void usage(CommandSender sender) {
        messages.lines("command.usage").forEach(sender::sendMessage);
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

    private List<String> format(ReloadResult result) {
        List<String> lines = new ArrayList<>();
        if (result.errorCount() == 0) {
            lines.add(messages.format("command.reload.loaded",
                    "VERB", result.dryRun() ? "would load" : "loaded",
                    "COUNT", result.abilityCount(), "GEN", result.generation()));
            return lines;
        }
        lines.add(messages.format("command.reload.errors-header", "N", result.errorCount()));
        for (Diagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.blocking()) {
                lines.add(messages.format("command.reload.error-line", "DIAGNOSTIC", diagnostic));
            }
        }
        return lines;
    }
}
