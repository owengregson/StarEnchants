package bootstrap;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import platform.content.ContentReloader;
import platform.content.ReloadResult;
import platform.sched.Scheduling;
import schema.diag.Diagnostic;

/**
 * The {@code /se} admin command. {@code /se reload} rebuilds the content library off-thread and swaps
 * it in transactionally (a fatal edit keeps the old content live); {@code /se reload --dry-run} checks
 * an edit and reports without swapping (ADR-0014, §10). The result is reported back to the sender on
 * the global thread by the {@link ContentReloader} callback.
 */
public final class SeCommand implements CommandExecutor {

    private final ContentReloader reloader;

    SeCommand(ContentReloader reloader) {
        this.reloader = reloader;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§eUsage: /se reload [--dry-run]");
            return true;
        }
        boolean dryRun = args.length >= 2 && args[1].equalsIgnoreCase("--dry-run");
        sender.sendMessage("§7StarEnchants: " + (dryRun ? "checking" : "reloading") + " content…");
        if (dryRun) {
            reloader.dryRun(result -> report(sender, result));
        } else {
            reloader.reload(result -> report(sender, result));
        }
        return true;
    }

    /**
     * Report the result. The reloader's callback fires on the GLOBAL thread; a {@link Player} sender is
     * region-owned, so its messages are routed to its own thread (Folia-correct). Console is fine inline.
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
