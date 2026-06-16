package engine.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.SpecRegistry;
import compile.model.Affinity;
import engine.effect.kind.BuiltinEffects;
import engine.effect.kind.DamageEffect;
import schema.diag.Diagnostics;
import schema.diag.Source;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class EffectRegistryTest {

    @Test
    void lookupIsCaseInsensitiveAndUnknownIsEmpty() {
        EffectRegistry reg = BuiltinEffects.registry();
        assertTrue(reg.lookup("DAMAGE").isPresent());
        assertTrue(reg.lookup("damage").isPresent());
        assertTrue(reg.lookup("nope").isEmpty());
        assertTrue(reg.heads().contains("DAMAGE"));
        assertTrue(reg.heads().contains("HEAL"));
    }

    @Test
    void duplicateHeadFailsFast() {
        EffectRegistry.Builder b = EffectRegistry.builder().register(new DamageEffect());
        assertThrows(IllegalArgumentException.class, () -> b.register(new DamageEffect()));
    }

    @Test
    void specRegistryBridgeValidatesAgainstRegisteredKinds() {
        SpecRegistry specs = BuiltinEffects.registry().specRegistry();
        assertTrue(specs.lookup("DAMAGE").isPresent());

        Diagnostics d = new Diagnostics();
        specs.lookup("DAMAGE").get().parse(List.of("6"), Source.UNKNOWN, d);
        assertFalse(d.hasErrors());
    }

    @Test
    void affinityBridgeReturnsDeclaredAffinityOrNull() {
        Function<String, Affinity> aff = BuiltinEffects.registry().affinityOf();
        assertEquals(Affinity.CONTEXT_LOCAL, aff.apply("DAMAGE"));
        assertEquals(Affinity.TARGET_ENTITY, aff.apply("HEAL"));
        assertNull(aff.apply("nope"));
    }
}
