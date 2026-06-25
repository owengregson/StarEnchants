package engine.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import compile.Compiler;
import compile.def.AbilityDef;
import compile.model.Ability;
import compile.model.Affinity;
import compile.model.Snapshot;
import compile.model.SourceKind;
import engine.effect.kind.BuiltinEffects;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The compiler validates/folds against engine-declared specs via {@link EffectRegistry}, with no {@code se-compile} → {@code se-engine} dependency (§2.1, §3.6). */
class EngineCompilerBridgeTest {

    @Test
    void compilesAnAbilityThroughTheRegistryBridge() {
        EffectRegistry reg = BuiltinEffects.registry();
        Compiler compiler = Compiler.of(reg.specRegistry(), reg.affinityOf());

        AbilityDef def = new AbilityDef(
                SourceKind.ENCHANT, "ench/test", 1, 2, 50.0, 0, 0,
                List.of("ATTACK"), List.of(), null,
                List.of(EffectLine.parse("DAMAGE:6", Source.of("enchants.yml", 1, 1)),
                        EffectLine.parse("MODIFY_HEALTH:3", Source.of("enchants.yml", 2, 1))),
                null, null, null, null, 0, Source.ofFile("enchants.yml"), 0);

        Diagnostics d = new Diagnostics();
        Snapshot snap = compiler.compile(List.of(def), 1, d);

        assertFalse(d.hasErrors());
        Ability a = snap.byStableKey("ench/test");
        assertNotNull(a);
        assertEquals(2, a.effects().length);
        assertEquals("DAMAGE", a.effects()[0].head());
        assertEquals(6.0, a.effects()[0].args().dbl("amount"));
        assertEquals("MODIFY_HEALTH", a.effects()[1].head());
        assertEquals(3.0, a.effects()[1].args().dbl("amount"));

        // Affinity folded through the bridge: DAMAGE(CONTEXT_LOCAL) ∨ MODIFY_HEALTH(TARGET_ENTITY)
        // ⇒ ability-level TARGET_ENTITY.
        assertEquals(Affinity.TARGET_ENTITY, a.affinity());
    }
}
