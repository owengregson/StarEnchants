package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks temporary equipment swaps (spooky's Scarecrow — a victim's helmet replaced by a pumpkin for a few
 * seconds): per player, the armour-slot index → the {@link Swap} (the ORIGINAL item plus the placeholder
 * material that was minted into the slot), so the swap reverts and death stays normal (the real piece drops /
 * is kept, never the placeholder) and the minted placeholder can always be reclaimed by material even after the
 * victim relocates it. Static + era-agnostic; cleared on disable. Armour indices match
 * {@code PlayerInventory.getArmorContents()}: 0 = boots, 1 = leggings, 2 = chestplate, 3 = helmet.
 */
public final class TempEquip {

    private TempEquip() {
    }

    /** Sentinel for "the slot was empty" (a ConcurrentHashMap forbids null values). */
    private static final ItemStack AIR = new ItemStack(Material.AIR);

    /**
     * One active swap: the {@code original} piece to restore (the {@link #AIR} sentinel if the slot was empty)
     * and the {@code placeholder} material that was minted into the slot — remembered so the death/quit/revert
     * paths can reclaim the minted item by material, not by re-reading a slot the victim may have emptied.
     */
    public record Swap(ItemStack original, Material placeholder) {
    }

    private static final Map<UUID, Map<Integer, Swap>> ORIGINALS = new ConcurrentHashMap<>();

    /** Record the swap displaced from {@code slot}; returns false (no-op) if a swap there is active. */
    public static boolean swap(UUID player, int slot, ItemStack original, Material placeholder) {
        Map<Integer, Swap> bySlot = ORIGINALS.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        if (bySlot.containsKey(slot)) {
            return false; // already swapped — never capture the placeholder as the "original"
        }
        bySlot.put(slot, new Swap(original == null ? AIR : original, placeholder));
        return true;
    }

    /** Whether {@code slot} is currently swapped for {@code player}. */
    public static boolean isSwapped(UUID player, int slot) {
        Map<Integer, Swap> bySlot = ORIGINALS.get(player);
        return bySlot != null && bySlot.containsKey(slot);
    }

    /** Take + forget the swap for {@code slot} (the revert / death restore); null if not swapped. */
    public static Swap end(UUID player, int slot) {
        Map<Integer, Swap> bySlot = ORIGINALS.get(player);
        if (bySlot == null) {
            return null;
        }
        Swap swap = bySlot.remove(slot);
        if (bySlot.isEmpty()) {
            ORIGINALS.remove(player);
        }
        return swap;
    }

    /** Every active swap for a player (slot → swap), for the death restore; empty if none. */
    public static Map<Integer, Swap> active(UUID player) {
        return ORIGINALS.getOrDefault(player, Map.of());
    }

    public static void clear(UUID player) {
        ORIGINALS.remove(player);
    }

    public static void clearAll() {
        ORIGINALS.clear();
    }

    /** Whether {@code item} is the empty-slot sentinel / nothing (restore to an empty slot). */
    public static boolean isAir(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }
}
