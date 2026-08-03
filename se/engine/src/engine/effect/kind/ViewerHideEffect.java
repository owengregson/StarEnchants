package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.Locale;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code VIEWER_HIDE} — remove the target from specific viewers' screens for a window: a connection-level
 * hide, so the armour goes with the body and the scope can be one attacker rather than the whole server.
 * An INVISIBILITY potion can do neither.
 *
 * <p>{@code viewer=attacker} needs a live attacker, so it is a DEFENSE-side tool; with none in scope it
 * hides the target from nobody rather than falling back to everyone, because a decoy that blinds the whole
 * server is a different ability.
 */
public final class ViewerHideEffect implements EffectKind {

    public static final EffectSpec SPEC = EffectSpec.of("VIEWER_HIDE")
            .param("duration", D.TICKS.def(20))
            .param("viewer", D.enumOf("attacker", "all").def("attacker"))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Hide the target player from the attacker (viewer=attacker) or from every online player "
                    + "(viewer=all) for duration ticks, restoring them at the window's close. A packet-level "
                    + "hide: worn armour vanishes with the body, unlike an INVISIBILITY potion. A relog on "
                    + "either side ends it early. viewer=attacker with no attacker in scope hides nothing.")
            .example("{ VIEWER_HIDE: { duration: 60, viewer: attacker } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int duration = ctx.integer("duration");
        boolean everyone = "all".equalsIgnoreCase(scope(ctx.str("viewer")));
        Player viewer = everyone ? null : ctx.attacker() instanceof Player p ? p : null;
        if (!everyone && viewer == null) {
            return;
        }
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player subject) {
                sink.viewerHide(subject, viewer, duration);
            }
        }
    }

    private static String scope(String viewer) {
        return viewer == null ? "" : viewer.toLowerCase(Locale.ROOT);
    }
}
