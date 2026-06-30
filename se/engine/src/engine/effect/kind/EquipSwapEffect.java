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
 * {@code EQUIP_SWAP} — temporarily replace one of the target's armour pieces with a placeholder material, then
 * restore it after {@code duration} (spooky's Scarecrow — a pumpkin helmet for 3s). Death stays normal: the
 * real piece drops / is kept (the death listener restores it), never the placeholder. Removing a set piece drops
 * the victim's set below complete, so their helmet-granted set bonus deactivates for the window (via the worn
 * re-resolve) and returns on restore. Player-only.
 */
public final class EquipSwapEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("EQUIP_SWAP")
            .param("slot", D.enumOf("helmet", "chestplate", "leggings", "boots").def("helmet"))
            .param("material", D.material())
            .param("duration", D.TICKS.def(60))
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Temporarily replace the target's `slot` armour piece with `material`, restoring it after "
                    + "`duration` ticks (death-safe: the real piece drops / is kept). Default target the victim.")
            .example("{ EQUIP_SWAP: { slot: helmet, material: CARVED_PUMPKIN, duration: 60, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int slotIndex = switch (ctx.str("slot").toLowerCase(Locale.ROOT)) {
            case "boots" -> 0;
            case "leggings" -> 1;
            case "chestplate" -> 2;
            default -> 3; // helmet (getArmorContents index 3)
        };
        int material = ctx.integer("material");
        int duration = ctx.integer("duration");
        for (LivingEntity who : ctx.targets("who")) {
            if (who instanceof Player p) {
                sink.swapEquipment(p, slotIndex, material, duration);
            }
        }
    }
}
