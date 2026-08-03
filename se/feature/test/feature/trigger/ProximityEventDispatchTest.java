package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import compile.model.Snapshot;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.ActorProbe;
import engine.sink.ModernDispatchSink;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.compat.DropControl;
import feature.compat.Hands;
import item.worn.WornStateStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Abilities;
import testfx.Envs;
import testfx.Snapshots;
import testfx.SyncSchedulerBackend;
import testfx.WornStates;

/**
 * The observer trigger's binding. Everything an ability can say about WHO died — the relation filter, the range
 * — is read off the activation's victim side, so if the dying player is not bound there the whole trigger
 * degrades to "somebody died somewhere" and every authored filter silently passes. The other half is that the
 * corpse must not run its own PROXIMITY_EVENT gear: that is DEATH's job, and double-firing it would pay a
 * revenge proc to the person who just lost the fight.
 */
class ProximityEventDispatchTest {

    private static final UUID DYING = UUID.randomUUID();
    private static final UUID OBSERVER = UUID.randomUUID();

    private final TriggerRegistry triggers = BuiltinTriggers.registry();
    private final int proximity = triggers.idOf("PROXIMITY_EVENT").orElseThrow();

    private AbilityExecutor executor;
    private WornStateStore worn;
    private TriggerListeners listeners;
    private World world;

    @BeforeEach
    void setUp() {
        Scheduling.install(new SyncSchedulerBackend());
        RuntimeHandles handles = new RuntimeHandles(new RegistryResolvers());
        SinkEnv env = Envs.sink().build();
        SinkFactory sinkFactory = mock(SinkFactory.class);
        when(sinkFactory.create(any())).thenReturn(new ModernDispatchSink(handles, env));
        Snapshot snapshot = Snapshots.snapshot()
                .abilities(Abilities.ability().id(0).build())
                .stableKeys("enchants/avengingangel/1")
                .build();
        ContentHolder content = mock(ContentHolder.class);
        when(content.snapshot()).thenReturn(snapshot);
        executor = mock(AbilityExecutor.class);
        worn = mock(WornStateStore.class);
        TriggerDispatch dispatch = new TriggerDispatch(executor, sinkFactory, mock(ActorProbe.class), content,
                worn, triggers, p -> Optional.empty(), env, mock(Hands.class), mock(DropControl.class));
        listeners = new TriggerListeners(dispatch, mock(Hands.class));
        world = mock(World.class);
    }

    @Test
    void aNearbyWearerActivatesWithTheDyingPlayerBoundVictimSide() {
        Player dying = player(DYING);
        Player observer = player(OBSERVER);
        wearingProximity(OBSERVER);
        nearby(dying, observer);

        listeners.onDeath(death(dying));

        ArgumentCaptor<ActivationContext> contexts = ArgumentCaptor.forClass(ActivationContext.class);
        verify(executor, times(1)).run(any(), any(), any(), contexts.capture(), any(), any());
        ActivationContext context = contexts.getValue();
        assertSame(observer, context.actor(), "the ability runs on the OBSERVER's gear");
        assertSame(dying, context.victim(),
                "the dying player is the victim — %victim.relation% and %distance% price against them");
        assertEquals(dying.getLocation(), context.location(), "anchored on the body, so an @Aoe centres there");
    }

    @Test
    void theCorpseNeverRunsItsOwnProximityGear() {
        Player dying = player(DYING);
        wearingProximity(DYING);
        nearby(dying); // Bukkit excludes self, but the dispatch guards it too — a self-proc is a real payout

        listeners.onDeath(death(dying));

        verify(executor, never()).run(any(), any(), any(), any(), any(), any());
    }

    @Test
    void everyNearbyWearerActivatesOffTheOneWalk() {
        UUID second = UUID.randomUUID();
        Player dying = player(DYING);
        Player first = player(OBSERVER);
        Player other = player(second);
        wearingProximity(OBSERVER);
        wearingProximity(second);
        nearby(dying, first, mock(Zombie.class), other);

        listeners.onDeath(death(dying));

        ArgumentCaptor<ActivationContext> contexts = ArgumentCaptor.forClass(ActivationContext.class);
        verify(executor, times(2)).run(any(), any(), any(), contexts.capture(), any(), any());
        assertEquals(List.of(first, other), contexts.getAllValues().stream().map(ActivationContext::actor).toList(),
                "a non-player in the same walk is skipped, not counted");
    }

    @Test
    void theWalkScansTheDeclaredCeilingOnEveryAxis() {
        Player dying = player(DYING);
        nearby(dying);

        listeners.onDeath(death(dying));

        // The ceiling is the ONLY thing bounding which observers the walk can ever see: an ability's own
        // %distance% condition can narrow the scan but never widen it, so a hard-coded literal here (or a
        // transposed axis) silently caps every authored range at whatever number crept in.
        double ceiling = TriggerDispatch.PROXIMITY_RADIUS;
        verify(dying).getNearbyEntities(ceiling, ceiling, ceiling);
    }

    @Test
    void aDyingMobObservesNobody() {
        Zombie dead = mock(Zombie.class);
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(dead);

        listeners.onDeath(event);

        verify(executor, never()).run(any(), any(), any(), any(), any(), any());
    }

    private void wearingProximity(UUID player) {
        when(worn.get(player)).thenReturn(WornStates.worn().byTrigger(proximity, 0).build());
    }

    private void nearby(Player dying, Entity... found) {
        when(dying.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(found));
    }

    private EntityDeathEvent death(Player dying) {
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(dying);
        return event;
    }

    private Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getLocation()).thenReturn(new Location(world, 0.0, 64.0, 0.0));
        return player;
    }
}
