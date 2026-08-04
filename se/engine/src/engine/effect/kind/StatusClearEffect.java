package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.sink.StatusKinds;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code STATUS_CLEAR} — lift a named ENGINE status window off a target.
 *
 * <p>{@code CURE} and {@code REMOVE_POTION} only reach vanilla potion effects. The engine's own windows —
 * teleblock, a potion lock, a disarm, a freeze — are plugin state with no cleanse at all, so a counterplay item
 * aimed at one had nothing to author. {@code TRAP_BREAK} is the sole precedent and it is scoped to physical
 * confinement, which is why it thaws a freeze but cannot lift a teleblock.
 *
 * <p>The status is an enum rather than a free string so an unknown name is a compile diagnostic instead of a
 * silent no-op — an item whose whole purpose is removing one window must not ship removing nothing.
 */
public final class StatusClearEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("STATUS_CLEAR")
            .param("status", D.enumOf("TELEBLOCK", "POTION_LOCK", "DISARM", "FREEZE"),
                    "which engine window to lift")
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Remove an active engine status window from each target: TELEBLOCK (the teleport denial), "
                    + "POTION_LOCK (every potion denial held on them), DISARM (the armed disarm window), or "
                    + "FREEZE (a live freeze, with its damage-over-time and both movement modifiers). "
                    + "Unlike CURE this touches no potion EFFECT — it lifts the plugin state that was denying "
                    + "one. Clearing a window nobody holds is a silent no-op, so the authored condition "
                    + "decides what a wasted use costs.")
            .example("{ STATUS_CLEAR: { status: TELEBLOCK, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int status = wireCode(ctx.str("status"));
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player player) {
                sink.clearStatus(player, status);
            }
        }
    }

    /** The authored token as the sink's wire code — resolved ONCE, above the fan-out loop. */
    private static int wireCode(String status) {
        return switch (status) {
            case "POTION_LOCK" -> StatusKinds.POTION_LOCK;
            case "DISARM" -> StatusKinds.DISARM;
            case "FREEZE" -> StatusKinds.FREEZE;
            default -> StatusKinds.TELEBLOCK; // the enum is closed at compile; TELEBLOCK is its first rung
        };
    }
}
