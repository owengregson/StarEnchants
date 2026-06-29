package feature.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import compile.load.MenuLayoutConfig;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * The §L merge of a menus/<name>.yml chrome override onto {@link MenuTheme#DEFAULT} (ADR-0030): a set button
 * material/name wins; an unset one keeps the default; {@code info-slot} relocates the info pane. Pure, no server.
 */
class MenuThemeFromTest {

    /** A config that overrides only the prev-button material + name and the info slot. */
    private static MenuLayoutConfig chrome(String prevMaterial, String prevName, int infoSlot) {
        return new MenuLayoutConfig(OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(),
                Optional.of(prevMaterial), Optional.of(prevName), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), OptionalInt.of(infoSlot));
    }

    @Test
    void nullOverrideReturnsTheDefaultUnchanged() {
        assertSame(MenuTheme.DEFAULT, MenuTheme.from(MenuTheme.DEFAULT, null));
    }

    @Test
    void setButtonFieldsWinAndUnsetKeepTheDefault() {
        MenuTheme merged = MenuTheme.from(MenuTheme.DEFAULT, chrome("DIAMOND", "&bWarp Back", 8));

        assertEquals("DIAMOND", merged.prev().material());
        assertEquals("&bWarp Back", merged.prev().name());
        assertEquals(8, merged.infoSlot());
        // An unset button keeps the shipped default (single-sourced from DEFAULT, not a re-typed literal).
        assertEquals(MenuTheme.DEFAULT.next().name(), merged.next().name());
        assertEquals(MenuTheme.DEFAULT.close().material(), merged.close().material());
    }
}
