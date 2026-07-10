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
 * {@code PARTICLE} — spawn a burst of particles at the activation location (§7); {@code particle} interned at
 * compile (§9). ADR-0049: an optional {@code block} material carries BLOCK_CRACK/BLOCK_DUST data (Bleed's
 * redstone crack), and an optional {@code who} target slot emits a per-target burst read at the target's location
 * at dispatch time — when {@code who} resolves no entities (the default {@code @Here}), it falls back to the
 * activation location as before.
 */
public final class ParticleEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("PARTICLE")
            .param("particle", D.particle())
            .param("count", D.INT.min(0).def(1))
            .param("block", D.material().optional())
            .target("who", T.HERE)
            .affinity(Affinity.REGION)
            .doc("Spawn particles at the activation location, or at each entity in `who` when given. `block` "
                    + "carries a block material as crack/dust data. No-op if there is no location.")
            .example("{ PARTICLE: { particle: BLOCK_CRACK, count: 20, block: REDSTONE_BLOCK, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int particleId = ctx.integer("particle");
        int count = ctx.integer("count");
        int blockId = ctx.args().has("block") ? ctx.integer("block") : -1; // optional block-crack material → interned id, or none
        java.util.Iterator<LivingEntity> targets = ctx.targets("who").iterator();
        if (targets.hasNext()) {
            // who resolved entities: a per-target burst read at each target's own location at dispatch time.
            do {
                sink.particle(targets.next(), particleId, count, blockId);
            } while (targets.hasNext());
            return;
        }
        org.bukkit.Location loc = ctx.location(); // no who (default @Here): the original activation-location burst
        if (loc != null) {
            sink.particle(loc, particleId, count, blockId);
        }
    }
}
