package item.codec;

import org.bukkit.inventory.ItemStack;

/**
 * The versioned "this item's lore was composed by {@link item.render.LoreRenderer}" marker (ADR-0040). Stored as
 * an INTEGER version off the combat blob, so it never thrashes the content-hash {@link ItemView} cache. An item
 * missing the current version predates the composer (or a build with an older layout): its lore may carry lines a
 * pre-composer writer positioned by visible text, so the one-time legacy migration shim runs on its first
 * recompose. Once {@link #stamp}ed at the current version an item is composer-owned and never consults the shim
 * again — which is what makes the shim bounded (marker-gated) and non-looping.
 */
public final class ComposerMark {

    /** Bump when the composed layout changes in a way that needs a fresh migration pass over already-marked items. */
    public static final int VERSION = 1;

    private final String key;

    public ComposerMark(String key) {
        this.key = key;
    }

    /** Whether {@code stack} was composed at the current layout version (so the legacy shim must NOT run). */
    public boolean isCurrent(ItemStack stack) {
        return ItemFlagStore.readInt(stack, key, 0) >= VERSION;
    }

    /** Stamp {@code stack} as composed at the current version. */
    public void stamp(ItemStack stack) {
        ItemFlagStore.writeInt(stack, key, VERSION);
    }
}
