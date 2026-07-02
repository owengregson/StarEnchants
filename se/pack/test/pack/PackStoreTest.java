package pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The on-disk pack store: export the live surface, list/peek, and the backup + stage + swap of apply. */
class PackStoreTest {

    private static void write(Path root, String rel, String content) throws Exception {
        Path file = root.resolve(rel);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /** A minimal live config surface under {@code root}. */
    private static void writeSurfaceA(Path root) throws Exception {
        write(root, "config.yml", "slots:\n  base: 9\n");
        write(root, "lang.yml", "prefix: A\n");
        write(root, "content/enchants/venom.yml", "display: venom-A\n");
        write(root, "content/tiers.yml", "default-tier: common\n");
        write(root, "items/soul-gem.yml", "type: soul-gem\n");
        write(root, "menus/apply.yml", "title: A\n");
        Files.createDirectories(root.resolve(".DS_Store").getParent());
        Files.writeString(root.resolve("content/.DS_Store"), "junk"); // a dotfile to prove it is skipped
    }

    @Test
    void exportSnapshotsTheSurfaceThenListAndInfoSeeIt(@TempDir Path data) throws Exception {
        writeSurfaceA(data);

        PackStore store = new PackStore(data);
        PackStore.ExportResult result = store.export("mine", "my pack", "owen", "2026-06-23T10:00:00",
                "1:feed", "effects 7");

        assertTrue(Files.isRegularFile(result.file()));
        assertEquals(6, result.fileCount()); // 2 files + 4 dir files; the .DS_Store dotfile is excluded

        List<PackStore.PackInfo> packs = store.list();
        assertEquals(1, packs.size());
        assertEquals("mine", packs.get(0).name());
        assertEquals("my pack", packs.get(0).manifest().description());
        PackManifest info = store.info("mine").orElseThrow();
        assertEquals("owen", info.author());
        assertEquals("1:feed", info.fingerprint()); // ADR-0046 stamp survives export → peek
        assertEquals("effects 7", info.surface());
    }

    @Test
    void applyReplacesTheWholeSurfaceAndBacksUpTheOldOne(@TempDir Path data) throws Exception {
        // Capture surface A as pack "a".
        writeSurfaceA(data);
        PackStore store = new PackStore(data);
        store.export("a", "surface A", "se", "2026-06-23T10:00:00", "1:aaaa", "effects 1");

        // Mutate the live config into a different surface B: change a file, drop one, add one.
        Files.writeString(data.resolve("config.yml"), "slots:\n  base: 12\n");
        Files.delete(data.resolve("content/enchants/venom.yml"));
        write(data, "content/enchants/bleed.yml", "display: bleed-B\n");
        write(data, "items/dust.yml", "type: dust\n");

        // Apply pack "a" — surface must become exactly A again, and B is backed up.
        PackStore.ApplyResult applied = store.apply("a", "backup-test", "2026-06-23T11:00:00", "1:live", "effects 2");
        assertTrue(applied.hasBackup());
        assertEquals("backup-test", applied.backupName());

        // Live surface is exactly A.
        assertEquals("slots:\n  base: 9\n", Files.readString(data.resolve("config.yml")));
        assertEquals("display: venom-A\n", Files.readString(data.resolve("content/enchants/venom.yml")));
        assertFalse(Files.exists(data.resolve("content/enchants/bleed.yml")), "a B-only file must be gone");
        assertFalse(Files.exists(data.resolve("items/dust.yml")), "a B-only file must be gone");
        assertFalse(Files.exists(data.resolve(".pack-staging")), "staging must be cleaned up");

        // The backup pack holds surface B (the pre-apply state).
        Pack backup;
        try (InputStream in = Files.newInputStream(store.directory().resolve("backup-test.zip"))) {
            backup = PackArchive.read(in);
        }
        assertEquals("slots:\n  base: 12\n", new String(backup.files().get("config.yml"), StandardCharsets.UTF_8));
        assertTrue(backup.files().containsKey("content/enchants/bleed.yml"));
        assertFalse(backup.files().containsKey("content/enchants/venom.yml"));
        assertEquals("1:live", backup.manifest().fingerprint()); // ADR-0046: the auto-backup is stamped with the LIVE surface
    }

    @Test
    void applyOnlyWritesInsideTheSurfaceAndSkipsEscapingEntries(@TempDir Path data) throws Exception {
        // Hand-craft a pack carrying a foreign top-level file and a path-escape attempt.
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("config.yml", "ok: true\n".getBytes(StandardCharsets.UTF_8));
        files.put("content/enchants/x.yml", "display: x\n".getBytes(StandardCharsets.UTF_8));
        files.put("evil.txt", "nope".getBytes(StandardCharsets.UTF_8));            // foreign top-level
        files.put("../escape.txt", "nope".getBytes(StandardCharsets.UTF_8));        // path escape
        Files.createDirectories(data.resolve("packs"));
        try (var out = Files.newOutputStream(data.resolve("packs/sketchy.zip"))) {
            PackArchive.write(out, PackManifest.of("sketchy", "", "", "t"), files);
        }

        PackStore store = new PackStore(data);
        PackStore.ApplyResult applied = store.apply("sketchy", "backup-x", "2026-06-23T12:00:00", "1:live", "s");

        assertTrue(Files.exists(data.resolve("config.yml")));
        assertTrue(Files.exists(data.resolve("content/enchants/x.yml")));
        assertFalse(Files.exists(data.resolve("evil.txt")), "a foreign top-level entry must be skipped");
        assertFalse(Files.exists(data.getParent().resolve("escape.txt")), "a path-escape entry must be skipped");
        assertTrue(applied.skipped().contains("evil.txt"));
        assertTrue(applied.skipped().contains("../escape.txt"));
    }

    @Test
    void invalidNamesAndMissingPacksAreRejected(@TempDir Path data) throws Exception {
        PackStore store = new PackStore(data);
        assertThrows(IllegalArgumentException.class, () -> store.export("../bad", "", "", "t", "", ""));
        assertThrows(IllegalArgumentException.class, () -> store.export("with/slash", "", "", "t", "", ""));
        assertThrows(java.io.IOException.class, () -> store.apply("ghost", "backup", "t", "", ""));
        assertFalse(store.exists("ghost"));
        assertTrue(store.list().isEmpty()); // no packs/ dir yet
    }

    // ── ADR-0046 gate surface: load() + stageInto() ──

    @Test
    void loadReadsTheManifestAndFilesAndMissingIsEmpty(@TempDir Path data) throws Exception {
        writeSurfaceA(data);
        PackStore store = new PackStore(data);
        store.export("a", "surface A", "se", "2026-06-23T10:00:00", "1:aaaa", "effects 1");

        Pack loaded = store.load("a").orElseThrow();
        assertEquals("1:aaaa", loaded.manifest().fingerprint());
        assertTrue(loaded.files().containsKey("content/enchants/venom.yml"));
        assertTrue(store.load("ghost").isEmpty());
    }

    @Test
    void stageIntoWritesTheSurfaceAndSkipsEscapes(@TempDir Path data) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("config.yml", "ok: true\n".getBytes(StandardCharsets.UTF_8));
        files.put("../escape.txt", "nope".getBytes(StandardCharsets.UTF_8));
        files.put("evil.txt", "nope".getBytes(StandardCharsets.UTF_8));

        Path scratch = data.resolve("scratch");
        List<String> skipped = PackStore.stageInto(files, scratch);

        assertTrue(Files.exists(scratch.resolve("config.yml")));
        assertTrue(skipped.contains("../escape.txt"));
        assertTrue(skipped.contains("evil.txt"));
        assertFalse(Files.exists(data.resolve("escape.txt")), "a path-escape entry must never leave the scratch dir");
    }
}
