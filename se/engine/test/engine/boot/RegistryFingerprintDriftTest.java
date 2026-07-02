package engine.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.effect.kind.BuiltinEffects;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;

/**
 * The §M stability golden (ADR-0046): the committed {@code docs/reference/authoring-surface.txt} must equal
 * {@link RegistryFingerprint#canonical(engine.effect.EffectRegistry)}, so any surface change is a reviewed
 * one-block diff in the PR. Recomputing {@code "1:" + sha256(committedBytes)} independently and asserting it
 * equals {@link RegistryFingerprint#hash} locks hash = f(canonical) and gives the cross-JVM stability
 * guarantee by construction (the only JVM-dependent input, iteration order, is sorted away).
 * Regenerate with {@code -Dse.doc.regen=true} (the root build forwards {@code -Dse.*} into the test JVM).
 */
class RegistryFingerprintDriftTest {

    private static final String RELATIVE = "docs/reference/authoring-surface.txt";

    private static final String REGEN =
            "`./gradlew :engine:test --tests \"*RegistryFingerprintDriftTest\" -Dse.doc.regen=true`";

    @Test
    void committedSurfaceMatchesTheCode() throws IOException {
        String fresh = RegistryFingerprint.canonical(BuiltinEffects.registry());
        Path file = repoRoot().resolve(RELATIVE);

        if (Boolean.getBoolean("se.doc.regen")) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, fresh, StandardCharsets.UTF_8);
            System.out.println("[RegistryFingerprintDriftTest] regenerated " + file);
            return;
        }

        assertTrue(Files.isRegularFile(file), "missing " + RELATIVE + " — generate it with " + REGEN);
        String committed = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals(committed, fresh,
                RELATIVE + " drifted from the authoring surface — regenerate with " + REGEN);
        assertEquals(RegistryFingerprint.VERSION + ":" + sha256Hex(committed),
                RegistryFingerprint.hash(BuiltinEffects.registry()),
                "the committed surface's hash must equal RegistryFingerprint.hash (hash = f(canonical))");
    }

    /** Walk up from the test working dir to the repo root (holds settings.gradle.kts). */
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

    private static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is guaranteed present on every JVM", impossible);
        }
    }
}
