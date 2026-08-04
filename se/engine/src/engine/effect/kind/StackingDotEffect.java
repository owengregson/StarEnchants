package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code STACKING_DOT} — a rot ladder that only burns while its victims stand on the wearer's ground.
 *
 * <p>{@code PERIODIC_DAMAGE} is the near-miss and it is a different thing: its window is fixed at arm time,
 * so it burns a victim who has already run out of the field and never touches one who walks in. This one is a
 * FIELD — every pulse re-asks whose ground is under the victim's feet ({@code TEMP_BLOCK}'s placements carry
 * their placer), and a pulse spent off it costs nothing and adds nothing. Stepping off pauses the ramp;
 * stepping back inside {@code stack-ttl} resumes it where it stood.
 *
 * <p>The stack count belongs to the VICTIM, not to the pair: two wearers standing one player in overlapping
 * fields climb the same ladder, so a crowd cannot multiply the ladder's ceiling.
 */
public final class StackingDotEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("STACKING_DOT")
            .param("step", D.DOUBLE.min(0).def(2), "damage added per live stack, in raw half-hearts")
            .param("period", D.TICKS.min(1).def(10), "ticks between pulses")
            .param("cap", D.INT.min(1).def(6), "the most stacks one victim's ladder can reach")
            .param("stack-ttl", D.TICKS.min(1).def(60),
                    "how long a ladder survives after its last pulse — the grace for stepping off the ground")
            .param("lead-in", D.TICKS.min(1).def(20), "delay before the first pulse reads the ground")
            .param("duration", D.TICKS.min(1).def(200), "how long each target is watched")
            .param("message", D.STRING.def(""),
                    "line sent to the victim on each damaging pulse ({damage}, {stacks}); empty = silent")
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Watch each target for `duration` and, every `period` ticks they spend standing on ground the "
                    + "ACTIVATOR placed with `TEMP_BLOCK`, deal `step` x their live stack count as real "
                    + "(pre-armour) damage credited to the activator. Stacks climb one per damaging pulse to "
                    + "`cap` and lapse `stack-ttl` after the last one, so leaving the field pauses the ramp "
                    + "rather than resetting it. The ladder is PER VICTIM and shared across every attacker — "
                    + "two overlapping fields ramp one ladder, not two. The first pulse waits `lead-in`, which "
                    + "is what lets one activation lay its field and its watcher together.")
            .example("{ STACKING_DOT: { step: 2, period: 10, cap: 6, stack-ttl: 60, lead-in: 20, duration: 200, "
                    + "message: \"&c&l* DECAYING [&7-{damage}HP ({stacks} stacks)&c&l] *\", who: \"@Aoe\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        UUID owner = ctx.actor() == null ? null : ctx.actor().getUniqueId();
        if (owner == null) {
            return; // no placer, so no ground can ever be theirs
        }
        double step = ctx.dbl("step");
        int period = ctx.integer("period");
        int cap = ctx.integer("cap");
        int stackTtl = ctx.integer("stack-ttl");
        int leadIn = ctx.integer("lead-in");
        int duration = ctx.integer("duration");
        String message = ctx.str("message");
        for (LivingEntity target : ctx.targets("who")) {
            sink.stackingDot(target, ctx.actor(), owner, step, period, cap, stackTtl, leadIn, duration, message);
        }
    }
}
