package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;

/**
 * {@code IGNORE_ARMOR} — make the triggering hit bypass the victim's armor + enchant-protection reduction
 * (§ combat-flags). An inline read-back like {@code CANCEL}: the combat dispatcher honours it by zeroing the
 * event's ARMOR/MAGIC modifiers after the fold (the reduction is the server's, not ours). Inert off combat.
 */
public final class IgnoreArmorEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("IGNORE_ARMOR")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Make the triggering hit ignore the victim's armor and enchant-protection reduction.")
            .example("{ IGNORE_ARMOR: {} }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.ignoreArmor();
    }
}
