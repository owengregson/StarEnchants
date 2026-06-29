package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import compile.load.MasterConfig;
import compile.load.ParticleSpec;
import compile.load.SetDef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The set-equip particle colour rule: the EQUIP dust is tinted to the set's own {@code &}-colour (when
 * use-set-color is on), but the UNEQUIP dust always keeps its configured colour (a gray cloud) so removing any
 * set reads the same.
 */
class SetEquipEffectsTest {

    private static final ParticleSpec EQUIP = new ParticleSpec("REDSTONE", 0, 0, 0, 20, 1.25, 1.0);
    private static final ParticleSpec UNEQUIP_GRAY = new ParticleSpec("REDSTONE", 170, 170, 170, 20, 1.25, 1.0);

    private static SetDef set(String display) {
        return new SetDef("sets/x", display, "", null, 1, List.of(), List.of(), null, List.of(), List.of(),
                Map.of(), Map.of(), true, "", "", schema.diag.Source.ofFile("t"));
    }

    private static MasterConfig.SetsSection cfg(boolean useSetColor) {
        return new MasterConfig.SetsSection(false, useSetColor, List.of(), List.of(), EQUIP, UNEQUIP_GRAY);
    }

    @Test
    void equipDustTakesTheSetColourWhenEnabled() {
        ParticleSpec spec = SetEquipEffects.particleFor(cfg(true), set("&4Supreme"), true);
        assertEquals(170, spec.colorR()); // &4 dark red
        assertEquals(0, spec.colorG());
        assertEquals(0, spec.colorB());
        assertEquals(20, spec.amount()); // density preserved
    }

    @Test
    void unequipDustStaysGrayEvenWithUseSetColorOn() {
        ParticleSpec spec = SetEquipEffects.particleFor(cfg(true), set("&4Supreme"), false);
        assertEquals(170, spec.colorR()); // the configured gray, NOT the set's dark red
        assertEquals(170, spec.colorG());
        assertEquals(170, spec.colorB());
    }

    @Test
    void equipKeepsTheConfiguredColourWhenUseSetColourOff() {
        ParticleSpec spec = SetEquipEffects.particleFor(cfg(false), set("&4Supreme"), true);
        assertEquals(0, spec.colorR()); // no override
    }
}
