package engine.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Drift guard for the docs-site / web-creator catalog (ADR-0028): the committed
 * {@code website/src/data/catalog.json} must equal {@link ReferenceCatalogJson#render()}, so changing any
 * effect/selector/trigger/operator/variable without regenerating it fails {@code ./gradlew build}.
 * Regenerate with {@code -Dse.doc.regen=true}.
 */
class ReferenceCatalogDriftTest {

    private static final String RELATIVE = "website/src/data/catalog.json";

    @Test
    void committedCatalogMatchesTheCode() throws IOException {
        String fresh = ReferenceCatalogJson.render();
        Path file = repoRoot().resolve(RELATIVE);

        if (Boolean.getBoolean("se.doc.regen")) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, fresh, StandardCharsets.UTF_8);
            System.out.println("[ReferenceCatalogDriftTest] regenerated " + file);
            return;
        }

        assertTrue(Files.isRegularFile(file),
                "missing " + RELATIVE + " — generate it with "
                        + "`./gradlew :engine:test --tests \"*ReferenceCatalogDriftTest\" -Dse.doc.regen=true`");
        String committed = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals(committed, fresh,
                "website/src/data/catalog.json drifted from the code — regenerate with "
                        + "`./gradlew :engine:test --tests \"*ReferenceCatalogDriftTest\" -Dse.doc.regen=true`");
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (no settings.gradle.kts above "
                + Path.of("").toAbsolutePath() + ")");
    }
}
