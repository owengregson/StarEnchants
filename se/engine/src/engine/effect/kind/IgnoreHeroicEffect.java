package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;

/**
 * {@code IGNORE_HEROIC} — drop the victim's heroic reduction from the triggering hit's damage fold
 * (ADR-0053 Midas). An inline read-back like {@code IGNORE_ARMOR}, honoured at the fold commit; the
 * attacker's own heroic weapon damage is untouched. Inert off combat.
 */
public final class IgnoreHeroicEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("IGNORE_HEROIC")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Make the triggering hit ignore the victim's heroic-upgrade damage reduction (percent and flat).")
            .example("{ IGNORE_HEROIC: {} }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.ignoreHeroic();
    }
}
