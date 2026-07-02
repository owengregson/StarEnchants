package feature.compat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The version edge for hand/equipment access in the feature shells (ADR-0044;
 * docs/legacy-1.8.9-codeshare-design.md §4): 1.9's off-hand slot, {@code PlayerInteractEvent.getHand()}, and
 * {@code ClickType.SWAP_OFFHAND} are all absent on 1.8.9, so which hand a click touches is the seam. Two
 * era-exclusive impls back it — {@code ModernHands} (off-hand aware, {@code overlay/modern}) and
 * {@code LegacyHands} (single 1.8 hand, {@code overlay/legacy}) — injected into the feature shells by the
 * composition root, so the shells stay version-agnostic.
 */
public interface Hands {

    ItemStack mainHand(Player player);

    void setMainHand(Player player, ItemStack item);

    /** The living entity's main-hand item, or {@code null} if it has no equipment. */
    ItemStack mainHand(LivingEntity entity);

    /** Whether an interact fired for the MAIN hand (1.9 fires twice — once per hand; 1.8 only the main). */
    boolean isMainHand(PlayerInteractEvent event);

    /** Whether a click is the off-hand-swap key ({@code F}); always false on 1.8 (no off-hand). */
    boolean isOffhandSwap(ClickType type);

    /** The player's off-hand item; always {@code null} on 1.8 (no off-hand). */
    ItemStack offHand(Player player);

    /** Set the player's off-hand item; a no-op on 1.8 (no off-hand). */
    void setOffHand(Player player, ItemStack item);

    /** The main storage contents (excludes armour + off-hand on modern; the whole inventory on 1.8). */
    ItemStack[] storageContents(Player player);
}
