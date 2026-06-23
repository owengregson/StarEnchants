package bootstrap;

import compile.load.ContentHolder;
import compile.load.CrystalDef;
import compile.load.EnchantDef;
import compile.load.ItemDef;
import feature.apply.ApplyResult;
import feature.apply.ItemEnchanter;
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
 * The {@code /se} admin command. {@code /se reload [--dry-run]} rebuilds the content library
 * off-thread and swaps it in transactionally (ADR-0014, §10). {@code /se enchant <key> [level]} and
 * {@code /se crystal <key>} apply content to the item in the sender's main hand through the
 * {@link ItemEnchanter}, which validates against the live content and re-renders the lore (§4.2).
 *
 * <p>Reload results report on the global thread; item commands mutate the player's inventory and
 * message back on the PLAYER's own thread (Folia-correct — {@code /se} runs on the command thread,
 * not the player's region thread).
 */
public final class SeCommand implements CommandExecutor, TabCompleter {

    /** The subcommands, for {@code args[0]} tab-completion + the usage text. */
    static final List<String> SUBCOMMANDS =
            List.of("reload", "give", "enchant", "removeenchant", "unenchant", "crystal", "heroic", "orb", "slotgem",
                    "gem", "book", "blackscroll", "randomizer", "transmog", "holy", "nametag", "unopened", "soulmode",
                    "split", "migrate", "menu", "effects", "selectors", "triggers", "conditions", "variables", "list");

    /** The {@code /se give <type> …} item types (§J), for tab-completion at arg index 1. */
    static final List<String> GIVE_TYPES =
            List.of("gem", "crystal", "extractor", "book", "item", "set", "heroic", "upgrade", "orb", "slotgem",
                    "blackscroll", "randomizer", "transmog", "holy", "nametag", "unopened");

    /** The set members {@code /se give set <player> <set> <member>} can mint (§6.6). */
    static final List<String> SET_MEMBERS = List.of("helmet", "chestplate", "leggings", "boots", "weapon");

    private final ContentReloader reloader;
    private final ItemEnchanter enchanter;
    private final Consumer<Player> refreshWorn;
    private final SoulService souls;
    private final Messages messages; // §L lang.yml — every player-facing /se message
    private final Path migrationTarget;
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

    SeCommand(ContentReloader reloader, ItemEnchanter enchanter, Consumer<Player> refreshWorn, SoulService souls,
              Path migrationTarget, MenuRegistry menus, ContentHolder content,
              java.util.function.Function<String, schema.spec.ParamSpec> migrateSpecs,
              feature.carrier.CarrierService carriers, feature.crystal.CrystalService crystals,
              feature.heroic.HeroicService heroics, feature.slot.SlotService slots,
              feature.scroll.ScrollService scrolls, feature.book.UnopenedBookService unopenedBooks,
              feature.scroll.HolyScrollService holyScrolls, feature.scroll.NametagService nametags,
              Messages messages) {
        this.reloader = reloader;
        this.enchanter = enchanter;
        this.refreshWorn = refreshWorn;
        this.souls = souls;
        this.messages = messages;
        this.migrationTarget = migrationTarget;
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
            case "orb" -> giveSlotItem(sender, true);
            case "slotgem" -> giveSlotItem(sender, false);
            case "gem" -> giveGem(sender);
            case "book" -> giveBook(sender, args);
            case "blackscroll" -> giveScroll(sender, true);
            case "randomizer" -> giveScroll(sender, false);
            case "transmog" -> giveSimpleItem(sender, scrolls.mintTransmog(), messages.format("command.give.transmog"));
            case "holy" -> giveSimpleItem(sender, holyScrolls.mint(), messages.format("command.give.holy"));
            case "nametag" -> giveSimpleItem(sender, nametags.mint(), messages.format("command.give.nametag"));
            case "unopened" -> giveUnopened(sender, args);
            case "soulmode" -> toggleSoulMode(sender);
            case "split" -> splitSoul(sender, args);
            case "migrate" -> migrate(sender, args);
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
                content.library().items().stream().map(ItemDef::key).toList(),
                Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),
                content.library().sets().stream().map(compile.load.SetDef::key).toList());
    }

    /** As the canonical form with no tier/menu completions (legacy 3-arg form). */
    static List<String> complete(String[] args, List<String> enchantKeys, List<String> crystalKeys) {
        return complete(args, enchantKeys, crystalKeys, List.of());
    }

    /** As the canonical form with no menu-name completions (4-arg form). */
    static List<String> complete(String[] args, List<String> enchantKeys, List<String> crystalKeys,
                                 List<String> tierNames) {
        return complete(args, enchantKeys, crystalKeys, tierNames, List.of());
    }

    /** As the canonical form with no item-id / player completions (5-arg form). */
    static List<String> complete(String[] args, List<String> enchantKeys, List<String> crystalKeys,
                                 List<String> tierNames, List<String> menuNames) {
        return complete(args, enchantKeys, crystalKeys, tierNames, menuNames, List.of(), List.of());
    }

    /** As the canonical form with no set completions (7-arg form). */
    static List<String> complete(String[] args, List<String> enchantKeys, List<String> crystalKeys,
                                 List<String> tierNames, List<String> menuNames, List<String> itemIds,
                                 List<String> playerNames) {
        return complete(args, enchantKeys, crystalKeys, tierNames, menuNames, itemIds, playerNames, List.of());
    }

    /**
     * Pure tab-completion: the subcommand at {@code args[0]}; then context-sensitive completions for the
     * flat verbs and the §J {@code give <type> <player> [type-arg]} tree (type at arg 1, online player at
     * arg 2, type-specific key at arg 3, and {@code give book <player> random <tier>} at arg 4). Extracted
     * from Bukkit so it is unit-tested without a server. (A {@code dust} is an item, so {@code @dusts} is
     * served by {@code itemIds} on the {@code give item} route.)
     */
    static List<String> complete(String[] args, List<String> enchantKeys, List<String> crystalKeys,
                                 List<String> tierNames, List<String> menuNames, List<String> itemIds,
                                 List<String> playerNames, List<String> setKeys) {
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
                case "reload" -> filter(List.of("--dry-run"), args[1]);
                default -> List.of();
            };
        }
        if (sub.equals("give") && args.length == 3) {
            return filter(playerNames, args[2]); // /se give <type> <player>
        }
        if (sub.equals("give") && args.length == 4) {
            return switch (args[1].toLowerCase(Locale.ROOT)) { // the type-specific key
                case "crystal" -> filter(crystalKeys, args[3]);
                case "book" -> filter(concat("random", enchantKeys), args[3]); // book key, or the `random` form
                case "unopened" -> filter(tierNames, args[3]);
                case "item" -> filter(itemIds, args[3]);
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

    /** {@code head} followed by {@code rest} — a small helper to offer a sentinel verb alongside a key list. */
    private static List<String> concat(String head, List<String> rest) {
        List<String> out = new java.util.ArrayList<>(rest.size() + 1);
        out.add(head);
        out.addAll(rest);
        return out;
    }

    /** The candidates that start with {@code prefix} (case-insensitive), in order. */
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

    /** Mint a DISTINCT soul gem from the configured likeness and give it to the sender (on their thread). */
    private void giveGem(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        Scheduling.onEntity(player, () -> {
            ItemStack gem = souls.mintGem();
            // Drop any overflow at the player's feet (on their own region thread) rather than losing it.
            player.getInventory().addItem(gem).values()
                    .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            player.sendMessage(messages.format("command.give.gem"));
        });
    }

    /** Toggle soul mode based on the gem in the sender's hand (on the sender's own thread). */
    private void toggleSoulMode(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        // The ENABLED/DISABLED feedback is sent by SoulService from the soul-gem config; only the
        // no-gem hint is the command's to relay.
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

    /** {@code /se menu [name]} — open a registered GUI (on the player's own region thread via the open-hop). */
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
     * {@code /se <effects|selectors|triggers|conditions|variables>} — dump one reference category (§J/§M). The
     * same five live vocabularies the in-game reference browser GUI and the committed {@code docs/reference/}
     * Markdown read; here as a one-line joined list of the category's heads/names (the GUI is the rich view).
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
        // File I/O runs off the command thread; results route back to the sender's thread. Effects are
        // written in the verbose v2 form via the live effect-spec lookup (migrateSpecs).
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
                player.getInventory().setItemInMainHand(held); // write the mutated copy back
                // Re-resolve the cached WornState: mutating the held item in place fires no equip event,
                // so without this the new enchant is in PDC + lore but inert until a re-equip.
                refreshWorn.accept(player);
            }
            player.sendMessage(result.message());
        });
    }

    /**
     * {@code /se give <type> <player> [type-args…]} — the §J give-to-player surface. Resolves the target
     * player, mints the requested item, and delivers it on the TARGET's own region thread (Folia-correct,
     * overflow dropped at the target's feet via {@link MenuItems#giveOrDrop}); the recipient gets the
     * item-type hint, the sender a "gave X to PLAYER" confirmation. The flat top-level verbs ({@code /se gem}
     * etc.) remain as self-give shortcuts.
     */
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
            case "slotgem" -> deliver(sender, target, slots.mintGem(), "command.give.slot", "slot gem");
            case "blackscroll" -> deliver(sender, target, scrolls.mintBlack(), "command.give.blackscroll", "black scroll");
            case "randomizer" -> deliver(sender, target, scrolls.mintRandomizer(), "command.give.randomizer", "randomizer scroll");
            case "transmog" -> deliver(sender, target, scrolls.mintTransmog(), "command.give.transmog", "transmog scroll");
            case "holy" -> deliver(sender, target, holyScrolls.mint(), "command.give.holy", "holy scroll");
            case "nametag" -> deliver(sender, target, nametags.mint(), "command.give.nametag", "item nametag");
            case "crystal" -> giveCrystalTo(sender, target, args);
            case "extractor" -> deliver(sender, target, crystals.mintExtractor(), "command.give.extractor", "crystal extractor");
            case "book" -> giveBookTo(sender, target, args);
            case "unopened" -> giveUnopenedTo(sender, target, args);
            case "item" -> giveItemTo(sender, target, args);
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

    /**
     * Deliver {@code item} to {@code target} on its own region thread (overflow → feet), send the recipient
     * the per-type hint {@code targetMsgKey}, and — when the sender is not the recipient — confirm to the
     * sender. {@code itemLabel} names the item in that confirmation.
     */
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

    /**
     * {@code /se give set <player> <set> <member>} — mint a set member (an armour slot the set declares —
     * {@code helmet}/{@code chestplate}/{@code leggings}/{@code boots} — or {@code weapon}) from its own
     * material + name, and give it to the target (§6.6). A member the set does not declare fails cleanly.
     */
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
     * {@code /se give item <player> <item-id> [args]} — the §J universal item dispatcher: mint any authored
     * carrier (book/tome/scroll/dust/gem) from {@code content.library().items()} by its id.
     */
    private void giveItemTo(CommandSender sender, Player target, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(messages.format("command.give.usage"));
            return;
        }
        String id = args[3];
        ItemDef def = content.library().items().stream()
                .filter(d -> d.key().equals(id) || d.key().equals(normalize(id, "items/"))
                        || d.key().endsWith("/" + id))
                .findFirst().orElse(null);
        if (def == null) {
            sender.sendMessage(messages.format("command.error.no-such-item", "ID", id));
            return;
        }
        Scheduling.onEntity(target, () -> {
            MenuItems.giveOrDrop(target, carriers.mint(def));
            target.sendMessage(messages.format("command.give.item", "ID", def.key()));
        });
        if (notSelf(sender, target)) {
            tell(sender, messages.format("command.give.delivered", "ITEM", def.key(), "PLAYER", target.getName()));
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
                refreshWorn.accept(player); // mutating in place fires no equip event — re-resolve WornState
            }
            player.sendMessage(result.message());
        });
    }

    /** Whether {@code sender} is a different entity than {@code target} (so the sender wants a confirmation). */
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
            // Drop any overflow at the player's feet (on their own region thread) rather than losing it.
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

    /** {@code /se orb} / {@code /se slotgem} — mint a slot expander (+N) / slot gem (+1) and give it. */
    private void giveSlotItem(CommandSender sender, boolean orb) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        Scheduling.onEntity(player, () -> {
            ItemStack item = orb ? slots.mintOrb() : slots.mintGem();
            player.getInventory().addItem(item).values()
                    .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            player.sendMessage(messages.format("command.give.slot", "KIND", orb ? "slot expander" : "slot gem"));
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
            // Drop any overflow at the player's feet (on their own region thread) rather than losing it.
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
