package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.boot.RegistryFingerprint;
import engine.effect.kind.BuiltinEffects;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import pack.PackManifest;

/**
 * The shipped signature pack's committed {@code pack.yml} (zipped verbatim by the {@code packSignaturePack} task)
 * must carry the builtin-only registry fingerprint (ADR-0046) — stamped in the repo and drift-guarded exactly
 * like {@code surface.json} / {@code dsl-reference.md}, so the shipped pack can never silently go stale. The
 * Zip task stays dumb; the PR diff shows any surface change. Builtin-only by design: addons cannot exist at
 * build time. Regenerate with {@code ./gradlew :bootstrap:regenDocs}.
 */
class SignaturePackFingerprintDriftTest {

    private static final Path PACK_YML = Path.of("packs-src/signature-pack/pack.yml");

    @Test
    void shippedPackIsStampedWithTheLiveFingerprint() throws IOException {
        assertTrue(Files.isRegularFile(PACK_YML),
                "signature-pack/pack.yml not found from " + Path.of("").toAbsolutePath());
        PackManifest committed = PackManifest.fromYaml(
                Files.readString(PACK_YML, StandardCharsets.UTF_8), "signature-pack");
        String live = RegistryFingerprint.hash(BuiltinEffects.registry());
        String summary = RegistryFingerprint.summary(BuiltinEffects.registry());

        if (Boolean.getBoolean("se.doc.regen")) {
            // Round-trips byte-stable: the committed file is already exactly toYaml() output plus the stamp.
            Files.writeString(PACK_YML, committed.stamped(live, summary).toYaml(), StandardCharsets.UTF_8);
            System.out.println("[SignaturePackFingerprintDriftTest] re-stamped " + PACK_YML);
            return;
        }

        assertEquals(live, committed.fingerprint(),
                "signature-pack/pack.yml fingerprint drifted — regenerate with `./gradlew :bootstrap:regenDocs`");
        assertEquals(summary, committed.surface(),
                "signature-pack/pack.yml surface drifted — regenerate with `./gradlew :bootstrap:regenDocs`");
    }
}
