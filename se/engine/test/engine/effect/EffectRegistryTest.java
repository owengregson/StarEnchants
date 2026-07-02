package engine.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.SpecRegistry;
import compile.model.Affinity;
import engine.effect.kind.BuiltinEffects;
import engine.effect.kind.DamageEffect;
import engine.effect.kind.HealthEffect;
import engine.effect.kind.IgniteEffect;
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
        assertTrue(reg.heads().contains("MODIFY_HEALTH"));
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
        assertEquals(Affinity.TARGET_ENTITY, aff.apply("MODIFY_HEALTH"));
        assertNull(aff.apply("nope"));
    }

    /** ADR-0039: {@code idOf} is a dense, case-insensitive id that indexes {@link EffectRegistry#kindsById()}; unknown is -1. */
    @Test
    void idOfIndexesKindsByIdAndIsCaseInsensitive() {
        EffectRegistry reg = BuiltinEffects.registry();
        EffectKind[] byId = reg.kindsById();

        assertEquals(byId.length, reg.heads().size());
        int damageId = reg.idOf("DAMAGE");
        assertTrue(damageId >= 0);
        assertEquals(damageId, reg.idOf("damage"));
        assertSame(reg.lookup("DAMAGE").orElseThrow(), byId[damageId]); // id → the same kind head resolves
        assertEquals(-1, reg.idOf("nope"));
    }

    /** Registration order fixes the ids, so a head keeps its id across a rebuild that appends kinds (the reload invariant). */
    @Test
    void idsFollowRegistrationOrderAndAreStableAcrossAnAppendingRebuild() {
        EffectKind a = new DamageEffect();
        EffectKind b = new IgniteEffect();
        EffectRegistry base = EffectRegistry.builder().register(a).register(b).build();
        EffectRegistry appended = EffectRegistry.builder().register(a).register(b).register(new HealthEffect()).build();

        assertEquals(0, base.idOf(a.head()));
        assertEquals(1, base.idOf(b.head()));
        assertEquals(base.idOf(a.head()), appended.idOf(a.head())); // prefix-stable: old ids unchanged
        assertEquals(base.idOf(b.head()), appended.idOf(b.head()));
    }
}
