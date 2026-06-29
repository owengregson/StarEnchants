package compile.load;

import schema.diag.DiagCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Loads the menus/ folder (§L) — one file per menu, surfaced fields, defaults, and the diagnostic cases. */
class MenusLoaderTest {

    @Test
    void absentFolderIsEmpty() {
        MenusConfig config = MenusLoader.load(Path.of("/no/such/menus"));
        assertFalse(config.hasErrors());
        assertTrue(config.forMenu("apply").isEmpty());
    }

    @Test
    void readsAFilePerMenuKeyedByStem(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("apply.yml"), """
                title: "&dApply"
                filler: "BLACK_STAINED_GLASS_PANE"
                frame: "border"
                rows: 5
                prev-slot: 36
                prev-material: "SPECTRAL_ARROW"
                info-name: "&bHelp"
                info-slot: 4
                """);
        Files.writeString(dir.resolve("enchanter.yml"), """
                title: "&3Shop"
                """);

        MenusConfig config = MenusLoader.load(dir);

        assertFalse(config.hasErrors());
        MenuLayoutConfig apply = config.forMenu("APPLY").orElseThrow(); // case-insensitive
        assertEquals("&dApply", apply.title().orElseThrow());
        assertEquals("BLACK_STAINED_GLASS_PANE", apply.filler().orElseThrow());
        assertEquals("border", apply.frame().orElseThrow());
        assertEquals(5, apply.rows().orElseThrow());
        assertEquals(36, apply.prevSlot().orElseThrow());
        assertEquals("SPECTRAL_ARROW", apply.prevButtonMaterial().orElseThrow()); // chrome key surfaced
        assertEquals("&bHelp", apply.infoName().orElseThrow());
        assertEquals(4, apply.infoSlot().orElseThrow());
        assertTrue(apply.nextSlot().isEmpty()); // unset stays absent → framework keeps its default
        assertTrue(apply.backButtonName().isEmpty()); // an unset chrome key stays absent too
        assertTrue(config.forMenu("enchanter").orElseThrow().title().isPresent());
    }

    @Test
    void invalidNumberWarnsAndLeavesFieldAbsent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("apply.yml"), """
                rows: lots
                """);

        MenusConfig config = MenusLoader.load(dir);

        assertFalse(config.hasErrors()); // a bad number warns, never blocks
        assertTrue(config.forMenu("apply").orElseThrow().rows().isEmpty());
        assertTrue(config.diagnostics().stream().anyMatch(d -> d.is(DiagCode.W_MENU_NUM)));
    }
}
