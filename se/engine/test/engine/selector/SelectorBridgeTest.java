package engine.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import compile.Compiler;
import compile.def.AbilityDef;
import compile.model.Ability;
import compile.model.Snapshot;
import compile.model.SourceKind;
import engine.effect.EffectRegistry;
import engine.effect.kind.BuiltinEffects;
import engine.selector.kind.BuiltinSelectors;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves the selector half of the engine↔compiler loop closes: the engine declares
 * selector kinds, exposes their specs through {@link SelectorRegistry}, and the pure
 * compiler validates an inline {@code @Head{...}} against exactly those (§3.5, §7).
 */
class SelectorBridgeTest {

    private static Compiler compiler() {
        EffectRegistry effects = BuiltinEffects.registry();
        SelectorRegistry selectors = BuiltinSelectors.registry();
        return Compiler.of(effects.specRegistry(), effects.affinityOf(),
                selectors.specRegistry(), effects.defaultSelectorOf());
    }

    private static AbilityDef ability(String stableKey, String effectLine) {
        return new AbilityDef(SourceKind.ENCHANT, stableKey, 1, 1, 100.0, 0, 0,
                List.of("ATTACK"), List.of(), null,
                List.of(EffectLine.parse(effectLine, Source.of("enchants.yml", 1, 1))),
                null, null, null, null, 0, Source.ofFile("enchants.yml"), 0);
    }

    @Test
    void inlineSelectorCompilesThroughTheRegistryBridge() {
        Diagnostics d = new Diagnostics();
        Snapshot snap = compiler().compile(List.of(ability("ench/aoe", "DAMAGE:6:@Aoe{r=3}")), 1, d);

        assertFalse(d.hasErrors());
        Ability a = snap.byStableKey("ench/aoe");
        assertNotNull(a);
        assertEquals("AOE", a.effects()[0].target().head());
        assertEquals(3.0, a.effects()[0].target().args().dbl("r"));
    }

    @Test
    void declaredDefaultTargetResolvesWhenNoInlineSelector() {
        // DAMAGE declares .target("who", T.VICTIM); with no inline selector that
        // declared default flows through the bridge into the compiled effect.
        Diagnostics d = new Diagnostics();
        Snapshot snap = compiler().compile(List.of(ability("ench/default", "DAMAGE:6")), 1, d);

        assertFalse(d.hasErrors());
        Ability a = snap.byStableKey("ench/default");
        assertEquals("VICTIM", a.effects()[0].target().head());
    }
}
