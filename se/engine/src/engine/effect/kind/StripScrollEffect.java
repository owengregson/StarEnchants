package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.Locale;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code STRIP_SCROLL} — remove one White / Holy-White protection marker from a random protected piece of
 * the target's equipment (ADR-0052 Anubis). The per-hit chance is the ability's own {@code chance} gate;
 * this kind only names WHICH protection and WHERE to look. The Sink runs the strip on the target's own
 * region thread through the composition-root {@code GearProtection} seam (marker release + lore recompose +
 * equipment write-back) — the {@code DISARM}/{@code REMOVE_ARMOR} shape.
 */
public final class StripScrollEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("STRIP_SCROLL")
            .param("scroll", D.enumOf("HOLY", "WHITE").def("HOLY"))
            .param("hand", D.BOOL.def(true))
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Remove one protection scroll marker from a random protected piece of the target's worn "
                    + "armour (+ held item unless hand: false): scroll HOLY strips a Holy White Scroll, WHITE "
                    + "a White Scroll (its guard flag included). A target with no protected piece is a no-op. "
                    + "Rate-limit with the ability's chance gate (the Anubis per-hit percent).")
            .example("{ STRIP_SCROLL: { scroll: HOLY, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        boolean holy = !"WHITE".equals(ctx.str("scroll").toUpperCase(Locale.ROOT));
        boolean hand = ctx.bool("hand");
        for (LivingEntity who : ctx.targets("who")) {
            sink.stripScroll(who, holy, hand);
        }
    }
}
