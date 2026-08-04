package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.boot.RegistryFingerprint;
import engine.effect.kind.BuiltinEffects;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pack.PackManifest;

/**
 * Every shipped pack's committed {@code pack.yml} (zipped verbatim by its {@code pack<Name>} Zip task) must
 * carry the size of the tree it ships and the builtin-only registry fingerprint (ADR-0046) — stamped in the
 * repo and drift-guarded exactly like {@code surface.json} / {@code dsl-reference.md}, so a shipped pack can
 * never silently go stale (a grown tree still claiming its old file count, a surface the running server no
 * longer has). The Zip tasks stay dumb; the PR diff shows any surface change. Builtin-only by design: addons
 * cannot exist at build time. Regenerate with {@code ./gradlew :bootstrap:regenDocs}.
 */
class ShippedPackManifestDriftTest {

    @ParameterizedTest
    @ValueSource(strings = {"cosmic-pack", "signature-pack"})
    void shippedPackIsStampedWithTheLiveSurface(String pack) throws IOException {
        Path packYml = Path.of("packs-src", pack, PackManifest.ENTRY);
        assertTrue(Files.isRegularFile(packYml), packYml + " not found from " + Path.of("").toAbsolutePath());
        PackManifest committed = PackManifest.fromYaml(Files.readString(packYml, StandardCharsets.UTF_8), pack);
        int files = shippedFileCount(packYml);
        String live = RegistryFingerprint.hash(BuiltinEffects.registry());
        String summary = RegistryFingerprint.summary(BuiltinEffects.registry());

        if (Boolean.getBoolean("se.doc.regen")) {
            // Round-trips byte-stable: the committed file is already exactly toYaml() output plus the stamp.
            Files.writeString(packYml, committed.withFileCount(files).stamped(live, summary).toYaml(),
                    StandardCharsets.UTF_8);
            System.out.println("[ShippedPackManifestDriftTest] re-stamped " + packYml);
            return;
        }

        String regen = " drifted — regenerate with `./gradlew :bootstrap:regenDocs`";
        assertEquals(files, committed.fileCount(), () -> pack + "/pack.yml file count" + regen);
        assertEquals(live, committed.fingerprint(), () -> pack + "/pack.yml fingerprint" + regen);
        assertEquals(summary, committed.surface(), () -> pack + "/pack.yml surface" + regen);
    }

    /**
     * What {@code PackArchive} would stamp on an export of this tree: every file the Zip task ships except the
     * manifest itself. A plain walk is the whole surface because the validation tests pin each pack's top level
     * to {@code pack.yml} + the {@code PackSurface} roots. Dotfiles are skipped, as {@code PackSurface} does.
     */
    private static int shippedFileCount(Path packYml) throws IOException {
        try (Stream<Path> walk = Files.walk(packYml.getParent())) {
            return (int) walk.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(p -> !p.equals(packYml))
                    .count();
        }
    }
}
