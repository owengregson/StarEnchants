package engine.sink;

import engine.interact.DamageFold;

/**
 * The complete concrete-readback + dispatch-control surface of the {@link Sink} impl, promoted to an
 * interface so shared engine/feature code depends on the abstraction rather than the version-specific
 * {@code DispatchSink} class (docs/legacy-1.8.9-codeshare-design.md §3.5). {@code DispatchSink} is the only
 * implementor and lives in the per-target overlay (modern: Bukkit API; legacy: {@code v1_8_R3} NMS); the
 * shared {@link engine.run.AbilityExecutor} and the feature dispatchers hold a {@code SinkReadback}, never
 * the concrete class, so they compile against either target.
 *
 * <p>Extends {@link Sink}: a {@code SinkReadback} IS a sink (effects emit through it) plus the inline
 * feedback the firing system reads back ({@link #fold()}, {@link #cancelled()}, the combat/mine/bow flags),
 * the deferred-intent {@link #flush()}, and the per-effect WAIT {@link #delay(int)}. None of these are on
 * {@link Sink} — they are read by the firing system, never by an effect.
 */
public interface SinkReadback extends Sink {

    /** The damage arbiter for this event; the trigger listener folds it onto the event once (§6.1). */
    DamageFold fold();

    /** Whether an effect asked for the triggering event to be cancelled (§3.6 event control). */
    boolean cancelled();

    /** The accumulated EXP_MULTIPLY factor for an EXP_GAIN activation (1.0 = unchanged). */
    double expMultiplier();

    /** Whether an effect asked the triggering hit to ignore armor (§ combat-flags). */
    boolean armorIgnored();

    /** Whether an effect asked the triggering block-break to auto-smelt (SMELT). */
    boolean smeltRequested();

    /** Whether an effect asked the broken block's drops to go to the breaker's inventory (TELEPORT_DROPS). */
    boolean teleportDropsRequested();

    /** Whether an effect asked the fired projectile to home onto a target (AUTO_LOCK). */
    boolean seekRequested();

    /** Schedule every deferred intent on its owning thread; call once after the gate walk. Idempotent. */
    void flush();

    /** Set the WAIT delay (ticks) applied to subsequent effects' world-mutation intents, until changed (§3.6). */
    void delay(int ticks);
}
