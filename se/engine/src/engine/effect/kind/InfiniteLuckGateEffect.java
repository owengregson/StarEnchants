package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectHalt;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/** {@code INFINITE_LUCK_GATE} — stop one incoming set bonus when the victim's Infinite Luck blocks it. */
public final class InfiniteLuckGateEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("INFINITE_LUCK_GATE")
            .param("required-level", D.INT.min(1).max(5))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Halt the current armor-set ability when the victim has Infinite Luck at the required level. "
                    + "Each equipped heroic armor piece independently contributes 12.5% to the source-code "
                    + "counter-roll that lets the set bonus through.")
            .example("{ INFINITE_LUCK_GATE: { required-level: 3, who: '@Victim' } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int required = ctx.integer("required-level");
        for (LivingEntity target : ctx.targets("who")) {
            if (!(target instanceof Player victim)) {
                continue;
            }
            int level = EnchantLevels.worn(victim, "enchants/infinite-luck");
            if (level >= required && sink.infiniteLuckBlocks(victim, required, level,
                    HeroicArmorPieces.count(victim))) {
                throw EffectHalt.INSTANCE;
            }
        }
    }
}
