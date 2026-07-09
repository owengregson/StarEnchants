package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.sink.TimedRevert;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

/** The quit drain (F07/F08): a logout runs only the quitting player's outstanding timed-buff reverts. */
class TimedRevertListenerTest {

    @Test
    void onQuitDrainsOnlyTheQuittingPlayersReverts() {
        TimedRevert reverts = new TimedRevert();
        UUID quitter = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        AtomicInteger quitterRuns = new AtomicInteger();
        AtomicInteger otherRuns = new AtomicInteger();
        reverts.begin(quitter, quitterRuns::incrementAndGet);
        reverts.begin(other, otherRuns::incrementAndGet);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(quitter);
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new TimedRevertListener(reverts).onQuit(event);

        assertEquals(1, quitterRuns.get(), "the quitter's revert ran");
        assertEquals(0, otherRuns.get(), "another player's revert is untouched");
    }
}
