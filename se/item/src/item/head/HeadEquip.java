package item.head;

import org.bukkit.inventory.ItemStack;

/**
 * Neutralises a head-item's client-side WEARABILITY so the client itself refuses to slot it into the helmet
 * (ADR-0052 pets / ADR-0053 masks are {@code PLAYER_HEAD}s, and a bare player head is natively helmet-wearable).
 * An ADR-0044 era seam: on 1.21.2+ this overrides the {@code minecraft:equippable} data component to a non-head
 * slot so vanilla's own armour-slot check rejects it (no server round-trip); below that era the component does
 * not exist and this is inert — the server-side {@code HeadEquipGuard} carries the block alone.
 *
 * <p>Pure item decoration: no entity/world read, safe from any thread. It is applied ONCE at mint; the codebase's
 * re-render path ({@code ItemFactory.decorated}) preserves the component across a pet's level-up re-render.
 */
public interface HeadEquip {

    /** Inert default — the item keeps its native wearability (1.8.9, pre-1.21.2, unsupported servers). */
    HeadEquip NONE = stack -> { };

    /** Make {@code stack} un-wearable in the helmet slot from the client's own point of view. */
    void unwearable(ItemStack stack);
}
