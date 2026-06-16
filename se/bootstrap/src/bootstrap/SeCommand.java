package bootstrap;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import platform.content.ContentReloader;
import platform.content.ReloadResult;
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

    private static void report(CommandSender sender, ReloadResult result) {
        if (result.errorCount() == 0) {
            sender.sendMessage("§aStarEnchants: " + (result.dryRun() ? "would load " : "loaded ")
                    + result.abilityCount() + " abilities (generation " + result.generation() + ").");
            return;
        }
        sender.sendMessage("§cStarEnchants: " + result.errorCount()
                + " error(s) — kept the previous content:");
        for (Diagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.blocking()) {
                sender.sendMessage("§c  " + diagnostic);
            }
        }
    }
}
