package compile.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import compile.MapSpecRegistry;
import compile.SpecRegistry;
import compile.def.AbilityDef;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.SourceKind;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;
import schema.spec.D;
import schema.spec.ParamSpec;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Selector resolution during lowering: inline override vs. declared default vs. SELF. */
class SelectorLoweringTest {

    private static final Source SRC = Source.of("enchants.yml", 1, 1);

    private static SpecRegistry effects() {
        return MapSpecRegistry.of(ParamSpec.of("DAMAGE").param("amount", D.DOUBLE.min(0)).build());
    }

    private static SpecRegistry selectors() {
        return MapSpecRegistry.of(ParamSpec.of("AOE").param("r", D.DOUBLE.min(0).def(4)).build());
    }

    private static DefaultLowerStage stage(Function<String, String> defaultSelectorOf) {
        return new DefaultLowerStage(effects(), head -> Affinity.CONTEXT_LOCAL,
                selectors(), defaultSelectorOf);
    }

    private static AbilityDef def(EffectLine... effects) {
        return new AbilityDef(SourceKind.ENCHANT, "test/sel", 1, 1, 100.0, 0, 0,
                List.of("ATTACK"), List.of(), null, List.of(effects),
                null, null, null, null, 0, SRC, 0);
    }

    @Test
    void inlineSelectorOverridesTheDefaultTarget() {
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = stage(head -> null)
                .lower(def(EffectLine.parse("DAMAGE:6:@Aoe{r=3}", SRC)), d);

        assertFalse(d.hasErrors());
        CompiledEffect e = lowered.effects().get(0);
        assertEquals("DAMAGE", e.head());
        assertEquals(6.0, e.args().dbl("amount"));
        assertEquals("AOE", e.target().head());
        assertEquals(3.0, e.target().args().dbl("r"));
    }

    @Test
    void declaredDefaultTargetIsUsedWhenNoInlineSelector() {
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = stage(head -> "DAMAGE".equals(head) ? "AOE" : null)
                .lower(def(EffectLine.parse("DAMAGE:6", SRC)), d);

        assertFalse(d.hasErrors());
        CompiledEffect e = lowered.effects().get(0);
        assertEquals("AOE", e.target().head());
        assertEquals(4.0, e.target().args().dbl("r"));
    }

    @Test
    void noDeclaredTargetAndNoInlineSelectorIsSelf() {
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = stage(head -> null)
                .lower(def(EffectLine.parse("DAMAGE:6", SRC)), d);

        assertFalse(d.hasErrors());
        assertSame(CompiledSelector.SELF, lowered.effects().get(0).target());
    }
}
