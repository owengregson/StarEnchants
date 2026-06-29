package feature.menu;

import item.mint.ItemFactory;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * One themed chrome button — a navigation arrow, the back door, the close barrier, or a menu's info pane
 * (docs/v3-directives.md §K, ADR-0030). The material is a config TOKEN so a pack can point it at a custom
 * ItemsAdder/Oraxen texture (ADR-0027) and still fall back to a vanilla material cross-version; the name and
 * lore carry legacy {@code &} colour codes. Immutable and shared — the dynamic page suffix is appended at
 * render by {@link MenuIcons}, never baked into the button.
 *
 * @param material a material/custom-item token (e.g. {@code ARROW}, {@code itemsadder:menu/next})
 * @param fallback the vanilla material used when {@code material} resolves to nothing on this server
 * @param name     the display name (legacy {@code &} codes)
 * @param lore     the static tooltip lines (legacy {@code &} codes); a page suffix may be appended at render
 */
public record NavButton(String material, Material fallback, String name, List<String> lore) {

    public NavButton {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(fallback, "fallback");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
    }

    /** Convenience for a vanilla-token button with no extra fallback distinction. */
    public static NavButton of(String material, Material fallback, String name, String... lore) {
        return new NavButton(material, fallback, name, List.of(lore));
    }

    /** Render this button to an icon, appending {@code extraLore} (e.g. a "Page 2/5" line), {@code glow} optional. */
    public ItemStack icon(List<String> extraLore, boolean glow) {
        List<String> full = extraLore == null || extraLore.isEmpty()
                ? lore
                : concat(lore, extraLore);
        ItemStack icon = ItemFactory.build(material, fallback, name, full);
        return glow ? ItemFactory.glow(icon) : icon;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new java.util.ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    /** Copy with the material token + fallback replaced (a pack/config override), keeping name + lore. */
    public NavButton withMaterial(String materialToken, Material fallbackMaterial) {
        return new NavButton(materialToken, fallbackMaterial, name, lore);
    }

    /** Copy with the display name replaced (a pack/config override), keeping material + lore. */
    public NavButton withName(String newName) {
        return new NavButton(material, fallback, newName, lore);
    }
}
