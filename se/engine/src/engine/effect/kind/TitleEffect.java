package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code TITLE} — show a centered title (and optional subtitle) to the activating
 * player (docs/architecture.md §7), the larger-format sibling of {@code MESSAGE} and
 * {@code ACTIONBAR}. Stateless; emits one {@code title} intent for the actor and
 * never touches the player directly. The fade-in / stay / fade-out durations are in
 * ticks. {@link Affinity#CONTEXT_LOCAL}: it applies on the firing thread.
 */
public final class TitleEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("TITLE")
            .param("title", D.STRING)
            .param("subtitle", D.STRING.def(""))
            .param("fadeIn", D.TICKS.def(10))
            .param("stay", D.TICKS.def(70))
            .param("fadeOut", D.TICKS.def(20))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Show a title + subtitle to the activating player with fade-in / stay / fade-out timings (ticks).")
            .example("TITLE:&cCRITICAL:&7you struck hard:10:40:10")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.title(ctx.actor(), ctx.str("title"), ctx.str("subtitle"),
                ctx.integer("fadeIn"), ctx.integer("stay"), ctx.integer("fadeOut"));
    }
}
