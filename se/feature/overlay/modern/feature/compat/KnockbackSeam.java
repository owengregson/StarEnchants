package feature.compat;

import feature.combat.KnockbackListener;
import feature.combat.KnockbackListener.Path;

/**
 * Modern knockback-applier selection — same-FQN counterpart to the {@code overlay/legacy} impl. On
 * 1.17.1 &rarr; 26.1.x the server fires a Bukkit/Paper knockback event, so the applier {@link Path} is chosen
 * by class-presence probe (the modern {@code EntityKnockbackEvent} when present, else Paper's legacy event,
 * else none). The 1.8 NMS lane is a COMPILE-TIME impossibility on this tree — that path lives only in the
 * {@code overlay/legacy} seam (docs/legacy-1.8.9-codeshare-design.md §4).
 */
public final class KnockbackSeam {

    private KnockbackSeam() {
    }

    public static Path resolve() {
        return KnockbackListener.resolve(present(KnockbackListener.MODERN_EVENT), present(KnockbackListener.LEGACY_EVENT));
    }

    private static boolean present(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }
}
