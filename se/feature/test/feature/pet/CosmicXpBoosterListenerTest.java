package feature.pet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.stores.VarStore;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.Test;

class CosmicXpBoosterListenerTest {

    @Test
    void multipliesOnlyKilledEntityDroppedXpWithCosmicTruncation() {
        UUID id = UUID.randomUUID();
        AtomicLong now = new AtomicLong(100);
        VarStore vars = new VarStore();
        vars.set(id, CosmicXpBoosterListener.BOOST_VAR, "1.45", now.get(), 200);
        CosmicXpBoosterListener listener = new CosmicXpBoosterListener(vars, now::get);

        Player killer = mock(Player.class);
        when(killer.getUniqueId()).thenReturn(id);
        LivingEntity dead = mock(LivingEntity.class);
        when(dead.getKiller()).thenReturn(killer);
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(dead);
        when(event.getDroppedExp()).thenReturn(3);

        listener.onDeath(event);

        verify(event).setDroppedExp(4); // (int) (3 * 1.45) = 4
    }

    @Test
    void ignoresDeathsWithoutAKillerAndExpiredOrMalformedBoosts() {
        AtomicLong now = new AtomicLong(100);
        VarStore vars = new VarStore();
        CosmicXpBoosterListener listener = new CosmicXpBoosterListener(vars, now::get);
        EntityDeathEvent noKiller = event(null, 10);
        listener.onDeath(noKiller);
        verify(noKiller, never()).setDroppedExp(org.mockito.ArgumentMatchers.anyInt());

        UUID id = UUID.randomUUID();
        Player killer = mock(Player.class);
        when(killer.getUniqueId()).thenReturn(id);
        vars.set(id, CosmicXpBoosterListener.BOOST_VAR, "2.0", now.get(), 5);
        now.set(105);
        EntityDeathEvent expired = event(killer, 10);
        listener.onDeath(expired);
        verify(expired, never()).setDroppedExp(org.mockito.ArgumentMatchers.anyInt());

        vars.set(id, CosmicXpBoosterListener.BOOST_VAR, "not-a-number", now.get(), 20);
        EntityDeathEvent malformed = event(killer, 10);
        listener.onDeath(malformed);
        verify(malformed, never()).setDroppedExp(org.mockito.ArgumentMatchers.anyInt());
    }

    private static EntityDeathEvent event(Player killer, int droppedExp) {
        LivingEntity dead = mock(LivingEntity.class);
        when(dead.getKiller()).thenReturn(killer);
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(dead);
        when(event.getDroppedExp()).thenReturn(droppedExp);
        return event;
    }
}
