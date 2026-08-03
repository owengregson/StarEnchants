package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
import feature.compat.ModernProjectiles;
import item.worn.WornStateStore;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.entity.ProjectileHitEvent;
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
 * PROJECTILE_LAND's two routing rules, both silent when broken: the activation has to anchor at the LANDING
 * point (anchor it on the shooter and every landing-AoE ability detonates under the archer instead), and an
 * entity hit must not reach it at all (BOW already dispatched that hit, so a landing pass would double-proc
 * the shot).
 */
class ProjectileLandListenerTest {

    private static final UUID SHOOTER = UUID.randomUUID();

    private final TriggerRegistry triggers = BuiltinTriggers.registry();
    private final int projectileLand = triggers.idOf("PROJECTILE_LAND").orElseThrow();

    private AbilityExecutor executor;
    private ProjectileLandListener listener;
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
                .stableKeys("enchants/webfield/1")
                .build();
        ContentHolder content = mock(ContentHolder.class);
        when(content.snapshot()).thenReturn(snapshot);
        executor = mock(AbilityExecutor.class);
        WornStateStore worn = mock(WornStateStore.class);
        when(worn.get(SHOOTER)).thenReturn(WornStates.worn().byTrigger(projectileLand, 0).build());
        TriggerDispatch dispatch = new TriggerDispatch(executor, sinkFactory, mock(ActorProbe.class), content,
                worn, triggers, p -> Optional.empty(), env, mock(Hands.class), mock(DropControl.class));
        listener = new ProjectileLandListener(dispatch, new ModernProjectiles());
        world = mock(World.class);
    }

    @Test
    void aBlockLandingAnchorsTheActivationOnTheBlockNotTheShooter() {
        Block hit = mock(Block.class);
        when(hit.getLocation()).thenReturn(new Location(world, 10.0, 64.0, -3.0));

        listener.onLand(hit(shooter(), hit, null));

        ArgumentCaptor<ActivationContext> contexts = ArgumentCaptor.forClass(ActivationContext.class);
        verify(executor, times(1)).run(any(), any(), any(), contexts.capture(), any(), any());
        // Block CENTRE: an @Aoe anchored on the corner reaches a block less on one side.
        assertEquals(new Location(world, 10.5, 64.5, -2.5), contexts.getValue().location());
    }

    @Test
    void anEntityHitNeverReachesTheTriggerSoBowKeepsIt() {
        listener.onLand(hit(shooter(), null, mock(Skeleton.class)));

        verify(executor, never()).run(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aNonPlayerShooterDispatchesNothing() {
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(mock(Skeleton.class));
        ProjectileHitEvent event = mock(ProjectileHitEvent.class);
        when(event.getEntity()).thenReturn(arrow);

        listener.onLand(event);

        verify(executor, never()).run(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aFlightExpiryWithNoHitBlockFallsBackToTheProjectilesOwnRestingPoint() {
        // A spent arrow that stops mid-air (or in a passable block) reports no hit block; the shot still landed
        // somewhere, and that somewhere is where the AoE belongs.
        listener.onLand(hit(shooter(), null, null));

        ArgumentCaptor<ActivationContext> contexts = ArgumentCaptor.forClass(ActivationContext.class);
        verify(executor, times(1)).run(any(), any(), any(), contexts.capture(), any(), any());
        assertEquals(new Location(world, 1.0, 70.0, 2.0), contexts.getValue().location());
    }

    private Player shooter() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(SHOOTER);
        when(player.getLocation()).thenReturn(new Location(world, 0.0, 64.0, 0.0));
        return player;
    }

    private ProjectileHitEvent hit(Player shooter, Block hitBlock, org.bukkit.entity.Entity hitEntity) {
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(shooter);
        when(arrow.getLocation()).thenReturn(new Location(world, 1.0, 70.0, 2.0));
        ProjectileHitEvent event = mock(ProjectileHitEvent.class);
        when(event.getEntity()).thenReturn(arrow);
        when(event.getHitBlock()).thenReturn(hitBlock);
        when(event.getHitEntity()).thenReturn(hitEntity);
        return event;
    }
}
