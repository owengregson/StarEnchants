package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import feature.compat.ModernProjectiles;
import item.worn.WornStateStore;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Abilities;
import testfx.Envs;
import testfx.RecordingSchedulerBackend;
import testfx.Snapshots;
import testfx.SyncSchedulerBackend;
import testfx.WornStates;

/**
 * The event payload the two combat {@link ActivationContext}s carry. This seam is the sole source of the
 * hit-shaped facts ({@code %posthit.health%}), and a context field that never gets filled fails SILENTLY —
 * the fact reads its default and every ability gating on it simply stops firing. So the contract pinned here
 * is what the DEFENSE and ATTACK contexts actually receive, captured off the executor.
 */
class CombatDispatchContextFactsTest {

    private static final int ATTACK_TRIGGER = 0;
    private static final int DEFENSE_TRIGGER = 1;

    private final UUID attackerId = UUID.randomUUID();
    private final UUID victimId = UUID.randomUUID();

    private AbilityExecutor executor;
    private WornStateStore worn;

    @BeforeEach
    void setUp() {
        Scheduling.install(new RecordingSchedulerBackend());
    }

    @AfterEach
    void reset() {
        ReHitGuard.clearSkipped();
        Scheduling.install(new SyncSchedulerBackend());
    }

    /** A dispatch whose attacker AND victim both carry one ability on their side's trigger, so both walks run. */
    private CombatDispatch dispatch() {
        RuntimeHandles handles = new RuntimeHandles(new RegistryResolvers());
        SinkEnv env = Envs.sink().build();
        SinkFactory sinkFactory = mock(SinkFactory.class);
        when(sinkFactory.create(any())).thenReturn(new ModernDispatchSink(handles, env));
        Snapshot snapshot = Snapshots.snapshot()
                .abilities(Abilities.ability().id(0).build())
                .stableKeys("enchants/probe/1")
                .build();
        ContentHolder content = mock(ContentHolder.class);
        when(content.snapshot()).thenReturn(snapshot);
        executor = mock(AbilityExecutor.class);
        worn = mock(WornStateStore.class);
        when(worn.get(attackerId)).thenReturn(WornStates.worn().byTrigger(ATTACK_TRIGGER, 0).build());
        when(worn.get(victimId)).thenReturn(WornStates.worn().byTrigger(DEFENSE_TRIGGER, 0).build());
        return new CombatDispatch(executor, sinkFactory, mock(ActorProbe.class), content, worn,
                ATTACK_TRIGGER, DEFENSE_TRIGGER, -1, -1, p -> Optional.empty(), env,
                CombatDispatch.Caps.unlimited(), new ModernProjectiles());
    }

    private Player player(UUID id) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(id);
        when(p.getLocation()).thenReturn(mock(Location.class));
        return p;
    }

    @Test
    void onlyTheDefenceContextCarriesThePendingVanillaFinalDamage() {
        CombatDispatch dispatch = dispatch();
        Player attacker = player(attackerId);
        Player victim = player(victimId);
        when(victim.getNoDamageTicks()).thenReturn(0);
        when(victim.getMaximumNoDamageTicks()).thenReturn(20);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamage()).thenReturn(9.0);
        when(event.getFinalDamage()).thenReturn(6.5); // what the server prices after armour/protection
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);

        dispatch.onDamage(event);

        ArgumentCaptor<ActivationContext> contexts = ArgumentCaptor.forClass(ActivationContext.class);
        verify(executor, times(2)).run(any(), any(), any(), contexts.capture(), any(), any());
        ActivationContext attackCtx = contexts.getAllValues().get(0);
        ActivationContext defenseCtx = contexts.getAllValues().get(1);
        assertEquals(6.5, defenseCtx.vanillaFinalDamage(),
                "the defender's walk prices %posthit.health% off the server's own figure");
        assertTrue(Double.isNaN(attackCtx.vanillaFinalDamage()),
                "the attacker takes no damage here — a value would make %posthit.health% read the wrong side");
    }
}
