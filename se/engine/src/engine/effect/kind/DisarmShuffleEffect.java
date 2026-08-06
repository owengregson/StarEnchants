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
 * {@code DISARM_SHUFFLE} — arm the Unhanding window (ADR-0071): the wearer's next landed melee hit on a
 * player within {@code duration} ticks swaps the victim's held item into a random OTHER hotbar slot
 * (re-selectable — shuffled, not locked) and deals {@code damage-malus}% less damage through the fold's
 * self-malus channel. NOT the {@code DISARM} head (that drops the item to the ground).
 */
public final class DisarmShuffleEffect implements EffectKind {

    public static final String HEAD = "DISARM_SHUFFLE";

    static final EffectSpec SPEC = EffectSpec.of(HEAD)
            // Both read INSIDE the target loop, so each armed body prices itself (ADR-0076); declared, because
            // PerTargetParamTest counts the reads and a hoist would otherwise pass silently.
            .param("duration", D.TICKS.def(80).perTarget())
            .param("damage-malus", D.DOUBLE.range(0, 100).def(20).perTarget())
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Arm an unhanding window on the wearer for `duration` ticks: their next landed "
                    + "melee hit on a player knocks the victim's held item into a random other "
                    + "hotbar slot (the victim can re-select it — shuffled, not locked; weapon-"
                    + "gated combos like Rage break naturally) and that hit deals `damage-malus`% "
                    + "less damage. One shot; a dodged/negated hit keeps the window armed.")
            .example("{ DISARM_SHUFFLE: { duration: 80, damage-malus: 20 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity who : ctx.targets("who")) {
            if (who instanceof Player p) {
                sink.armDisarmShuffle(p, ctx.integer("duration"), ctx.dbl("damage-malus") / 100.0);
            }
        }
    }
}
