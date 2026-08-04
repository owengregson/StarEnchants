package item.render;

import java.util.List;
import java.util.function.Function;

/**
 * Single source of truth for a crystal item's NAME string (ADR-0034 §1/§5). The {@code {CRYSTAL}} token expands
 * to the component crystals' STYLED display names, comma-joined so a merge reads each name in its own colour
 * (e.g. {@code &4&lChaos&6&l, &e&lLight}). Shared by the mint path (the physical item name) and the gear
 * renderer (the on-item line), so the two never diverge.
 *
 * <p>The join itself is {@link StyledNames}, shared with the composite-mask {@code {NAME}} token (ADR-0074);
 * this class is the crystal family's binding of that rule to its own token.
 */
public final class CrystalNames {

    /** The token a crystal likeness spells its component names with. */
    public static final String TOKEN = "CRYSTAL";

    private CrystalNames() {
    }

    /** Render {@code template}'s {@code {CRYSTAL}} token from {@code componentKeys} (single or merged). */
    public static String render(String template, List<String> componentKeys, Function<String, String> displayNameOf) {
        return StyledNames.render(template, TOKEN, componentKeys, displayNameOf);
    }

    /** The comma-joined styled display names for {@code componentKeys} (an unknown key falls back to the key). */
    public static String join(String template, List<String> componentKeys, Function<String, String> displayNameOf) {
        return StyledNames.join(template, componentKeys, displayNameOf);
    }
}
