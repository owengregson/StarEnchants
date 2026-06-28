package feature.soul;

import item.lang.Messages;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import platform.sched.Scheduling;

/**
 * The {@code /splitsouls <amount>} command (§D) — a top-level alias for {@code /se split} so the soul gem's
 * own lore can advertise a short, memorable command. Registered dynamically on the server command map (the
 * name is fixed, but it is wired the same way as the COMMAND-trigger command rather than declared in
 * plugin.yml, keeping all soul wiring in one place). Player-only; the split runs on the player's own thread
 * so reading the held gem is region-safe on Folia.
 */
public final class SplitSoulsCommand extends Command {

    private final SoulService souls;
    private final Messages messages;

    public SplitSoulsCommand(String name, SoulService souls, Messages messages) {
        super(name);
        this.souls = Objects.requireNonNull(souls, "souls");
        this.messages = Objects.requireNonNull(messages, "messages");
        setDescription("Split souls off your active gem into a new gem.");
        setUsage("/" + name + " <amount>");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(messages.format("command.soul.split-usage"));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[0]);
        } catch (NumberFormatException bad) {
            sender.sendMessage(messages.format("command.error.bad-number", "ARG", args[0]));
            return true;
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
        return true;
    }
}
