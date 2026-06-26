package bootstrap.compat;

import org.bukkit.Server;
import org.bukkit.command.Command;

/**
 * Modern dynamic command registration via {@code Server.getCommandMap()}. Same-FQN counterpart to the
 * {@code overlay/legacy} impl; {@code Server.getCommandMap()} is a Paper/Spigot addition absent on the 1.8.8
 * API (the legacy impl reaches it through {@code CraftServer}) — docs/legacy-1.8.9-codeshare-design.md §4.
 */
public final class Commands {

    private Commands() {
    }

    public static void register(Server server, String fallbackPrefix, Command command) {
        server.getCommandMap().register(fallbackPrefix, command);
    }
}
