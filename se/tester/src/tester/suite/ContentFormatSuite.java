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
 * Live checks for content format v2 (ADR-0016) — the things a unit test cannot prove because they
 * depend on the REAL effect-spec registry + selector vocabulary + handle resolvers (the unit tests use
 * a tiny hand-built registry). A small v2 tree is compiled through the production {@link ContentCompiler}
 * on this server, exercising verbose effects against the real {@code ParamSpec}s, level scaling, tier
 * subfolders with key stability, and a carrier {@code ItemDef}.
 *
 * <ul>
 *   <li>{@code content.v2.verbose} — a verbose {@code IGNITE: { duration }} / {@code MESSAGE: { text }}
 *       enchant compiles clean against the real specs, and a colon-bearing message text survives.</li>
 *   <li>{@code content.v2.scale} — {@code scale:} + {@code $token} expands one effect line over 3 levels.</li>
 *   <li>{@code content.v2.tier} — a file under {@code enchants/mythic/} keeps the tier OFF the stable key.</li>
 *   <li>{@code content.v2.item} — an {@code items/book/} carrier loads as a zero-ability ItemDef.</li>
 * </ul>
 */
public final class ContentFormatSuite implements Harness.Scenario {

    private static final String STORMCALLER = """
            display: "&dStormcaller"
            trigger: ATTACK
            applies-to: [SWORD, AXE]
            max-level: 3
            scale:
              burn: { 1: 20, 2: 40, 3: 60 }
            effects:
              - { IGNITE: { duration: $burn, who: "@Victim" } }
              - { MESSAGE: { text: "Zap: the storm strikes!" } }
            levels:
              3:
                effects+:
                  - { HEAL: { amount: 4, who: "@Self" } }
            """;
    private static final String STORM_BOOK = """
            display: "&dStorm Book"
            kind: book
            grants: { enchant: enchants/stormcaller, level: 3 }
            apply: { success-chance: 80 }
            """;

    private final Plugin plugin;

    public ContentFormatSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("content.v2.verbose");
        h.expect("content.v2.scale");
        h.expect("content.v2.tier");
        h.expect("content.v2.item");

        Path root;
        try {
            root = Files.createTempDirectory("se-content-v2-suite");
            write(root, "enchants/mythic/stormcaller.yml", STORMCALLER);
            write(root, "items/book/storm-book.yml", STORM_BOOK);
        } catch (IOException e) {
            for (String key : new String[] {"content.v2.verbose", "content.v2.scale", "content.v2.tier", "content.v2.item"}) {
                h.fail(key, e.toString());
            }
            return;
        }

        // accept() runs on the global thread, so the load (which resolves handles + selectors against the
        // live registries) is safe here.
        Compiler compiler = ContentCompiler.production();
        Library lib = LibraryLoader.load(root, compiler, 0);

        h.guard("content.v2.verbose", () -> {
            if (lib.hasErrors()) {
                throw new IllegalStateException("v2 load reported errors: " + lib.diagnostics());
            }
            var level1 = lib.snapshot().byStableKey("enchants/stormcaller/1");
            if (level1 == null) {
                throw new IllegalStateException("enchants/stormcaller/1 missing (verbose effect failed to compile?)");
            }
            // The MESSAGE is the second effect; its colon-bearing text must have survived as one arg.
            String text = level1.effects()[1].args().str("text");
            if (!"Zap: the storm strikes!".equals(text)) {
                throw new IllegalStateException("verbose MESSAGE text was shredded: '" + text + "'");
            }
        });

        h.guard("content.v2.scale", () -> {
            // $burn expands over 3 levels from ONE effect line; all three abilities must exist.
            for (int level = 1; level <= 3; level++) {
                if (lib.snapshot().byStableKey("enchants/stormcaller/" + level) == null) {
                    throw new IllegalStateException("scaled level " + level + " missing");
                }
            }
            // Level 3 gained the appended HEAL (effects+): IGNITE, MESSAGE, HEAL.
            int l3 = lib.snapshot().byStableKey("enchants/stormcaller/3").effects().length;
            if (l3 != 3) {
                throw new IllegalStateException("level 3 effects+ append did not land; effect count=" + l3);
            }
        });

        h.guard("content.v2.tier", () -> {
            // The tier folder is NOT part of the key (live gear keeps resolving); the tier is metadata.
            if (lib.snapshot().byStableKey("enchants/mythic/stormcaller/1") != null) {
                throw new IllegalStateException("tier folder leaked into the stable key");
            }
            if (!"mythic".equals(lib.tierOf("enchants/stormcaller"))) {
                throw new IllegalStateException("folder-derived tier wrong: " + lib.tierOf("enchants/stormcaller"));
            }
        });

        h.guard("content.v2.item", () -> {
            if (lib.items().size() != 1) {
                throw new IllegalStateException("expected one carrier ItemDef, got " + lib.items().size());
            }
            if (!"book".equals(lib.items().get(0).kind())
                    || !"enchants/stormcaller".equals(lib.items().get(0).grant().enchant())) {
                throw new IllegalStateException("carrier ItemDef parsed wrong: " + lib.items().get(0));
            }
        });
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
