package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import platform.caps.Regions;
import schema.spec.D;

/**
 * {@code CAGE} — the ADR-0052 Cage pet: a temporary sealed cage (floor + roof plates, a wall ring, an AIR
 * {@code width × height × depth} interior) built {@code rise} blocks above the midpoint between the activator
 * and the target, with BOTH parties teleported to opposite interior cells facing each other. The Sink owns
 * the safety check (the FULL structure volume must be air or nothing happens), the per-tile region re-keyed
 * placement through the shared {@code TempBlockLedger}, and the teleports — this kind only computes the base
 * point and emits one intent per target.
 */
public final class CageEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("CAGE")
            .param("floor", D.material())
            .param("walls", D.material())
            .param("roof", D.material())
            .param("width", D.INT.range(1, 8).def(3))
            .param("height", D.INT.range(2, 8).def(4))
            .param("depth", D.INT.range(1, 8).def(3))
            .param("rise", D.INT.range(0, 8).def(2))
            .param("ticks", D.TICKS.def(150))
            .target("who", T.VICTIM)
            .affinity(Affinity.REGION)
            .actorOrigin()
            .doc("Trap the target AND the activator in a temporary cage: floor/roof plates, a walls ring, an "
                    + "air width × height × depth interior, base-centred rise blocks above the midpoint between "
                    + "the two, reverting after ticks. The full volume is safety-checked (every cell must be "
                    + "air) before anything is placed; both parties teleport to opposite interior cells facing "
                    + "each other. Gate the ability on a target existing (e.g. %nearbyenemies% >= 1) so a "
                    + "no-target use fails BEFORE the cooldown arms.")
            .example("{ CAGE: { floor: STONE_BRICKS, walls: IRON_BARS, roof: STONE_BRICKS, ticks: 150, "
                    + "who: \"@NearestPlayer{r=10}\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Player actor = ctx.actor();
        Location origin = ctx.actorOrigin(); // demand-captured actor snapshot (ADR-0043)
        if (actor == null || origin == null) {
            return;
        }
        int floor = ctx.integer("floor");
        int walls = ctx.integer("walls");
        int roof = ctx.integer("roof");
        int width = ctx.integer("width");
        int height = ctx.integer("height");
        int depth = ctx.integer("depth");
        int rise = ctx.integer("rise");
        int ticks = ctx.integer("ticks");
        for (LivingEntity who : ctx.targets("who")) {
            if (who == actor) {
                continue; // the cage needs a second party; caging yourself alone is never the intent
            }
            Location at;
            try {
                at = who.getLocation();
            } catch (RuntimeException unreadable) {
                Regions.swallowed("CageEffect.target", unreadable);
                continue;
            }
            if (at.getWorld() == null || at.getWorld() != origin.getWorld()) {
                continue;
            }
            Location base = new Location(at.getWorld(),
                    Math.floor((origin.getX() + at.getX()) / 2.0),
                    Math.floor(Math.min(origin.getY(), at.getY())) + rise,
                    Math.floor((origin.getZ() + at.getZ()) / 2.0));
            sink.cage(base, actor, who, floor, walls, roof, width, height, depth, ticks);
        }
    }
}
