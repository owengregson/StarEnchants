package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import compile.model.Snapshot;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.ActorProbe;
import engine.sink.ModernDispatchSink;
import engine.sink.Sink;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.compat.DropControl;
import feature.compat.Hands;
import item.worn.WornStateStore;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Abilities;
import testfx.Envs;
import testfx.Snapshots;
import testfx.SyncSchedulerBackend;
import testfx.WornStates;

/**
 * {@code EXP_MULTIPLY} on MINE. Block-sourced XP has no other expression on the surface —
 * {@code PlayerExpChangeEvent} names no source, so the EXP_GAIN path cannot tell a mined block from a bottle,
 * a furnace or a trade — and before this read-back the effect compiled on MINE and did nothing at all, which
 * is the failure this pins: silent, and indistinguishable from a factor of 1.
 */
class MineExpMultiplierTest {

    private final TriggerRegistry triggers = BuiltinTriggers.registry();

    private static final UUID MINER = UUID.randomUUID();

    private AbilityExecutor executor;
    private TriggerDispatch dispatch;
    // Held as a FIELD, not an inline argument: Location keeps only a WeakReference to its world, so a mock
    // reachable from nowhere else is collectible mid-test and getWorld() then throws "World unloaded".
    private World world;

    @BeforeEach
    void setUp() {
        Scheduling.install(new SyncSchedulerBackend());
        SinkEnv env = Envs.sink().build();
        SinkFactory sinkFactory = mock(SinkFactory.class);
        when(sinkFactory.create(any()))
                .thenReturn(new ModernDispatchSink(new RuntimeHandles(new RegistryResolvers()), env));
        Snapshot snapshot = Snapshots.snapshot()
                .abilities(Abilities.ability().id(0).build())
                .stableKeys("enchants/experience/1")
                .build();
        ContentHolder content = mock(ContentHolder.class);
        when(content.snapshot()).thenReturn(snapshot);
        executor = mock(AbilityExecutor.class);
        WornStateStore worn = mock(WornStateStore.class);
        when(worn.get(MINER)).thenReturn(
                WornStates.worn().byTrigger(triggers.idOf("MINE").orElseThrow(), 0).build());
        dispatch = new TriggerDispatch(executor, sinkFactory, mock(ActorProbe.class), content,
                worn, triggers, p -> Optional.empty(), env, mock(Hands.class), mock(DropControl.class));
    }

    /** Stage an ability that asks for {@code factor}, break a block worth {@code exp}, return what it yields. */
    private int mined(int exp, double factor) {
        doAnswer(invocation -> {
            ((Sink) invocation.getArgument(4)).multiplyExp(factor);
            return null;
        }).when(executor).run(any(), any(), any(), any(), any(), any());

        world = mock(World.class);
        Location at = new Location(world, 1, 2, 3);
        Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(at);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(MINER);
        when(player.getLocation()).thenReturn(at);
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        event.setExpToDrop(exp);

        dispatch.fireMine(player, new ActivationContext(player, null, null, at, 0.0, block), event);
        return event.getExpToDrop();
    }

    /**
     * The measured ladder off a 7-XP block, which is also the truncation contract: 8.75 XP is 8 orbs, not 9.
     * Rounding instead would hand back 9/11/12/14/16 — the same enchant, one orb richer at four of five rungs.
     */
    @ParameterizedTest
    @CsvSource({"1.25, 8", "1.5, 10", "1.75, 12", "2.0, 14", "2.25, 15"})
    void theBlocksYieldScalesByTheAccumulatedFactor(double factor, int expected) {
        assertEquals(expected, mined(7, factor));
    }

    @Test
    void aZeroExperienceBlockStaysZero() {
        // Cobblestone drops no XP at any level; a multiplier must never invent some.
        assertEquals(0, mined(0, 2.25));
    }

    @Test
    void anUnaskedForActivationLeavesTheYieldAlone() {
        // The factor an empty walk leaves behind is 1.0, and the untouched path must not round-trip the value
        // through the scaler at all — every MINE break on every server pays for this branch.
        assertEquals(7, mined(7, 1.0));
    }
}
