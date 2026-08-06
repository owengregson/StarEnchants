package engine.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.Affinity;
import engine.condition.BuiltinVars;
import engine.condition.VarVocabulary;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.effect.EffectRegistry;
import engine.effect.kind.BuiltinEffects;
import engine.selector.SelectorKind;
import engine.selector.SelectorRegistry;
import engine.selector.kind.BuiltinSelectors;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import engine.trigger.BuiltinTriggers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import schema.spec.D;
import schema.spec.Param;

/**
 * Registry-driven sensitivity gate for {@link RegistryFingerprint} — mirrors {@code SpecConformanceTest}'s
 * walk (dynamic tests derived from the live registry, never hand-listed): every kind/param-level change moves
 * the hash, while excluded dimensions (doc/example/affinity/target slots, enum-value & registration order) do
 * not. So a surface change can never silently keep a stale fingerprint.
 */
class RegistryFingerprintTest {

    // ── Effect-level sensitivity ──

    @TestFactory
    Stream<DynamicTest> everyBuiltinKindMovesTheHash() {
        EffectRegistry full = BuiltinEffects.registry();
        String fullHash = RegistryFingerprint.hash(full);
        List<EffectKind> all = new ArrayList<>(full.kinds());
        return all.stream().map(omit -> DynamicTest.dynamicTest("without " + omit.head(), () -> {
            EffectRegistry.Builder reduced = EffectRegistry.builder();
            for (EffectKind kind : all) {
                if (kind != omit) {
                    reduced.register(kind);
                }
            }
            assertNotEquals(fullHash, RegistryFingerprint.hash(reduced.build()),
                    "dropping " + omit.head() + " must change the surface hash");
        }));
    }

    @Test
    void addingASyntheticKindMovesTheHash() {
        EffectRegistry.Builder builder = EffectRegistry.builder();
        BuiltinEffects.registry().kinds().forEach(builder::register);
        builder.register(fake(EffectSpec.of("ZZ_FAKE").param("x", D.DOUBLE.min(0)).build()));
        assertNotEquals(RegistryFingerprint.hash(BuiltinEffects.registry()),
                RegistryFingerprint.hash(builder.build()));
    }

    // ── Per-param-dimension sensitivity ──

    /** One fake kind, varying exactly ONE param dimension per case; each variant's declared param list. */
    private static Map<String, EffectSpec> paramVariants() {
        Map<String, EffectSpec> variants = new LinkedHashMap<>();
        variants.put("addParam", base()
                .param("b", D.DOUBLE).build());
        variants.put("renameParam", EffectSpec.of("ZZ")
                .param("z", D.INT.min(0).max(10).def(2))
                .param("r", D.INT.min(0))
                .param("mat", D.material())
                .param("mode", D.enumOf("ON", "OFF")).build());
        variants.put("changeKind", EffectSpec.of("ZZ")
                .param("a", D.DOUBLE.min(0).max(10).def(2))
                .param("r", D.INT.min(0))
                .param("mat", D.material())
                .param("mode", D.enumOf("ON", "OFF")).build());
        variants.put("requiredToDefault", EffectSpec.of("ZZ")
                .param("a", D.INT.min(0).max(10).def(2))
                .param("r", D.INT.min(0).def(0))
                .param("mat", D.material())
                .param("mode", D.enumOf("ON", "OFF")).build());
        variants.put("changeDefault", EffectSpec.of("ZZ")
                .param("a", D.INT.min(0).max(10).def(5))
                .param("r", D.INT.min(0))
                .param("mat", D.material())
                .param("mode", D.enumOf("ON", "OFF")).build());
        variants.put("changeMin", EffectSpec.of("ZZ")
                .param("a", D.INT.min(1).max(10).def(2))
                .param("r", D.INT.min(0))
                .param("mat", D.material())
                .param("mode", D.enumOf("ON", "OFF")).build());
        variants.put("changeMax", EffectSpec.of("ZZ")
                .param("a", D.INT.min(0).max(20).def(2))
                .param("r", D.INT.min(0))
                .param("mat", D.material())
                .param("mode", D.enumOf("ON", "OFF")).build());
        variants.put("addEnumValue", EffectSpec.of("ZZ")
                .param("a", D.INT.min(0).max(10).def(2))
                .param("r", D.INT.min(0))
                .param("mat", D.material())
                .param("mode", D.enumOf("ON", "OFF", "AUTO")).build());
        variants.put("changeHandle", EffectSpec.of("ZZ")
                .param("a", D.INT.min(0).max(10).def(2))
                .param("r", D.INT.min(0))
                .param("mat", D.sound())
                .param("mode", D.enumOf("ON", "OFF")).build());
        return variants;
    }

    /** The shared base spec the variants perturb one dimension of. */
    private static EffectSpec.Builder base() {
        return EffectSpec.of("ZZ")
                .param("a", D.INT.min(0).max(10).def(2))
                .param("r", D.INT.min(0))
                .param("mat", D.material())
                .param("mode", D.enumOf("ON", "OFF"));
    }

    @TestFactory
    Stream<DynamicTest> everyParamDimensionMovesTheHash() {
        String baseHash = RegistryFingerprint.hash(single(base().build()));
        return paramVariants().entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey(),
                () -> assertNotEquals(baseHash, RegistryFingerprint.hash(single(e.getValue())),
                        e.getKey() + " must move the hash")));
    }

    @Test
    void everyParamDimensionIsPairwiseDistinct() {
        Set<String> hashes = new HashSet<>();
        hashes.add(RegistryFingerprint.hash(single(base().build())));
        Map<String, EffectSpec> variants = paramVariants();
        for (EffectSpec spec : variants.values()) {
            hashes.add(RegistryFingerprint.hash(single(spec)));
        }
        assertEquals(variants.size() + 1, hashes.size(), "base + every variant must be pairwise distinct");
    }

    // ── Exclusions ──

    @Test
    void docExampleAffinityAndTargetsAreExcluded() {
        String plain = RegistryFingerprint.hash(single(
                EffectSpec.of("ZZ").param("a", D.DOUBLE.min(0)).build()));
        String documented = RegistryFingerprint.hash(single(EffectSpec.of("ZZ")
                .param("a", D.DOUBLE.min(0), "the a param")
                .doc("a documented kind").example("ZZ: { a: 1 }").build()));
        assertEquals(plain, documented, "doc/example/per-param docs are prose, not surface");

        String routed = RegistryFingerprint.hash(single(EffectSpec.of("ZZ")
                .param("a", D.DOUBLE.min(0))
                .affinity(Affinity.AOE)
                .target("t", T.HERE).build()));
        assertEquals(plain, routed, "affinity + a location slot are runtime routing, not authorability");

        // An ENTITY slot is different since ADR-0076: declaring one DECLARES the three per-target knobs, which
        // are genuinely authorable — so this one must move the hash, or a pack exported before the wave would
        // compare equal to one exported after and the mismatch report would lie.
        String perTarget = RegistryFingerprint.hash(single(EffectSpec.of("ZZ")
                .param("a", D.DOUBLE.min(0))
                .affinity(Affinity.AOE)
                .target("t", T.AOE).build()));
        assertNotEquals(plain, perTarget, "an entity slot declares each-if/each-chance/each-cooldown");
    }

    // ── Vocabulary sensitivity (package-private canonical seam) ──

    @Test
    void removingATriggerMovesTheCanonicalText() {
        EffectRegistry effects = BuiltinEffects.registry();
        SelectorRegistry selectors = BuiltinSelectors.registry();
        VarVocabulary vars = BuiltinVars.vocabulary();
        List<String> triggers = BuiltinTriggers.registry().names();
        List<String> fewer = new ArrayList<>(triggers);
        fewer.remove(0);
        assertNotEquals(RegistryFingerprint.canonical(effects, selectors, triggers, vars),
                RegistryFingerprint.canonical(effects, selectors, fewer, vars));
    }

    @Test
    void addingAVariableMovesTheCanonicalText() {
        EffectRegistry effects = BuiltinEffects.registry();
        SelectorRegistry selectors = BuiltinSelectors.registry();
        List<String> triggers = BuiltinTriggers.registry().names();
        VarVocabulary one = VarVocabulary.builder().number("a.x").build();
        VarVocabulary two = VarVocabulary.builder().number("a.x").number("a.y").build();
        assertNotEquals(RegistryFingerprint.canonical(effects, selectors, triggers, one),
                RegistryFingerprint.canonical(effects, selectors, triggers, two));
    }

    @Test
    void enumValueReorderDoesNotMoveTheHash() {
        assertEquals(
                RegistryFingerprint.hash(single(EffectSpec.of("ZZ").param("m", D.enumOf("A", "B", "C")).build())),
                RegistryFingerprint.hash(single(EffectSpec.of("ZZ").param("m", D.enumOf("C", "B", "A")).build())));
    }

    @Test
    void registrationOrderDoesNotMoveTheHash() {
        EffectSpec a = EffectSpec.of("ZZ_A").param("x", D.DOUBLE.min(0)).build();
        EffectSpec b = EffectSpec.of("ZZ_B").param("y", D.INT.min(0)).build();
        EffectRegistry first = EffectRegistry.builder().register(fake(a)).register(fake(b)).build();
        EffectRegistry second = EffectRegistry.builder().register(fake(b)).register(fake(a)).build();
        assertEquals(RegistryFingerprint.hash(first), RegistryFingerprint.hash(second));
    }

    // ── Selectors appear in the surface ──

    @Test
    void everySelectorHeadAndParamAppearsInTheSurface() {
        String canonical = RegistryFingerprint.canonical(BuiltinEffects.registry());
        String selectors = canonical.substring(canonical.indexOf("[selectors]"), canonical.indexOf("[triggers]"));
        for (SelectorKind kind : BuiltinSelectors.registry().kinds()) {
            assertTrue(selectors.contains("selector " + kind.spec().head()),
                    () -> "missing selector head " + kind.spec().head());
            for (Param param : kind.spec().paramSpec().params()) {
                assertTrue(selectors.contains("  param " + param.name() + " "),
                        () -> "missing selector param " + kind.spec().head() + "." + param.name());
            }
        }
    }

    // ── Format guarantees ──

    @Test
    void hashHasVersionPrefixAndLength() {
        assertTrue(RegistryFingerprint.hash(BuiltinEffects.registry()).matches("^1:[0-9a-f]{64}$"));
    }

    @Test
    void shortFormTruncates() {
        String full = RegistryFingerprint.hash(BuiltinEffects.registry());
        assertEquals("1:" + full.substring(2, 14), RegistryFingerprint.shortForm(full));
        assertEquals("garbage", RegistryFingerprint.shortForm("garbage")); // no colon → unchanged
        assertEquals("1:short", RegistryFingerprint.shortForm("1:short")); // < 12 hex → unchanged
        assertEquals("", RegistryFingerprint.shortForm(""));
    }

    private static EffectRegistry single(EffectSpec spec) {
        return EffectRegistry.builder().register(fake(spec)).build();
    }

    /** A no-op {@link EffectKind} carrying only its spec — the fingerprint reads nothing else. */
    private static EffectKind fake(EffectSpec spec) {
        return new EffectKind() {
            @Override
            public EffectSpec spec() {
                return spec;
            }

            @Override
            public void run(EffectCtx ctx, Sink sink) {
            }
        };
    }
}
