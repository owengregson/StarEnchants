package bootstrap;

import feature.apply.ApplyResult;
import feature.apply.ItemEnchanter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
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

    SeCommand(ContentReloader reloader, ItemEnchanter enchanter, Consumer<Player> refreshWorn) {
        this.reloader = reloader;
        this.enchanter = enchanter;
        this.refreshWorn = refreshWorn;
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
            default -> usage(sender);
        }
        return true;
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
