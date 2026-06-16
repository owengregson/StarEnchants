package engine.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.cond.VarBinding;
import compile.cond.VarKind;
import org.junit.jupiter.api.Test;

class VarVocabularyTest {

    @Test
    void assignsDensePerKindSlotsInOrder() {
        VarVocabulary v = VarVocabulary.builder()
                .number("a").number("b")
                .flag("f1").flag("f2").flag("f3")
                .string("s")
                .build();
        assertEquals(2, v.numberSlots());
        assertEquals(3, v.flagSlots());
        assertEquals(1, v.stringSlots());
        assertEquals(new VarBinding(VarKind.NUM, 0), v.lookup(null, "a").orElseThrow());
        assertEquals(new VarBinding(VarKind.NUM, 1), v.lookup(null, "b").orElseThrow());
        assertEquals(new VarBinding(VarKind.BOOL, 2), v.lookup(null, "f3").orElseThrow());
        assertEquals(new VarBinding(VarKind.STR, 0), v.lookup(null, "s").orElseThrow());
    }

    @Test
    void lookupIsCaseInsensitiveAndScopeAware() {
        VarVocabulary v = VarVocabulary.builder().number("victim.health").flag("sneaking").build();
        assertTrue(v.lookup("victim", "health").isPresent());
        assertTrue(v.lookup("VICTIM", "HEALTH").isPresent());
        assertTrue(v.lookup(null, "SNEAKING").isPresent());
        assertTrue(v.lookup(null, "unknown").isEmpty());
    }

    @Test
    void builtinsHaveTheExpectedShape() {
        VarVocabulary v = BuiltinVars.vocabulary();
        assertEquals(4, v.numberSlots()); // actor.health, victim.health, damage, combo
        assertEquals(3, v.flagSlots());   // sneaking, blocking, flying
        assertEquals(0, v.stringSlots());
        assertEquals(VarKind.NUM, v.lookup("victim", "health").orElseThrow().kind());
        assertEquals(VarKind.BOOL, v.lookup(null, "blocking").orElseThrow().kind());
    }

    @Test
    void newFactBufferIsSizedToTheVocabulary() {
        VarVocabulary v = BuiltinVars.vocabulary();
        FactBuffer f = v.newFactBuffer();
        int healthSlot = v.lookup("victim", "health").orElseThrow().slot();
        f.setNumber(healthSlot, 12.0); // would AIOOBE if undersized
        assertEquals(12.0, f.number(healthSlot));
    }

    @Test
    void duplicateVariableFailsFast() {
        VarVocabulary.Builder b = VarVocabulary.builder().number("x");
        assertThrows(IllegalArgumentException.class, () -> b.flag("X")); // case-insensitive clash
    }
}
