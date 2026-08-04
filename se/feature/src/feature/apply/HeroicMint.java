package feature.apply;

import org.bukkit.inventory.ItemStack;

/**
 * Makes a freshly minted piece heroic (§F) — the narrow seam the set minter needs so it can honour a member's
 * {@code heroic: true} without depending on the whole upgrade economy. {@code feature.heroic.HeroicStamp} is
 * the implementation; the interface lives here so the apply package never depends on the heroic one.
 */
public interface HeroicMint {

    /**
     * Stamp the pack's heroic stats onto {@code gear} and re-render it. {@code false} when there was nothing
     * to do — absent gear, or a piece that already carries heroic stats.
     *
     * @param weapon {@code true} for the outgoing-damage side, {@code false} for the incoming-reduction side
     */
    boolean stampOn(ItemStack gear, boolean weapon);

    /** No heroic economy wired (fixtures, and any pack with no {@code items/heroic.yml}): nothing is stamped. */
    HeroicMint NONE = (gear, weapon) -> false;
}
