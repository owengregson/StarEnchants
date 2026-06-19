package engine.run;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The actors one activation runs against (docs/architecture.md §3.5): the firing player and the
 * captured victim/attacker/location the selectors and effect contexts read, plus the event payload the
 * combat/block condition facts are sourced from (v3.1 §A). Built once per event by the trigger listener
 * on the firing thread and passed to {@link AbilityExecutor#run}; everything in it is either the
 * firing-thread actor or a snapshot-safe value, never a live cross-region entity (§3.4). Any field may be
 * {@code null}/0 for a non-combat or non-positional activation.
 *
 * @param actor    the player whose ability fired (may be {@code null} only in tests / synthetic runs)
 * @param victim   the combat victim, or {@code null}
 * @param attacker the attacker that hit the activator, or {@code null}
 * @param location the relevant location (AoE centre, block, …), or {@code null}
 * @param damage   the event's damage at fire time (the {@code %damage%} fact), captured pre-fold; 0 if none
 * @param block    the block this activation concerns (the MINE/BREAK block — the {@code %block.type%} /
 *                 {@code %isblock%} facts), region-owned on the firing thread; {@code null} for non-block triggers
 */
public record ActivationContext(Player actor, LivingEntity victim, LivingEntity attacker, Location location,
                                double damage, Block block) {

    /**
     * The common non-combat, non-block activation: no damage payload and no block. Keeps every existing
     * call site terse while the combat/mine dispatchers use the full constructor to source {@code %damage%}
     * and {@code %block.type%}.
     */
    public ActivationContext(Player actor, LivingEntity victim, LivingEntity attacker, Location location) {
        this(actor, victim, attacker, location, 0.0, null);
    }
}
