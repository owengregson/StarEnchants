package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code FREEZE} — pin the target(s) fully frozen for {@code duration} (the vanilla powder-snow visual,
 * fire-coexistent via Paper's freeze-tick lock, ADR-0065), with an attacker-attributed {@code dot} every
 * {@code dot-period} ticks (raw pre-armor half-hearts, the ADR-0054 deferred path — bleed's) and a
 * {@code slow}% movement slow through the plugin-owned MOVEMENT_SPEED channel. A re-proc on a frozen
 * victim refreshes the window; it never stacks a second DoT. Optional {@code no-jump} pins the victim to
 * the ground for the window — a FLAG, not a redefinition of frozen, so every existing consumer keeps the
 * feel it was tuned at. The 1.17.1 floor degrades the visual while burning; 1.8.9 keeps DoT + slow only, and
 * {@code no-jump} with it (the recorded era degrades).
 */
public final class FreezeEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FREEZE")
            .param("duration", D.TICKS.def(60))
            .param("dot", D.DOUBLE.min(0).def(2))
            .param("dot-period", D.TICKS.def(20))
            .param("slow", D.DOUBLE.min(0).max(100).def(5))
            .param("neutralize-frost-slow", D.BOOL.def(true))
            .param("breakout-chance", D.DOUBLE.range(0, 100).def(0),
                    "percent chance per DoT pulse that the victim shatters the root early")
            .param("no-jump", D.BOOL.def(false),
                    "also stop the victim jumping for the window (modern lane only; inert on 1.8.9)")
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Fully freeze the target for a span of ticks (vanilla powder-snow visual: blue hearts + "
                    + "full vignette, held even while the victim burns), dealing dot damage every dot-period "
                    + "ticks (attributed to the activator; raw pre-armor half-hearts) and slowing them by "
                    + "slow percent. Re-procs refresh the window instead of stacking. neutralize-frost-slow "
                    + "cancels vanilla's own ~50% fully-frozen slow so the authored percent is the real one. "
                    + "breakout-chance rolls once per DoT pulse: on a hit the root shatters there and then, so "
                    + "a long freeze becomes a struggle the victim can win early instead of a fixed sentence. "
                    + "no-jump additionally pins the victim to the ground: a frozen player cannot jump out of "
                    + "the root. It is off by default because it re-tunes the feel of every freeze it is added "
                    + "to, and it is MODERN-ONLY — the 1.8.9 lane has no cancellable jump event, so a freeze "
                    + "there keeps its DoT and slow and the victim can still hop (the recorded era degrade, "
                    + "as with the powder-snow visual).")
            .example("{ FREEZE: { duration: 100, dot: 2, dot-period: 20, slow: 5 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int duration = ctx.integer("duration");
        double dot = ctx.dbl("dot");
        int dotPeriod = ctx.integer("dot-period");
        double slow = ctx.dbl("slow");
        boolean neutralize = ctx.bool("neutralize-frost-slow");
        double breakout = ctx.dbl("breakout-chance");
        boolean noJump = ctx.bool("no-jump");
        for (LivingEntity target : ctx.targets("who")) {
            // The activator attributes the DoT ticks (ADR-0054) — kill credit, era-combat delivery.
            sink.freeze(target, duration, dot, dotPeriod, slow, neutralize, breakout, noJump, ctx.actor());
        }
    }
}
