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

/** The files/dirs a pack captures (ADR-0023). {@link #writeAll} rejects escapes (.., absolute, foreign top-level). */
final class PackSurface {

    static final List<String> FILES = List.of("config.yml", "lang.yml");

    static final List<String> DIRS = List.of("content", "items", "menus");

    private PackSurface() {
    }

    /** Collect the live surface under {@code root} into a path→bytes map; dotfiles skipped, absent entries omitted. */
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

    /** Write {@code files} under {@code target}; out-of-surface entries are skipped and returned. */
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

    static void clear(Path root) throws IOException {
        for (String name : FILES) {
            Files.deleteIfExists(root.resolve(name));
        }
        for (String dir : DIRS) {
            deleteRecursively(root.resolve(dir));
        }
    }

    /** Move each surface root from {@code staging} into {@code root}; a per-root rename within one filesystem (~atomic). */
    static void promote(Path staging, Path root) throws IOException {
        for (String name : FILES) {
            moveIfPresent(staging.resolve(name), root.resolve(name));
        }
        for (String dir : DIRS) {
            moveIfPresent(staging.resolve(dir), root.resolve(dir));
        }
    }

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
