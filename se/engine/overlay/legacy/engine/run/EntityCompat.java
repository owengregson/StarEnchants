package engine.run;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Legacy (1.8.9) entity/material predicates — same-FQN counterpart to the {@code overlay/modern} impl. 1.8
 * has no swimming (1.13) or gliding/elytra (1.9) player state and no {@code Material.isAir()} (1.13), so
 * these resolve to the 1.8-correct constants (docs/legacy-1.8.9-codeshare-design.md §3.3).
 */
public final class EntityCompat {

    private EntityCompat() {
    }

    public static boolean isSwimming(Player player) {
        return false; // no swimming mechanic on 1.8
    }

    public static boolean isGliding(Player player) {
        return false; // no elytra/gliding on 1.8
    }

    public static boolean isAir(Material material) {
        return material == Material.AIR; // no Material.isAir() on 1.8
    }
}
