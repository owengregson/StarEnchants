package compile.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link ScopeKinds} — the suppress/cooldown scope-kind wire ids the erase stage and gate 5 share. */
final class ScopeKindsTest {

    @Test
    void constantsAreTheWireIds() {
        assertEquals(0, ScopeKinds.ENCHANT);
        assertEquals(1, ScopeKinds.GROUP);
        assertEquals(2, ScopeKinds.TYPE);
    }

    @Test
    void ofMapsTheScopeToken() {
        assertEquals(ScopeKinds.GROUP, ScopeKinds.of("GROUP"));
        assertEquals(ScopeKinds.GROUP, ScopeKinds.of("group"));
        assertEquals(ScopeKinds.TYPE, ScopeKinds.of("TYPE"));
        assertEquals(ScopeKinds.ENCHANT, ScopeKinds.of("ENCHANT"));
        assertEquals(ScopeKinds.ENCHANT, ScopeKinds.of("anything"), "unrecognised → the ENCHANT default");
    }
}
