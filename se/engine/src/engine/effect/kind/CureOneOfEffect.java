package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.Arrays;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/** {@code CURE_ONE_OF} — remove the first active potion whose type is in an authored allow-list. */
public final class CureOneOfEffect implements EffectKind {

    private static final int MAX_EFFECTS = 16;

    private static EffectSpec buildSpec() {
        EffectSpec.Builder spec = EffectSpec.of("CURE_ONE_OF")
                .param("effect-1", D.potionEffect())
                .param("success-message", D.STRING.def(""))
                .param("message-throttle-set", D.STRING.def(""))
                .param("message-throttle", D.TICKS.def(0));
        for (int i = 2; i <= MAX_EFFECTS; i++) {
            spec.param("effect-" + i, D.potionEffect().optional());
        }
        return spec.target("who", T.SELF)
                .affinity(Affinity.TARGET_ENTITY)
                .doc("Remove exactly one active potion effect, choosing the first effect encountered by the "
                        + "server whose type appears in effect-1 through effect-16. Unlisted effects remain. "
                        + "Optionally throttle the success message only while one authored armor set is active.")
                .example("{ CURE_ONE_OF: { effect-1: POISON, effect-2: WITHER, who: \"@Self\" } }")
                .build();
    }

    static final EffectSpec SPEC = buildSpec();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int[] allowed = new int[MAX_EFFECTS];
        int size = 0;
        for (int i = 1; i <= MAX_EFFECTS; i++) {
            String key = "effect-" + i;
            if (ctx.args().has(key)) {
                allowed[size++] = ctx.integer(key);
            }
        }
        int[] exact = Arrays.copyOf(allowed, size);
        for (LivingEntity target : ctx.targets("who")) {
            String throttleSet = ctx.str("message-throttle-set");
            boolean throttle = target instanceof Player player && ActiveSets.has(player, throttleSet);
            sink.cureOneOf(target, exact, ctx.str("success-message"),
                    throttle ? ctx.integer("message-throttle") : 0);
        }
    }
}
