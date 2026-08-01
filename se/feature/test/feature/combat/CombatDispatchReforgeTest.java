package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import engine.run.AbilityExecutor;
import engine.run.ActorProbe;
import engine.sink.ModernDispatchSink;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import feature.compat.ModernProjectiles;
import item.worn.WornStateStore;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.RecordingSchedulerBackend;
import testfx.Snapshots;

/**
 * The ADR-0071 reforge armed-window consults at the {@link CombatDispatch} seam (§2.5): a live Quickening
 * window taxes the melee commit through the fold's self-malus, an Unhanding window prices the hit −20% on a
 * player victim only, a Supernova core joins its bank as an un-scaled rider — all melee-only, all deferred to
 * MONITOR via {@link ReforgeStrikeRelay}, and all vanishing whenever the pvp/pve/friendly context gate closes.
 */
class CombatDispatchReforgeTest {

    private SinkEnv env;
    private ModernDispatchSink sink;

    private final UUID attackerId = UUID.randomUUID();
    private final UUID victimId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Scheduling.install(new RecordingSchedulerBackend());
    }

    @AfterEach
    void clearRelays() {
        ReforgeStrikeRelay.clear();
        ReHitGuard.clearSkipped();
        CombatDispatch.friendlyFire(null); // restore the default "never friendly"
    }

    /** A dispatch over a fresh env + sink, with the given live caps (attack-scale / pvp / pve). */
    private CombatDispatch dispatch(CombatDispatch.Caps caps) {
        RuntimeHandles handles = new RuntimeHandles(new RegistryResolvers());
        env = Envs.sink().build();
        sink = new ModernDispatchSink(handles, env);
        SinkFactory sinkFactory = mock(SinkFactory.class);
        when(sinkFactory.create(any())).thenReturn(sink);
        ContentHolder content = mock(ContentHolder.class);
        when(content.snapshot()).thenReturn(Snapshots.snapshot().build());
        return new CombatDispatch(mock(AbilityExecutor.class), sinkFactory, mock(ActorProbe.class), content,
                mock(WornStateStore.class), 0, 1, -1, -1, p -> Optional.empty(), env, caps, new ModernProjectiles());
    }

    private Player attacker() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(attackerId);
        when(p.isValid()).thenReturn(true);
        when(p.getLocation()).thenReturn(mock(Location.class));
        return p;
    }

    private Player victimPlayer() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(victimId);
        when(p.isValid()).thenReturn(true);
        when(p.getNoDamageTicks()).thenReturn(0);
        when(p.getMaximumNoDamageTicks()).thenReturn(20);
        when(p.isDead()).thenReturn(false);
        when(p.getLocation()).thenReturn(mock(Location.class));
        return p;
    }

    private LivingEntity victimMob() {
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.getUniqueId()).thenReturn(victimId);
        when(mob.getNoDamageTicks()).thenReturn(0);
        when(mob.getMaximumNoDamageTicks()).thenReturn(20);
        when(mob.getLocation()).thenReturn(mock(Location.class));
        return mob;
    }

    private EntityDamageByEntityEvent hit(org.bukkit.entity.Entity damager, LivingEntity victim, double damage,
                                          double[] committed) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(damager);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamage()).thenReturn(damage);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        if (committed != null) {
            doAnswer(inv -> {
                committed[0] = inv.getArgument(0);
                return null;
            }).when(event).setDamage(anyDouble());
        }
        return event;
    }

    @Test
    void tempoWindowTaxesMeleeCommit() {
        CombatDispatch dispatch = dispatch(CombatDispatch.Caps.unlimited());
        env.stores().hitTempo().arm(attackerId, 0L, 100, 0, 1.0 / 3.0);
        double[] committed = {Double.NaN};

        dispatch.onDamage(hit(attacker(), victimPlayer(), 9.0, committed));

        assertEquals(3.0, committed[0], 1e-9, "9.0 base × 1/3 self-malus");
    }

    @Test
    void tempoIgnoresProjectileHits() {
        CombatDispatch dispatch = dispatch(CombatDispatch.Caps.unlimited());
        env.stores().hitTempo().arm(attackerId, 0L, 100, 0, 1.0 / 3.0);
        Player shooter = attacker();
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(shooter);
        when(arrow.getLocation()).thenReturn(mock(Location.class));
        double[] committed = {Double.NaN};
        EntityDamageByEntityEvent event = hit(arrow, victimPlayer(), 9.0, committed);

        dispatch.onDamage(event);

        assertEquals(9.0, committed[0], 1e-9, "a bow shot is not a melee hit — no tempo tax");
        assertNull(ReforgeStrikeRelay.consume(event, env.stores()), "no relay mark on a projectile hit");
    }

    @Test
    void unhandingFeltTwentyPercentAtScaleFive() {
        CombatDispatch dispatch = dispatch(new CombatDispatch.Caps(() -> -1.0, () -> -1.0, () -> 5.0,
                () -> true, () -> true));
        env.stores().disarmWindows().arm(attackerId, 0L, 80, 0.20);
        double[] committed = {Double.NaN};

        dispatch.onDamage(hit(attacker(), victimPlayer(), 7.0, committed));

        assertEquals(7.0 * 0.8, committed[0], 1e-9, "the −20% survives attack-scale 5.0 (a post-fold factor)");
    }

    @Test
    void unhandingFeltTwentyPercentAtScaleOne() {
        CombatDispatch dispatch = dispatch(CombatDispatch.Caps.unlimited());
        env.stores().disarmWindows().arm(attackerId, 0L, 80, 0.20);
        double[] committed = {Double.NaN};

        dispatch.onDamage(hit(attacker(), victimPlayer(), 7.0, committed));

        assertEquals(7.0 * 0.8, committed[0], 1e-9, "the same felt −20% at scale 1.0");
    }

    @Test
    void unhandingSkipsMobVictims() {
        CombatDispatch dispatch = dispatch(CombatDispatch.Caps.unlimited());
        env.stores().disarmWindows().arm(attackerId, 0L, 80, 0.20);
        double[] committed = {Double.NaN};
        EntityDamageByEntityEvent event = hit(attacker(), victimMob(), 7.0, committed);

        dispatch.onDamage(event);

        assertEquals(7.0, committed[0], 1e-9, "a mob has no hotbar — the window waits, no tax");
        assertNull(ReforgeStrikeRelay.consume(event, env.stores()), "no relay mark on a mob victim");
    }

    @Test
    void batterySkipsMobVictims() {
        CombatDispatch dispatch = dispatch(CombatDispatch.Caps.unlimited());
        env.stores().battery().arm(attackerId, 1.0, 3);
        env.stores().battery().bank(attackerId, 3.0);
        double[] committed = {Double.NaN};
        EntityDamageByEntityEvent event = hit(attacker(), victimMob(), 7.0, committed);

        dispatch.onDamage(event);

        assertEquals(7.0, committed[0], 1e-9, "a stray mob swat must not dump the bank — the core waits");
        assertNull(ReforgeStrikeRelay.consume(event, env.stores()), "no relay mark on a mob victim");
        assertEquals(3.0, env.stores().battery().peek(attackerId), 1e-9, "the charge is untouched");
    }

    @Test
    void batteryPeekJoinsAsUnscaledRider() {
        CombatDispatch dispatch = dispatch(new CombatDispatch.Caps(() -> -1.0, () -> -1.0, () -> 5.0,
                () -> true, () -> true));
        env.stores().battery().arm(attackerId, 1.0, 3);
        env.stores().battery().bank(attackerId, 3.0); // banked 3.0 health-space
        double[] committed = {Double.NaN};

        dispatch.onDamage(hit(attacker(), victimPlayer(), 7.0, committed));

        assertEquals(7.0 + 3.0, committed[0], 1e-9, "the discharge rides addEffectiveDamage — never attack-scaled");
        assertEquals(3.0, env.stores().battery().peek(attackerId), 1e-9, "the consult only peeks — spend is at MONITOR");
    }

    @Test
    void batteryZeroBankAddsNothingButMarks() {
        CombatDispatch dispatch = dispatch(CombatDispatch.Caps.unlimited());
        env.stores().battery().arm(attackerId, 0.2, 3); // armed, nothing banked
        double[] committed = {Double.NaN};
        EntityDamageByEntityEvent event = hit(attacker(), victimPlayer(), 7.0, committed);

        dispatch.onDamage(event);

        assertEquals(7.0, committed[0], 1e-9, "an empty core adds a 0.0 rider");
        ReforgeStrikeRelay.Pending marked = ReforgeStrikeRelay.consume(event, env.stores());
        assertNotNull(marked, "an armed empty core still marks — the next hit spends it regardless");
        assertEquals(true, marked.batterySpend());
    }

    @Test
    void noWindowsNoMark() {
        CombatDispatch dispatch = dispatch(CombatDispatch.Caps.unlimited());
        double[] committed = {Double.NaN};
        EntityDamageByEntityEvent event = hit(attacker(), victimPlayer(), 7.0, committed);

        dispatch.onDamage(event);

        assertEquals(7.0, committed[0], 1e-9, "a clean hit is untouched");
        assertNull(ReforgeStrikeRelay.consume(event, env.stores()), "no window armed → no mark");
    }

    @Test
    void pvpContextDisabledSkipsConsults() {
        CombatDispatch dispatch = dispatch(new CombatDispatch.Caps(() -> -1.0, () -> -1.0, () -> 1.0,
                () -> false, () -> true)); // pvp OFF
        env.stores().hitTempo().arm(attackerId, 0L, 100, 0, 1.0 / 3.0);
        env.stores().disarmWindows().arm(attackerId, 0L, 80, 0.20);
        env.stores().battery().arm(attackerId, 1.0, 3);
        env.stores().battery().bank(attackerId, 3.0);
        double[] committed = {Double.NaN};
        EntityDamageByEntityEvent event = hit(attacker(), victimPlayer(), 7.0, committed);

        dispatch.onDamage(event);

        assertEquals(7.0, committed[0], 1e-9, "pvp off closes the attacker branch — no reforge consults run");
        assertNull(ReforgeStrikeRelay.consume(event, env.stores()), "no relay mark in a disabled context");
    }

    @Test
    void friendlyHitSkipsConsults() {
        CombatDispatch dispatch = dispatch(CombatDispatch.Caps.unlimited());
        CombatDispatch.friendlyFire((a, v) -> true);
        env.stores().hitTempo().arm(attackerId, 0L, 100, 0, 1.0 / 3.0);
        env.stores().disarmWindows().arm(attackerId, 0L, 80, 0.20);
        env.stores().battery().arm(attackerId, 1.0, 3);
        env.stores().battery().bank(attackerId, 3.0);
        double[] committed = {Double.NaN};
        EntityDamageByEntityEvent event = hit(attacker(), victimPlayer(), 7.0, committed);

        dispatch.onDamage(event);

        assertEquals(7.0, committed[0], 1e-9, "friendly fire skips ALL SE combat — reforge consults included");
        assertNull(ReforgeStrikeRelay.consume(event, env.stores()), "no relay mark between friendlies");
    }
}
