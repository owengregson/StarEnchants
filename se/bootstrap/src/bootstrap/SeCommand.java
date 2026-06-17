package bootstrap;

import compile.load.ContentHolder;
import compile.load.CrystalDef;
import compile.load.EnchantDef;
import feature.apply.ApplyResult;
import feature.apply.ItemEnchanter;
import feature.menu.EnchantMenu;
import feature.soul.SoulService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
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
            List.of("reload", "enchant", "crystal", "gem", "book", "soulmode", "migrate", "menu");

    private final ContentReloader reloader;
    private final ItemEnchanter enchanter;
    private final Consumer<Player> refreshWorn;
    private final SoulService souls;
    private final Path migrationTarget;
    private final EnchantMenu menu;
    private final ContentHolder content;
    private final java.util.function.Function<String, schema.spec.ParamSpec> migrateSpecs;
    private final feature.carrier.CarrierService carriers;
    private final feature.crystal.CrystalService crystals;

    SeCommand(ContentReloader reloader, ItemEnchanter enchanter, Consumer<Player> refreshWorn, SoulService souls,
              Path migrationTarget, EnchantMenu menu, ContentHolder content,
              java.util.function.Function<String, schema.spec.ParamSpec> migrateSpecs,
              feature.carrier.CarrierService carriers, feature.crystal.CrystalService crystals) {
        this.reloader = reloader;
        this.enchanter = enchanter;
        this.refreshWorn = refreshWorn;
        this.souls = souls;
        this.migrationTarget = migrationTarget;
        this.menu = menu;
        this.content = content;
        this.migrateSpecs = migrateSpecs;
        this.carriers = carriers;
        this.crystals = crystals;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender, args);
            case "enchant" -> applyHeld(sender, args);
            case "crystal" -> giveCrystal(sender, args);
            case "gem" -> giveGem(sender);
            case "book" -> giveBook(sender, args);
            case "soulmode" -> toggleSoulMode(sender);
            case "migrate" -> migrate(sender, args);
            case "menu" -> openMenu(sender);
            default -> usage(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return complete(args,
                content.library().catalog().stream().map(EnchantDef::key).toList(),
                content.library().crystals().stream().map(CrystalDef::key).toList());
    }

    /**
     * Pure tab-completion: the subcommand at {@code args[0]}, then context-sensitive completions for the
     * first argument (enchant/crystal keys from the live content, {@code ee}/{@code ea} for migrate,
     * {@code --dry-run} for reload). Extracted from Bukkit so it is unit-tested without a server.
     */
    static List<String> complete(String[] args, List<String> enchantKeys, List<String> crystalKeys) {
        if (args.length <= 1) {
            return filter(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "enchant", "book" -> filter(enchantKeys, args[1]);
                case "crystal" -> filter(crystalKeys, args[1]);
                case "migrate" -> filter(List.of("ee", "ea", "ae"), args[1]);
                case "reload" -> filter(List.of("--dry-run"), args[1]);
                default -> List.of();
            };
        }
        return List.of();
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
            sender.sendMessage("§cThat command can only be run by a player.");
            return;
        }
        Scheduling.onEntity(player, () -> {
            ItemStack gem = souls.mintGem();
            // Drop any overflow at the player's feet (on their own region thread) rather than losing it.
            player.getInventory().addItem(gem).values()
                    .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            player.sendMessage("§aSoul gem minted. §7Right-click it (or /se soulmode) to toggle soul mode.");
        });
    }

    /** Toggle soul mode based on the gem in the sender's hand (on the sender's own thread). */
    private void toggleSoulMode(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThat command can only be run by a player.");
            return;
        }
        // The ENABLED/DISABLED feedback is sent by SoulService from the soul-gem config; only the
        // no-gem hint is the command's to relay.
        Scheduling.onEntity(player, () -> {
            if (souls.toggle(player) == SoulService.Toggle.NO_GEM) {
                player.sendMessage("§cHold a soul gem first (/se gem).");
            }
        });
    }

    private void reload(CommandSender sender, String[] args) {
        boolean dryRun = args.length >= 2 && args[1].equalsIgnoreCase("--dry-run");
        sender.sendMessage("§7StarEnchants: " + (dryRun ? "checking" : "reloading") + " content…");
        if (dryRun) {
            reloader.dryRun(result -> report(sender, result));
        } else {
            reloader.reload(result -> report(sender, result));
        }
    }

    /** {@code /se menu} — open the enchant-application menu (on the player's own region thread). */
    private void openMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThat command can only be run by a player.");
            return;
        }
        menu.open(player);
    }

    /** {@code /se migrate <ee|ea|ae> <sourcePath>} — import legacy configs into the migrated/ folder for review. */
    private void migrate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eUsage: /se migrate <ee|ea|ae> <sourcePath>");
            sender.sendMessage("§7  ee §8— path to EliteEnchantments' enchantments.yml");
            sender.sendMessage("§7  ea §8— path to EliteArmor's armor/ directory");
            sender.sendMessage("§7  ae §8— path to AdvancedEnchantments' enchantments.yml");
            return;
        }
        String format = args[1].toLowerCase(Locale.ROOT);
        Path source = Path.of(args[2]);
        sender.sendMessage("§7StarEnchants: migrating " + format + " from §f" + source + "§7…");
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
                    tell(sender, "§cUnknown format '" + format + "' — use §fee§c, §fea§c or §fae§c.");
                    return;
                }
                int written = result.writeTo(migrationTarget);
                int skipped = result.files().size() - written;
                tell(sender, "§aMigrated " + result.files().size() + " file(s): §f" + written + "§a written, §f"
                        + skipped + "§a already present; §f" + result.diagnostics().size() + "§a review note(s).");
                tell(sender, "§7Review the §f# TODO §7markers in §f" + migrationTarget + "§7, then move files into content/.");
            } catch (IOException e) {
                tell(sender, "§cMigration failed reading §f" + source + "§c: " + e.getMessage());
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
            sender.sendMessage("§cThat command can only be run by a player.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /se enchant <key> [level]");
            return;
        }
        String key = normalize(args[1], "enchants/");
        int level = 1;
        if (args.length >= 3) {
            try {
                level = Integer.parseInt(args[2]);
            } catch (NumberFormatException bad) {
                sender.sendMessage("§cLevel must be a number, got §f" + args[2]);
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

    /** {@code /se crystal <key>} — mint a physical crystal item and give it (drag it onto gear to apply). */
    private void giveCrystal(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThat command can only be run by a player.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /se crystal <key> §7— a crystal you drag onto gear to apply");
            return;
        }
        String key = normalize(args[1], "crystals/");
        if (content.library().crystals().stream().noneMatch(d -> d.key().equals(key))) {
            player.sendMessage("§cNo such crystal: §f" + key);
            return;
        }
        Scheduling.onEntity(player, () -> {
            ItemStack crystal = crystals.mint(java.util.List.of(key));
            // Drop any overflow at the player's feet (on their own region thread) rather than losing it.
            player.getInventory().addItem(crystal).values()
                    .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            player.sendMessage("§aMinted a crystal: §f" + key + "§a. §7Drag it onto gear to apply.");
        });
    }

    /** {@code /se book <enchant> [level]} — mint an enchant book carrier and give it to the sender. */
    private void giveBook(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThat command can only be run by a player.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /se book <enchant> [level] §7— a book you drag onto gear to apply it");
            return;
        }
        String key = normalize(args[1], "enchants/");
        EnchantDef def = content.library().catalog().stream()
                .filter(d -> d.key().equals(key)).findFirst().orElse(null);
        if (def == null) {
            player.sendMessage("§cNo such enchant: §f" + key);
            return;
        }
        int level = 1;
        if (args.length >= 3) {
            try {
                level = Integer.parseInt(args[2]);
            } catch (NumberFormatException bad) {
                sender.sendMessage("§cLevel must be a number, got §f" + args[2]);
                return;
            }
        }
        if (level < 1 || level > def.maxLevel()) {
            player.sendMessage("§cLevel must be 1–" + def.maxLevel() + " for §f" + key);
            return;
        }
        int bookLevel = level;
        Scheduling.onEntity(player, () -> {
            org.bukkit.inventory.ItemStack book = carriers.mintBook(key, bookLevel);
            // Drop any overflow at the player's feet (on their own region thread) rather than losing it.
            player.getInventory().addItem(book).values()
                    .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            player.sendMessage("§aMinted an enchant book for §f" + key + " §7(level " + bookLevel + ")§a.");
        });
    }

    /** Prepend the namespace prefix when the operator typed a short key (no {@code /}). */
    private static String normalize(String key, String prefix) {
        return key.indexOf('/') >= 0 ? key : prefix + key;
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage("§eStarEnchants commands:");
        sender.sendMessage("§e  /se reload [--dry-run] §7— rebuild content");
        sender.sendMessage("§e  /se enchant <key> [level] §7— apply an enchant to the held item");
        sender.sendMessage("§e  /se crystal <key> §7— mint a crystal item (drag it onto gear to apply)");
        sender.sendMessage("§e  /se menu §7— open the enchant-application menu");
        sender.sendMessage("§e  /se gem §7— mint a soul gem (right-click it to toggle soul mode)");
        sender.sendMessage("§e  /se soulmode §7— toggle soul mode for the held gem");
        sender.sendMessage("§e  /se migrate <ee|ea|ae> <path> §7— import legacy EE/EA/AdvancedEnchantments configs");
    }

    /**
     * Report a reload result. The reloader's callback fires on the GLOBAL thread; a {@link Player}
     * sender is region-owned, so its messages route to its own thread. Console is fine inline.
     */
    private static void report(CommandSender sender, ReloadResult result) {
        List<String> lines = format(result);
        if (sender instanceof Player player) {
            Scheduling.onEntity(player, () -> lines.forEach(player::sendMessage));
        } else {
            lines.forEach(sender::sendMessage);
        }
    }

    private static List<String> format(ReloadResult result) {
        List<String> lines = new ArrayList<>();
        if (result.errorCount() == 0) {
            lines.add("§aStarEnchants: " + (result.dryRun() ? "would load " : "loaded ")
                    + result.abilityCount() + " abilities (generation " + result.generation() + ").");
            return lines;
        }
        lines.add("§cStarEnchants: " + result.errorCount() + " error(s) — kept the previous content:");
        for (Diagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.blocking()) {
                lines.add("§c  " + diagnostic);
            }
        }
        return lines;
    }
}
