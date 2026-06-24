package pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Defines and manipulates the <em>config surface</em> a pack captures (ADR-0023): the top-level
 * {@code config.yml} + {@code lang.yml} files and the {@code content/}, {@code items/}, {@code menus/}
 * trees — exactly what {@code StarEnchantsPlugin.saveDefaults()} extracts on first boot. Everything
 * else in the data folder (the {@code packs/} dir itself, {@code migrated/}, staging, dotfiles) is
 * outside the surface and is never collected or overwritten.
 *
 * <p>All paths are keyed forward-slash, relative to a root. {@link #writeAll} validates every entry
 * stays within the surface (no {@code ..}, no absolute path, no foreign top-level), so applying an
 * untrusted pack can never write outside {@code content/items/menus/config/lang}.
 */
final class PackSurface {

    /** Top-level single files in the surface. */
    static final List<String> FILES = List.of("config.yml", "lang.yml");

    /** Top-level recursive directories in the surface. */
    static final List<String> DIRS = List.of("content", "items", "menus");

    private PackSurface() {
    }

    /**
     * Collect the live config surface under {@code root} into a path→bytes map. Dotfiles (e.g.
     * {@code .DS_Store}) are skipped so a pack never carries OS junk. An absent file/dir contributes
     * nothing (a pack faithfully omits what the config does not have).
     */
    static Map<String, byte[]> collect(Path root) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String name : FILES) {
            Path file = root.resolve(name);
            if (Files.isRegularFile(file)) {
                files.put(name, Files.readAllBytes(file));
            }
        }
        for (String dir : DIRS) {
            Path base = root.resolve(dir);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(base)) {
                List<Path> regular = walk.filter(Files::isRegularFile)
                        .filter(p -> !p.getFileName().toString().startsWith("."))
                        .sorted(Comparator.naturalOrder())
                        .toList();
                for (Path p : regular) {
                    String rel = root.relativize(p).toString().replace('\\', '/');
                    files.put(rel, Files.readAllBytes(p));
                }
            }
        }
        return files;
    }

    /**
     * Materialise {@code files} under {@code target} (creating parents). Entries that escape the
     * surface (a {@code ..}, an absolute path, or a top-level name that is not a surface file/dir) are
     * skipped and returned, so the caller can report a sanitised count.
     */
    static List<String> writeAll(Map<String, byte[]> files, Path target) throws IOException {
        List<String> skipped = new ArrayList<>();
        Path base = target.normalize();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String rel = entry.getKey().replace('\\', '/');
            if (!withinSurface(rel)) {
                skipped.add(rel);
                continue;
            }
            Path out = base.resolve(rel).normalize();
            if (!out.startsWith(base)) {
                skipped.add(rel); // defence in depth: never escape the target root
                continue;
            }
            Files.createDirectories(out.getParent());
            Files.write(out, entry.getValue());
        }
        return skipped;
    }

    /** Delete the live surface under {@code root} (the surface files + the surface dirs, recursively). */
    static void clear(Path root) throws IOException {
        for (String name : FILES) {
            Files.deleteIfExists(root.resolve(name));
        }
        for (String dir : DIRS) {
            deleteRecursively(root.resolve(dir));
        }
    }

    /**
     * Move every surface root present in {@code staging} into {@code root}, replacing what is there.
     * Within one filesystem this is a per-root rename (effectively atomic), so the swap is fast and
     * leaves no half-written tree.
     */
    static void promote(Path staging, Path root) throws IOException {
        for (String name : FILES) {
            moveIfPresent(staging.resolve(name), root.resolve(name));
        }
        for (String dir : DIRS) {
            moveIfPresent(staging.resolve(dir), root.resolve(dir));
        }
    }

    /** Whether a relative entry path is inside the surface (a surface file, or under a surface dir). */
    private static boolean withinSurface(String rel) {
        if (rel.isEmpty() || rel.startsWith("/") || rel.contains("..")) {
            return false;
        }
        if (FILES.contains(rel)) {
            return true;
        }
        int slash = rel.indexOf('/');
        String top = slash < 0 ? rel : rel.substring(0, slash);
        return slash > 0 && DIRS.contains(top);
    }

    private static void moveIfPresent(Path from, Path to) throws IOException {
        if (!Files.exists(from)) {
            return;
        }
        Files.createDirectories(to.getParent());
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            List<Path> entries = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : entries) {
                Files.deleteIfExists(p);
            }
        }
    }
}
