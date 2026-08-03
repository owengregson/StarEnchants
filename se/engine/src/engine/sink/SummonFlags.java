package engine.sink;

import java.util.List;

/**
 * Per-summon behaviour flags for {@code SPAWN_ENTITY} (ADR-0052): resolved at spawn time inside the sink's
 * region op (spawns are fire-and-forget — an effect never sees the entity, so all customization happens
 * here). Flags needing a live listener (target suppression, hit-gated detonation, invincibility) register
 * the spawn in {@link PetSummons}; the feature layer enforces them.
 *
 * @param powered             charge a spawned creeper
 * @param noAi                disable mob AI (modern {@code setAI(false)}; 1.8 via the NMS NoAI leaf)
 * @param noTarget            the summon never acquires a target (enforced by the summon-guard listener)
 * @param saddled             saddle a spawned horse-type so it is steerable
 * @param mountActivator      seat the activator on the summon after spawning
 * @param detonateOnPlayerHit a spawned creeper detonates ONLY when a player hits it (listener-gated fuse)
 * @param invincible          the summon cannot die but still takes hits/knockback: every damage event is
 *                            ZEROED (never cancelled — a cancel would eat the knockback too), so a burst
 *                            that would out-damage any health pool in one tick still kills nothing
 * @param speedMultiplier     scale the summon's vanilla movement-speed attribute BASE by this factor (0 =
 *                            untouched); modern writes the GENERIC_MOVEMENT_SPEED base, 1.8 the NMS
 *                            {@code GenericAttributes.MOVEMENT_SPEED} instance
 * @param name                custom name shown above the summon ({@code &}-colour codes); empty = unnamed
 * @param effects             interned potion-effect ids held for the summon's whole life, all at level 1
 */
public record SummonFlags(boolean powered, boolean noAi, boolean noTarget, boolean saddled,
                          boolean mountActivator, boolean detonateOnPlayerHit, boolean invincible,
                          double speedMultiplier, String name, List<Integer> effects) {

    public static final SummonFlags NONE =
            new SummonFlags(false, false, false, false, false, false, false, 0.0, "", List.of());

    /** Whether any flag needs the {@link PetSummons} registry + the summon-guard listener. */
    public boolean tracked() {
        return noTarget || detonateOnPlayerHit || invincible;
    }

    /** Whether this is a plain spawn (every flag default) — routed to the legacy-stable spawn path. */
    public boolean none() {
        return equals(NONE);
    }
}
