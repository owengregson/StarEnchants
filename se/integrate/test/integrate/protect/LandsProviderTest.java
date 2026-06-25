package integrate.protect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import me.angeschossen.lands.api.flags.type.Flags;
import me.angeschossen.lands.api.land.Area;
import org.junit.jupiter.api.Test;

/**
 * Pins the gate decision of {@link LandsProvider} against a mocked {@code Area}: unclaimed land allows
 * everything; inside a claim the actor's BLOCK_PLACE role-flag (by UUID) decides. End-to-end with real Lands
 * is verified out-of-matrix (docs/decisions/0027).
 */
class LandsProviderTest {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Test
    void unclaimedLandAllowsEverything() {
        assertTrue(LandsProvider.buildAllowed(null, ACTOR), "no area ⇒ wilderness ⇒ allow");
    }

    @Test
    void claimDefersToBlockPlaceFlag() {
        Area allowing = mock(Area.class);
        when(allowing.hasRoleFlag(eq(ACTOR), any())).thenReturn(true);
        assertTrue(LandsProvider.buildAllowed(allowing, ACTOR));

        Area denying = mock(Area.class);
        when(denying.hasRoleFlag(any(UUID.class), eq(Flags.BLOCK_PLACE))).thenReturn(false);
        assertFalse(LandsProvider.buildAllowed(denying, ACTOR));
    }
}
