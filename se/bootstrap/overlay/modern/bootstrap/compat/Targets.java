package bootstrap.compat;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Modern raytrace targeting ({@code getTargetEntity}/{@code getTargetBlockExact}, 1.13+). Same-FQN
 * counterpart to the {@code overlay/legacy} impl, which approximates these with the 1.8 line-of-sight APIs
 * (docs/legacy-1.8.9-codeshare-design.md §4).
 */
public final class Targets {

    private Targets() {
    }

    public static Entity targetEntity(Player from, int maxDistance) {
        return from.getTargetEntity(maxDistance);
    }

    public static Block targetBlock(Player from, int maxDistance) {
        return from.getTargetBlockExact(maxDistance);
    }
}
