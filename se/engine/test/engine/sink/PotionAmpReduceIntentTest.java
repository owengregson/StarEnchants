package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.RecordingSchedulerBackend;

/**
 * {@code POTION_AMP_REDUCE} against a victim that behaves like the server does: removing HEALTH_BOOST clamps
 * current health to the smaller max (measured on 1.8.8's {@code MobEffectHealthBoost} and unchanged since).
 * That clamp is the whole trap — the obvious remove-then-add costs the holder EVERY bonus heart the source
 * granted, which is exactly the full-strip outcome this primitive exists to avoid, and no assertion about the
 * amplifier alone would notice.
 */
class PotionAmpReduceIntentTest {

    private static final int BOOST_ID = 1;
    private static final double BASE_MAX = 20.0;
    private static final double PER_LEVEL = 4.0; // vanilla HEALTH_BOOST grants 4 HP per amplifier step
    private static final int SOURCE_TICKS = 1_000_000; // what the worn-buff driver applies

    private final Map<PotionEffectType, PotionEffect> live = new LinkedHashMap<>();

    private RecordingSchedulerBackend scheduler;
    private RecordingSink sink;
    private PotionEffectType boost;
    private LivingEntity victim;
    private double health;

    @BeforeEach
    void setUp() {
        scheduler = new RecordingSchedulerBackend();
        Scheduling.install(scheduler);
        sink = new RecordingSink(Envs.sink().nowTicks(() -> 0L).build());
        boost = mock(PotionEffectType.class);
        when(boost.getName()).thenReturn("HEALTH_BOOST");
        sink.potions.put(BOOST_ID, boost);
        sink.effectiveMaxHealth = entity -> BASE_MAX + PER_LEVEL * boostLevels();

        victim = mock(LivingEntity.class);
        when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        when(victim.isValid()).thenReturn(true);
        when(victim.getActivePotionEffects()).thenAnswer(call -> List.copyOf(live.values()));
        when(victim.addPotionEffect(any(PotionEffect.class))).thenAnswer(call -> {
            PotionEffect applied = call.getArgument(0);
            live.put(applied.getType(), applied);
            return true;
        });
        doAnswer(call -> {
            live.remove((PotionEffectType) call.getArgument(0));
            health = Math.min(health, sink.effectiveMaxHealth.applyAsDouble(victim)); // vanilla's own clamp
            return null;
        }).when(victim).removePotionEffect(any(PotionEffectType.class));
        when(victim.getHealth()).thenAnswer(call -> health);
        doAnswer(call -> {
            health = call.getArgument(0);
            return null;
        }).when(victim).setHealth(anyDouble());
    }

    @AfterEach
    void clean() {
        ReducedPotions.clearAll();
    }

    @Test
    void sappingTwoLevelsTakesTwoLevelsOfHeartsAndNotTheWholeBuff() {
        wearing(5); // HEALTH_BOOST VI: 24 bonus HP, at full health

        reduce(2, 48);

        assertEquals(3, live.get(boost).getAmplifier(), "VI less two levels is IV");
        assertEquals(BASE_MAX + 16.0, sink.effectiveMaxHealth.applyAsDouble(victim));
        assertEquals(36.0, health, "8 HP gone — the two levels sapped, not the 24 a strip would have taken");
        assertEquals(SOURCE_TICKS, live.get(boost).getDuration(), "the source's own remaining duration rides along");
    }

    @Test
    void aRefreshInsideTheWindowIsPulledBackToTheSameCeiling() {
        // The "capped at source − N" half: the worn-buff driver re-asserting HEALTH_BOOST VI must not undo it.
        wearing(5);
        reduce(2, 48);

        live.put(boost, new PotionEffect(boost, SOURCE_TICKS, 5)); // the driver refreshes at full strength
        assertEquals(1, scheduler.repeating.size());
        scheduler.repeating.get(0).task.run();

        assertEquals(3, live.get(boost).getAmplifier(), "back to the ceiling the window fixed at the arm");
    }

    @Test
    void theWindowClosingGivesTheBuffBackButNeverTheHearts() {
        wearing(5);
        reduce(2, 48);

        scheduler.runDelayed();

        assertEquals(5, live.get(boost).getAmplifier(), "the source is restored");
        assertEquals(BASE_MAX + 24.0, sink.effectiveMaxHealth.applyAsDouble(victim));
        assertEquals(36.0, health, "downward-only: vanilla returns the max, never the health the clamp took");
        assertTrue(scheduler.repeating.get(0).isCancelled(), "the re-assert stops with the window");
    }

    @Test
    void aSapPastTheLastLevelDeniesTheEffectForTheWindowThenRestoresIt() {
        wearing(0); // HEALTH_BOOST I — two levels off it leaves nothing at all

        reduce(2, 48);
        assertNull(live.get(boost), "denied outright rather than applied at a negative amplifier");
        assertEquals(BASE_MAX, health);

        scheduler.runDelayed();
        assertEquals(0, live.get(boost).getAmplifier(), "the denied source comes back at its own level");
        assertEquals(SOURCE_TICKS - 48, live.get(boost).getDuration(), "less the window it sat out");
    }

    @Test
    void aVictimWithoutTheEffectArmsNothingAtAll() {
        // The ceiling is measured from the live source; with no source there is nothing to cap a later
        // application against, so the window would be arbitrary. No task, no claim, no restore.
        reduce(2, 48);

        assertTrue(scheduler.repeating.isEmpty());
        assertTrue(scheduler.delayed.isEmpty());
    }

    @Test
    void aSecondProcInsideTheWindowDoesNotCompoundTheDrain() {
        // Two attackers on one victim: a second subtraction would take multiples of the authored hearts off a
        // max-health pool that only gets one restore each. The incumbent window holds.
        wearing(5);
        reduce(2, 48);

        reduce(2, 48);

        assertEquals(3, live.get(boost).getAmplifier(), "still one sap deep, not two");
        assertEquals(36.0, health);
        assertEquals(1, scheduler.repeating.size(), "and no second re-assert running against the same type");
    }

    private void reduce(int amount, int durationTicks) {
        sink.potionAmpReduce(victim, BOOST_ID, amount, durationTicks);
        sink.flush();
    }

    /** Put the victim on HEALTH_BOOST at {@code amplifier}, at full health for the max that grants. */
    private void wearing(int amplifier) {
        live.put(boost, new PotionEffect(boost, SOURCE_TICKS, amplifier));
        health = sink.effectiveMaxHealth.applyAsDouble(victim);
    }

    private int boostLevels() {
        PotionEffect effect = live.get(boost);
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }
}
