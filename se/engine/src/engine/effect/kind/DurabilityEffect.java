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
            .param("target", D.enumOf("item", "armor", "all").def("item"))
            .param("mode", D.enumOf("restore", "damage").def("restore"))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Modify durability of the player's held item and/or worn armor: restore (amount<0 = full) "
                    + "or damage. Replaces ADD_DURABILITY/ADD_DURABILITY_ITEM/REPAIR/DAMAGE_ARMOR.")
            .example("DURABILITY:-1:item")
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
        for (LivingEntity who : ctx.targets("who")) {
            if (damage) {
                if (armor) {
                    sink.damageArmor(who, amount);
                }
                if (item && who instanceof Player p) {
                    sink.damageHand(p, amount);
                }
            } else if (who instanceof Player p) {
                if (item) {
                    sink.repairHand(p, amount);
                }
                if (armor) {
                    sink.repairArmor(p, amount);
                }
            }
        }
    }
}
