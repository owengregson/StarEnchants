package feature.trigger;

import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The configurable command that fires the §B {@code COMMAND} trigger (§B, ADR-0022) — a Cosmic Enchants-style
 * custom command (e.g. {@code /cast}) that activates a player's COMMAND-type enchants. The name is dynamic
 * ({@code config.yml}, not declarable in {@code plugin.yml}), so it registers through the server command map.
 *
 * <p>Player-only: a non-player sender has no worn items to scan. A player's run hands straight to
 * {@link TriggerDispatch#fireCommand}; the command itself does no gating.
 */
public final class CommandTriggerCommand extends Command {

    private final TriggerDispatch dispatch;
    private final String playersOnlyMessage;

    public CommandTriggerCommand(String name, String description, TriggerDispatch dispatch,
                                 String playersOnlyMessage) {
        super(name);
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.playersOnlyMessage = Objects.requireNonNull(playersOnlyMessage, "playersOnlyMessage");
        setDescription(Objects.requireNonNullElse(description, ""));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (sender instanceof Player player) {
            dispatch.fireCommand(player);
        } else {
            sender.sendMessage(playersOnlyMessage);
        }
        return true;
    }
}
