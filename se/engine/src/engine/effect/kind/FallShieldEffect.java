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
 * {@code FALL_SHIELD} — give a player one free fall inside a window.
 *
 * <p>The only fall knob before this was the {@code FALL} trigger, which fires off the falling player's OWN
 * gear. An ability that throws, freezes or displaces a stranger cannot reach that: the person about to hit the
 * ground need carry nothing at all. This arms the cancel on the VICTIM's side, so a proc can take
 * responsibility for the drop it caused.
 *
 * <p>One shot, not a window of immunity: the first fall inside {@code window} is cancelled and the shield is
 * spent, so a displacement proc pays for exactly the landing it arranged and not for the next cliff.
 */
public final class FallShieldEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FALL_SHIELD")
            .param("window", D.TICKS.min(1).def(200), "how long the unspent shield waits for a fall")
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Arm a ONE-SHOT cancel of each target's next fall damage within `window` ticks. The target "
                    + "need not carry any enchant — this is how a proc that displaces someone pays for their "
                    + "landing. Re-arming refreshes the window; it never banks a second shield.")
            .example("{ FALL_SHIELD: { window: 200, who: \"@AOE{radius: 8, filter: ENEMIES}\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int window = ctx.integer("window");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player player) {
                sink.fallShield(player, window);
            }
        }
    }
}
