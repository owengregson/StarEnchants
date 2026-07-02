package bootstrap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The fast era-tree gate (ADR-0044 G1): the derived companion of the release-time {@code MegaJarGate}, run in
 * every {@code ./gradlew build} so a broken era seam fails LOCALLY, not on the legacy/matrix lanes.
 *
 * <ul>
 *   <li><b>G1-a</b> — the ONLY same-FQN {@code overlay/modern}∩{@code overlay/legacy} twins are the declared
 *       {@link #BINDINGS}; any other twin must become a seam interface + era-exclusive impls.</li>
 *   <li><b>G1-b</b> — the bindings twins are composition-only: no {@code if/else/for/while/switch/try} control
 *       flow, and every instance field is {@code final} (so no state accretes / mutates outside construction).</li>
 *   <li><b>G1-c</b> — no {@code se/*}/{@code src} file names an era-exclusive overlay class (imports or code),
 *       so a missed injection is a compile-time-adjacent failure here, not a legacy-lane surprise.</li>
 *   <li><b>G1-d</b> — {@code EraBindings} declares {@code implements EraServices} in both eras (API parity is
 *       then a per-era javac fact).</li>
 * </ul>
 */
class EraTreeGateTest {

    /** The ONLY same-FQN overlay twins allowed — the two composition-only bindings (dotted FQNs). */
    private static final Set<String> BINDINGS =
            Set.of("bootstrap.compat.EraBindings", "platform.resolve.HandleLookups");

    @Test
    void g1aOnlyBindingsAreSameFqnTwins() {
        Set<String> modern = overlayFqns("modern");
        Set<String> legacy = overlayFqns("legacy");
        Set<String> twins = new TreeSet<>(modern);
        twins.retainAll(legacy);
        for (String twin : twins) {
            if (!BINDINGS.contains(twin)) {
                fail("era-gate: '" + twin + "' is a same-FQN twin but not a declared bindings class — make it a "
                        + "seam interface + era-exclusive impls (ADR-0044 G1-a).");
            }
        }
        for (String binding : BINDINGS) {
            assertTrue(twins.contains(binding),
                    "era-gate: declared binding '" + binding + "' is not present as a same-FQN twin in both eras "
                            + "(ADR-0044 G1-a).");
        }
    }

    @Test
    void g1bBindingsAreCompositionOnly() {
        for (Path source : bindingSources()) {
            String code = stripCommentsAndStrings(read(source));
            Matcher control = CONTROL_FLOW.matcher(code);
            if (control.find()) {
                fail("era-gate: bindings class " + source.getFileName() + " uses control flow '" + control.group()
                        + "' — bindings are construction + one-line delegation only (ADR-0044 G1-b).");
            }
            Matcher mutable = MUTABLE_FIELD.matcher(code);
            if (mutable.find()) {
                fail("era-gate: bindings class " + source.getFileName() + " declares a non-final instance field '"
                        + mutable.group(1).trim() + "' — bindings hold no mutable state (ADR-0044 G1-b).");
            }
        }
    }

    @Test
    void g1cSrcNamesNoEraExclusiveClass() {
        Set<String> modern = overlayFqns("modern");
        Set<String> legacy = overlayFqns("legacy");
        // Era-exclusive = present under exactly one era (the twins are shared, so excluded).
        Set<String> exclusive = new TreeSet<>(modern);
        exclusive.addAll(legacy);
        Set<String> both = new TreeSet<>(modern);
        both.retainAll(legacy);
        exclusive.removeAll(both);
        // Map each era-exclusive simple name to its FQN (the distinctive Modern*/Legacy*/Nms*/Pdc*/Nbt* names).
        Map<String, String> bySimpleName = new TreeMap<>();
        for (String fqn : exclusive) {
            bySimpleName.put(fqn.substring(fqn.lastIndexOf('.') + 1), fqn);
        }
        for (Path source : srcFiles()) {
            String code = stripCommentsAndStrings(read(source));
            for (Map.Entry<String, String> era : bySimpleName.entrySet()) {
                if (Pattern.compile("\\b" + Pattern.quote(era.getKey()) + "\\b").matcher(code).find()) {
                    fail("era-gate: shared src " + rel(source) + " names the era-exclusive class '" + era.getValue()
                            + "' — inject the seam interface instead so both eras compile (ADR-0044 G1-c).");
                }
            }
        }
    }

    @Test
    void g1dEraBindingsImplementsTheSeam() {
        for (Path source : bindingSources()) {
            if (source.getFileName().toString().equals("EraBindings.java")) {
                assertTrue(stripCommentsAndStrings(read(source)).contains("implements EraServices"),
                        "era-gate: " + rel(source) + " must declare 'implements EraServices' so cross-era API "
                                + "parity is a javac fact (ADR-0044 G1-d).");
            }
        }
    }

    // ── helpers ──

    /** Keyword control flow forbidden in bindings (word-bounded, on comment/string-stripped code). */
    private static final Pattern CONTROL_FLOW =
            Pattern.compile("\\b(if|else|for|while|switch|try|catch)\\b");
    /** A non-final instance field declaration (a private field without {@code final}). */
    private static final Pattern MUTABLE_FIELD =
            Pattern.compile("(?m)^\\s+private\\s+(?!final\\b|static\\b)([\\w.<>,\\[\\] ]+\\s+\\w+)\\s*[=;]");

    /** All top-level class FQNs (dotted) under any {@code se/*}/{@code overlay/<era>} tree. */
    private static Set<String> overlayFqns(String era) {
        String marker = "/overlay/" + era + "/";
        Set<String> out = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(repoRoot().resolve("se"))) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains(marker))
                    .forEach(p -> out.add(fqnAfter(p, marker)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** The dotted top-level FQN of a source file, taken from the path segment after {@code marker}. */
    private static String fqnAfter(Path file, String marker) {
        String s = file.toString().replace('\\', '/');
        String tail = s.substring(s.indexOf(marker) + marker.length());
        return tail.substring(0, tail.length() - ".java".length()).replace('/', '.');
    }

    /** Both era source files of every declared bindings class. */
    private static List<Path> bindingSources() {
        List<Path> out = new java.util.ArrayList<>();
        try (Stream<Path> walk = Files.walk(repoRoot().resolve("se"))) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String u = p.toString().replace('\\', '/');
                        return u.contains("/overlay/modern/") || u.contains("/overlay/legacy/");
                    })
                    .filter(p -> {
                        String u = p.toString().replace('\\', '/');
                        String marker = u.contains("/overlay/modern/") ? "/overlay/modern/" : "/overlay/legacy/";
                        return BINDINGS.contains(fqnAfter(p, marker));
                    })
                    .forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /**
     * The modules that do NOT dual-compile, so an era-class reference in their src cannot break the other era:
     * the tool-only modules (never shipped — tester/testfx/imagegen/bench) and the modern-only {@code integrate}
     * (excluded from the 1.8 tree). Only the shared dual-compiled plugin src is subject to G1-c.
     */
    private static final Set<String> NON_DUAL_COMPILED =
            Set.of("/se/tester/", "/se/testfx/", "/se/imagegen/", "/se/bench/", "/se/integrate/");

    /** Every SHARED (dual-compiled) {@code se/*}/{@code src} Java source (never a test/overlay/tool tree). */
    private static List<Path> srcFiles() {
        List<Path> out = new java.util.ArrayList<>();
        try (Stream<Path> walk = Files.walk(repoRoot().resolve("se"))) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String u = p.toString().replace('\\', '/');
                        if (!u.contains("/src/") || u.contains("/overlay/") || u.contains("/test/")) {
                            return false;
                        }
                        return NON_DUAL_COMPILED.stream().noneMatch(u::contains);
                    })
                    .forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String rel(Path p) {
        return repoRoot().relativize(p).toString().replace('\\', '/');
    }

    /** Replace every line comment, block comment, and string/char literal with spaces so token scans see code only. */
    private static String stripCommentsAndStrings(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            char next = i + 1 < n ? src.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    out.append(src.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                i += 2;
            } else if (c == '"' || c == '\'') {
                char quote = c;
                out.append(' ');
                i++;
                while (i < n && src.charAt(i) != quote) {
                    if (src.charAt(i) == '\\' && i + 1 < n) {
                        i++;
                    }
                    i++;
                }
                out.append(' ');
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (no settings.gradle.kts found)");
    }
}
