package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.PotionCategories;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.Locale;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code CURE} — clear the target(s)' active potion effects, optionally only one {@code category}
 * (HARMFUL / BENEFICIAL / NEUTRAL); the default ALL is the broad counterpart of {@code REMOVE_POTION}.
 * {@code category: HARMFUL} is a "never hold a debuff" cleanse — clarity's Bless sweeps it on a timer.
 */
public final class CureEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("CURE")
            .param("category", D.enumOf("ALL", "HARMFUL", "BENEFICIAL", "NEUTRAL").def("ALL"))
            .param("count", D.INT.min(0).def(0), "remove at most this many effects; 0 = every match")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Clear active potion effects of one category from the target(s): ALL (default), HARMFUL, "
                    + "BENEFICIAL, or NEUTRAL. category HARMFUL strips only debuffs (positive effects "
                    + "untouched). count bounds how many matching effects are removed (0 = all of them) in the "
                    + "server's own enumeration order — a count: 1 HARMFUL cure strips exactly one debuff, "
                    + "whichever the server lists first.")
            .example("{ CURE: { category: HARMFUL } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int category = switch (ctx.str("category").toUpperCase(Locale.ROOT)) {
            case "HARMFUL" -> PotionCategories.HARMFUL;
            case "BENEFICIAL" -> PotionCategories.BENEFICIAL;
            case "NEUTRAL" -> PotionCategories.NEUTRAL;
            default -> PotionCategories.ALL;
        };
        int count = ctx.integer("count");
        for (LivingEntity target : ctx.targets("who")) {
            sink.cureByCategory(target, category, count);
        }
    }
}
