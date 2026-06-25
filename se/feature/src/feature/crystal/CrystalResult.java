package feature.crystal;

import org.bukkit.inventory.ItemStack;

/**
 * Outcome of a crystal gesture for {@link CrystalListener} to commit. {@code give} is the minted crystal an
 * EXTRACT hands back ({@code null} for apply/merge); the rest are read only when {@code commit}.
 */
public record CrystalResult(boolean commit, ItemStack newTarget, ItemStack give, String sound, String message) {

    static CrystalResult unchanged(String message) {
        return new CrystalResult(false, null, null, null, message);
    }

    static CrystalResult committed(ItemStack newTarget, String sound, String message) {
        return new CrystalResult(true, newTarget, null, sound, message);
    }

    static CrystalResult extracted(ItemStack newTarget, ItemStack give, String sound, String message) {
        return new CrystalResult(true, newTarget, give, sound, message);
    }
}
