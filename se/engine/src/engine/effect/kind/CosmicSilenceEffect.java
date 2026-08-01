package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/** {@code COSMIC_SILENCE} — exact Silence + same-item Solitude composition. */
public final class CosmicSilenceEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("COSMIC_SILENCE")
            .param("level", D.INT.min(1).max(4))
            .param("solitude", D.STRING.def("enchants/solitude"))
            .param("sound", D.sound())
            .param("enchantment-particle", D.particle())
            .param("portal-particle", D.particle())
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Roll Cosmic Silence at 2% per Silence+held-Solitude level, honor Dragon Slayer's 75% block, "
                    + "then suppress only defense procs for one second per combined level with exact feedback.")
            .example("{ COSMIC_SILENCE: { level: 4, sound: WITHER_HURT, "
                    + "enchantment-particle: ENCHANTMENT_TABLE, portal-particle: PORTAL, who: '@Victim' } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Player actor = ctx.actor();
        if (actor == null) {
            return;
        }
        int combined = ctx.integer("level") + HeldEnchantLevels.held(actor, ctx.str("solitude"));
        if (ThreadLocalRandom.current().nextDouble() >= combined * 0.02) {
            return;
        }
        for (LivingEntity target : ctx.targets("who")) {
            if (!(target instanceof Player victim)) {
                continue;
            }
            if (ActiveSets.has(victim, "sets/dragon-slayer")
                    && ThreadLocalRandom.current().nextDouble() < 0.75) {
                sink.message(actor, "&c&l* SILENCE BLOCKED [&7" + victim.getName() + "&c&l] *");
                continue;
            }
            sink.cosmicSilence(victim, combined * 20, ctx.sourceDefId(), ctx.integer("sound"),
                    ctx.integer("enchantment-particle"), ctx.integer("portal-particle"));
        }
    }
}
