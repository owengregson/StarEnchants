package pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/** The pure pack codec: a tree of files ↔ a ZIP, plus manifest round-trip and the fast manifest peek. */
class PackArchiveTest {

    private static Map<String, byte[]> sampleFiles() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("config.yml", "slots:\n  base: 9\n".getBytes(StandardCharsets.UTF_8));
        files.put("content/enchants/venom.yml", "display: \"&2Venom\"\n".getBytes(StandardCharsets.UTF_8));
        files.put("items/soul-gem.yml", "type: soul-gem\n".getBytes(StandardCharsets.UTF_8));
        return files;
    }

    @Test
    void roundTripPreservesManifestAndFiles() throws Exception {
        PackManifest manifest = PackManifest.of("demo", "A demo pack", "tester", "2026-06-23T00:00:00");
        byte[] bytes = PackArchive.toBytes(manifest, sampleFiles());

        Pack pack = PackArchive.read(new ByteArrayInputStream(bytes));
        assertEquals("demo", pack.manifest().name());
        assertEquals("A demo pack", pack.manifest().description());
        assertEquals("tester", pack.manifest().author());
        assertEquals(3, pack.manifest().fileCount()); // re-stamped from the entries on write
        assertEquals(3, pack.files().size());
        assertArrayEquals("slots:\n  base: 9\n".getBytes(StandardCharsets.UTF_8), pack.files().get("config.yml"));
        assertArrayEquals("type: soul-gem\n".getBytes(StandardCharsets.UTF_8), pack.files().get("items/soul-gem.yml"));
        assertFalse(pack.files().containsKey(PackManifest.ENTRY)); // the manifest is not a file entry
    }

    @Test
    void peekManifestReadsTheHeaderWithoutInflatingEverything() throws Exception {
        byte[] bytes = PackArchive.toBytes(
                PackManifest.of("elite", "EE port", "se", "2026-06-23T00:00:00"), sampleFiles());
        PackManifest peeked = PackArchive.peekManifest(new ByteArrayInputStream(bytes), "fallback");
        assertEquals("elite", peeked.name());
        assertEquals("EE port", peeked.description());
        assertEquals(3, peeked.fileCount());
    }

    @Test
    void sameSurfaceProducesByteIdenticalArchives() throws Exception {
        PackManifest manifest = PackManifest.of("demo", "d", "a", "2026-06-23T00:00:00");
        byte[] a = PackArchive.toBytes(manifest, sampleFiles());
        byte[] b = PackArchive.toBytes(manifest, sampleFiles());
        assertArrayEquals(a, b); // deterministic: zeroed timestamps + sorted entries
    }

    @Test
    void anArchiveWithNoManifestFallsBackToTheGivenName() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("content/enchants/x.yml"));
            zip.write("display: x\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Pack pack = PackArchive.read(new ByteArrayInputStream(buffer.toByteArray()));
        assertEquals("pack", pack.manifest().name());       // read() default
        assertEquals(1, pack.files().size());
        PackManifest peeked = PackArchive.peekManifest(new ByteArrayInputStream(buffer.toByteArray()), "from-filename");
        assertEquals("from-filename", peeked.name());       // peek() fallback
    }

    @Test
    void manifestYamlSurvivesSpecialCharacters() {
        PackManifest manifest = PackManifest.of("p", "Colons: yes, \"quotes\" and \\backslash", "me", "t");
        PackManifest parsed = PackManifest.fromYaml(manifest.toYaml(), "fallback");
        assertEquals("Colons: yes, \"quotes\" and \\backslash", parsed.description());
        assertTrue(manifest.toYaml().contains("name: \"p\""));
    }
}
