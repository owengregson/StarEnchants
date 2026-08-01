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

/** {@code SOUL_TRAP} — Cosmic's atomic soul-mode trap, drain, fallback hurt, and feedback transaction. */
public final class SoulTrapEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SOUL_TRAP")
            .param("key", D.STRING, "soul group key; compile-lowered into the shared suppression namespace")
            .param("duration", D.TICKS)
            .param("retrap-grace", D.TICKS.def(100))
            .param("steal", D.INT.min(1))
            .param("fallback-damage", D.DOUBLE.min(0))
            .param("cost", D.INT.min(0).def(5))
            .param("cost-period", D.TICKS.def(20))
            .param("anti-swap", D.TICKS.def(5))
            .param("message", D.STRING)
            .param("sound", D.sound())
            .param("sound-volume", D.DOUBLE.min(0).def(1))
            .param("sound-pitch", D.DOUBLE.min(0).def(1))
            .param("particle-1", D.particle())
            .param("particle-1-count", D.INT.min(0))
            .param("particle-1-speed", D.DOUBLE.min(0))
            .param("particle-2", D.particle())
            .param("particle-2-count", D.INT.min(0))
            .param("particle-2-speed", D.DOUBLE.min(0))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Require active attacker soul mode and a settled held slot, enforce trap/grace windows, "
                    + "rate-limit the attacker cost, steal victim souls or deal fallback damage, suppress the "
                    + "authored soul group, disable victim soul mode, and emit exact private feedback.")
            .example("{ SOUL_TRAP: { key: soul, duration: 80, steal: 10, fallback-damage: 2, "
                    + "message: \"&9&l** SOUL TRAP &7[4s]&9&l**\", sound: ENDERMAN_SCREAM, "
                    + "particle-1: SPELL_WITCH, particle-1-count: 60, particle-1-speed: 0.7, "
                    + "particle-2: SPELL, particle-2-count: 25, particle-2-speed: 0.4 } }")
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
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player player) {
                sink.soulTrap(actor, player, ctx.activeGem(), ctx.integer("key"), ctx.integer("duration"),
                        ctx.integer("retrap-grace"), ctx.integer("steal"), ctx.dbl("fallback-damage"),
                        ctx.integer("cost"), ctx.integer("cost-period"), ctx.integer("anti-swap"),
                        ctx.str("message"), ctx.integer("sound"), (float) ctx.dbl("sound-volume"),
                        (float) ctx.dbl("sound-pitch"), ctx.integer("particle-1"),
                        ctx.integer("particle-1-count"), ctx.dbl("particle-1-speed"),
                        ctx.integer("particle-2"), ctx.integer("particle-2-count"),
                        ctx.dbl("particle-2-speed"), ctx.sourceDefId());
            }
        }
    }
}
