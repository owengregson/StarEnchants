package feature.compat;

import feature.combat.KnockbackListener.Path;

/**
 * Legacy (1.8.9) knockback-applier selection — same-FQN counterpart to the {@code overlay/modern} impl. 1.8
 * fires no Bukkit/Paper knockback event, so the NMS-source applier (the legacy {@code LegacyKnockbackListener},
 * which scales at the knockback-resistance attribute) is ALWAYS the path. This is the compile-time era fact
 * that replaces the former {@code v1_8_R3} runtime class probe (docs/legacy-1.8.9-codeshare-design.md §6).
 */
public final class KnockbackSeam {

    private KnockbackSeam() {
    }

    public static Path resolve() {
        return Path.LEGACY;
    }
}
