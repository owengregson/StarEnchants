package engine.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.SpecRegistry;
import engine.selector.kind.BuiltinSelectors;
import engine.selector.kind.SelfSelector;
import org.junit.jupiter.api.Test;

class SelectorRegistryTest {

    @Test
    void lookupIsCaseInsensitiveAndCoversBuiltins() {
        SelectorRegistry reg = BuiltinSelectors.registry();
        assertTrue(reg.lookup("AOE").isPresent());
        assertTrue(reg.lookup("aoe").isPresent());
        assertTrue(reg.lookup("SELF").isPresent());
        assertTrue(reg.lookup("VICTIM").isPresent());
        assertTrue(reg.lookup("ATTACKER").isPresent());
        assertTrue(reg.lookup("NEAREST").isPresent());
        assertTrue(reg.lookup("nope").isEmpty());
    }

    @Test
    void duplicateHeadFailsFast() {
        SelectorRegistry.Builder b = SelectorRegistry.builder().register(new SelfSelector());
        assertThrows(IllegalArgumentException.class, () -> b.register(new SelfSelector()));
    }

    /** ADR-0039: {@code idOf} indexes {@link SelectorRegistry#selectorsById()} case-insensitively; unknown is -1. */
    @Test
    void idOfIndexesSelectorsById() {
        SelectorRegistry reg = BuiltinSelectors.registry();
        SelectorKind[] byId = reg.selectorsById();

        int selfId = reg.idOf("SELF");
        assertTrue(selfId >= 0);
        assertEquals(selfId, reg.idOf("self"));
        assertSame(reg.lookup("SELF").orElseThrow(), byId[selfId]);
        assertEquals(-1, reg.idOf("nope"));
    }

    @Test
    void specRegistryBridgeExposesSelectorParams() {
        SpecRegistry specs = BuiltinSelectors.registry().specRegistry();
        assertTrue(specs.lookup("AOE").isPresent());
        assertFalse(specs.lookup("AOE").get().params().isEmpty()); // AOE declares 'r'
        assertTrue(specs.lookup("SELF").get().params().isEmpty()); // SELF takes no args
    }
}
