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
 * {@code SUPPRESS_IMMUNE} — a maintained PASSIVE flag making the wearer immune to enchant-cancelling
 * (DISABLE_ENCHANT/GROUP/TYPE no-op against them). Dragon's Dovahkiin. Armed on equip ({@link #run}) and lifted
 * on unequip ({@link #stop}) by the HELD/PASSIVE lifecycle (ADR-0022), so it can never leak. The counterpart to
 * {@code SUPPRESS}: where that silences a target, this makes a target unsilenceable.
 *
 * <p>The optional {@code chance} (default 100 = absolute immunity) makes each suppression a per-event roll, so a
 * crystal can grant a PARTIAL "ignore Silence" (ADR-0034, e.g. Chaos's 4%).
 */
public final class SuppressImmuneEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SUPPRESS_IMMUNE")
            .param("chance", D.INT.min(0).max(100).def(100))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Make the target(s) immune to suppression (DISABLE_ENCHANT/GROUP/TYPE) while worn — a maintained "
                    + "PASSIVE flag, armed on equip and lifted on unequip. An optional chance (default 100) makes "
                    + "it a per-suppression roll instead of absolute. Player-only.")
            .example("{ SUPPRESS_IMMUNE: { chance: 4, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        toggle(ctx, sink, ctx.integer("chance")); // arm at the configured chance (default 100 = absolute)
    }

    @Override
    public void stop(EffectCtx ctx, Sink sink) {
        toggle(ctx, sink, 0); // unequip → lift the immunity entirely
    }

    private static void toggle(EffectCtx ctx, Sink sink, int chance) {
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.suppressImmune(p, chance);
            }
        }
    }
}
