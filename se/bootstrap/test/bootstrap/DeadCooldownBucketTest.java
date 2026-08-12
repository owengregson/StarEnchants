package bootstrap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import schema.diag.DiagCode;
import schema.diag.Diagnostic;

/**
 * No shipped content tree may author an ability that is INERT by construction — a {@code cooldown: 0} block
 * sharing a bucket a sibling arms, which gate 6 silences for that sibling's whole window
 * ({@code W_DEAD_COOLDOWN_BUCKET}). The warning is non-blocking, so the pack-validation tests would never see
 * it; this is the gate that makes it load-bearing for OUR content, which is the only content whose dead
 * blocks are our defect. It cost Demonic Gateway all three of its skull payloads once already.
 */
class DeadCooldownBucketTest {

    @ParameterizedTest
    @ValueSource(strings = {"resources/content",
                            "packs-src/cosmic-pack/content",
                            "packs-src/signature-pack/content"})
    void noShippedAbilityIsDeadInItsOwnCooldownBucket(String contentPath) {
        Path content = Path.of(contentPath);
        assertTrue(Files.isDirectory(content), () -> content + " not found from " + Path.of("").toAbsolutePath());

        Compiler compiler = ContentCompiler.production(testfx.PermissiveResolvers.INSTANCE);
        Library library = LibraryLoader.load(content, compiler, 0);

        String dead = library.diagnostics().stream()
                .filter(d -> d.is(DiagCode.W_DEAD_COOLDOWN_BUCKET))
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertTrue(dead.isEmpty(), () -> contentPath + " ships abilities that can never fire:\n  " + dead);
    }
}
