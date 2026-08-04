package engine.run;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The actors and event payload one activation runs against (docs/architecture.md §3.5). Built once per
 * event on the firing thread; every field is a firing-thread actor or a snapshot-safe value, never a live
 * cross-region entity. Any field may be {@code null}/0 for a non-combat/non-positional activation.
 *
 * @param actor           the player whose ability fired ({@code null} only in tests / synthetic runs)
 * @param victim          the combat victim, or {@code null}
 * @param attacker        the attacker that hit the activator, or {@code null}
 * @param location        the relevant location (AoE centre, block, …), or {@code null}
 * @param damage          the {@code %damage%} fact, captured pre-fold; 0 if none
 * @param block           the MINE/BREAK block ({@code %block.type%}/{@code %isblock%}), region-owned on the firing
 *                        thread; {@code null} for non-block triggers
 * @param combo           the {@code %combo%} hit streak; 0 outside a tracked attack
 * @param damageCauseName the Bukkit {@code DamageCause} name of the triggering damage event ({@code %damagecause%});
 *                        empty for non-damage activations
 * @param itemDamageArmor on an ITEM_DAMAGE activation, whether the worn ARMOR (vs the held item) took the durability
 *                        loss ({@code %itemdamage.armor%}); false elsewhere
 * @param recentAttackers distinct attackers that hit the actor in the recent gank window ({@code %recentattackers%})
 * @param attackerIndex   on a DEFENSE-side hit, the 1-based first-seen order of this attacker among the actor's
 *                        recent attackers ({@code %attackerindex%}); 0 when absent
 * @param vanillaFinalDamage the damage the actor is ABOUT to take, as the server priced it — post-armor,
 *                        post-protection, post-resistance, but PRE-SE-fold ({@code %posthit.health%}). Set only
 *                        on a damage-taken context (DEFENSE/HURT/FALL/FIRE); {@link Double#NaN} everywhere else,
 *                        where the fact stays 0
 * @param impactHeight    on a projectile hit, how far ABOVE the struck entity's feet the projectile was
 *                        ({@code %impactheight%}); 0 for every other activation. Differenced at the hit site so
 *                        both combat sides read the same geometry and no live projectile rides the context
 * @param projectileKind  the {@code %projectilekind%} bucket of the projectile that landed the hit
 *                        (ARROW/FIREBALL/THROWN/OTHER); empty for a non-projectile activation
 * @param equipChange     {@code EQUIP} or {@code UNEQUIP} on an EQUIP_CHANGE activation ({@code %equipchange%});
 *                        empty everywhere else, so an unrelated trigger can never satisfy a direction gate
 * @param itemDurabilityPercent on an ITEM_DAMAGE activation, the damaged item's REMAINING durability as a percent
 *                        of its effective max ({@code %item.durabilitypercent%}), measured on the firing thread
 *                        from that exact stack. {@link Double#NaN} everywhere else (and for an item that carries
 *                        no durability bar), where the fact stays 0
 */
public record ActivationContext(Player actor, LivingEntity victim, LivingEntity attacker, Location location,
                                double damage, Block block, int combo, String damageCauseName,
                                boolean itemDamageArmor, int recentAttackers, int attackerIndex,
                                double vanillaFinalDamage, double impactHeight, String projectileKind,
                                String equipChange, double itemDurabilityPercent) {

    public ActivationContext {
        damageCauseName = damageCauseName == null ? "" : damageCauseName;
        projectileKind = projectileKind == null ? "" : projectileKind;
        equipChange = equipChange == null ? "" : equipChange;
    }

    /** Full combat payload with no equipment transition — every context but EQUIP_CHANGE's. */
    public ActivationContext(Player actor, LivingEntity victim, LivingEntity attacker, Location location,
                             double damage, Block block, int combo, String damageCauseName,
                             boolean itemDamageArmor, int recentAttackers, int attackerIndex,
                             double vanillaFinalDamage, double impactHeight, String projectileKind) {
        this(actor, victim, attacker, location, damage, block, combo, damageCauseName, itemDamageArmor,
                recentAttackers, attackerIndex, vanillaFinalDamage, impactHeight, projectileKind, "", Double.NaN);
    }

    /** Combat payload with the ADR-0049 gank/cause/item-damage facts but no pending damage or projectile. */
    public ActivationContext(Player actor, LivingEntity victim, LivingEntity attacker, Location location,
                             double damage, Block block, int combo, String damageCauseName,
                             boolean itemDamageArmor, int recentAttackers, int attackerIndex) {
        this(actor, victim, attacker, location, damage, block, combo, damageCauseName, itemDamageArmor,
                recentAttackers, attackerIndex, Double.NaN, 0.0, "");
    }

    /** Combat/positional payload with a hit streak but no ADR-0049 gank/cause/item-damage facts. */
    public ActivationContext(Player actor, LivingEntity victim, LivingEntity attacker, Location location,
                             double damage, Block block, int combo) {
        this(actor, victim, attacker, location, damage, block, combo, "", false, 0, 0);
    }

    /** Damage/block payload, no combat streak (defense side, MINE, …); only the attack side passes combo. */
    public ActivationContext(Player actor, LivingEntity victim, LivingEntity attacker, Location location,
                             double damage, Block block) {
        this(actor, victim, attacker, location, damage, block, 0);
    }

    /** Non-combat, non-block activation: no damage, block, or streak. */
    public ActivationContext(Player actor, LivingEntity victim, LivingEntity attacker, Location location) {
        this(actor, victim, attacker, location, 0.0, null, 0);
    }

    /** This context with the ADR-0049 gank/cause facts populated, preserving everything else (combat dispatch). */
    public ActivationContext withCombatFacts(String damageCauseName, int recentAttackers, int attackerIndex) {
        return new ActivationContext(actor, victim, attacker, location, damage, block, combo,
                damageCauseName, itemDamageArmor, recentAttackers, attackerIndex, vanillaFinalDamage,
                impactHeight, projectileKind, equipChange, itemDurabilityPercent);
    }

    /** The EQUIP_CHANGE payload: an equipment transition on {@code actor}, no combat and no position of its own. */
    public static ActivationContext equipChange(Player actor, String direction) {
        return new ActivationContext(actor, null, null, actor.getLocation(), 0.0, null, 0, "", false, 0, 0,
                Double.NaN, 0.0, "", direction, Double.NaN);
    }

    /**
     * The ITEM_DAMAGE payload, built by whichever era source observed the wear: {@code points} of durability
     * lost ({@code %damage%}), whether it was a worn armour piece ({@code %itemdamage.armor%}), and the item's
     * remaining durability ({@code %item.durabilitypercent%}; {@link Double#NaN} when it carries no bar).
     */
    public static ActivationContext itemDamage(Player actor, double points, boolean armor, double durabilityPercent) {
        return new ActivationContext(actor, null, null, actor.getLocation(), points, null, 0, "", armor, 0, 0,
                Double.NaN, 0.0, "", "", durabilityPercent);
    }
}
