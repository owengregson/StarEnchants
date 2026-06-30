package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks temporary equipment swaps (spooky's Scarecrow — a victim's helmet replaced by a pumpkin for a few
 * seconds): per player, the armour-slot index → the ORIGINAL item, so the swap reverts and death stays normal
 * (the real piece drops / is kept, never the placeholder). Static + era-agnostic; cleared on disable. Armour
 * indices match {@code PlayerInventory.getArmorContents()}: 0 = boots, 1 = leggings, 2 = chestplate, 3 = helmet.
 */
public final class TempEquip {

    private TempEquip() {
    }

    /** Sentinel for "the slot was empty" (a ConcurrentHashMap forbids null values). */
    private static final ItemStack AIR = new ItemStack(Material.AIR);

    private static final Map<UUID, Map<Integer, ItemStack>> ORIGINALS = new ConcurrentHashMap<>();

    /** Record the original item displaced from {@code slot}; returns false (no-op) if a swap there is active. */
    public static boolean swap(UUID player, int slot, ItemStack original) {
        Map<Integer, ItemStack> bySlot = ORIGINALS.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        if (bySlot.containsKey(slot)) {
            return false; // already swapped — never capture the placeholder as the "original"
        }
        bySlot.put(slot, original == null ? AIR : original);
        return true;
    }

    /** Whether {@code slot} is currently swapped for {@code player}. */
    public static boolean isSwapped(UUID player, int slot) {
        Map<Integer, ItemStack> bySlot = ORIGINALS.get(player);
        return bySlot != null && bySlot.containsKey(slot);
    }

    /** Take + forget the original for {@code slot} (the revert / death restore); null if not swapped. */
    public static ItemStack end(UUID player, int slot) {
        Map<Integer, ItemStack> bySlot = ORIGINALS.get(player);
        if (bySlot == null) {
            return null;
        }
        ItemStack original = bySlot.remove(slot);
        if (bySlot.isEmpty()) {
            ORIGINALS.remove(player);
        }
        return original;
    }

    /** Every active swap for a player (slot → original), for the death restore; empty if none. */
    public static Map<Integer, ItemStack> active(UUID player) {
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
