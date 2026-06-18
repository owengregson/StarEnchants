package feature.trigger;

import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The configurable standalone command that fires the §B {@code COMMAND} trigger (docs/v3-directives.md §B,
 * ADR-0022). EE lets a server bind a custom command (e.g. {@code /cast}) that activates a player's
 * COMMAND-type enchants; this is that command. Its name/description come from {@code config.yml}
 * ({@code command-trigger.*}) and it is registered once at boot through the server command map, so it works
 * even though the name is dynamic (not declarable in {@code plugin.yml}).
 *
 * <p>Player-only: a console/command-block sender has no worn items to scan, so it gets the
 * {@code command.not-a-player} message (resolved from {@code lang.yml} at registration). A player's run hands
 * straight to {@link TriggerDispatch#fireCommand}, which runs their worn COMMAND abilities through the full
 * gate sequence — the command itself does no gating.
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
