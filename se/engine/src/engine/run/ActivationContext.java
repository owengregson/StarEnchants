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
 * @param actor    the player whose ability fired ({@code null} only in tests / synthetic runs)
 * @param victim   the combat victim, or {@code null}
 * @param attacker the attacker that hit the activator, or {@code null}
 * @param location the relevant location (AoE centre, block, …), or {@code null}
 * @param damage   the {@code %damage%} fact, captured pre-fold; 0 if none
 * @param block    the MINE/BREAK block ({@code %block.type%}/{@code %isblock%}), region-owned on the firing
 *                 thread; {@code null} for non-block triggers
 * @param combo    the {@code %combo%} hit streak; 0 outside a tracked attack
 */
public record ActivationContext(Player actor, LivingEntity victim, LivingEntity attacker, Location location,
                                double damage, Block block, int combo) {

    /** Damage/block payload, no combat streak (defense side, MINE, …); only the attack side passes combo. */
    public ActivationContext(Player actor, LivingEntity victim, LivingEntity attacker, Location location,
                             double damage, Block block) {
        this(actor, victim, attacker, location, damage, block, 0);
    }

    /** Non-combat, non-block activation: no damage, block, or streak. */
    public ActivationContext(Player actor, LivingEntity victim, LivingEntity attacker, Location location) {
        this(actor, victim, attacker, location, 0.0, null, 0);
    }
}
