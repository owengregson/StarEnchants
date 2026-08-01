package feature.combat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.sink.HeadDropMarks;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class HeadDropMarkListenerTest {

    @AfterEach
    void clearMarks() {
        HeadDropMarks.clearAll();
    }

    @Test
    void deathConsumesEveryIndependentEnchantMarkExactlyOnce() {
        SinkFactory factory = mock(SinkFactory.class);
        SinkEnv env = mock(SinkEnv.class);
        SinkReadback sink = mock(SinkReadback.class);
        when(factory.create(env)).thenReturn(sink);
        HeadDropMarkListener listener = new HeadDropMarkListener(factory, env);

        Player victim = mock(Player.class);
        Player killer = mock(Player.class);
        UUID victimId = UUID.randomUUID();
        when(victim.getUniqueId()).thenReturn(victimId);
        when(victim.getKiller()).thenReturn(killer);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(victim);
        HeadDropMarks.mark(victimId, "headless");
        HeadDropMarks.mark(victimId, "decapitation");

        listener.onDeath(event);
        listener.onDeath(event);

        verify(sink, org.mockito.Mockito.times(2)).dropHead(victim, killer);
        verify(sink).flush();
        verify(factory).create(env);
    }

    @Test
    void unmarkedDeathDoesNotAllocateASink() {
        SinkFactory factory = mock(SinkFactory.class);
        SinkEnv env = mock(SinkEnv.class);
        HeadDropMarkListener listener = new HeadDropMarkListener(factory, env);
        Player victim = mock(Player.class);
        when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(victim);

        listener.onDeath(event);

        verify(factory, never()).create(env);
    }
}
