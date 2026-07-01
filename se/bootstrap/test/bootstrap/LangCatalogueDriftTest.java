package bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.load.Lang;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The single-source guard for the unified message catalogue (§L). Now that {@link Lang#defaults()} parses the
 * ONE bundled {@code /lang.yml} resource (no second Java copy), these tests keep it honest:
 * <ol>
 *   <li>the bundled resource loads clean and non-empty — a packaging slip that dropped it would blank every
 *       message, so fail the build instead;</li>
 *   <li>every message key {@code messages.format/lines/send("…")} referenced in production code exists in the
 *       catalogue — this is what turns the class of bug this refactor fixed (a menu looking up a key that only
 *       lived in the shipped yml, not the fallback) into an offline build failure rather than a live
 *       {@code &c<key>?} marker;</li>
 *   <li>every key a pack {@code lang.yml} overrides is a real catalogue key — a pack is an OVERLAY, so a stale
 *       or typo'd key would silently never render.</li>
 * </ol>
 *
 * <p>Keys are structural identifiers, not user-facing copy, so asserting their presence does not re-type any
 * shipped message (writing-tests Rule 1). Only literal keys are checked; a key built from a variable (a few
 * dynamic command paths) can't be verified statically — the dominant literal-key path is what drifts.
 */
class LangCatalogueDriftTest {

    // messages.format("key") / messages.lines("key") — the key is the first string arg.
    private static final Pattern FORMAT_OR_LINES = Pattern.compile(
            "\\b(?:messages|lang)\\.(?:format|lines)\\(\\s*\"([a-z][a-z0-9]*(?:[.-][a-z0-9]+)+)\"");
    // messages.send(recipient, "key", …) — the key is the SECOND arg (the recipient comes first).
    private static final Pattern SEND = Pattern.compile(
            "\\bmessages\\.send\\(\\s*[^,]+,\\s*\"([a-z][a-z0-9]*(?:[.-][a-z0-9]+)+)\"");

    @Test
    void bundledDefaultsLoadCleanAndAreNonEmpty() {
        Lang defaults = Lang.defaults();
        assertFalse(defaults.hasErrors(),
                () -> "the bundled /lang.yml has blocking diagnostics: " + defaults.diagnostics());
        assertFalse(defaults.singles().isEmpty() && defaults.lists().isEmpty(),
                "the bundled /lang.yml resource did not load — the catalogue is empty, which would render every "
                        + "message as a &c<key>? marker (a packaging regression)");
    }

    @Test
    void everyLangKeyReferencedInProductionCodeExistsInTheCatalogue() throws IOException {
        Set<String> known = knownKeys();
        Path root = repoRoot();
        Set<String> referenced = new HashSet<>();
        Map<String, String> missing = new TreeMap<>(); // key -> first file it was seen in
        for (Path java : productionSources(root)) {
            String text = Files.readString(java);
            for (Pattern p : List.of(FORMAT_OR_LINES, SEND)) {
                Matcher m = p.matcher(text);
                while (m.find()) {
                    String key = m.group(1);
                    referenced.add(key);
                    if (!known.contains(key)) {
                        missing.putIfAbsent(key, root.relativize(java).toString());
                    }
                }
            }
        }
        // Guard against a vacuous pass: the scan must actually be finding the (well over a hundred) keyed
        // lookups, so a broken regex fails loudly instead of silently matching nothing.
        assertTrue(referenced.size() >= 100,
                () -> "the key scan found only " + referenced.size() + " lang lookups — the regex likely broke");
        assertTrue(missing.isEmpty(), () -> "production code references lang keys absent from "
                + "se/compile/resources/lang.yml (they would render as &c<key>? markers). Add them:\n  "
                + missing.entrySet().stream()
                        .map(e -> e.getKey() + "   (" + e.getValue() + ")")
                        .collect(Collectors.joining("\n  ")));
    }

    @Test
    void everyPackOverlayKeyIsAKnownCatalogueKey() throws IOException {
        Set<String> known = knownKeys();
        Path root = repoRoot();
        Map<String, String> unknown = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root.resolve("se/bootstrap/packs-src"))) {
            List<Path> packLangs = walk
                    .filter(p -> p.getFileName().toString().equals("lang.yml"))
                    .collect(Collectors.toList());
            for (Path langFile : packLangs) {
                for (String key : topLevelKeys(Files.readString(langFile))) {
                    if (!known.contains(key)) {
                        unknown.putIfAbsent(key, root.relativize(langFile).toString());
                    }
                }
            }
        }
        assertTrue(unknown.isEmpty(), () -> "a pack lang.yml overrides keys absent from the default catalogue "
                + "(a pack is an overlay, so these would never render):\n  "
                + unknown.entrySet().stream()
                        .map(e -> e.getKey() + "   (" + e.getValue() + ")")
                        .collect(Collectors.joining("\n  ")));
    }

    private static Set<String> knownKeys() {
        Lang defaults = Lang.defaults();
        Set<String> keys = new HashSet<>(defaults.singles().keySet());
        keys.addAll(defaults.lists().keySet());
        return keys;
    }

    /** Every production {@code .java} under {@code se/} — the flat {@code src}/{@code overlay} trees, no tests. */
    private static List<Path> productionSources(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root.resolve("se"))) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return !s.contains("/test/") && !s.contains("/build/") && !s.contains("/build-legacy/");
                    })
                    .collect(Collectors.toList());
        }
    }

    /** Top-level (column-0) dotted keys of a lang.yml overlay — the keys the file overrides. */
    private static List<String> topLevelKeys(String yaml) {
        List<String> keys = new ArrayList<>();
        Matcher m = Pattern.compile("^([a-z][a-zA-Z0-9._-]*):", Pattern.MULTILINE).matcher(yaml);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
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
