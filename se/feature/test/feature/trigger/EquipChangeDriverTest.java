package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import compile.model.Snapshot;
import engine.pipeline.Activation;
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
import org.bukkit.entity.Player;
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
 * The EQUIP_CHANGE diff. Two contracts nothing else can prove: the LEAVING piece's ability still fires once,
 * even though it has already left the worn state the walk normally reads from; and a refresh that changed no
 * EQUIP_CHANGE ability — a chest close, a Q-drop, a durability tick, all of which re-resolve worn state — fires
 * nothing. Getting either wrong is silent: one drops half the trigger, the other spams it.
 */
class EquipChangeDriverTest {

    private static final UUID ACTOR = UUID.randomUUID();

    private final TriggerRegistry triggers = BuiltinTriggers.registry();
    private final int equipChange = triggers.idOf("EQUIP_CHANGE").orElseThrow();

    private AbilityExecutor executor;
    private WornStateStore worn;
    private EquipChangeDriver driver;
    private Player player;

    @BeforeEach
    void setUp() {
        Scheduling.install(new SyncSchedulerBackend());
        RuntimeHandles handles = new RuntimeHandles(new RegistryResolvers());
        SinkEnv env = Envs.sink().build();
        SinkFactory sinkFactory = mock(SinkFactory.class);
        when(sinkFactory.create(any())).thenReturn(new ModernDispatchSink(handles, env));
        Snapshot snapshot = Snapshots.snapshot()
                .abilities(Abilities.ability().id(0).build(), Abilities.ability().id(1).build())
                .stableKeys("enchants/laststand/1", "enchants/vigil/1")
                .build();
        ContentHolder content = mock(ContentHolder.class);
        when(content.snapshot()).thenReturn(snapshot);
        executor = mock(AbilityExecutor.class);
        worn = mock(WornStateStore.class);
        TriggerDispatch dispatch = new TriggerDispatch(executor, sinkFactory, mock(ActorProbe.class), content,
                worn, triggers, p -> Optional.empty(), env, mock(Hands.class), mock(DropControl.class));
        driver = new EquipChangeDriver(dispatch, content, worn);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ACTOR);
        Location at = mock(Location.class);
        when(at.getWorld()).thenReturn(mock(World.class));
        when(player.getLocation()).thenReturn(at);
    }

    /** Point the store at the abilities the player is now wearing on EQUIP_CHANGE. */
    private void wearing(int... abilityIds) {
        when(worn.get(ACTOR)).thenReturn(WornStates.worn().byTrigger(equipChange, abilityIds).build());
    }

    @Test
    void puttingAPieceOnFiresEquipForItsAbilityAlone() {
        wearing(0);
        driver.refresh(player);

        assertEquals(List.of(EquipChangeDriver.EQUIP), directions(1));
        assertArrayEquals(new int[] {0}, candidates(1).get(0));
    }

    @Test
    void takingAPieceOffStillFiresItsAbilityOnceAfterItLeftTheWornState() {
        wearing(0);
        driver.refresh(player);
        wearing();
        driver.refresh(player);

        assertEquals(List.of(EquipChangeDriver.EQUIP, EquipChangeDriver.UNEQUIP), directions(2));
        assertArrayEquals(new int[] {0}, candidates(2).get(1),
                "the leaving ability's dense id is re-resolved from the snapshot by stable key");
    }

    @Test
    void aRefreshThatChangedNoAbilityFiresNothing() {
        wearing(0);
        driver.refresh(player);
        driver.refresh(player); // the chest-close / Q-drop / durability-tick case: same gear, fresh resolve

        verify(executor, times(1)).run(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aSwapShedsTheOldPieceBeforeTheNewOneRuns() {
        wearing(0);
        driver.refresh(player);
        wearing(1);
        driver.refresh(player);

        assertEquals(List.of(EquipChangeDriver.EQUIP, EquipChangeDriver.UNEQUIP, EquipChangeDriver.EQUIP),
                directions(3), "UNEQUIP precedes EQUIP so a paired arm/disarm cannot land inverted");
        assertArrayEquals(new int[] {0}, candidates(3).get(1));
        assertArrayEquals(new int[] {1}, candidates(3).get(2));
    }

    @Test
    void aStaleWornStateIsSkippedRatherThanFiredAgainstTheWrongGeneration() {
        when(worn.get(ACTOR)).thenReturn(WornStates.worn().gen(7).byTrigger(equipChange, 0).build());

        driver.refresh(player);

        verify(executor, never()).run(any(), any(), any(), any(), any(), any());
    }

    private List<String> directions(int expected) {
        ArgumentCaptor<ActivationContext> contexts = ArgumentCaptor.forClass(ActivationContext.class);
        verify(executor, times(expected)).run(any(), any(), any(), contexts.capture(), any(), any());
        return contexts.getAllValues().stream().map(ActivationContext::equipChange).toList();
    }

    private List<int[]> candidates(int expected) {
        ArgumentCaptor<int[]> ids = ArgumentCaptor.forClass(int[].class);
        ArgumentCaptor<Activation> activations = ArgumentCaptor.forClass(Activation.class);
        verify(executor, times(expected)).run(any(), ids.capture(), activations.capture(), any(), any(), any());
        for (Activation activation : activations.getAllValues()) {
            assertEquals(equipChange, activation.triggerId());
        }
        return ids.getAllValues();
    }
}
