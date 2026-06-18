package compile.load;

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
                rows: 5
                prev-slot: 36
                """);
        Files.writeString(dir.resolve("enchanter.yml"), """
                title: "&3Shop"
                """);

        MenusConfig config = MenusLoader.load(dir);

        assertFalse(config.hasErrors());
        MenuLayoutConfig apply = config.forMenu("APPLY").orElseThrow(); // case-insensitive
        assertEquals("&dApply", apply.title().orElseThrow());
        assertEquals("BLACK_STAINED_GLASS_PANE", apply.filler().orElseThrow());
        assertEquals(5, apply.rows().orElseThrow());
        assertEquals(36, apply.prevSlot().orElseThrow());
        assertTrue(apply.nextSlot().isEmpty());      // unset field stays absent → framework keeps the default
        assertTrue(config.forMenu("enchanter").orElseThrow().title().isPresent());
    }

    @Test
    void invalidNumberWarnsAndLeavesFieldAbsent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("apply.yml"), """
                rows: lots
                """);

        MenusConfig config = MenusLoader.load(dir);

        assertFalse(config.hasErrors()); // a bad number is a warning, not blocking
        assertTrue(config.forMenu("apply").orElseThrow().rows().isEmpty());
        assertTrue(config.diagnostics().stream().anyMatch(d -> d.code().equals("W_MENU_NUM")));
    }
}
