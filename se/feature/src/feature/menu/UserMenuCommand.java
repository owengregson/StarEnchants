package feature.menu;

import platform.lang.Messages;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * A player-facing command that opens one StarEnchants menu directly (ADR-0030). {@code /se} is
 * {@code starenchants.admin}-gated, so without these a normal player has no way to reach the merchant benches or
 * browsers; each command opens a single {@link MenuRegistry} entry ({@code /enchants} and {@code /enchanter} →
 * the enchanter bench, {@code /pets} → the pets browser, and so on). The target menu is looked up by name at
 * execute time, so it resolves whichever module registered it — the registry is fully populated before any
 * command runs. Everything reachable this way is permission-free in the hub, so {@code starenchants.use}
 * (default true) is the right gate; an operator can still disable a command by revoking it. Registered
 * dynamically on the server command map like {@code /splitsouls}.
 */
public final class UserMenuCommand extends Command {

    public static final String PERMISSION = "starenchants.use";

    private final String menuName;
    private final MenuRegistry menus;
    private final Messages messages;

    /** A command labelled {@code label} that opens the registry menu named {@code menuName}. */
    public UserMenuCommand(String label, String menuName, MenuRegistry menus, Messages messages) {
        super(label);
        this.menuName = Objects.requireNonNull(menuName, "menuName");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        setDescription("Open the StarEnchants " + menuName + " menu.");
        setUsage("/" + label);
        setPermission(PERMISSION);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return true;
        }
        // open() does the Folia open-hop to the player's region thread itself. A menu missing (its family disabled)
        // reports gracefully, exactly as /se menu <name> does.
        menus.get(menuName).ifPresentOrElse(menu -> menu.open(player),
                () -> player.sendMessage(messages.format("command.menu.unknown",
                        "NAME", menuName, "AVAILABLE", String.join(", ", menus.names()))));
        return true;
    }
}
