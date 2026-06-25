package integrate.protect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massivecraft.factions.Faction;
import org.junit.jupiter.api.Test;

/**
 * Pins which territory {@link FactionsProvider} gates. The relation comparison for a gated claim can only be
 * verified live — the {@code Relation} enum's static initializer needs the running plugin — so it is verified
 * out-of-matrix (docs/decisions/0027).
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
