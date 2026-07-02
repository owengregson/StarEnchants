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
        // An absent file falls back to defaults — compare to the default source, never re-type the message.
        assertEquals(Lang.defaults().format("apply.hold-item"), lang.format("apply.hold-item"));
        assertTrue(lang.has("command.give.book"));
    }

    @Test
    void substitutesPlaceholdersInTheDefaultTemplate() {
        // Prove the substitution mechanism on the real default template WITHOUT re-typing the shipped copy:
        // the supplied values appear and no {PLACEHOLDER} is left unresolved.
        String out = Lang.defaults().format("command.give.book", "KEY", "enchants/keen", "LEVEL", 3);
        assertTrue(out.contains("enchants/keen") && out.contains("3"), out);
        assertFalse(out.contains("{KEY}") || out.contains("{LEVEL}"), () -> "unresolved placeholder: " + out);
    }

    @Test
    void missingKeyIsAVisibleMarker() {
        assertEquals("&cnope.not.here?", Lang.defaults().format("nope.not.here"));
    }

    @Test
    void readsMultiLineBlocks() {
        List<String> usage = Lang.defaults().lines("command.pack.usage");
        assertTrue(usage.size() > 3, "a list block yields several lines");
        assertFalse(usage.get(0).isBlank(), "the first line is real content");
    }

    @Test
    void soulModeMessagesAreMultiLineBlocks() {
        // soul.activate/deactivate/empty live in the lists map (multi-line); the runtime sends them via lines()
        assertEquals(4, Lang.defaults().lines("soul.activate").size());
        assertEquals(4, Lang.defaults().lines("soul.deactivate").size());
        assertEquals(4, Lang.defaults().lines("soul.empty").size());
        // soul-use stays a single-line template that substitutes the remaining-count placeholder
        assertTrue(Lang.defaults().format("soul.soul-use", "AMOUNT", 99).contains("99"));
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
        assertEquals("&6grab something!", lang.format("apply.hold-item"));
        assertEquals("&agave x lvl 2", lang.format("command.give.book", "KEY", "x", "LEVEL", 2));
        // an un-overridden key keeps its default — compared to the default source, not a re-typed literal
        assertEquals(Lang.defaults().format("apply.no-such-enchant", "KEY", "z"),
                lang.format("apply.no-such-enchant", "KEY", "z"));
    }

    @Test
    void readsNestedMappingsAsDottedKeys(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("lang.yml");
        Files.writeString(file, """
                apply:
                  hold-item: "&6nested!"
                """);

        Lang lang = LangLoader.load(file);

        assertEquals("&6nested!", lang.format("apply.hold-item"));
    }
}
