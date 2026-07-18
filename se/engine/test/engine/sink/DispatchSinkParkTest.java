package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.SyncSchedulerBackend;

/**
 * The sink park boundary (ADR-0069): a deferred/periodic engine hurt on a player under an active combo banks in
 * the {@link DotParkLedger} instead of applying, non-combo damage applies exactly as before, and the zero-WAIT
 * same-hit rider still folds (never parks). {@link SyncSchedulerBackend} runs the deferred op inline on flush.
 */
class DispatchSinkParkTest {

    private RuntimeHandles handles;

    @BeforeEach
    void setUp() {
        handles = new RuntimeHandles(new RegistryResolvers());
        Scheduling.install(new SyncSchedulerBackend());
    }

    private static Player player() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        return p;
    }

    private static LivingEntity attacker() {
        LivingEntity e = mock(LivingEntity.class);
        when(e.getUniqueId()).thenReturn(UUID.randomUUID());
        return e;
    }

    private static double drainSum(SinkEnv env, Player victim, LivingEntity attacker) {
        return env.dotPark().drainFor(victim.getUniqueId(), attacker.getUniqueId())
                .stream().mapToDouble(DotParkLedger.Bucket::amount).sum();
    }

    @Test
    void deferredDamageOnComboHeldPlayerParksInsteadOfHurting() {
        SinkEnv env = Envs.sink().build();
        ModernDispatchSink sink = new ModernDispatchSink(handles, env);
        Player victim = player();
        LivingEntity attacker = attacker();
        env.dotPark().comboStarted(victim.getUniqueId(), attacker.getUniqueId(), 0L);

        sink.delay(5); // a WAIT-tier bleed, not a same-hit rider
        sink.damage(victim, 4.0, attacker);
        sink.flush();

        verify(victim, never()).damage(anyDouble(), any());
        assertEquals(4.0, drainSum(env, victim, attacker), 1e-9, "the would-be hurt banks under this attacker");
    }

    @Test
    void deferredDamageWithoutComboHurtsExactlyAsBefore() {
        SinkEnv env = Envs.sink().build();
        ModernDispatchSink sink = new ModernDispatchSink(handles, env);
        Player victim = player();
        LivingEntity attacker = attacker();
        boolean[] framed = {false};
        doAnswer(inv -> {
            framed[0] = EngineDamage.active();
            return null;
        }).when(victim).damage(anyDouble(), any());

        sink.delay(5);
        sink.damage(victim, 4.0, attacker); // no active combo → applies normally
        sink.flush();

        verify(victim).damage(4.0, attacker);
        assertTrue(framed[0], "the un-parked hurt still runs inside the engine-damage frame");
        assertFalse(env.dotPark().hasParked(victim.getUniqueId()));
    }

    @Test
    void percentOfMaxAndLightningParkRows() {
        // Site #2 — percent-of-max deferred damage parks the resolved half-heart amount.
        SinkEnv env = Envs.sink().build();
        ModernDispatchSink sink = new ModernDispatchSink(handles, env);
        Player victim = player();
        LivingEntity attacker = attacker();
        AttributeInstance maxHp = mock(AttributeInstance.class);
        when(victim.getAttribute(any(Attribute.class))).thenReturn(maxHp);
        when(maxHp.getValue()).thenReturn(20.0);
        env.dotPark().comboStarted(victim.getUniqueId(), attacker.getUniqueId(), 0L);

        sink.delay(5);
        sink.damagePercentOfMax(victim, 50.0, attacker); // 50% of 20 = 10.0
        sink.flush();

        verify(victim, never()).damage(anyDouble(), any());
        assertEquals(10.0, drainSum(env, victim, attacker), 1e-9);

        // Site #4 — the lightning bolt payload parks (the visual still fires; a mock world skips the strike).
        SinkEnv env2 = Envs.sink().build();
        ModernDispatchSink sink2 = new ModernDispatchSink(handles, env2);
        Player victim2 = player();
        LivingEntity attacker2 = attacker();
        env2.dotPark().comboStarted(victim2.getUniqueId(), attacker2.getUniqueId(), 0L);

        sink2.lightningAndDamage(victim2, 6.0, attacker2);
        sink2.flush();

        verify(victim2, never()).damage(anyDouble(), any());
        assertEquals(6.0, drainSum(env2, victim2, attacker2), 1e-9);
    }

    @Test
    void zeroWaitEventEntityRiderStillFoldsNeverParks() {
        SinkEnv env = Envs.sink().build();
        ModernDispatchSink sink = new ModernDispatchSink(handles, env);
        Player victim = player();
        LivingEntity attacker = attacker();
        env.dotPark().comboStarted(victim.getUniqueId(), attacker.getUniqueId(), 0L);

        sink.eventEntity(victim); // this hit's own victim
        sink.damage(victim, 2.0, attacker); // zero-WAIT rider → folds even under an active combo, never parks

        assertEquals(2.0, sink.fold().effectiveDamage(), 1e-9);
        assertFalse(env.dotPark().hasParked(victim.getUniqueId()), "a same-hit rider never touches the park ledger");
    }

    @Test
    void nonPlayerTargetNeverParks() {
        SinkEnv env = Envs.sink().build();
        ModernDispatchSink sink = new ModernDispatchSink(handles, env);
        LivingEntity mob = mock(LivingEntity.class);
        UUID mobId = UUID.randomUUID();
        when(mob.getUniqueId()).thenReturn(mobId);
        LivingEntity attacker = attacker();
        env.dotPark().comboStarted(mobId, attacker.getUniqueId(), 0L); // combo state keyed to the mob's id

        sink.delay(5);
        sink.damage(mob, 3.0, attacker);
        sink.flush();

        verify(mob).damage(3.0, attacker);
        assertFalse(env.dotPark().hasParked(mobId), "a non-player target applies normally, never parks");
    }
}
