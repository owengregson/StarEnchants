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

/**
 * {@code BLESS} — an atomic guarded Bleed reset plus one-of potion cleanse.
 * Atomicity matters: the chance gate must roll once, then either emit the guard feedback or perform every
 * cleanse step; separate abilities would roll independently and could not reproduce that behavior.
 */
public final class BlessEffect implements EffectKind {

    private static final int MAX_EFFECTS = 16;

    private static EffectSpec buildSpec() {
        EffectSpec.Builder spec = EffectSpec.of("BLESS")
                .param("blocked-by", D.STRING.def(""))
                .param("blocked-message", D.STRING.def(""))
                .param("success-message", D.STRING.def(""))
                .param("sound", D.sound())
                .param("volume", D.DOUBLE.min(0).def(1))
                .param("pitch", D.DOUBLE.min(0).def(1))
                .param("message-throttle-set", D.STRING.def(""))
                .param("message-throttle", D.TICKS.def(0))
                .param("effect-1", D.potionEffect());
        for (int i = 2; i <= MAX_EFFECTS; i++) {
            spec.param("effect-" + i, D.potionEffect().optional());
        }
        return spec.target("who", T.SELF)
                .affinity(Affinity.TARGET_ENTITY)
                .doc("If blocked-by is active, show blocked-message and stop. Otherwise clear Cosmic Bleed, "
                        + "play the private sound, remove the first active authored potion, and show the success "
                        + "message. Optionally throttle only that message while one armor set is active.")
                .example("{ BLESS: { blocked-by: deep-wounds, sound: SPLASH, effect-1: POISON } }")
                .build();
    }

    static final EffectSpec SPEC = buildSpec();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int[] effects = new int[MAX_EFFECTS];
        int size = 0;
        for (int i = 1; i <= MAX_EFFECTS; i++) {
            String key = "effect-" + i;
            if (ctx.args().has(key)) {
                effects[size++] = ctx.integer(key);
            }
        }
        int[] exact = Arrays.copyOf(effects, size);
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player player) {
                String throttleSet = ctx.str("message-throttle-set");
                sink.bless(player, ctx.str("blocked-by"), ctx.str("blocked-message"),
                        ctx.str("success-message"), ctx.integer("sound"), (float) ctx.dbl("volume"),
                        (float) ctx.dbl("pitch"), exact, ActiveSets.has(player, throttleSet),
                        ctx.integer("message-throttle"));
            }
        }
    }
}
