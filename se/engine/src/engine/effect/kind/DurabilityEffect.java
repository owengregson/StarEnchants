package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code DURABILITY} — canonical item-durability primitive (§C). Direction is the {@code mode} enum, not the
 * sign of {@code amount}, since {@code amount < 0} already means "fully repair" on restore — so
 * {@code DURABILITY:1:armor:damage} is an unambiguous one-point wear, never the full-repair sentinel.
 * Restore is player-only (held/worn item); armor damage works on any {@link LivingEntity} victim.
 */
public final class DurabilityEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DURABILITY")
            .param("amount", D.INT.def(-1), "durability points; negative fully restores (restore mode)")
            .param("percent", D.DOUBLE.range(0, 100).optional(),
                    "damage mode only: percent of each armor piece type's maximum durability")
            .param("target", D.enumOf("item", "armor", "all").def("item"))
            .param("mode", D.enumOf("restore", "damage").def("restore"))
            .param("selection", D.enumOf("all", "most-damaged", "random").def("all"),
                    "armor selection: most-damaged for restore, random for one uniformly-selected damage slot")
            .param("slot", D.enumOf("boots", "leggings", "chestplate", "helmet").optional(),
                    "damage exactly one armor slot instead of all armor")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Modify durability of the player's held item and/or worn armor: restore (amount<0 = full) "
                    + "or damage. Replaces ADD_DURABILITY/ADD_DURABILITY_ITEM/REPAIR/DAMAGE_ARMOR.")
            .example("{ DURABILITY: { amount: -1, target: item } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int amount = ctx.integer("amount");
        String target = ctx.str("target");
        boolean armor = "armor".equalsIgnoreCase(target) || "all".equalsIgnoreCase(target);
        boolean item = "item".equalsIgnoreCase(target) || "all".equalsIgnoreCase(target);
        boolean damage = "damage".equalsIgnoreCase(ctx.str("mode"));
        String selection = ctx.args().has("selection") ? ctx.str("selection") : "all";
        boolean mostDamaged = "most-damaged".equalsIgnoreCase(selection);
        boolean randomArmor = "random".equalsIgnoreCase(selection);
        int armorSlot = ctx.args().has("slot") ? switch (ctx.str("slot").toLowerCase(java.util.Locale.ROOT)) {
            case "boots" -> 0;
            case "leggings" -> 1;
            case "chestplate" -> 2;
            case "helmet" -> 3;
            default -> -1;
        } : -1;
        for (LivingEntity who : ctx.targets("who")) {
            if (damage) {
                if (armor) {
                    if (ctx.args().has("percent") && armorSlot < 0) {
                        sink.damageArmorPercent(who, ctx.dbl("percent"));
                    } else if (armorSlot >= 0) {
                        sink.damageArmorSlot(who, armorSlot, amount);
                    } else if (randomArmor) {
                        sink.damageArmorSlot(who, java.util.concurrent.ThreadLocalRandom.current().nextInt(4), amount);
                    } else {
                        sink.damageArmor(who, amount);
                    }
                }
                if (item && who instanceof Player p) {
                    sink.damageHand(p, amount);
                }
            } else if (who instanceof Player p) {
                if (item) {
                    sink.repairHand(p, amount);
                }
                if (armor) {
                    if (mostDamaged) {
                        sink.repairMostDamagedArmor(p, amount);
                    } else {
                        sink.repairArmor(p, amount);
                    }
                }
            }
        }
    }
}
