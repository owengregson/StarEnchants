package engine.run;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The actors one activation runs against (docs/architecture.md §3.5): the firing player and the
 * captured victim/attacker/location the selectors and effect contexts read. Built once per event by
 * the trigger listener on the firing thread and passed to {@link AbilityExecutor#run}; everything in
 * it is either the firing-thread actor or a snapshot-safe value, never a live cross-region entity
 * (§3.4). Any field may be {@code null} for a non-combat or non-positional activation.
 *
 * @param actor    the player whose ability fired (may be {@code null} only in tests / synthetic runs)
 * @param victim   the combat victim, or {@code null}
 * @param attacker the attacker that hit the activator, or {@code null}
 * @param location the relevant location (AoE centre, block, …), or {@code null}
 */
public record ActivationContext(Player actor, LivingEntity victim, LivingEntity attacker, Location location) {
}
