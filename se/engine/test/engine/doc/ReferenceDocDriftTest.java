package engine.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The §M drift guard (docs/v3-directives.md §M): the committed {@code docs/reference/dsl-reference.md} must
 * exactly equal {@link ReferenceDoc#render()}. This runs in {@code ./gradlew build}, so adding or changing an
 * effect/selector/trigger/operator/variable without regenerating the doc fails the build — the reference can
 * never silently drift from the code.
 *
 * <p>Regenerate after an intended change with
 * {@code ./gradlew :engine:test --tests "*ReferenceDocDriftTest" -Dse.doc.regen=true} (the root build forwards
 * {@code -Dse.*} into the test JVM); the regen branch writes the file and passes, the normal branch asserts.
 */
class ReferenceDocDriftTest {

    private static final String RELATIVE = "docs/reference/dsl-reference.md";

    @Test
    void committedReferenceMatchesTheCode() throws IOException {
        String fresh = ReferenceDoc.render();
        Path file = repoRoot().resolve(RELATIVE);

        if (Boolean.getBoolean("se.doc.regen")) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, fresh, StandardCharsets.UTF_8);
            System.out.println("[ReferenceDocDriftTest] regenerated " + file);
            return;
        }

        assertTrue(Files.isRegularFile(file),
                "missing " + RELATIVE + " — generate it with "
                        + "`./gradlew :engine:test --tests \"*ReferenceDocDriftTest\" -Dse.doc.regen=true`");
        String committed = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals(committed, fresh,
                "docs/reference/dsl-reference.md drifted from the code — regenerate with "
                        + "`./gradlew :engine:test --tests \"*ReferenceDocDriftTest\" -Dse.doc.regen=true`");
    }

    /** Walk up from the test working dir (the module dir on Gradle) to the repo root (holds settings.gradle.kts). */
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
