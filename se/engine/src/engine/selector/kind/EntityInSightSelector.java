package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;
import java.util.List;

/**
 * {@code @EntityInSight{r=16}} — the living entity the actor is looking at within {@code r}, else empty
 * (Cosmic Enchants-style parity). Raytrace via the world-access seam on the actor's own firing region
 * thread, so Folia-correct.
 *
 * <p>An ALLIED player in the crosshair resolves to nothing (R-QC17) — a smite or a grapple aimed through a
 * party-mate must not land on them. Only players are tested: a mob is never anyone's ally, and filtering by
 * hostility instead would silently stop the family working on passive animals.
 */
public final class EntityInSightSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("ENTITYINSIGHT")
            .param("r", D.DOUBLE.min(0).def(16), "maximum line-of-sight distance in blocks")
            .param("allies", D.BOOL.def(false), "include an allied player in the crosshair; the default skips one")
            .doc("The living entity the activator is looking at within r blocks, or nothing. An allied player "
                    + "is skipped unless allies: true; mobs are never filtered.")
            .example("@EntityInSight{r=16}")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<LivingEntity> resolve(SelectorCtx ctx) {
        LivingEntity hit = ctx.entityInSight(ctx.dbl("r"));
        if (hit == null || hit.equals(ctx.actor())) {
            return List.of();
        }
        if (hit instanceof Player player && !ctx.args().bool("allies") && Allies.allied(ctx.actor(), player)) {
            return List.of();
        }
        return List.of(hit);
    }
}
