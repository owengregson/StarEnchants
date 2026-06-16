package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code @Aoe{r=4}} — every living entity within {@code r} blocks of the activation
 * centre, excluding the activator (docs/architecture.md §7, the {@code SMITE}
 * example). The centre is the activation's location, falling back to the victim's
 * then the actor's location. The radius defaults to {@code 4} so the no-argument form
 * is a valid default target.
 */
public final class AoeSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("AOE")
            .param("r", D.DOUBLE.min(0).def(4), "radius in blocks")
            .doc("Every living entity within r blocks of the target, except the activator.")
            .example("@Aoe{r=4}")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<LivingEntity> resolve(SelectorCtx ctx) {
        Location center = Centers.of(ctx);
        if (center == null) {
            return List.of();
        }
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : ctx.nearbyLiving(center, ctx.dbl("r"))) {
            if (!e.equals(ctx.actor())) {
                out.add(e);
            }
        }
        return out;
    }
}
