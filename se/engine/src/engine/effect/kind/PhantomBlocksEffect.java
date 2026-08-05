package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import platform.caps.Regions;
import schema.spec.D;

/**
 * {@code PHANTOM_BLOCKS} — repaint the ground under a fight per VIEWER, so the actor's side and everyone else
 * are looking at two different battlefields. Rot and Decay's only tell that a field is there at all.
 *
 * <p>{@code TEMP_BLOCK}/{@code WALKER} are the near misses and are a different thing: they place REAL blocks,
 * which every viewer sees identically, which physics and pistons see, and which a proc would have to rewrite
 * across a 13x13 patch of somebody's build. Nothing here touches the world — the overlay is packets, it blocks
 * nothing, and it cannot be mined, exploded or duplicated.
 *
 * <p>The revert re-sends what the ground REALLY holds when the window closes, not an arm-time snapshot, so a
 * block mined during the overlay converges to truth rather than being stranded as a ghost. A client that
 * relogs is served the true chunk by the server, which is why there is no rejoin hook here.
 *
 * <p>Placing nothing does not mean claiming nothing: the patch is registered with {@code PhantomFields} for the
 * window, so {@code OWNED_GROUND} answers for it exactly as it does for a placed floor. Without that a field
 * had a look and no claimant, and the ramping {@code STACKING_DOT} half of the one enchant that lays one never
 * found anybody standing in it.
 */
public final class PhantomBlocksEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("PHANTOM_BLOCKS")
            // Capped at 8 (17x17) rather than the ladder's own 6: each column is one packet per viewer, and
            // nothing authored needs a wider illusion than the ground two players can stand on.
            .param("radius", D.INT.range(0, 8).def(3), "blocks each way from the target the overlay covers")
            .param("material-ally", D.material().def("GLOWSTONE"), "what the actor and their allies are shown")
            .param("material-enemy", D.material().def("END_STONE"), "what everyone else is shown")
            .param("duration", D.TICKS.min(1).def(200), "ticks before the real ground is sent back")
            .target("who", T.SELF)
            .affinity(Affinity.REGION)
            .doc("Show every nearby player a client-only overlay across the qualifying surface of the "
                    + "(2*radius+1)^2 patch under each target for `duration` ticks: material-ally to the actor "
                    + "and anyone allied to them, material-enemy to everyone else. A column qualifies when its "
                    + "first solid block down from the target's own level is a full opaque cube with a passable "
                    + "cell above it — see-through floors, roofed columns and anything more than a few steps "
                    + "below are skipped. NOTHING is written to the world: the patch blocks no movement, breaks "
                    + "nothing and survives no reload, and the window's close re-sends the ground as it really "
                    + "is then (so a block mined meanwhile is not stranded). A viewer who relogs is served the "
                    + "true chunk by the server. The patch IS the actor's owned ground for the window, so "
                    + "%actor.ownedground% and a STACKING_DOT laid over it both see who is standing in it.")
            .example("{ PHANTOM_BLOCKS: { radius: 4, material-ally: GLOWSTONE, material-enemy: END_STONE, "
                    + "duration: 100, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int radius = ctx.integer("radius");
        int ally = ctx.integer("material-ally");
        int enemy = ctx.integer("material-enemy");
        int duration = ctx.integer("duration");
        for (LivingEntity who : ctx.targets("who")) {
            Location origin;
            try {
                origin = who.getLocation(); // guarded like every other field origin: @Attacker can be cross-region (ADR-0043)
            } catch (RuntimeException unreadable) {
                Regions.swallowed("PhantomBlocksEffect.origin", unreadable);
                continue;
            }
            if (origin.getWorld() == null) {
                continue;
            }
            sink.phantomBlocks(origin, ctx.actor(), radius, ally, enemy, duration);
        }
    }
}
