package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;

/**
 * {@code ECHO_STRIKE} — re-run the attacker-side activation walk once over the same hit (ADR-0049 Double Strike),
 * so enchants can re-proc and both passes' damage folds into the ONE event. No params, no target: it just requests
 * the extra pass; the combat dispatcher runs it exactly once (a second request during the echo pass is ignored).
 */
public final class EchoStrikeEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("ECHO_STRIKE")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Re-run the attack activation once over the same hit (enchants can re-proc); all damage folds "
                    + "into the one event. No effect off the attack side.")
            .example("{ ECHO_STRIKE: {} }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.requestEchoStrike();
    }
}
