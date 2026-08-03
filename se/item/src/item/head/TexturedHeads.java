package item.head;

import org.bukkit.inventory.ItemStack;

/**
 * Mints a base64-textured player head (ADR-0052 pets) — an ADR-0044 era seam: the modern impl uses Paper's
 * API-level profile surface (mapping-flip immune), the 1.8 impl a {@code SKULL_ITEM:3} + authlib
 * {@code GameProfile} reflection write. Pure item construction (no entity/world read), safe from any thread;
 * an impl must NEVER complete a profile from the network — the base64 property is set directly.
 */
public interface TexturedHeads {

    /** The inert default (tests, unsupported servers): callers fall back to a plain material mint. */
    TexturedHeads NONE = base64 -> null;

    /**
     * A fresh player-head {@link ItemStack} skinned with the base64 {@code textures} value, or {@code null}
     * when head texturing is unsupported here (the caller mints its fallback material instead). The head is
     * undecorated — name/lore come from the universal likeness afterwards.
     */
    ItemStack head(String base64);

    /**
     * A fresh player-head {@link ItemStack} skinned as the real player {@code owner}/{@code ownerName}
     * (HEAD_TROPHY), or {@code null} when unsupported. Unlike {@link #head} the skin is a live identity, so
     * the two impls diverge on WHICH identity the era can carry: modern owns the profile by UUID, 1.8 only by
     * name. Default {@code null} keeps {@link #NONE} a lambda and a trophy-less server harmless.
     */
    default ItemStack playerHead(java.util.UUID owner, String ownerName) {
        return null;
    }
}
