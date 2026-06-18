package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Loads lang.yml (§L) — defaults, key override, placeholder substitution, list blocks, missing-key marker. */
class LangLoaderTest {

    @Test
    void absentFileIsDefaults() {
        Lang lang = LangLoader.load(Path.of("/no/such/lang.yml"));
        assertFalse(lang.hasErrors());
        assertEquals("&cHold an item first.", lang.format("apply.hold-item"));
        assertTrue(lang.has("command.give.book"));
    }

    @Test
    void substitutesPlaceholders() {
        Lang lang = Lang.defaults();
        assertEquals("&aMinted an enchant book for &fenchants/keen &7(level 3)&a.",
                lang.format("command.give.book", "KEY", "enchants/keen", "LEVEL", 3));
    }

    @Test
    void missingKeyIsAVisibleMarker() {
        assertEquals("&cnope.not.here?", Lang.defaults().format("nope.not.here"));
    }

    @Test
    void readsMultiLineBlocks() {
        List<String> usage = Lang.defaults().lines("command.usage");
        assertTrue(usage.size() > 5);
        assertEquals("&eStarEnchants commands:", usage.get(0));
    }

    @Test
    void fileOverridesADefaultKeyAndKeepsTheRest(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("lang.yml");
        Files.writeString(file, """
                apply.hold-item: "&6grab something!"
                command.give.book: "&agave {KEY} lvl {LEVEL}"
                """);

        Lang lang = LangLoader.load(file);

        assertFalse(lang.hasErrors());
        assertEquals("&6grab something!", lang.format("apply.hold-item"));                 // overridden
        assertEquals("&agave x lvl 2", lang.format("command.give.book", "KEY", "x", "LEVEL", 2));
        assertEquals("&cNo such enchant: &fz", lang.format("apply.no-such-enchant", "KEY", "z")); // default kept
    }

    @Test
    void readsNestedMappingsAsDottedKeys(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("lang.yml");
        Files.writeString(file, """
                apply:
                  hold-item: "&6nested!"
                """);

        Lang lang = LangLoader.load(file);

        assertEquals("&6nested!", lang.format("apply.hold-item")); // command.give.* etc. flat or nested both work
    }
}
