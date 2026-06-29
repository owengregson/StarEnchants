package feature.menu;

import item.lang.Messages;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The {@code /enchants} command (ADR-0030) — the player-facing entry to the GUI, since {@code /se} is
 * {@code starenchants.admin}-gated and would otherwise leave a normal player no way to reach the merchant
 * benches. Opens the {@link UserHubMenu} ({@code hub}); the hub and everything it links to are permission-free,
 * so a user can navigate the whole user surface. Registered dynamically on the server command map like
 * {@code /splitsouls}, gated by {@code starenchants.use} (default true) so an operator can still disable it.
 */
public final class UserMenuCommand extends Command {

    public static final String PERMISSION = "starenchants.use";
    private static final String HUB = "hub";

    private final MenuRegistry menus;
    private final Messages messages;

    public UserMenuCommand(String name, MenuRegistry menus, Messages messages) {
        super(name);
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        setDescription("Open the StarEnchants menu.");
        setUsage("/" + name);
        setPermission(PERMISSION);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return true;
        }
        // open() does the Folia open-hop to the player's region thread itself.
        menus.get(HUB).ifPresentOrElse(menu -> menu.open(player),
                () -> player.sendMessage(messages.format("command.menu.unknown",
                        "NAME", HUB, "AVAILABLE", String.join(", ", menus.names()))));
        return true;
    }
}
