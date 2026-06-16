package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import org.bukkit.Location;

/**
 * {@code STRIKE} — call a purely cosmetic lightning bolt at the activation location
 * (docs/architecture.md §7), distinct from {@code LIGHTNING}, which strikes and
 * damages a specific entity. Stateless and paramless; emits one location-targeted
 * {@code lightning} intent and never touches the world directly. No-op when the
 * activation carries no location ({@link EffectCtx#location()} may be {@code null}
 * for non-positional activations). {@link Affinity#REGION}: the strike routes to the
 * region thread owning the target location.
 */
public final class StrikeEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("STRIKE")
            .affinity(Affinity.REGION)
            .doc("Call a cosmetic lightning bolt at the activation location. No-op if the activation has no location.")
            .example("STRIKE")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Location loc = ctx.location();
        if (loc != null) {
            sink.lightning(loc);
        }
    }
}
