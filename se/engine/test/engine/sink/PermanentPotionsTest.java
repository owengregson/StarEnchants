package engine.sink;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

/**
 * The rule separating a debuff someone LANDED from one its holder carries by choice (ADR-0072) — the single
 * thing standing between a cleanse and a strip-then-reapply fight with the passive-potion driver.
 */
class PermanentPotionsTest {

    private static PotionEffect effect(int durationTicks) {
        PotionEffect effect = mock(PotionEffect.class);
        when(effect.getType()).thenReturn(mock(PotionEffectType.class));
        when(effect.getDuration()).thenReturn(durationTicks);
        return effect;
    }

    @Test
    void anOrdinaryDebuffDurationIsCleansable() {
        assertFalse(PermanentPotions.permanentDuration(200), "10 seconds of poison");
        assertFalse(PermanentPotions.permanentDuration(9600), "a full vanilla 8-minute potion");
    }

    @Test
    void aLongButFiniteDebuffIsStillCleansable() {
        // Vanilla Bad Omen runs 100 minutes and is a genuine landed debuff — the floor sits above it on purpose.
        assertFalse(PermanentPotions.permanentDuration(120_000));
    }

    @Test
    void theInfiniteMarkerIsPermanent() {
        assertTrue(PermanentPotions.permanentDuration(-1), "the 1.19.4+ infinite duration");
    }

    @Test
    void theFloorItselfIsPermanent() {
        assertTrue(PermanentPotions.permanentDuration(PermanentPotions.PERMANENT_FLOOR_TICKS));
        assertFalse(PermanentPotions.permanentDuration(PermanentPotions.PERMANENT_FLOOR_TICKS - 1));
    }

    @Test
    void theDriversOwnGrantIsSparedAtAnyDuration() {
        // SE re-applies its worn grants at 1 000 000 ticks, but between refreshes the live duration is whatever
        // has ticked down — so the exact bridge, not the floor, is what actually protects a worn debuff.
        LivingEntity wearer = mock(LivingEntity.class);
        PotionEffect fatigue = effect(40); // nearly elapsed; the driver will renew it
        PermanentPotions grants = (target, type) -> target == wearer && type == fatigue.getType();

        assertTrue(grants.spares(wearer, fatigue));
    }

    @Test
    void anEffectTheBridgeDisownsIsCleansable() {
        LivingEntity victim = mock(LivingEntity.class);
        assertFalse(PermanentPotions.NONE.spares(victim, effect(200)));
    }

    @Test
    void aForeignPermanentGrantIsSparedWithNoBridgeAtAll() {
        // Another plugin's permanent debuff: SE's driver knows nothing about it, so only the duration test stands.
        assertTrue(PermanentPotions.NONE.spares(mock(LivingEntity.class),
                effect(PermanentPotions.PERMANENT_FLOOR_TICKS)));
    }

    @Test
    void aFaultyBridgeDegradesToCleansableRatherThanThrowing() {
        PermanentPotions faulty = (target, type) -> {
            throw new IllegalStateException("bridge blew up");
        };

        assertFalse(faulty.spares(mock(LivingEntity.class), effect(200)));
    }

    @Test
    void nullsAreNeverSpared() {
        PermanentPotions always = (target, type) -> true;
        assertFalse(always.spares(mock(LivingEntity.class), null));
        assertFalse(always.spares(null, effect(200)), "no holder → no bridge answer, and 200t is short");
    }
}
