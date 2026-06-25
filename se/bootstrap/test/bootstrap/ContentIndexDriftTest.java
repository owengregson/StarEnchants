package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Asserts {@code content/index.txt} lists exactly the on-disk {@code *.yml}. A file the manifest omits
 * ships in the jar but is never extracted or compiled — the bug that once left 5 enchants unreachable.
 * Regenerate with {@code ./gradlew :bootstrap:test --tests "*ContentIndexDriftTest" -Dse.index.regen=true}.
 */
class ContentIndexDriftTest {

    private static final Path CONTENT = Path.of("resources/content");
    private static final Path INDEX = CONTENT.resolve("index.txt");

    @Test
    void manifestListsExactlyTheContentFilesOnDisk() throws IOException {
        assertTrue(Files.isDirectory(CONTENT),
                "content dir not found from " + Path.of("").toAbsolutePath());

        List<String> onDisk;
        try (Stream<Path> walk = Files.walk(CONTENT)) {
            onDisk = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .map(p -> CONTENT.relativize(p).toString().replace('\\', '/'))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }

        if (Boolean.getBoolean("se.index.regen")) {
            Files.writeString(INDEX, String.join("\n", onDisk) + "\n", StandardCharsets.UTF_8);
            System.out.println("[ContentIndexDriftTest] regenerated " + INDEX + " (" + onDisk.size() + " entries)");
            return;
        }

        assertTrue(Files.isRegularFile(INDEX),
                "missing " + INDEX + " — generate it with "
                        + "`./gradlew :bootstrap:test --tests \"*ContentIndexDriftTest\" -Dse.index.regen=true`");
        List<String> listed = Files.readAllLines(INDEX, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .sorted(Comparator.naturalOrder())
                .toList();

        assertEquals(onDisk, listed,
                "content/index.txt drifted from the files on disk — regenerate with "
                        + "`./gradlew :bootstrap:test --tests \"*ContentIndexDriftTest\" -Dse.index.regen=true`");
    }
}
