package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;

/**
 * {@code IGNORE_ARMOR} — make the triggering hit bypass the victim's armor (and enchant-protection) damage
 * reduction (docs/v3-directives.md § combat-flags). Stateless and paramless; emits a single {@code
 * ignoreArmor} read-back which the combat dispatcher honours by zeroing the event's ARMOR/MAGIC damage
 * modifiers after the fold (the reduction is the server's, not ours). Inert on a non-combat trigger.
 * {@link Affinity#CONTEXT_LOCAL}: it feeds back into the firing event on the firing thread, like {@code CANCEL}.
 */
public final class IgnoreArmorEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("IGNORE_ARMOR")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Make the triggering hit ignore the victim's armor and enchant-protection reduction.")
            .example("IGNORE_ARMOR")
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
