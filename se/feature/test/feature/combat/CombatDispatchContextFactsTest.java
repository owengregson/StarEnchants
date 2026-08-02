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
import feature.compat.Projectiles;
import item.worn.WornStateStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
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

    private Player victim() {
        Player victim = player(victimId);
        when(victim.getNoDamageTicks()).thenReturn(0);
        when(victim.getMaximumNoDamageTicks()).thenReturn(20);
        return victim;
    }

    private EntityDamageByEntityEvent hit(org.bukkit.entity.Entity damager, Player victim) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(damager);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamage()).thenReturn(9.0);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        return event;
    }

    /** Both walks' contexts, attack first — the order {@code onDamage} runs them in. */
    private List<ActivationContext> capture() {
        ArgumentCaptor<ActivationContext> contexts = ArgumentCaptor.forClass(ActivationContext.class);
        verify(executor, times(2)).run(any(), any(), any(), contexts.capture(), any(), any());
        return contexts.getAllValues();
    }

    @Test
    void onlyTheDefenceContextCarriesThePendingVanillaFinalDamage() {
        CombatDispatch dispatch = dispatch();
        EntityDamageByEntityEvent event = hit(player(attackerId), victim());
        when(event.getFinalDamage()).thenReturn(6.5); // what the server prices after armour/protection

        dispatch.onDamage(event);

        List<ActivationContext> contexts = capture();
        assertEquals(6.5, contexts.get(1).vanillaFinalDamage(),
                "the defender's walk prices %posthit.health% off the server's own figure");
        assertTrue(Double.isNaN(contexts.get(0).vanillaFinalDamage()),
                "the attacker takes no damage here — a value would make %posthit.health% read the wrong side");
    }

    @Test
    void bothSidesSeeTheSameProjectileGeometryMeasuredAgainstTheStruckEntity() {
        CombatDispatch dispatch = dispatch();
        Player shooter = player(attackerId);
        Player victim = victim();
        when(victim.getLocation()).thenReturn(new Location(null, 0.0, 64.0, 0.0));
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(shooter);
        when(arrow.getLocation()).thenReturn(new Location(null, 0.0, 65.6, 0.0));

        dispatch.onDamage(hit(arrow, victim));

        List<ActivationContext> contexts = capture();
        for (ActivationContext context : contexts) {
            // Measured against the VICTIM on both sides: the defense context's `victim` is the shooter, so a
            // populator-side subtraction would price the arrow against the person who fired it.
            assertEquals(1.6, context.impactHeight(), 1e-9);
            assertEquals(Projectiles.ARROW, context.projectileKind());
        }
    }

    @Test
    void aMeleeHitCarriesNoProjectileGeometry() {
        CombatDispatch dispatch = dispatch();

        dispatch.onDamage(hit(player(attackerId), victim()));

        for (ActivationContext context : capture()) {
            assertEquals(0.0, context.impactHeight());
            assertEquals("", context.projectileKind(), "a sword swing must not satisfy a %projectilekind% gate");
        }
    }
}
