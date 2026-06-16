package bootstrap;

import feature.apply.ApplyResult;
import feature.apply.ItemEnchanter;
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
public final class SeCommand implements CommandExecutor {

    private final ContentReloader reloader;
    private final ItemEnchanter enchanter;
    private final Consumer<Player> refreshWorn;
    private final SoulService souls;
    private final Path migrationTarget;

    SeCommand(ContentReloader reloader, ItemEnchanter enchanter, Consumer<Player> refreshWorn, SoulService souls,
              Path migrationTarget) {
        this.reloader = reloader;
        this.enchanter = enchanter;
        this.refreshWorn = refreshWorn;
        this.souls = souls;
        this.migrationTarget = migrationTarget;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender, args);
            case "enchant" -> applyHeld(sender, args, false);
            case "crystal" -> applyHeld(sender, args, true);
            case "gem" -> stampGem(sender);
            case "soulmode" -> toggleSoulMode(sender);
            case "migrate" -> migrate(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    /** Stamp a fresh soul gem onto the sender's held item (on the sender's own thread). */
    private void stampGem(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThat command can only be run by a player.");
            return;
        }
        Scheduling.onEntity(player, () -> player.sendMessage(souls.stampGem(player)
                ? "§aSoul gem stamped onto your held item. §7Toggle it with /se soulmode."
                : "§cHold an item first."));
    }

    /** Toggle soul mode based on the gem in the sender's hand (on the sender's own thread). */
    private void toggleSoulMode(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThat command can only be run by a player.");
            return;
        }
        Scheduling.onEntity(player, () -> {
            switch (souls.toggle(player)) {
                case ENABLED -> player.sendMessage("§aSoul mode §lON§a — soul-cost abilities now spend souls.");
                case DISABLED -> player.sendMessage("§7Soul mode §lOFF§7.");
                case NO_GEM -> player.sendMessage("§cHold a soul gem first (/se gem).");
                default -> { }
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

    /** {@code /se migrate <ee|ea> <sourcePath>} — import legacy configs into the migrated/ folder for review. */
    private void migrate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eUsage: /se migrate <ee|ea> <sourcePath>");
            sender.sendMessage("§7  ee §8— path to EliteEnchantments' enchantments.yml");
            sender.sendMessage("§7  ea §8— path to EliteArmor's armor/ directory");
            return;
        }
        String format = args[1].toLowerCase(Locale.ROOT);
        Path source = Path.of(args[2]);
        sender.sendMessage("§7StarEnchants: migrating " + format + " from §f" + source + "§7…");
        // File I/O runs off the command thread; results route back to the sender's thread.
        Scheduling.async(() -> {
            try {
                Migrator.Result result = switch (format) {
                    case "ee" -> Migrator.eliteEnchantments(Files.readString(source, StandardCharsets.UTF_8));
                    case "ea" -> migrateArmorDir(source);
                    default -> null;
                };
                if (result == null) {
                    tell(sender, "§cUnknown format '" + format + "' — use §fee§c or §fea§c.");
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
    private static Migrator.Result migrateArmorDir(Path dir) throws IOException {
        java.util.Map<String, String> files = new java.util.LinkedHashMap<>();
        schema.diag.Diagnostics diagnostics = new schema.diag.Diagnostics();
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path file : entries.filter(p -> p.toString().endsWith(".yml")).sorted().toList()) {
                String id = file.getFileName().toString().replaceFirst("\\.yml$", "");
                Migrator.Result one = Migrator.eliteArmorSet(id, Files.readString(file, StandardCharsets.UTF_8));
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

    /** Apply an enchant/crystal to the sender's held item, on the sender's own thread (Folia-correct). */
    private void applyHeld(CommandSender sender, String[] args, boolean crystal) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThat command can only be run by a player.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(crystal ? "§eUsage: /se crystal <key>" : "§eUsage: /se enchant <key> [level]");
            return;
        }
        String key = normalize(args[1], crystal ? "crystals/" : "enchants/");
        int level = 1;
        if (!crystal && args.length >= 3) {
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
            ApplyResult result = crystal
                    ? enchanter.applyCrystal(held, key)
                    : enchanter.applyEnchant(held, key, appliedLevel);
            if (result.ok()) {
                player.getInventory().setItemInMainHand(held); // write the mutated copy back
                // Re-resolve the cached WornState: mutating the held item in place fires no equip event,
                // so without this the new enchant/crystal is in PDC + lore but inert until a re-equip.
                refreshWorn.accept(player);
            }
            player.sendMessage(result.message());
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
        sender.sendMessage("§e  /se crystal <key> §7— apply a crystal to the held item");
        sender.sendMessage("§e  /se gem §7— stamp a soul gem onto the held item");
        sender.sendMessage("§e  /se soulmode §7— toggle soul mode for the held gem");
        sender.sendMessage("§e  /se migrate <ee|ea> <path> §7— import legacy EliteEnchantments/EliteArmor configs");
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
