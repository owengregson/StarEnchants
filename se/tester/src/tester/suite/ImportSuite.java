package tester.suite;

import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import feature.imports.ImportCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.Plugin;
import platform.content.ContentReloader;
import platform.content.ReloadResult;
import tester.harness.Harness;

/**
 * Live check for {@code /se import} (ADR-0029) on the real server's bundled SnakeYAML + production compiler,
 * on Paper AND Folia: an {@code SE1} code decodes, validates clean through {@link ContentReloader#validateCandidate},
 * writes {@code content/enchants/<key>.yml}, and the transactional reload makes the enchant active. A malformed
 * code is rejected by the decoder before any disk/reload step, so nothing changes.
 */
public final class ImportSuite implements Harness.Scenario {

    private final Plugin plugin;

    public ImportSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    /** A self-built SE1 code (the encoder is the codec's own inverse) — exercises the full decode pipeline. */
    private static String knownCode() {
        Map<String, Object> potion = new LinkedHashMap<>();
        potion.put("effect", "STRENGTH");
        potion.put("level", 2);
        potion.put("duration", 60);
        Map<String, Object> level = new LinkedHashMap<>();
        level.put("chance", 100);
        level.put("effects", List.of(Map.of("POTION", potion)));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("display", "Imported Spark");
        content.put("trigger", "ATTACK");
        content.put("levels", Map.of(1, level));
        return ImportCode.encode(new ImportCode.Envelope(1, "enchant", "imported-spark", content));
    }

    @Override
    public void accept(Harness h) {
        h.expect("import.applies");        // a valid code → file written + enchant live after reload
        h.expect("import.rejectsGarbage"); // a malformed code → decode fails, nothing touched

        h.guard("import.rejectsGarbage", () -> {
            try {
                ImportCode.decode("SE1:this-is-not-a-real-code");
                throw new IllegalStateException("a garbage code decoded instead of being rejected");
            } catch (ImportCode.DecodeException expected) {
                // correct: a malformed code never reaches validate/write/reload
            }
        });

        Path root;
        ContentReloader reloader;
        ContentHolder holder;
        try {
            root = Files.createTempDirectory("se-import-suite");
            Library initial = LibraryLoader.load(root, ContentCompiler.production(), 0);
            holder = new ContentHolder(initial);
            reloader = new ContentReloader(holder, ContentCompiler::production, root, 0);
        } catch (IOException e) {
            h.fail("import.applies", e.toString());
            return;
        }

        ImportCode.Envelope envelope = ImportCode.decode(knownCode());
        String key = envelope.key();
        String yaml = ImportCode.toYaml(envelope.content());
        String relative = "enchants/" + key + ".yml";

        // accept() runs on the global thread, so resolving STRENGTH against the live registries is safe.
        ReloadResult validation = reloader.validateCandidate(relative, yaml);
        if (validation.errorCount() != 0) {
            h.fail("import.applies", "valid import did not validate clean: " + validation.diagnostics());
            return;
        }
        try {
            Path target = root.resolve(relative);
            Files.createDirectories(target.getParent());
            Files.writeString(target, yaml, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            h.fail("import.applies", e.toString());
            return;
        }
        reloader.reload(result -> h.guard("import.applies", () -> {
            if (!result.published()) {
                throw new IllegalStateException("import reload did not publish: " + result.diagnostics());
            }
            if (holder.snapshot().byStableKey("enchants/" + key + "/1") == null) {
                throw new IllegalStateException("enchants/" + key + "/1 missing after import reload");
            }
        }));
    }
}
