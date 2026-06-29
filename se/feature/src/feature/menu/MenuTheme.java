package feature.menu;

import compile.load.MenuLayoutConfig;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;

/**
 * The look-only chrome of a menu (docs/v3-directives.md §K, ADR-0030): the four navigation buttons and the
 * info pane, separated from {@link MenuLayout}'s geometry so a pack can restyle the chrome (button material /
 * name, info pane) without touching slot positions. Resolved per render from a programmatic {@link #DEFAULT}
 * merged with the menu's {@code menus/<name>.yml} override ({@link #from}), mirroring {@link MenuLayout#from}.
 *
 * @param prev     the previous-page button
 * @param next     the next-page button
 * @param back     the drill-up button (only shown by menus that have a parent view)
 * @param close    the close button
 * @param info     the info pane describing the menu (rendered only where it would not clobber content)
 * @param infoSlot the raw slot of the info pane, or {@code -1} to hide it
 */
public record MenuTheme(NavButton prev, NavButton next, NavButton back, NavButton close,
                        NavButton info, int infoSlot) {

    public MenuTheme {
        Objects.requireNonNull(prev, "prev");
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(back, "back");
        Objects.requireNonNull(close, "close");
        Objects.requireNonNull(info, "info");
    }

    /**
     * The shipped default chrome — a consistent, hand-crafted set every menu inherits (consistency is what
     * makes the whole GUI read as one designed surface). Unicode glyphs render on every supported client.
     */
    public static final MenuTheme DEFAULT = new MenuTheme(
            NavButton.of("ARROW", Material.ARROW, "&a&l« Previous", "&7Flip back a page."),
            NavButton.of("ARROW", Material.ARROW, "&a&lNext »", "&7Flip forward a page."),
            NavButton.of("OAK_DOOR", Material.ARROW, "&e&l⤶ Go Back", "&7Return to the previous menu."),
            NavButton.of("BARRIER", Material.BARRIER, "&c&l✖ Close", "&7Close this menu."),
            NavButton.of("NETHER_STAR", Material.PAPER, "&b&lStarEnchants", "&7Hover an item for details."),
            4);

    /**
     * Merge an operator's {@link MenuLayoutConfig} chrome overrides onto {@code def}: a set button material or
     * name wins, an unset one keeps the default; {@code info-slot} relocates/hides the info pane. A {@code null}
     * override returns {@code def} unchanged. Button LORE stays code-owned (the configurable surface is the
     * material, name, frame and slots — enough to restyle without an unbounded config).
     */
    public static MenuTheme from(MenuTheme def, MenuLayoutConfig override) {
        if (override == null) {
            return def;
        }
        return new MenuTheme(
                button(def.prev(), override.prevButtonMaterial(), override.prevButtonName()),
                button(def.next(), override.nextButtonMaterial(), override.nextButtonName()),
                button(def.back(), override.backButtonMaterial(), override.backButtonName()),
                button(def.close(), override.closeButtonMaterial(), override.closeButtonName()),
                button(def.info(), override.infoMaterial(), override.infoName()),
                override.infoSlot().orElse(def.infoSlot()));
    }

    private static NavButton button(NavButton def, java.util.Optional<String> material,
                                    java.util.Optional<String> name) {
        NavButton out = def;
        if (material.isPresent() && !material.get().isBlank()) {
            out = out.withMaterial(material.get(), def.fallback());
        }
        if (name.isPresent()) {
            out = out.withName(name.get());
        }
        return out;
    }

    /** Replace the info pane's name + lore (a menu describing itself), keeping the configured material/slot. */
    public MenuTheme withInfo(String name, List<String> lore) {
        return new MenuTheme(prev, next, back, close,
                new NavButton(info.material(), info.fallback(), name, lore), infoSlot);
    }
}
