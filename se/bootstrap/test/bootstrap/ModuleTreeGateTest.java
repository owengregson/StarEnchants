package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The feature-module tree gate (ADR-0047 §8.1): lexical, comment/string-stripped source scans that make
 * off-registry wiring a build failure — the structural half of the fold's locks (the semantic golden orders
 * live in {@code RegistryWiringTest} / a real boot).
 *
 * <ul>
 *   <li><b>G2-a</b> — registration monopoly: {@code registerEvents} appears ONLY in {@code StarEnchantsPlugin}
 *       (the one {@code PluginRegistrar} edge), exactly once; {@code getCommandMap} appears nowhere in
 *       bootstrap src (the command map is an era seam).</li>
 *   <li><b>G2-b</b> — module membership: every {@code wire/*Module.java} is constructed AND folded in
 *       {@code Modules} (an orphan module file fails: wire it into the registry or delete it).</li>
 *   <li><b>G2-c</b> — static-installer ratchet: the set of {@code private static volatile} fields across the
 *       shipped {@code se/*}/{@code src} trees equals the frozen allowlist, so a NEW boot-time installer fails
 *       the build un-adjudicated (the two dissolved statics stay dissolved).</li>
 *   <li><b>G2-d</b> — disable monopoly: {@code StarEnchantsPlugin} names no {@code clearAll(} / {@code disarmAll(}
 *       / {@code engine.sink.} token (onDisable is the fold's) and {@code onDisable} calls {@code fold.stop}.</li>
 *   <li><b>G2-e</b> — mint monopoly: {@code MintCatalog.Entry} construction lives only under {@code bootstrap/wire}
 *       (the module {@code Mints}) or {@code MintCatalog} itself, and the old {@code GIVE_TYPES} hand-list is gone —
 *       the module-declared mintables are the ONE source the three mint surfaces derive from (ADR-0047 §7).</li>
 * </ul>
 */
class ModuleTreeGateTest {

    // ── G2-a registration monopoly ──────────────────────────────────────────────────────────────

    @Test
    void g2aRegisterEventsOnlyInThePluginExactlyOnce() {
        int total = 0;
        for (Path source : bootstrapSrc()) {
            String code = stripCommentsAndStrings(read(source));
            int count = countOccurrences(code, "registerEvents");
            if (count > 0 && !source.getFileName().toString().equals("StarEnchantsPlugin.java")) {
                fail("module-gate: " + rel(source) + " calls registerEvents — every listener registration is a "
                        + "FeatureModule wire folded by ModuleFold; only StarEnchantsPlugin's PluginRegistrar may "
                        + "call registerEvents (ADR-0047 G2-a).");
            }
            total += source.getFileName().toString().equals("StarEnchantsPlugin.java") ? count : 0;
        }
        assertEquals(1, total, "module-gate: StarEnchantsPlugin must call registerEvents EXACTLY once (the one "
                + "PluginRegistrar edge) — found " + total + " (ADR-0047 G2-a).");
    }

    @Test
    void g2aCommandMapIsAnEraSeamNotNamedInBootstrap() {
        for (Path source : bootstrapSrc()) {
            String code = stripCommentsAndStrings(read(source));
            if (code.contains("getCommandMap")) {
                fail("module-gate: " + rel(source) + " names getCommandMap — the command map is an era seam "
                        + "(bindings.registerCommand); bootstrap src must not reach it directly (ADR-0047 G2-a).");
            }
        }
    }

    // ── G2-b module membership ──────────────────────────────────────────────────────────────────

    @Test
    void g2bEveryModuleFileIsConstructedAndFolded() {
        List<String> moduleClasses = new ArrayList<>();
        for (Path source : wireDir()) {
            String name = source.getFileName().toString();
            if (name.endsWith("Module.java") && !name.equals("FeatureModule.java")) {
                moduleClasses.add(name.substring(0, name.length() - ".java".length()));
            }
        }
        assertTrue(moduleClasses.size() >= 19,
                "module-gate: expected ≥19 *Module files, found " + moduleClasses.size());

        String modules = stripCommentsAndStrings(read(wireFile("Modules.java")));
        for (String cls : moduleClasses) {
            if (!Pattern.compile("\\bnew\\s+" + Pattern.quote(cls) + "\\s*\\(").matcher(modules).find()) {
                fail("module-gate: " + cls + " is not constructed in Modules — wire it into the registry or delete "
                        + "it (ADR-0047 G2-b).");
            }
        }
        // Every constructed module is folded: the registry List.of has one .module() per module file.
        int folded = countOccurrences(modules, ".module()");
        assertEquals(moduleClasses.size(), folded,
                "module-gate: the Modules registry folds " + folded + " modules but there are " + moduleClasses.size()
                        + " *Module files — every module must appear in the registry List.of (ADR-0047 G2-b).");
    }

    // ── G2-c static-installer ratchet ───────────────────────────────────────────────────────────

    /**
     * The frozen set of {@code private static volatile} fields (ADR-0047 §4): the ratcheted boot-time
     * installers plus the three pre-existing non-installer statics. The two dissolved installers
     * (DispatchSinkBase.movementExemption → SinkEnv, ItemFactory.enchantResolver → VanillaEnchants) are ABSENT.
     * Colors#hexMode is adjudicated (ADR-0062): the era-selected {#RRGGBB} translation mode, installed once in
     * the BootCore ctor — the ItemFactory#itemWrapWidth idiom, a render-prep knob with no per-event state.
     * SpawnInvulnerability#resolved is adjudicated (ADR-0071 amendments): a per-JVM reflective MEMO of the
     * 1.16.5–1.20.6 companion-timer field — no boot-time install, no teardown, derived purely from the handle
     * class; volatile only for safe cross-thread publication of the resolved Field.
     * FactPopulator#enchantLevelSource is adjudicated: the {@code %scope.enchlevel.<key>%} worn-level hook, the
     * FactPopulator#entityTypeResolver idiom exactly — the populator is constructed inside the dispatchers, so
     * the composition root (the only layer where the engine reader and the item store meet) has no instance to
     * wire; a stateless read-only lookup with no teardown.
     */
    private static final Set<String> FROZEN_STATICS = Set.of(
            "CombatDispatch#friendlyFire",
            "FactPopulator#enchantLevelSource",
            "FactPopulator#entityTypeResolver",
            "ItemFactory#customItemResolver",
            "ItemFactory#itemWrapWidth",
            "Colors#hexMode",
            "Allies#hook",
            "Regions#folia",
            "Scheduling#backend",
            "SpawnInvulnerability#resolved");

    private static final Pattern STATIC_VOLATILE =
            Pattern.compile("private\\s+static\\s+volatile\\s+[\\w.<>,\\[\\]\\s]+?\\s+(\\w+)\\s*[=;]");

    @Test
    void g2cStaticInstallerSetIsFrozen() {
        Set<String> found = new TreeSet<>();
        for (Path source : shippedSrc()) {
            String cls = source.getFileName().toString().replace(".java", "");
            Matcher m = STATIC_VOLATILE.matcher(stripCommentsAndStrings(read(source)));
            while (m.find()) {
                found.add(cls + "#" + m.group(1));
            }
        }
        assertEquals(new TreeSet<>(FROZEN_STATICS), found,
                "module-gate: the mutable static-installer set drifted from the frozen allowlist — a new boot-time "
                        + "installer must be adjudicated (make it instance wiring like SinkEnv/VanillaEnchants, or "
                        + "extend this allowlist with rationale) (ADR-0047 G2-c). Found: " + found);
    }

    // ── G2-d disable monopoly ───────────────────────────────────────────────────────────────────

    @Test
    void g2dDisableIsTheFolds() {
        String plugin = stripCommentsAndStrings(read(bootstrapFile("StarEnchantsPlugin.java")));
        for (String forbidden : List.of("clearAll(", "disarmAll(", "engine.sink.")) {
            if (plugin.contains(forbidden)) {
                fail("module-gate: StarEnchantsPlugin names '" + forbidden + "' — onDisable is the fold's stop(), so "
                        + "driver/sink-static teardown is a module Stop declaration, not inline here (ADR-0047 G2-d).");
            }
        }
        assertTrue(plugin.contains("fold.stop"),
                "module-gate: onDisable must call fold.stop() (ADR-0047 G2-d).");
    }

    // ── G2-e mint monopoly ──────────────────────────────────────────────────────────────────────

    @Test
    void g2eMintEntryConstructionIsConfinedToTheModuleMints() {
        for (Path source : shippedSrc()) {
            String code = stripCommentsAndStrings(read(source));
            if (!code.contains("MintCatalog.Entry(")) {
                continue;
            }
            String unix = source.toString().replace('\\', '/');
            boolean allowed = unix.contains("/bootstrap/wire/")
                    || source.getFileName().toString().equals("MintCatalog.java");
            if (!allowed) {
                fail("module-gate: " + rel(source) + " constructs a MintCatalog.Entry — mint tiles derive from the "
                        + "module-declared Mintables (bootstrap/wire Mints), the ONE mint source; nothing else may "
                        + "hand-build a mint entry (ADR-0047 G2-e).");
            }
        }
    }

    @Test
    void g2eGiveTypesHandListIsGone() {
        for (Path source : bootstrapSrc()) {
            String code = stripCommentsAndStrings(read(source));
            if (code.contains("GIVE_TYPES")) {
                fail("module-gate: " + rel(source) + " names GIVE_TYPES — the /se give type list derives from the "
                        + "module mintables now (types then aliases); the hand-list must not return (ADR-0047 G2-e).");
            }
        }
    }

    // ── helpers (the EraTreeGate source-scan pattern) ───────────────────────────────────────────

    private static List<Path> bootstrapSrc() {
        return srcUnder(repoRoot().resolve("se/bootstrap/src"), u -> true);
    }

    private static List<Path> wireDir() {
        return srcUnder(repoRoot().resolve("se/bootstrap/src/bootstrap/wire"), u -> true);
    }

    private static final Set<String> NON_DUAL_COMPILED =
            Set.of("/se/tester/", "/se/testfx/", "/se/imagegen/", "/se/bench/", "/se/integrate/");

    /** Every SHARED (shipped) {@code se/*}/{@code src} Java source (never a test/overlay/tool tree). */
    private static List<Path> shippedSrc() {
        return srcUnder(repoRoot().resolve("se"), u ->
                u.contains("/src/") && !u.contains("/overlay/") && !u.contains("/test/")
                        && NON_DUAL_COMPILED.stream().noneMatch(u::contains));
    }

    private static List<Path> srcUnder(Path root, java.util.function.Predicate<String> keep) {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> keep.test(p.toString().replace('\\', '/')))
                    .forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private static Path wireFile(String name) {
        return repoRoot().resolve("se/bootstrap/src/bootstrap/wire").resolve(name);
    }

    private static Path bootstrapFile(String name) {
        return repoRoot().resolve("se/bootstrap/src/bootstrap").resolve(name);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
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
