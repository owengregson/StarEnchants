package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import schema.diag.Diagnostics;

/** Parsing the unified {@code { particle: NAME, count: N, color: { r, g, b }, spread: S, y-offset: Y }} form. */
class ParticleSpecTest {

    private static YamlNode yaml(String body, Diagnostics diags) {
        return YamlNode.compose("test.yml", body, diags);
    }

    @Test
    void readsDustWithColourSpreadAndOffset() {
        Diagnostics diags = new Diagnostics();
        ParticleSpec spec = ParticleSpec.from(
                yaml("{ particle: REDSTONE, count: 20, color: { r: 91, g: 245, b: 83 }, spread: 1.25, y-offset: 1.0 }",
                        diags),
                diags);
        assertEquals("REDSTONE", spec.type());
        assertEquals(91, spec.colorR());
        assertEquals(245, spec.colorG());
        assertEquals(83, spec.colorB());
        assertEquals(20, spec.amount());
        assertEquals(1.25, spec.spread());
        assertEquals(1.0, spec.yOffset());
        assertFalse(spec.isEmpty());
    }

    @Test
    void readsAColourlessParticle() {
        Diagnostics diags = new Diagnostics();
        ParticleSpec spec = ParticleSpec.from(
                yaml("{ particle: ENCHANTMENT_TABLE, count: 8, spread: 0.75, y-offset: 1.0 }", diags), diags);
        assertEquals("ENCHANTMENT_TABLE", spec.type());
        assertEquals(0, spec.colorR()); // no color map → 0,0,0 (ignored for a non-dust particle)
        assertEquals(8, spec.amount());
        assertEquals(0.75, spec.spread());
    }

    @Test
    void aMissingParticleTokenIsEmpty() {
        Diagnostics diags = new Diagnostics();
        assertTrue(ParticleSpec.from(yaml("{ count: 5 }", diags), diags).isEmpty());
        assertTrue(ParticleSpec.none().isEmpty());
    }

    @Test
    void clampsColourComponentsAmountAndSpread() {
        ParticleSpec spec = new ParticleSpec("DUST", 300, -5, 128, -3, -1.0, 1.0);
        assertEquals(255, spec.colorR()); // over-255 clamped down
        assertEquals(0, spec.colorG()); // negative clamped up
        assertEquals(128, spec.colorB()); // in range, untouched
        assertEquals(0, spec.amount()); // negative count clamped to 0
        assertEquals(0.0, spec.spread()); // negative spread clamped to 0
    }
}
