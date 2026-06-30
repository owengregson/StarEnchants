package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * {@code SUPPRESS_IMMUNE} — a maintained PASSIVE flag making the wearer immune to ALL enchant-cancelling
 * (DISABLE_ENCHANT/GROUP/TYPE no-op against them). Dragon's Dovahkiin. Armed on equip ({@link #run}) and lifted
 * on unequip ({@link #stop}) by the HELD/PASSIVE lifecycle (ADR-0022), so it can never leak. The counterpart to
 * {@code SUPPRESS}: where that silences a target, this makes a target unsilenceable.
 */
public final class SuppressImmuneEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SUPPRESS_IMMUNE")
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Make the target(s) immune to all suppression (DISABLE_ENCHANT/GROUP/TYPE) while worn — a "
                    + "maintained PASSIVE flag, armed on equip and lifted on unequip. Player-only.")
            .example("{ SUPPRESS_IMMUNE: { who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        toggle(ctx, sink, true);
    }

    @Override
    public void stop(EffectCtx ctx, Sink sink) {
        toggle(ctx, sink, false);
    }

    private static void toggle(EffectCtx ctx, Sink sink, boolean on) {
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.suppressImmune(p, on);
            }
        }
    }
}
