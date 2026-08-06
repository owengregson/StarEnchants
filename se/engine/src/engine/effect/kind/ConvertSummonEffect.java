package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code CONVERT_SUMMON} — the Grand Bell (ADR-0071): EVERY mob within {@code radius} blocks permanently
 * joins the wearer's side (owner ruling) — ownership (and GUARDIAN_HURT) rebinds to the wearer, Tameables
 * are re-tamed, and each convert is turned on an enemy (an enemy summon on its FORMER owner, a wild mob on
 * the wearer's nearest enemy player), and enemy bat clouds are turned to orbit/blind their former owner.
 * Only Players, ArmorStands, and the wearer are exempt.
 */
public final class ConvertSummonEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("CONVERT_SUMMON")
            // Read INSIDE the target loop, so each body prices its own ring (ADR-0076); declared, because
            // PerTargetParamTest counts the reads and a hoist would otherwise pass silently.
            .param("radius", D.DOUBLE.range(1, 32).def(12).perTarget())
            .param("whiff-sound", D.sound().def("BLOCK_ANVIL_LAND"),
                    "played (low-pitched) when the ring converts nothing — the ring sound alone reads "
                            + "as success")
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Convert EVERY mob within `radius` blocks of the wearer to the wearer's side, "
                    + "permanently: each rebinds its ownership (a hit on it now fires the wearer's "
                    + "GUARDIAN_HURT) and tamed mobs re-tame; an enemy summon turns on its former "
                    + "owner, a wild mob on the wearer's nearest enemy player, and bat swarm clouds "
                    + "permanently swarm their former owner. Only players, armour stands and the "
                    + "wearer are exempt.")
            .example("{ CONVERT_SUMMON: { radius: 12 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity who : ctx.targets("who")) {
            if (who instanceof Player p) {
                sink.convertSummons(p, ctx.dbl("radius"), ctx.integer("whiff-sound"));
            }
        }
    }
}
