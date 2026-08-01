package feature.combat;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

final class CosmicWeaponListenerTest {

    @Test
    void paralyzeStrikesTheRoutedTargetInsteadOfTheSource() {
        Player source = mock(Player.class);
        Player target = mock(Player.class);
        Location targetLocation = mock(Location.class);
        Location strike = mock(Location.class);
        when(target.getLocation()).thenReturn(targetLocation);
        when(targetLocation.clone()).thenReturn(strike);

        assertSame(strike, CosmicWeaponListener.paralyzeStrikeLocation(source, target));

        verify(target).getLocation();
        verify(source, never()).getLocation();
    }
}
