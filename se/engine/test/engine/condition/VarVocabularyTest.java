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
        // Slot counts are load-bearing: the FactBuffer is sized to them. Breakdown justifying 15/18/9 lives in
        // v3.1 §A (numeric/flag base + exotic-effect port), v3.7 §N (victim.mobtype string), the Cosmic Pack
        // sets (victim.inzone flag — devil's hellfire zone), and ADR-0035 (actor.groundblock string — Frost "on ice").
        assertEquals(15, v.numberSlots());
        assertEquals(18, v.flagSlots());
        assertEquals(9, v.stringSlots());
        assertEquals(VarKind.NUM, v.lookup("victim", "health").orElseThrow().kind());
        assertEquals(VarKind.NUM, v.lookup("actor", "maxhealth").orElseThrow().kind());
        assertEquals(VarKind.NUM, v.lookup("world", "time").orElseThrow().kind());
        assertEquals(VarKind.BOOL, v.lookup(null, "blocking").orElseThrow().kind());
        assertEquals(VarKind.BOOL, v.lookup("victim", "sneaking").orElseThrow().kind());
        assertEquals(VarKind.BOOL, v.lookup(null, "isblock").orElseThrow().kind());
        assertEquals(VarKind.STR, v.lookup("actor", "world").orElseThrow().kind());
        assertEquals(VarKind.STR, v.lookup("victim", "type").orElseThrow().kind());
        assertEquals(VarKind.STR, v.lookup("block", "type").orElseThrow().kind());
        assertEquals(VarKind.STR, v.lookup("actor", "groundblock").orElseThrow().kind()); // ADR-0035 "on ice"
        assertTrue(v.flagSlots() <= FactBuffer.MAX_FLAGS); // must fit the flag bitset
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
