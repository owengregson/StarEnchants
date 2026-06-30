package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.CombatTag;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * {@code FLY_MODE} — grant flight to the target(s) while they are NOT in combat, revoke it while they are
 * (supreme's Gifted Child). Author on {@code trigger: [REPEATING, PASSIVE]}: REPEATING re-checks each period,
 * PASSIVE's {@link #stop} clears flight on unequip so it can never leak. Reads the wall-clock {@link CombatTag}
 * (written by {@code CombatDispatch} on every hit); flight only toggles in survival/adventure (the Sink guards).
 */
public final class FlyModeEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FLY_MODE")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Grant flight to the target(s) while NOT in combat, revoke it while in combat (survival/adventure "
                    + "only). Author on trigger [REPEATING, PASSIVE] with a repeat period so it re-checks and "
                    + "tears down on unequip.")
            .example("{ FLY_MODE: { who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity who : ctx.targets("who")) {
            if (who instanceof Player p) {
                sink.flyMode(p, !CombatTag.inCombat(p.getUniqueId())); // allow when out of combat, revoke when in
            }
        }
    }

    @Override
    public void stop(EffectCtx ctx, Sink sink) {
        for (LivingEntity who : ctx.targets("who")) {
            if (who instanceof Player p) {
                sink.flyMode(p, false); // unequip → revoke
            }
        }
    }
}
