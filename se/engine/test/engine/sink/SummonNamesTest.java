package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class SummonNamesTest {

    @Test
    void ownerTokenIsFilledInPlaceLeavingTheAuthoredColoursAlone() {
        assertEquals("&b&lNotch's Guardians", SummonNames.fill("&b&l{OWNER}'s Guardians", "Notch"));
        assertEquals("{#af2f39}Notch", SummonNames.fill("{#af2f39}{OWNER}", "Notch"),
                "a hex colour token is not ours to touch — only {OWNER} is");
    }

    @Test
    void aNameWithoutTheTokenIsReturnedUntouched() {
        // Identity, not equality: the spawn path must not allocate a copy for the names that carry no token —
        // and a stray {#RRGGBB} must not drag one in either.
        String plain = "&b&lGuardians";
        assertSame(plain, SummonNames.fill(plain, "Notch"));
        String hex = "{#af2f39}&lGuardians";
        assertSame(hex, SummonNames.fill(hex, "Notch"));
        assertSame(null, SummonNames.fill(null, "Notch"));
    }

    @Test
    void anUnresolvableOwnerStripsTheTokenRatherThanShowingIt() {
        // A summon outliving its owner's session still wears a readable nameplate; the authoring syntax is
        // never something a player should be shown.
        assertEquals("&b&l's Guardians", SummonNames.fill("&b&l{OWNER}'s Guardians", null));
        assertEquals("&b&l's Guardians", SummonNames.fill("&b&l{OWNER}'s Guardians", ""));
    }
}
