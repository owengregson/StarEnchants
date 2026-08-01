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
            .param("spread", D.DOUBLE.min(0).max(4).def(0.4))
            .param("spread-y", D.DOUBLE.min(-1).max(4).def(-1))
            .param("random-spread", D.BOOL.def(false), "choose each authored axis spread independently in [0, spread)")
            .param("speed", D.DOUBLE.min(0).def(0), "particle packet/API speed-extra value")
            .param("anchor", D.enumOf("body", "feet", "eye").def("body"),
                    "entity target anchor; ignored for @Here")
            .param("y-offset", D.DOUBLE.min(-16).max(16).def(0),
                    "vertical position offset after selecting the anchor")
            .target("who", T.HERE)
            .affinity(Affinity.REGION)
            .doc("Spawn particles at the activation location, or at each entity in `who` when given (centered on "
                    + "the body, not the feet). `block` carries a block material as crack/dust data. `spread` is the "
                    + "horizontal Gaussian offset (set 0 for a point burst); `spread-y` the vertical offset, where "
                    + "the -1 default means \"use `spread`\". No-op if there is no location.")
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
        double spread = ctx.dbl("spread");
        double spreadY = spreadY(spread, ctx.dbl("spread-y")); // horizontal offset on X/Z; the -1 sentinel folds Y onto it
        double spreadX = spread;
        double spreadZ = spread;
        if (ctx.bool("random-spread")) {
            java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
            spreadX = random.nextDouble() * spread;
            spreadY = random.nextDouble() * spreadY;
            spreadZ = random.nextDouble() * spread;
        }
        double speed = ctx.dbl("speed");
        double yOffset = ctx.dbl("y-offset");
        int anchor = switch (ctx.str("anchor").toLowerCase(java.util.Locale.ROOT)) {
            case "feet" -> 1;
            case "eye" -> 2;
            default -> 0;
        };
        java.util.Iterator<LivingEntity> targets = ctx.targets("who").iterator();
        if (targets.hasNext()) {
            // who resolved entities: a per-target burst read at each target's own location at dispatch time.
            do {
                sink.particle(targets.next(), particleId, count, blockId, spreadX, spreadY, spreadZ, speed,
                        anchor, yOffset);
            } while (targets.hasNext());
            return;
        }
        org.bukkit.Location loc = ctx.location(); // no who (default @Here): the original activation-location burst
        if (loc != null) {
            org.bukkit.Location at = yOffset == 0.0 ? loc : loc.clone().add(0.0, yOffset, 0.0);
            sink.particle(at, particleId, count, blockId,
                    spreadX, spreadY, spreadZ, speed);
        }
    }

    /** The vertical particle offset: a negative {@code spreadY} (the {@code -1} default sentinel) folds onto {@code spread}. */
    static double spreadY(double spread, double spreadY) {
        return spreadY < 0 ? spread : spreadY;
    }
}
