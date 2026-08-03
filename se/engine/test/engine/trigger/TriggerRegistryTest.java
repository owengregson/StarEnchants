package engine.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TriggerRegistryTest {

    @Test
    void assignsIdsInRegistrationOrderCaseInsensitively() {
        TriggerRegistry r = TriggerRegistry.builder()
                .register(Trigger.attack("ATTACK"))
                .register(Trigger.defense("DEFENSE"))
                .build();
        assertEquals(0, r.idOf("ATTACK").orElseThrow());
        assertEquals(1, r.idOf("defense").orElseThrow()); // case-insensitive
        assertTrue(r.idOf("nope").isEmpty());
        assertEquals(2, r.count());
        assertEquals(java.util.List.of("ATTACK", "DEFENSE"), r.names());
    }

    @Test
    void directionPredicatesClassifyCombatArrays() {
        TriggerRegistry r = BuiltinTriggers.registry();
        int attack = r.idOf("ATTACK").orElseThrow();
        int defense = r.idOf("DEFENSE").orElseThrow();
        int mine = r.idOf("MINE").orElseThrow();

        assertTrue(r.attackTriggers().test(attack));
        assertFalse(r.attackTriggers().test(defense));
        assertTrue(r.defenseTriggers().test(defense));
        assertFalse(r.defenseTriggers().test(mine)); // neutral is neither
        assertFalse(r.attackTriggers().test(mine));
    }

    @Test
    void metadataIsCarried() {
        TriggerRegistry r = BuiltinTriggers.registry();
        TriggerKind attack = r.byId(r.idOf("ATTACK").orElseThrow());
        assertTrue(attack.needsTarget());
        assertTrue(attack.scansEquipment());
        assertFalse(attack.usesHeld());

        TriggerKind held = r.byId(r.idOf("HELD").orElseThrow());
        assertTrue(held.usesHeld());
        assertFalse(held.scansEquipment());
        assertFalse(held.needsTarget());
    }

    @Test
    void hurtRoutesExactlyLikeFall() {
        // HURT is FALL widened to every cause, so anything that would route it differently — a direction that
        // feeds the wrong WornState array, a stray needs-target — is a bug. Compared against FALL rather than
        // re-typed, so the pair can never drift apart silently.
        TriggerRegistry r = BuiltinTriggers.registry();
        TriggerKind fall = r.byId(r.idOf("FALL").orElseThrow());
        TriggerKind hurt = r.byId(r.idOf("HURT").orElseThrow());
        assertEquals(fall.direction(), hurt.direction());
        assertEquals(fall.usesHeld(), hurt.usesHeld());
        assertEquals(fall.scansEquipment(), hurt.scansEquipment());
        assertEquals(fall.needsTarget(), hurt.needsTarget());
    }

    @Test
    void duplicateTriggerFailsFast() {
        TriggerRegistry.Builder b = TriggerRegistry.builder().register(Trigger.attack("ATTACK"));
        assertThrows(IllegalArgumentException.class, () -> b.register(Trigger.defense("attack")));
    }

    @Test
    void overflowBeyondThirtyTwoFailsFast() {
        TriggerRegistry.Builder b = TriggerRegistry.builder();
        for (int i = 0; i < TriggerRegistry.MAX_TRIGGERS; i++) {
            b.register(Trigger.neutral("T" + i));
        }
        assertThrows(IllegalArgumentException.class, () -> b.register(Trigger.neutral("ONE_TOO_MANY")));
    }

    @Test
    void builtinsFitTheMask() {
        assertTrue(BuiltinTriggers.registry().count() <= TriggerRegistry.MAX_TRIGGERS);
    }
}
