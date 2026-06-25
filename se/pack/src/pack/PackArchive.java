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
 * The pure pack codec (ADR-0023): config files ↔ ZIP. Two invariants callers rely on: the manifest is
 * the FIRST entry so {@link #peekManifest} reads metadata without inflating the archive, and entries are
 * sorted with timestamps zeroed so a given surface yields a byte-identical archive (diffable, reproducible).
 */
public final class PackArchive {

    private PackArchive() {
    }

    /** Write the manifest (file count re-stamped) as the first entry, then files in sorted order. Caller closes {@code out}. */
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

    /** Read only the manifest, stopping at the first ({@code pack.yml}) entry; falls back to a filename-derived manifest. */
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

    public static byte[] toBytes(PackManifest manifest, Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        write(buffer, manifest, files);
        return buffer.toByteArray();
    }
}
