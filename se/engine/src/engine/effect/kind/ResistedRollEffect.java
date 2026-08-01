package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectHalt;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code RESISTED_ROLL} — one random roll first tests the base proc chance, then the same roll tests a
 * resistance-adjusted chance. A miss silently halts the ability; a resisted hit emits its optional message
 * and halts. This preserves mechanics where resistance narrows an already-rolled proc window.
 */
public final class ResistedRollEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("RESISTED_ROLL")
            .param("chance", D.DOUBLE.min(0).max(100))
            .param("resistance-level", D.DOUBLE.min(0).def(0))
            .param("resist-per-level", D.DOUBLE.min(0).def(0))
            .param("min-chance", D.DOUBLE.min(0).max(100).def(0))
            .param("blocked-message", D.STRING.def(""))
            .param("immune", D.DOUBLE.min(0).def(0))
            .param("immune-message", D.STRING.def(""))
            .param("immune-chance", D.DOUBLE.min(0).max(100).def(0))
            .param("immune-chance-message", D.STRING.def(""))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Gate the remaining effects with one base-chance roll and the same roll against chance minus"
                    + " resistance-level*resist-per-level. Values are percentages.")
            .example("{ RESISTED_ROLL: { chance: 12, resistance-level: '%victim.metaphysical%',"
                    + " resist-per-level: 2.5, blocked-message: '&8&l** METAPHYSICAL blocked! **' } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double base = ctx.dbl("chance");
        double roll = ThreadLocalRandom.current().nextDouble(100.0);
        if (roll >= base) {
            throw EffectHalt.INSTANCE;
        }
        double adjusted = Math.max(ctx.dbl("min-chance"),
                base - ctx.dbl("resistance-level") * ctx.dbl("resist-per-level"));
        if (roll > adjusted) {
            if (ctx.victim() instanceof Player victim && !ctx.str("blocked-message").isEmpty()) {
                sink.message(victim, ctx.str("blocked-message"));
            }
            throw EffectHalt.INSTANCE;
        }
        if (ctx.dbl("immune") > 0) {
            if (ctx.victim() instanceof Player victim && !ctx.str("immune-message").isEmpty()) {
                sink.message(victim, ctx.str("immune-message"));
            }
            throw EffectHalt.INSTANCE;
        }
        double immuneChance = ctx.dbl("immune-chance");
        if (immuneChance > 0 && ThreadLocalRandom.current().nextDouble(100.0) <= immuneChance) {
            if (ctx.victim() instanceof Player victim && !ctx.str("immune-chance-message").isEmpty()) {
                sink.message(victim, ctx.str("immune-chance-message"));
            }
            throw EffectHalt.INSTANCE;
        }
    }
}
