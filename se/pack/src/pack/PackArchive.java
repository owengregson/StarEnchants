package pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The pure pack codec (ADR-0023): a tree of config files ↔ a ZIP archive. The manifest is always the
 * FIRST entry ({@code pack.yml}), so {@link #peekManifest(InputStream)} can read a pack's metadata
 * (for {@code /se pack list}) without inflating the whole archive. Entries are written in sorted order
 * with timestamps zeroed, so the same config surface always produces a byte-identical archive
 * (deterministic for review, diffing, and reproducible jars).
 *
 * <p>This class only moves bytes — it never walks a filesystem (that is {@link PackStore}) and never
 * interprets the YAML inside an entry. Directory entries are not written; the relative paths carry the
 * structure and the extractor recreates parents.
 */
public final class PackArchive {

    private PackArchive() {
    }

    /**
     * Write a pack: the {@code manifest} (with its file count re-stamped from {@code files}) as the
     * first entry, then every file entry in sorted-path order. Bytes are flushed to {@code out}; the
     * caller owns closing it.
     */
    public static void write(OutputStream out, PackManifest manifest, Map<String, byte[]> files)
            throws IOException {
        Map<String, byte[]> sorted = new TreeMap<>(files);
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            putEntry(zip, PackManifest.ENTRY,
                    manifest.withFileCount(sorted.size()).toYaml().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (Map.Entry<String, byte[]> file : sorted.entrySet()) {
                putEntry(zip, file.getKey(), file.getValue());
            }
        }
    }

    /** Read an entire pack from {@code in} into memory. {@code pack.yml} becomes the manifest. */
    public static Pack read(InputStream in) throws IOException {
        PackManifest manifest = null;
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                byte[] bytes = zip.readAllBytes();
                if (name.equals(PackManifest.ENTRY)) {
                    manifest = PackManifest.fromYaml(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), "pack");
                } else {
                    files.put(name, bytes);
                }
            }
        }
        if (manifest == null) {
            manifest = new PackManifest("pack", "", "", PackManifest.CURRENT_FORMAT, "", files.size());
        }
        return new Pack(manifest, files);
    }

    /**
     * Read only the manifest from {@code in}, stopping as soon as {@code pack.yml} is seen (it is the
     * first entry). Returns a filename-derived manifest if the archive has no {@code pack.yml}.
     */
    public static PackManifest peekManifest(InputStream in, String fallbackName) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().replace('\\', '/').equals(PackManifest.ENTRY)) {
                    String yaml = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    return PackManifest.fromYaml(yaml, fallbackName);
                }
            }
        }
        return PackManifest.of(fallbackName, "", "", "");
    }

    private static void putEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L); // zero timestamps → a given config surface yields a byte-identical archive
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    /** A pack's bytes when a caller wants the whole archive in memory. */
    public static byte[] toBytes(PackManifest manifest, Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        write(buffer, manifest, files);
        return buffer.toByteArray();
    }
}
