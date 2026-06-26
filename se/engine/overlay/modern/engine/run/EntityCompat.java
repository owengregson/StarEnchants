package engine.run;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Modern (1.9+/1.13+) entity/material predicates for {@link FactPopulator} that have no 1.8 equivalent —
 * the swimming/gliding player states and {@code Material.isAir()} (docs/legacy-1.8.9-codeshare-design.md
 * §3.3). Same-FQN counterpart to the {@code overlay/legacy} impl, which returns the 1.8-correct constants.
 */
public final class EntityCompat {

    private EntityCompat() {
    }

    public static boolean isSwimming(Player player) {
        return player.isSwimming();
    }

    public static boolean isGliding(Player player) {
        return player.isGliding();
    }

    public static boolean isAir(Material material) {
        return material.isAir();
    }
}
