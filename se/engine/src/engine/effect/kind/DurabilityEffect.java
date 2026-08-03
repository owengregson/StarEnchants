package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.ArmorSelect;
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
 *
 * <p>The {@code percent-*} modes move a fraction of each item's OWN max durability instead of a flat point
 * count, so one authored value wears a leather cap and a netherite chestplate proportionally; they read
 * {@code percent} rather than {@code amount} because {@code amount} is an INT and the consumers need fractions
 * of a percent. {@code select} addresses ONE worn piece instead of the whole set, and {@code skip-undamaged}
 * drops pristine pieces from the candidate set so a scatter pick never lands on gear it would leave untouched.
 */
public final class DurabilityEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DURABILITY")
            .param("amount", D.INT.def(-1), "durability points; negative fully restores (restore mode)")
            .param("target", D.enumOf("item", "armor", "all").def("item"))
            .param("mode", D.enumOf("restore", "damage", "percent-restore", "percent-damage").def("restore"))
            .param("percent", D.DOUBLE.min(0).def(0),
                    "percent-* modes only: how much of each item's MAX durability to move")
            .param("select", D.enumOf("whole-set", "slot:helmet", "slot:chestplate", "slot:leggings",
                            "slot:boots", "most-damaged", "least-damaged", "random-piece").def("whole-set"),
                    "which worn piece target: armor addresses")
            .param("skip-undamaged", D.BOOL.def(false), "leave pieces already at full durability untouched")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Modify durability of the player's held item and/or worn armor: restore (amount<0 = full) "
                    + "or damage, flat by amount or proportionally by percent of each item's max durability "
                    + "(percent-restore/percent-damage). select addresses ONE worn piece — a named slot, the "
                    + "most/least damaged, or a random one — instead of the whole set; skip-undamaged leaves "
                    + "pieces at full durability alone and out of that pick. Replaces "
                    + "ADD_DURABILITY/ADD_DURABILITY_ITEM/REPAIR/DAMAGE_ARMOR.")
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
        String mode = ctx.str("mode");
        boolean damage = "damage".equalsIgnoreCase(mode) || "percent-damage".equalsIgnoreCase(mode);
        // A percent mode hands the sink the fraction and lets it read each item's own max durability; a flat
        // mode hands it 0, which is the "use amount" sentinel — so today's calls are unchanged.
        double percent = mode.regionMatches(true, 0, "percent-", 0, 8) ? ctx.dbl("percent") : 0;
        int select = ArmorSelect.of(ctx.str("select"));
        boolean skipUndamaged = ctx.bool("skip-undamaged");
        for (LivingEntity who : ctx.targets("who")) {
            if (damage) {
                if (armor) {
                    sink.damageArmor(who, amount, percent, select, skipUndamaged);
                }
                if (item && who instanceof Player p) {
                    sink.damageHand(p, amount, percent, skipUndamaged);
                }
            } else if (who instanceof Player p) {
                if (item) {
                    sink.repairHand(p, amount, percent, skipUndamaged);
                }
                if (armor) {
                    sink.repairArmor(p, amount, percent, select, skipUndamaged);
                }
            }
        }
    }
}
