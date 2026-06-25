package pack;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The on-disk authority over {@code packs/} (ADR-0023): list, export, apply. Pure filesystem +
 * {@link PackArchive}; it never reloads (the composition root pairs {@link #apply} with the reloader).
 */
public final class PackStore {

    /** No path separators or dots, so a pack name can never escape packs/. */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private static final String EXTENSION = ".zip";
    private static final String STAGING = ".pack-staging";

    private final Path dataRoot;
    private final Path packsDir;

    public PackStore(Path dataRoot) {
        this.dataRoot = dataRoot;
        this.packsDir = dataRoot.resolve("packs");
    }

    public static boolean isValidName(String name) {
        return name != null && SAFE_NAME.matcher(name).matches();
    }

    public Path directory() {
        return packsDir;
    }

    public boolean exists(String name) {
        return isValidName(name) && Files.isRegularFile(packFile(name));
    }

    /** List every {@code *.zip} pack with its peeked manifest and size; a damaged manifest still lists under its filename. */
    public List<PackInfo> list() throws IOException {
        if (!Files.isDirectory(packsDir)) {
            return List.of();
        }
        List<PackInfo> out = new ArrayList<>();
        try (Stream<Path> entries = Files.list(packsDir)) {
            List<Path> zips = entries.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(EXTENSION))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path zip : zips) {
                String name = stem(zip);
                PackManifest manifest;
                try (InputStream in = Files.newInputStream(zip)) {
                    manifest = PackArchive.peekManifest(in, name);
                } catch (IOException corrupt) {
                    manifest = PackManifest.of(name, "(unreadable archive)", "", "");
                }
                out.add(new PackInfo(name, manifest, Files.size(zip)));
            }
        }
        return out;
    }

    public Optional<PackManifest> info(String name) throws IOException {
        if (!exists(name)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(packFile(name))) {
            return Optional.of(PackArchive.peekManifest(in, name));
        }
    }

    /**
     * Snapshot the live surface into {@code packs/<name>.zip}, overwriting any existing pack of that name.
     *
     * @throws IllegalArgumentException if {@code name} is not a safe identifier
     */
    public ExportResult export(String name, String description, String author, String createdIso)
            throws IOException {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("invalid pack name '" + name + "' (use letters/digits/_/-)");
        }
        Map<String, byte[]> surface = PackSurface.collect(dataRoot);
        PackManifest manifest = new PackManifest(name, description, author, PackManifest.CURRENT_FORMAT,
                createdIso, surface.size());
        Files.createDirectories(packsDir);
        Path file = packFile(name);
        try (OutputStream out = Files.newOutputStream(file)) {
            PackArchive.write(out, manifest, surface);
        }
        return new ExportResult(name, file, surface.size());
    }

    /**
     * Apply {@code name} over the live config. The sequence is fail-safe: read → stage in a temp dir →
     * back up the current surface → clear → promote, so a failed write never half-clobbers the live config.
     *
     * @param backupLabel a safe pack name for the pre-apply backup (e.g. {@code backup-2026-06-23-1430})
     * @param createdIso  the ISO timestamp stamped into the backup manifest
     */
    public ApplyResult apply(String name, String backupLabel, String createdIso) throws IOException {
        if (!exists(name)) {
            throw new IOException("no such pack '" + name + "'");
        }
        Pack pack;
        try (InputStream in = Files.newInputStream(packFile(name))) {
            pack = PackArchive.read(in);
        }

        // Stage in a temp dir first — if this throws, nothing live has changed yet.
        Path staging = dataRoot.resolve(STAGING);
        PackSurface.deleteRecursively(staging);
        List<String> skipped = PackSurface.writeAll(pack.files(), staging);

        String backupName = null;
        Map<String, byte[]> current = PackSurface.collect(dataRoot);
        if (!current.isEmpty() && isValidName(backupLabel)) {
            PackManifest backupManifest = new PackManifest(backupLabel,
                    "Automatic backup taken before applying '" + name + "'.", "auto",
                    PackManifest.CURRENT_FORMAT, createdIso, current.size());
            Files.createDirectories(packsDir);
            try (OutputStream out = Files.newOutputStream(packFile(backupLabel))) {
                PackArchive.write(out, backupManifest, current);
            }
            backupName = backupLabel;
        }

        PackSurface.clear(dataRoot);
        PackSurface.promote(staging, dataRoot);
        PackSurface.deleteRecursively(staging);

        return new ApplyResult(pack.manifest(), pack.files().size(), backupName, skipped);
    }

    private Path packFile(String name) {
        return packsDir.resolve(name + EXTENSION);
    }

    private static String stem(Path zip) {
        String file = zip.getFileName().toString();
        return file.endsWith(EXTENSION) ? file.substring(0, file.length() - EXTENSION.length()) : file;
    }

    public record PackInfo(String name, PackManifest manifest, long sizeBytes) {
    }

    public record ExportResult(String name, Path file, int fileCount) {
    }

    /** {@code backupName} is null when the live config was empty; {@code skipped} are out-of-surface entries. */
    public record ApplyResult(PackManifest manifest, int fileCount, String backupName, List<String> skipped) {

        public ApplyResult {
            skipped = List.copyOf(skipped);
        }

        public boolean hasBackup() {
            return backupName != null;
        }
    }
}
