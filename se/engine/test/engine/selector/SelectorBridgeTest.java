package engine.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import compile.Compiler;
import compile.def.AbilityDef;
import compile.model.Ability;
import compile.model.Snapshot;
import engine.effect.EffectRegistry;
import engine.effect.kind.BuiltinEffects;
import engine.selector.kind.BuiltinSelectors;
import schema.diag.Diagnostics;
import schema.diag.Source;
import testfx.Defs;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The pure compiler validates an inline {@code @Head{...}} against the engine's selector specs, exposed through {@link SelectorRegistry} (§3.5, §7). */
class SelectorBridgeTest {

    private static Compiler compiler() {
        EffectRegistry effects = BuiltinEffects.registry();
        SelectorRegistry selectors = BuiltinSelectors.registry();
        return Compiler.of(effects.specRegistry(), effects.affinityOf(),
                selectors.specRegistry(), effects.defaultSelectorOf());
    }

    private static AbilityDef ability(String stableKey, String effectLine) {
        return Defs.ability().stableKey(stableKey).effectLines(effectLine)
                .source(Source.of("enchants.yml", 1, 1)).build();
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
    void selectorHandleArgsAreInternedByTheResolveStage() {
        // The resolve stage used to walk EFFECT params only, so a selector's own HANDLE list would reach the
        // runtime as the raw token and filter nothing — a break tool that silently ate every block.
        Diagnostics d = new Diagnostics();
        Compiler compiler = Compiler.of(BuiltinEffects.registry().specRegistry(),
                BuiltinEffects.registry().affinityOf(), BuiltinSelectors.registry().specRegistry(),
                BuiltinEffects.registry().defaultSelectorOf(), FIXED_HANDLES);
        Snapshot snap = compiler.compile(
                List.of(ability("ench/bore", "BREAK_BLOCK:@Bore{depth=2, materials=[STONE,DIRT]}")), 1, d);

        assertFalse(d.hasErrors(), () -> d.all().toString());
        Ability a = snap.byStableKey("ench/bore");
        assertEquals("BORE", a.effects()[0].target().head());
        assertEquals(List.of(1, 2), a.effects()[0].target().args().ids("materials"));
    }

    /** Two distinct material ids, so a transposed or collapsed list is visible in the assertion. */
    private static final compile.resolve.PlatformResolvers FIXED_HANDLES =
            new compile.resolve.PlatformResolvers() {
                @Override
                public java.util.OptionalInt material(String token) {
                    return switch (token) {
                        case "STONE" -> java.util.OptionalInt.of(1);
                        case "DIRT" -> java.util.OptionalInt.of(2);
                        default -> java.util.OptionalInt.empty();
                    };
                }

                @Override
                public java.util.OptionalInt sound(String token) {
                    return java.util.OptionalInt.empty();
                }

                @Override
                public java.util.OptionalInt potionEffect(String token) {
                    return java.util.OptionalInt.empty();
                }

                @Override
                public java.util.OptionalInt particle(String token) {
                    return java.util.OptionalInt.empty();
                }

                @Override
                public java.util.OptionalInt entityType(String token) {
                    return java.util.OptionalInt.empty();
                }

                @Override
                public java.util.OptionalInt attribute(String token) {
                    return java.util.OptionalInt.empty();
                }

                @Override
                public java.util.OptionalInt enchantment(String token) {
                    return java.util.OptionalInt.empty();
                }
            };

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
