package feature.crystal;

import org.bukkit.inventory.ItemStack;

/**
 * Outcome of a crystal gesture (apply / merge / extract), for {@link CrystalListener} to commit.
 * {@code newTarget}/{@code sound}/{@code message} are read only when {@code commit}; {@code give} is an
 * extra item handed to the player (the minted crystal an EXTRACT yields), {@code null} for apply/merge.
 */
public record CrystalResult(boolean commit, ItemStack newTarget, ItemStack give, String sound, String message) {

    /** Nothing changed (ineligible target / failed roll with no consume) — relay {@code message} only. */
    static CrystalResult unchanged(String message) {
        return new CrystalResult(false, null, null, null, message);
    }

    static CrystalResult committed(ItemStack newTarget, String sound, String message) {
        return new CrystalResult(true, newTarget, null, sound, message);
    }

    /** Extraction: {@code give} is the popped crystal handed back; the extractor cursor is spent. */
    static CrystalResult extracted(ItemStack newTarget, ItemStack give, String sound, String message) {
        return new CrystalResult(true, newTarget, give, sound, message);
    }
}
