package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.RecordingSchedulerBackend;

/**
 * {@code CURE category: HARMFUL} is THE cleanse (ADR-0072) — the single definition every caller shares:
 * clarity's Bless on a timer, the Cow Pet on right-click, and {@code /bless} once on demand. It differs from a
 * plain filtered clear in two ways, both pinned here: it spares {@link PermanentPotions permanent} effects, and
 * it extinguishes burning. The other categories stay a blunt clear — nothing lands a buff on you, so there is
 * nothing there to protect.
 */
class DispatchSinkCleanseTest {

    private RuntimeHandles handles;

    @BeforeEach
    void setUp() {
        handles = new RuntimeHandles(new RegistryResolvers());
        Scheduling.install(new RecordingSchedulerBackend());
    }

    private static PotionEffect effect(String canonicalName, int durationTicks) {
        PotionEffectType type = mock(PotionEffectType.class);
        when(type.getName()).thenReturn(canonicalName);
        PotionEffect effect = mock(PotionEffect.class);
        when(effect.getType()).thenReturn(type);
        when(effect.getDuration()).thenReturn(durationTicks);
        return effect;
    }

    private static LivingEntity holder(PotionEffect... effects) {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.isValid()).thenReturn(true);
        when(entity.getActivePotionEffects()).thenReturn(new ArrayList<>(List.of(effects)));
        return entity;
    }

    /** Run one cleanse of {@code category} against {@code target}, flushed — the sink buffers until then. */
    private void cure(LivingEntity target, int category) {
        cure(target, category, PermanentPotions.NONE);
    }

    /** As above, with {@code grants} standing in for the wearer's own permanent-while-worn potions. */
    private void cure(LivingEntity target, int category, PermanentPotions grants) {
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().permanentPotions(grants).build());
        sink.cureByCategory(target, category);
        sink.flush();
    }

    @Test
    void aHarmfulSweepLiftsLandedDebuffsAndLeavesBuffsAlone() {
        PotionEffect poison = effect("POISON", 200);
        PotionEffect slow = effect("SLOW", 100);
        PotionEffect strength = effect("INCREASE_DAMAGE", 200);
        LivingEntity target = holder(poison, slow, strength);

        cure(target, PotionCategories.HARMFUL);

        verify(target).removePotionEffect(poison.getType());
        verify(target).removePotionEffect(slow.getType());
        verify(target, never()).removePotionEffect(strength.getType());
    }

    @Test
    void aHarmfulSweepSparesTheHoldersOwnPermanentGrant() {
        // The stated rule: a helmet granting permanent mining fatigue is the wearer's trade-off, not a debuff
        // someone landed — and the passive driver would re-apply it on its next refresh regardless.
        PotionEffect fatigue = effect("SLOW_DIGGING", 400);
        PotionEffect poison = effect("POISON", 200);
        LivingEntity target = holder(fatigue, poison);

        cure(target, PotionCategories.HARMFUL, (who, type) -> type == fatigue.getType());

        verify(target, never()).removePotionEffect(fatigue.getType());
        verify(target).removePotionEffect(poison.getType()); // the landed debuff still goes
    }

    @Test
    void aHarmfulSweepSparesAForeignPermanentGrantOnDurationAlone() {
        PotionEffect foreign = effect("SLOW_DIGGING", PermanentPotions.PERMANENT_FLOOR_TICKS);
        LivingEntity target = holder(foreign);

        cure(target, PotionCategories.HARMFUL);

        verify(target, never()).removePotionEffect(foreign.getType());
    }

    @Test
    void aHarmfulSweepExtinguishesBurning() {
        LivingEntity target = holder();

        cure(target, PotionCategories.HARMFUL);

        verify(target).setFireTicks(0);
    }

    @Test
    void theOtherCategoriesStayABluntClearAndNeverTouchFire() {
        PotionEffect strength = effect("INCREASE_DAMAGE", PermanentPotions.PERMANENT_FLOOR_TICKS);
        LivingEntity target = holder(strength);

        cure(target, PotionCategories.BENEFICIAL);

        // Permanence protects a DEBUFF from a cleanse; it is not a general "cannot be removed" flag.
        verify(target).removePotionEffect(strength.getType());
        verify(target, never()).setFireTicks(0);
    }

    @Test
    void cureAllStillClearsEverythingIncludingHarmfulPermanents() {
        PotionEffect fatigue = effect("SLOW_DIGGING", 400);
        LivingEntity target = holder(fatigue);

        cure(target, PotionCategories.ALL, (who, type) -> true);

        verify(target).removePotionEffect(fatigue.getType());
        verify(target, never()).setFireTicks(0);
    }

    @Test
    void theCategoryCodesAreUnchanged() {
        assertEquals(0, PotionCategories.ALL);
        assertEquals(1, PotionCategories.HARMFUL);
    }
}
