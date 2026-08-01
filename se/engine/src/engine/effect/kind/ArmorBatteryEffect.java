package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import org.bukkit.entity.Player;
import schema.spec.D;

/** Repair every worn armor piece and damage the opponent's slot matching the first source-enchanted piece. */
public final class ArmorBatteryEffect implements EffectKind {
    static final EffectSpec SPEC = EffectSpec.of("ARMOR_BATTERY")
            .param("source-enchant", D.STRING)
            .param("repair", D.INT.min(0).def(2))
            .param("retaliation", D.INT.min(0).def(1))
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Repair all of the actor's armor, then damage the opponent armor slot matching the actor's first worn source enchant.")
            .example("{ ARMOR_BATTERY: { source-enchant: enchants/immortal, repair: 2, retaliation: 1 } }")
            .build();

    @Override public EffectSpec spec() { return SPEC; }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Player actor = ctx.actor();
        if (actor == null) {
            return;
        }
        int slot = EnchantArmorSlots.first(actor, ctx.str("source-enchant"));
        if (slot < 0) {
            return;
        }
        sink.repairArmor(actor, ctx.integer("repair"));
        if (ctx.victim() != null && ctx.integer("retaliation") > 0) {
            sink.damageArmorSlot(ctx.victim(), slot, ctx.integer("retaliation"));
        }
    }
}
