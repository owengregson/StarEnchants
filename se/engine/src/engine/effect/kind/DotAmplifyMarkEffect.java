package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import engine.stores.DotAmplifyStore;
import java.util.Locale;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code DOT_AMPLIFY_MARK} — multiply the target's INCOMING wither/poison damage by {@code factor} for
 * {@code duration}. A pure force multiplier: it adds no damage of its own, so a bow carrying only this is
 * worth exactly what its wielder's other DoT sources are worth.
 *
 * <p>Nothing else reaches those ticks — {@code MARK} scales only the marker's own later hits, and the
 * damage-mod kinds act on the triggering fold. Player bearers only: the amplification is read on the
 * environmental damage path, which is a player path.
 */
public final class DotAmplifyMarkEffect implements EffectKind {

    public static final EffectSpec SPEC = EffectSpec.of("DOT_AMPLIFY_MARK")
            .param("causes", D.enumOf("wither", "poison", "dot").def("dot"),
                    "which damage-over-time causes are amplified; dot = both")
            .param("factor", D.DOUBLE.min(1).def(2))
            .param("duration", D.TICKS.def(100))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Mark the target so their incoming wither and/or poison damage is multiplied by factor for "
                    + "duration ticks. Amplifies EVERY source of those causes, not just the marker's own. "
                    + "Re-marking refreshes the window outright, weaker factor included — a re-infection is "
                    + "a fresh infection. Player targets only.")
            .example("{ DOT_AMPLIFY_MARK: { causes: dot, factor: 3, duration: 60, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int causes = causeMask(ctx.str("causes"));
        double factor = ctx.dbl("factor");
        int duration = ctx.integer("duration");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player player) {
                sink.dotAmplify(player, factor, causes, duration);
            }
        }
    }

    /** Map the authored enum to the {@link DotAmplifyStore} cause bits. */
    private static int causeMask(String causes) {
        return switch (causes == null ? "" : causes.toLowerCase(Locale.ROOT)) {
            case "wither" -> DotAmplifyStore.CAUSE_WITHER;
            case "poison" -> DotAmplifyStore.CAUSE_POISON;
            default -> DotAmplifyStore.CAUSE_DOT;
        };
    }
}
