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
 * The on-disk authority over the {@code packs/} directory (ADR-0023). It lists the available packs,
 * {@linkplain #export exports} the live config into a new pack, and {@linkplain #apply applies} a pack
 * over the live config — first snapshotting the current config into a backup pack, then staging the
 * new surface and swapping it in, so a failed write never half-clobbers the live config.
 *
 * <p>Pure filesystem + {@link PackArchive}; it never reloads anything. The composition root pairs
 * {@link #apply} with the transactional content reloader so a swapped pack takes effect live. A pack
 * name is its file stem and must be a safe identifier ({@code [A-Za-z0-9_-]+}); the {@code .zip}
 * extension is implicit.
 */
public final class PackStore {

    /** Pack names are plain identifiers — no path separators or dots, so a name can never escape packs/. */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private static final String EXTENSION = ".zip";
    private static final String STAGING = ".pack-staging";

    private final Path dataRoot;
    private final Path packsDir;

    /** @param dataRoot the plugin data folder (its surface is the live config; {@code packs/} sits under it). */
    public PackStore(Path dataRoot) {
        this.dataRoot = dataRoot;
        this.packsDir = dataRoot.resolve("packs");
    }

    /** Whether {@code name} is a valid pack identifier (the only names {@link #export}/{@link #apply} accept). */
    public static boolean isValidName(String name) {
        return name != null && SAFE_NAME.matcher(name).matches();
    }

    /** The packs directory this store manages (created lazily on the first export/backup). */
    public Path directory() {
        return packsDir;
    }

    /** Whether a pack of this name exists on disk. */
    public boolean exists(String name) {
        return isValidName(name) && Files.isRegularFile(packFile(name));
    }

    /**
     * List every {@code *.zip} pack in {@code packs/}, newest manifest first by name, each with its
     * peeked manifest and on-disk size. A pack with a damaged/absent manifest still lists, under its
     * filename. An absent {@code packs/} dir yields an empty list.
     */
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

    /** The manifest of one pack, or empty if no such pack exists. */
    public Optional<PackManifest> info(String name) throws IOException {
        if (!exists(name)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(packFile(name))) {
            return Optional.of(PackArchive.peekManifest(in, name));
        }
    }

    /**
     * Snapshot the current live config surface into {@code packs/<name>.zip}. Overwrites an existing
     * pack of the same name (re-export is intentional). {@code createdIso} stamps the manifest.
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
     * Apply {@code name} over the live config, returning what changed. The sequence is fail-safe:
     * read the pack (fail fast on a corrupt archive) → stage the new surface in a temp dir (fail
     * before touching anything live) → back up the current surface into {@code packs/<backupLabel>.zip}
     * → clear the live surface → promote staging into place. The caller reloads afterwards; a broken
     * (but well-formed) pack still applies on disk and the reloader reports the faults while keeping
     * the previous in-memory state.
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

        // 1. Stage the new surface in a temp dir — if this throws, nothing live has changed yet.
        Path staging = dataRoot.resolve(STAGING);
        PackSurface.deleteRecursively(staging);
        List<String> skipped = PackSurface.writeAll(pack.files(), staging);

        // 2. Back up the current surface (skip if there is nothing to snapshot).
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

        // 3. Swap: clear the live surface, promote the staged surface, drop staging.
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

    /** A pack listed on disk: its name, peeked manifest, and archive size in bytes. */
    public record PackInfo(String name, PackManifest manifest, long sizeBytes) {
    }

    /** The outcome of {@link #export}: the pack name, the file written, and how many config files it holds. */
    public record ExportResult(String name, Path file, int fileCount) {
    }

    /**
     * The outcome of {@link #apply}: the applied pack's manifest, how many files it wrote, the backup
     * pack's name (or {@code null} if nothing was backed up), and any entries skipped as out-of-surface.
     */
    public record ApplyResult(PackManifest manifest, int fileCount, String backupName, List<String> skipped) {

        public ApplyResult {
            skipped = List.copyOf(skipped);
        }

        /** Whether a pre-apply backup was written (false only when the live config was empty). */
        public boolean hasBackup() {
            return backupName != null;
        }
    }
}
