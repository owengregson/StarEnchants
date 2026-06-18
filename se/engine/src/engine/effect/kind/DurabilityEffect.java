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
 * {@code DURABILITY} — the canonical item-durability primitive (docs/v3-directives.md §C), collapsing
 * {@code ADD_DURABILITY} (armor), {@code ADD_DURABILITY_ITEM} / {@code REPAIR} (held item), and
 * {@code DAMAGE_ARMOR} into one parameterized kind:
 *
 * <ul>
 *   <li>{@code mode=restore} (default) — restore durability; {@code amount < 0} fully repairs;</li>
 *   <li>{@code mode=damage} — wear durability down by {@code amount};</li>
 *   <li>{@code target=item} (default) — the held main-hand item (player-only); {@code armor} — worn
 *       armor (any living target); {@code all} — both.</li>
 * </ul>
 *
 * <p>A {@code mode} enum carries direction rather than overloading the sign of {@code amount}, because
 * {@code amount < 0} already means "fully repair" on the restore path — so {@code DURABILITY:1:armor:damage}
 * is an unambiguous one-point wear, never confused with the full-repair sentinel.
 *
 * <p>The restore paths are player-only (a held/worn item belongs to a player inventory); armor damage
 * works on any {@link LivingEntity} victim, preserving {@code DAMAGE_ARMOR}'s {@code @Victim} default.
 * Default target is {@code @Self} (matching the three restore kinds); damage content carries an explicit
 * {@code who: "@Victim"}. {@link Affinity#TARGET_ENTITY}: the Sink routes each intent to the owner's thread.
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
