package bootstrap.compat;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;

/**
 * Legacy (1.8.9) dynamic command registration — same-FQN counterpart to the {@code overlay/modern} impl.
 * 1.8's {@code Server} interface has no {@code getCommandMap()}; the command map is reached through the
 * {@code CraftServer} impl (docs/legacy-1.8.9-codeshare-design.md §4).
 */
public final class Commands {

    private Commands() {
    }

    public static void register(Server server, String fallbackPrefix, Command command) {
        ((CraftServer) server).getCommandMap().register(fallbackPrefix, command);
    }
}
