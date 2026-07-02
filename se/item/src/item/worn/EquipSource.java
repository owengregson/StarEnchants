package item.worn;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

/**
 * The version edge for reading an entity's worn + held equipment into the array {@link WornResolver} flattens
 * (§5.5, docs/legacy-1.8.9-codeshare-design.md §3.3): the 1.9+ off-hand read is absent on 1.8.9, so which slots
 * exist is the seam. Two era-exclusive impls back it — {@code ModernEquipSource} (6 slots incl. off-hand,
 * {@code overlay/modern}) and {@code LegacyEquipSource} (5 slots, main-hand only, {@code overlay/legacy}) —
 * injected into {@link WornResolver} by the composition root (ADR-0044). Resolve runs per equip event, never
 * per hit, so an interface call here carries no hot-path cost.
 */
public interface EquipSource {

    /**
     * {@code [helmet, chest, legs, boots, mainHand(, offHand)]} with {@code null} for empty slots, or
     * {@code null} when the entity has no equipment (a non-living / equipment-less mob).
     */
    ItemStack[] snapshot(LivingEntity entity);
}
