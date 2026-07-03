package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.boot.ContentCompiler;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pack.Pack;
import pack.PackManifest;
import schema.diag.DiagCode;
import schema.diag.Diagnostic;
import testfx.PermissiveResolvers;

/**
 * Unit coverage for the ADR-0046 {@link PackGate}: fingerprint status, whole-surface dry-run compile through
 * the REAL loaders, and the file:line diagnostics — all in memory (no zips, no server). The compiler uses the
 * structural-validation {@link PermissiveResolvers} the sibling compile tests share.
 */
class PackGateTest {

    private static final PackGate GATE = new PackGate(
            () -> ContentCompiler.production(PermissiveResolvers.INSTANCE),
            () -> "1:live", () -> "effects 1");

    // A minimal shipped enchant (scorch), known clean under PermissiveResolvers (CatalogValidationTest proves it).
    private static final String SCORCH =
            "tier: common\n"
            + "display: \"&6Scorch\"\n"
            + "description: \"Ignite the enemy you strike.\"\n"
            + "trigger: ATTACK\n"
            + "applies-to: [SWORD, AXE]\n"
            + "group: combat\n"
            + "levels:\n"
            + "  1:\n"
            + "    chance: 30\n"
            + "    effects:\n"
            + "      - { IGNITE: { duration: 40 } }\n";

    @Test
    void cleanStampedPackMatches() {
        PackGate.Report report = GATE.check(pack(stamped("1:live"),
                Map.of("content/enchants/scorch.yml", bytes(SCORCH))));
        assertEquals(PackGate.FingerprintStatus.MATCH, report.status());
        assertTrue(report.clean());
        assertTrue(report.abilityCount() > 0, "the enchant should load one ability");
        assertTrue(report.blocking().isEmpty());
    }

    @Test
    void mismatchStampedStillCompiles() {
        PackGate.Report report = GATE.check(pack(stamped("1:other"),
                Map.of("content/enchants/scorch.yml", bytes(SCORCH))));
        assertEquals(PackGate.FingerprintStatus.MISMATCH, report.status()); // surface differs…
        assertTrue(report.clean()); // …but the pack still compiles → proceeds
    }

    @Test
    void unstampedPackReportsUnstamped() {
        PackGate.Report report = GATE.check(pack(PackManifest.of("p", "", "", "t"),
                Map.of("content/enchants/scorch.yml", bytes(SCORCH))));
        assertEquals(PackGate.FingerprintStatus.UNSTAMPED, report.status());
    }

    @Test
    void unknownEffectHeadIsALineDiagnostic() {
        String broken =
                "tier: common\n"
                + "display: \"&cBroken\"\n"
                + "trigger: ATTACK\n"
                + "applies-to: [SWORD]\n"
                + "levels:\n"
                + "  1:\n"
                + "    chance: 30\n"
                + "    effects:\n"
                + "      - { NOT_A_REAL_KIND: {} }\n";
        PackGate.Report report = GATE.check(pack(stamped("1:live"),
                Map.of("content/enchants/broken.yml", bytes(broken))));
        assertFalse(report.clean());
        Diagnostic diagnostic = report.blocking().stream()
                .filter(d -> d.is(DiagCode.E_UNKNOWN_KIND)).findFirst().orElseThrow();
        assertTrue(diagnostic.source().file().endsWith("enchants/broken.yml"), diagnostic.source().file());
        assertTrue(diagnostic.source().line() > 0, "the file:line promise — a real authored line, not temp-dir noise");
    }

    @Test
    void wholeSurfaceIsGatedForLangAndConfigShape() {
        // A broken lang.yml would brick the disk surface then fail the reload — the exact failure the gate prevents.
        PackGate.Report langBad = GATE.check(pack(stamped("1:live"), Map.of("lang.yml", bytes("- a\n- b\n"))));
        assertTrue(langBad.blocking().stream().anyMatch(d -> d.is(DiagCode.E_LANG_SHAPE)));

        PackGate.Report configBad = GATE.check(pack(stamped("1:live"), Map.of("config.yml", bytes("- a\n- b\n"))));
        assertTrue(configBad.blocking().stream().anyMatch(d -> d.is(DiagCode.E_CONFIG_SHAPE)));
    }

    @Test
    void partialPackGatesOnlyWhatItCarries() {
        PackGate.Report report = GATE.check(pack(stamped("1:live"), Map.of("menus/apply.yml", bytes("title: A\n"))));
        assertTrue(report.clean());
        assertEquals(0, report.abilityCount()); // no content/ → nothing loads, but that is clean
    }

    @Test
    void escapingEntriesAreIgnored() {
        PackGate.Report report = GATE.check(pack(stamped("1:live"),
                Map.of("../evil.yml", bytes("nope\n"), "content/enchants/scorch.yml", bytes(SCORCH))));
        assertTrue(report.clean()); // the escape entry is skipped by stageInto, never compiled
    }

    @Test
    void warningsAreCountedButDoNotBlock() {
        // A non-numeric souls-per-kill warns (W_ITEM_NUM) and falls back — clean, but the count is surfaced.
        String gem =
                "type: soul-gem\n"
                + "material: EMERALD\n"
                + "name: \"&aSoul Gem\"\n"
                + "souls-per-kill: notanumber\n";
        PackGate.Report report = GATE.check(pack(stamped("1:live"), Map.of("items/soul-gem.yml", bytes(gem))));
        assertTrue(report.clean());
        assertTrue(report.warningCount() > 0);
    }

    private static PackManifest stamped(String fingerprint) {
        return PackManifest.of("p", "", "", "t").stamped(fingerprint, "effects 1");
    }

    private static Pack pack(PackManifest manifest, Map<String, byte[]> files) {
        return new Pack(manifest, files);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
