package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import api.spi.AddonAffinity;
import api.spi.AddonEffect;
import api.spi.AddonEffectCtx;
import api.spi.AddonSink;
import api.spi.AddonSpec;
import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.model.Ability;
import compile.model.Affinity;
import compile.resolve.PlatformResolvers;
import engine.boot.ContentCompiler;
import engine.effect.EffectRegistry;
import engine.effect.kind.BuiltinEffects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import schema.diag.Diagnostic;
import schema.spec.D;

/**
 * The end-to-end registration contract (ADR-0038): once an {@link AddonEffect} is folded into the effect
 * registry, its head is authorable in content YAML and compiles clean through the REAL compiler, and the
 * lowered ability carries the add-on's declared {@link AddonAffinity}. Mirrors {@code CatalogValidationTest}'s
 * permissive-resolver pattern (structural validation, no server).
 */
class AddonCompileTest {

    /** Accepts every handle token (id 0) — structural validation only, no server. */
    private static final PlatformResolvers PERMISSIVE = new PlatformResolvers() {
        @Override public OptionalInt material(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt sound(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt potionEffect(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt particle(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt enchantment(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt entityType(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt attribute(String token) { return OptionalInt.of(0); }
    };

    private static final String HEAD = "ADDON_TEST_BOLT";

    @Test
    void addonHeadCompilesAndLowersWithItsAffinity(@TempDir Path root) throws Exception {
        Path enchants = root.resolve("enchants");
        Files.createDirectories(enchants);
        Files.writeString(enchants.resolve("addon-test.yml"), """
                tier: common
                display: "&aAddon Test"
                description: "an add-on effect head, authored like any built-in"
                trigger: ATTACK
                applies-to: [WEAPON]
                levels:
                  1:
                    chance: 100
                    effects:
                      - { %s: { power: 5 } }
                """.formatted(HEAD));

        Compiler compiler = ContentCompiler.production(PERMISSIVE, registryWithAddon());
        Library library = LibraryLoader.load(root, compiler, 0);

        String blocking = library.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(library.hasErrors(), () -> "add-on content had blocking diagnostics:\n  " + blocking);

        assertEquals(1, library.snapshot().abilityCount());
        Ability ability = library.snapshot().abilities()[0];
        assertEquals(Affinity.GLOBAL, ability.affinity(),
                "the ability must fold to the add-on effect's declared GLOBAL affinity");
    }

    private static EffectRegistry registryWithAddon() {
        EffectRegistry.Builder b = EffectRegistry.builder();
        BuiltinEffects.registry().kinds().forEach(b::register);
        b.register(new AddonBridge(new SampleBolt()));
        return b.build();
    }

    /** A minimal add-on effect declaring a distinctive GLOBAL affinity so the fold is observable. */
    private static final class SampleBolt implements AddonEffect {
        @Override public AddonSpec spec() {
            return AddonSpec.of(HEAD)
                    .param("power", D.DOUBLE.min(0))
                    .affinity(AddonAffinity.GLOBAL)
                    .build();
        }

        @Override public void run(AddonEffectCtx ctx, AddonSink sink) {
            sink.lightningAndDamage(ctx.victim(), ctx.dbl("power"));
        }
    }
}
