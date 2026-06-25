package tester.suite;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.plugin.Plugin;
import tester.harness.Harness;

/**
 * Live checks for the content format, compiled through the production {@link ContentCompiler} against
 * the real spec registry + resolvers (unit tests use a hand-built registry): verbose effects, the
 * explicit per-level shape, and tier subfolders kept off the stable key.
 */
public final class ContentFormatSuite implements Harness.Scenario {

    private static final String STORMCALLER = """
            display: "&dStormcaller"
            trigger: ATTACK
            applies-to: [SWORD, AXE]
            levels:
              1:
                effects:
                  - { IGNITE: { duration: 20, who: "@Victim" } }
                  - { MESSAGE: { text: "Zap: the storm strikes!" } }
              2:
                effects:
                  - { IGNITE: { duration: 40, who: "@Victim" } }
                  - { MESSAGE: { text: "Zap: the storm strikes!" } }
              3:
                effects:
                  - { IGNITE: { duration: 60, who: "@Victim" } }
                  - { MESSAGE: { text: "Zap: the storm strikes!" } }
                  - { MODIFY_HEALTH: { amount: 4, who: "@Self" } }
            """;
    private final Plugin plugin;

    public ContentFormatSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("content.format.verbose");
        h.expect("content.format.levels");
        h.expect("content.format.tier");

        Path root;
        try {
            root = Files.createTempDirectory("se-content-format-suite");
            write(root, "enchants/mythic/stormcaller.yml", STORMCALLER);
        } catch (IOException e) {
            for (String key : new String[] {
                    "content.format.verbose", "content.format.levels", "content.format.tier"}) {
                h.fail(key, e.toString());
            }
            return;
        }

        // accept() runs on the global thread, so resolving handles/selectors against the live registries is safe.
        Compiler compiler = ContentCompiler.production();
        Library lib = LibraryLoader.load(root, compiler, 0);

        h.guard("content.format.verbose", () -> {
            if (lib.hasErrors()) {
                throw new IllegalStateException("content load reported errors: " + lib.diagnostics());
            }
            var level1 = lib.snapshot().byStableKey("enchants/stormcaller/1");
            if (level1 == null) {
                throw new IllegalStateException("enchants/stormcaller/1 missing (verbose effect failed to compile?)");
            }
            // Colon-bearing MESSAGE text (2nd effect) must survive as one arg, not be split on the colon.
            String text = level1.effects()[1].args().str("text");
            if (!"Zap: the storm strikes!".equals(text)) {
                throw new IllegalStateException("verbose MESSAGE text was shredded: '" + text + "'");
            }
        });

        h.guard("content.format.levels", () -> {
            for (int level = 1; level <= 3; level++) {
                if (lib.snapshot().byStableKey("enchants/stormcaller/" + level) == null) {
                    throw new IllegalStateException("level " + level + " missing");
                }
            }
            // Level 3 lists three effects (IGNITE, MESSAGE, MODIFY_HEALTH) — the others two.
            int l3 = lib.snapshot().byStableKey("enchants/stormcaller/3").effects().length;
            if (l3 != 3) {
                throw new IllegalStateException("level 3 effect count wrong; got " + l3);
            }
        });

        h.guard("content.format.tier", () -> {
            // Tier folder must stay out of the key (so live gear keeps resolving); the tier is metadata.
            if (lib.snapshot().byStableKey("enchants/mythic/stormcaller/1") != null) {
                throw new IllegalStateException("tier folder leaked into the stable key");
            }
            if (!"mythic".equals(lib.tierOf("enchants/stormcaller"))) {
                throw new IllegalStateException("folder-derived tier wrong: " + lib.tierOf("enchants/stormcaller"));
            }
        });
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
