package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code SET_BLOCK} — set the block at the activation location to a material (docs/v3-directives.md §C).
 * Stateless; emits one {@code blockChange} intent and never touches the world directly. The {@code material}
 * is a handle arg authored as a token (e.g. {@code OBSIDIAN}) and resolved to an interned id at compile time,
 * read with {@link EffectCtx#integer} (§9). No-op when there is no activation location. {@link Affinity#REGION}:
 * the change routes to the region thread owning the location.
 */
public final class SetBlockEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SET_BLOCK")
            .param("material", D.material())
            .affinity(Affinity.REGION)
            .doc("Set the block at the activation location to a material. No-op if there is no location.")
            .example("SET_BLOCK:OBSIDIAN")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Location loc = ctx.location();
        if (loc != null) {
            sink.blockChange(loc, ctx.integer("material"));
        }
    }
}
