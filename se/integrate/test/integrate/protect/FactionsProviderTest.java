package integrate.protect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massivecraft.factions.Faction;
import org.junit.jupiter.api.Test;

/**
 * Pins which territory is gated by {@link FactionsProvider}: wilderness and the system zones (safezone /
 * warzone) are NOT gated (allow everything), while a normal player-faction claim IS. The relation comparison
 * for a gated claim ({@code at.getRelationTo(actor).isAtLeast(TRUCE)}) can only be verified on a live Factions
 * server — referencing the {@code Relation} enum triggers a static initializer that needs the running plugin —
 * so it is verified out-of-matrix (docs/decisions/0027).
 */
class FactionsProviderTest {

    @Test
    void wildernessIsNotGated() {
        Faction wilderness = mock(Faction.class);
        when(wilderness.isWilderness()).thenReturn(true);
        assertFalse(FactionsProvider.isClaimGated(wilderness), "wilderness ⇒ not gated ⇒ allow");
    }

    @Test
    void systemZonesAreNotGated() {
        Faction safe = mock(Faction.class);
        when(safe.isSafeZone()).thenReturn(true);
        assertFalse(FactionsProvider.isClaimGated(safe));

        Faction war = mock(Faction.class);
        when(war.isWarZone()).thenReturn(true);
        assertFalse(FactionsProvider.isClaimGated(war));
    }

    @Test
    void noFactionAtLocationIsNotGated() {
        assertFalse(FactionsProvider.isClaimGated(null));
    }

    @Test
    void normalPlayerFactionClaimIsGated() {
        // A mock Faction returns false for all the zone predicates by default ⇒ a normal player claim.
        assertTrue(FactionsProvider.isClaimGated(mock(Faction.class)));
    }
}
