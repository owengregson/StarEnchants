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
 * {@code CONVERT_SUMMON} — the Summoner's Bell (ADR-0071): every tracked enemy-owned summon within
 * {@code radius} blocks permanently changes sides — ownership (and GUARDIAN_HURT) rebinds to the wearer,
 * Tameables are re-tamed, converted guards target their FORMER owner, and enemy bat clouds are turned to
 * orbit/blind their former owner. Unowned wild spawns have no side to flip and are untouched.
 */
public final class ConvertSummonEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("CONVERT_SUMMON")
            .param("radius", D.DOUBLE.range(1, 32).def(12))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Convert every enemy-summoned ally within `radius` blocks of the wearer to the "
                    + "wearer's side, permanently: summoned guards/zombies/sentries/mounts rebind "
                    + "their ownership (a hit on them now fires the wearer's GUARDIAN_HURT), "
                    + "targeting summons turn on their former owner, tamed summons re-tame, and "
                    + "bat swarm clouds permanently swarm their former owner instead.")
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
                sink.convertSummons(p, ctx.dbl("radius"));
            }
        }
    }
}
