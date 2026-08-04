package engine.sink;

import engine.interact.DamageFold;
import org.bukkit.entity.LivingEntity;

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

    /** The accumulated EXP_MULTIPLY factor (1.0 = unchanged); read by the EXP_GAIN and MINE dispatchers. */
    double expMultiplier();

    /** Whether an effect asked the triggering hit to ignore armor (§ combat-flags). */
    boolean armorIgnored();

    /** Whether an effect asked the triggering block-break to auto-smelt (SMELT). */
    boolean smeltRequested();

    /** Whether an effect asked the broken block's drops to go to the breaker's inventory (TELEPORT_DROPS). */
    boolean teleportDropsRequested();

    /** Whether an effect asked the fired projectile to home onto a target (AUTO_LOCK). */
    boolean seekRequested();

    /** Whether an effect requested an extra attacker-side echo pass (ECHO_STRIKE). Read by the combat dispatcher. */
    boolean echoRequested();

    /** The rider an effect asked to seat on the fired projectile (PROJECTILE_DRESSING), or {@code null} for none. */
    ProjectileDressing projectileDressing();

    /**
     * Declare the entity whose pending damage the firing event itself will still apply (the combat victim):
     * zero-WAIT health writes to it run inline at {@link #flush()} — before the event's outcome — so a
     * same-hit heal participates in the vanilla kill decision instead of racing it (ADR-0051), and
     * zero-WAIT {@code damage} intents to it join the damage fold — one hurt, one immunity window — so a
     * same-hit DAMAGE rider never window-rejects the next melee (ADR-0054). Dispatchers whose event
     * carries no such entity simply never call this; the default is a no-op so non-combat sinks and test
     * doubles are unaffected.
     */
    default void eventEntity(LivingEntity entity) {
    }

    /**
     * Open the PROC_REBOUND window: subsequent {@link #fold()} contributions accumulate against the ORIGINAL
     * ATTACKER instead of the incoming event, so a re-executed damage-mod is felt by the person who threw it
     * (ADR-0054 stands — no second dispatch pass, no re-entry). Close it with {@link #endRebound()}.
     * A no-op default: a sink that carries no second accumulator simply never rebounds.
     */
    default void beginRebound() {
    }

    /** Close the PROC_REBOUND window — contributions return to the incoming event's fold. */
    default void endRebound() {
    }

    /**
     * The marginal damage the rebound window accumulated over {@code base}, for the dispatcher to commit
     * against the attacker; {@code 0} when nothing rebounded. Read once, after the walks.
     */
    default double reboundContribution(double base) {
        return 0.0;
    }

    /** Schedule every deferred intent on its owning thread; call once after the gate walk. Idempotent. */
    void flush();

    /** Set the WAIT delay (ticks) applied to subsequent effects' world-mutation intents, until changed (§3.6). */
    void delay(int ticks);
}
