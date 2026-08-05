package engine.sink;

import java.util.List;

/**
 * Per-summon behaviour flags for {@code SPAWN_ENTITY} (ADR-0052): resolved at spawn time inside the sink's
 * region op (spawns are fire-and-forget — an effect never sees the entity, so all customization happens
 * here). Flags needing a live listener (target suppression, hit-gated detonation, invincibility, a payload
 * phase) register the spawn in {@link PetSummons}; the feature layer enforces them.
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
 * @param effects             potion loadout held for the summon's whole life, packed id+level per entry
 * @param payloadPhase        when the payload fires: {@code none|detonate|death|periodic|strike}
 * @param payloadPeriod       ticks between pulses on the {@code periodic} phase
 * @param payloadRadius       XZ half-extent of the payload's target box
 * @param payloadHeight       Y half-extent; 0 means "use {@link #payloadRadius}"
 * @param payloadFilter       the {@code engine.selector.kind.Targets} filter vocabulary, {@code A+B} admitted
 * @param payloadMaxTargets   nearest-first cap on the payload's targets (0 = unlimited)
 * @param payloadConsume      {@code strike} phase only: the hit the summon lands also despawns it
 * @param payloadCancel       {@code strike} phase only: the summon's own melee damage is cancelled
 * @param scatter             each summon lands at a random ±N XZ offset, air-scanned (0 = the exact point)
 * @param sourceGroup         the interned {@code group:} of the summoning ability, carried so the
 *                            {@link #PHASE_STRIKE} courier fires only THAT feature's {@code IMPACT} abilities
 *                            (ADR-0074); {@code -1} = ungrouped, which fires the owner's whole IMPACT roster
 * @param fuseTicks           primed TNT only: ticks until it detonates, overriding vanilla's 80; {@code 0}
 *                            keeps vanilla's. Deliberately NOT expressible as {@code ttl}, which DESPAWNS —
 *                            any ttl at or under the fuse removes the charge before it can ever explode
 */
public record SummonFlags(boolean powered, boolean noAi, boolean noTarget, boolean saddled,
                          boolean mountActivator, boolean detonateOnPlayerHit, boolean invincible,
                          double speedMultiplier, String name, List<Integer> effects,
                          String payloadPhase, int payloadPeriod, double payloadRadius, double payloadHeight,
                          String payloadFilter, int payloadMaxTargets, boolean payloadConsume,
                          boolean payloadCancel, int scatter, int sourceGroup, int fuseTicks) {

    /** The payload phase that arms nothing — {@code SPAWN_ENTITY}'s {@code payload-phase} default. */
    public static final String PHASE_NONE = "none";
    public static final String PHASE_DETONATE = "detonate";
    public static final String PHASE_DEATH = "death";
    public static final String PHASE_PERIODIC = "periodic";
    /** The odd rung out: it runs the owner's {@code IMPACT} abilities, not their {@code SUMMON_PAYLOAD} ones. */
    public static final String PHASE_STRIKE = "strike";

    /** Mirrors every {@code SPAWN_ENTITY} spec default, so an unconfigured spawn still reports {@link #none()}. */
    public static final SummonFlags NONE =
            new SummonFlags(false, false, false, false, false, false, false, 0.0, "", List.of(),
                    PHASE_NONE, 40, 4.0, 0.0, "ALL", 0, true, true, 0, -1, 0);

    /** The ADR-0052 flag set with no payload and no scatter — every payload component at its spec default. */
    public static SummonFlags of(boolean powered, boolean noAi, boolean noTarget, boolean saddled,
                                 boolean mountActivator, boolean detonateOnPlayerHit, boolean invincible,
                                 double speedMultiplier, String name, List<Integer> effects) {
        return new SummonFlags(powered, noAi, noTarget, saddled, mountActivator, detonateOnPlayerHit,
                invincible, speedMultiplier, name, effects, NONE.payloadPhase(), NONE.payloadPeriod(),
                NONE.payloadRadius(), NONE.payloadHeight(), NONE.payloadFilter(), NONE.payloadMaxTargets(),
                NONE.payloadConsume(), NONE.payloadCancel(), NONE.scatter(), NONE.sourceGroup(), NONE.fuseTicks());
    }

    /** This flag set with a payload armed — the payload params every phase shares, in spec order. */
    public SummonFlags withPayload(String phase, int period, double radius, double height,
                                   String filter, int maxTargets, int scatter) {
        return new SummonFlags(powered, noAi, noTarget, saddled, mountActivator, detonateOnPlayerHit,
                invincible, speedMultiplier, name, effects, phase, period, radius, height, filter,
                maxTargets, payloadConsume, payloadCancel, scatter, sourceGroup, fuseTicks);
    }

    /** This flag set with the {@link #PHASE_STRIKE} pair overridden — the rung's own params, as {@code withPayload}. */
    public SummonFlags withStrike(boolean consume, boolean cancel) {
        return new SummonFlags(powered, noAi, noTarget, saddled, mountActivator, detonateOnPlayerHit,
                invincible, speedMultiplier, name, effects, payloadPhase, payloadPeriod, payloadRadius,
                payloadHeight, payloadFilter, payloadMaxTargets, consume, cancel, scatter, sourceGroup,
                fuseTicks);
    }

    /** This flag set scoped to the arming ability's {@code group:} — the IMPACT filter the courier carries. */
    public SummonFlags withSourceGroup(int sourceGroup) {
        return new SummonFlags(powered, noAi, noTarget, saddled, mountActivator, detonateOnPlayerHit,
                invincible, speedMultiplier, name, effects, payloadPhase, payloadPeriod, payloadRadius,
                payloadHeight, payloadFilter, payloadMaxTargets, payloadConsume, payloadCancel, scatter,
                sourceGroup, fuseTicks);
    }

    /** Whether this summon runs a payload — its owner's {@code SUMMON_PAYLOAD} abilities, or on the
     *  {@link #PHASE_STRIKE} rung their {@code IMPACT} ones — at some point in its life. */
    public boolean payloadArmed() {
        return !PHASE_NONE.equalsIgnoreCase(payloadPhase);
    }

    /** Whether the payload fires on {@code phase} (one of the {@code PHASE_*} constants). */
    public boolean payloadOn(String phase) {
        return phase.equalsIgnoreCase(payloadPhase);
    }

    /** Whether any flag needs the {@link PetSummons} registry + the summon-guard listener. */
    public boolean tracked() {
        return noTarget || detonateOnPlayerHit || invincible || payloadArmed();
    }

    /** Whether this is a plain spawn (every flag default) — routed to the legacy-stable spawn path. */
    public boolean none() {
        return equals(NONE);
    }
}
