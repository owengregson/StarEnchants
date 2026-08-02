package feature.bless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.sink.DamageMarks;
import engine.sink.DotParkLedger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What {@code /bless} strips and — the part that actually needs guarding — what it must leave alone. A cleanse
 * that also removed the wearer's own permanent gear debuff would fight the passive driver every time it ran, and
 * one that spared a landed slow would read as simply not working.
 */
class CleanseServiceTest {

    private final UUID id = UUID.randomUUID();
    private DotParkLedger dotPark;
    /** Effect types the fake passive authority claims as permanent-while-worn grants. */
    private final Set<PotionEffectType> maintained = new HashSet<>();

    @BeforeEach
    void setUp() {
        dotPark = new DotParkLedger();
        maintained.clear();
        DamageMarks.clearAll();
    }

    @AfterEach
    void tearDown() {
        DamageMarks.clearAll();
    }

    private CleanseService service() {
        return new CleanseService((player, type) -> maintained.contains(type), dotPark);
    }

    /** A player carrying exactly {@code effects}, with no fire and no marks unless a test adds them. */
    private Player player(PotionEffect... effects) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getActivePotionEffects()).thenReturn(new ArrayList<>(List.of(effects)));
        when(player.getFireTicks()).thenReturn(0);
        return player;
    }

    private static PotionEffect effect(String canonicalName, int durationTicks) {
        PotionEffectType type = mock(PotionEffectType.class);
        when(type.getName()).thenReturn(canonicalName);
        PotionEffect effect = mock(PotionEffect.class);
        when(effect.getType()).thenReturn(type);
        when(effect.getDuration()).thenReturn(durationTicks);
        return effect;
    }

    @Test
    void landedHarmfulEffectsAreStripped() {
        PotionEffect poison = effect("POISON", 200);
        PotionEffect slow = effect("SLOW", 100);
        Player player = player(poison, slow);

        CleanseService.Report report = service().cleanse(player);

        assertEquals(2, report.potions());
        verify(player).removePotionEffect(poison.getType());
        verify(player).removePotionEffect(slow.getType());
    }

    @Test
    void beneficialAndNeutralEffectsAreNeverTouched() {
        PotionEffect strength = effect("INCREASE_DAMAGE", 200);
        PotionEffect glowing = effect("GLOWING", 200);
        Player player = player(strength, glowing);

        CleanseService.Report report = service().cleanse(player);

        assertEquals(0, report.potions());
        verify(player, never()).removePotionEffect(strength.getType());
        verify(player, never()).removePotionEffect(glowing.getType());
    }

    @Test
    void aPermanentWhileWornGrantSurvivesTheCleanse() {
        // The stated rule: a helmet granting permanent mining fatigue is the wearer's own choice, not a debuff
        // someone landed — and the driver would re-apply it on its next refresh anyway.
        PotionEffect fatigue = effect("SLOW_DIGGING", 1_000_000);
        maintained.add(fatigue.getType());
        Player player = player(fatigue);

        CleanseService.Report report = service().cleanse(player);

        assertEquals(0, report.potions());
        verify(player, never()).removePotionEffect(fatigue.getType());
    }

    @Test
    void anEffectivelyPermanentEffectSurvivesEvenWhenSeIsNotItsSource() {
        // Another plugin's permanent grant: SE's passive driver knows nothing about it, so the duration rule is
        // the only thing standing between it and a strip-then-reapply flicker every bless.
        PotionEffect foreign = effect("SLOW_DIGGING", CleanseService.PERMANENT_FLOOR_TICKS);
        Player player = player(foreign);

        assertEquals(0, service().cleanse(player).potions());
        verify(player, never()).removePotionEffect(foreign.getType());
    }

    @Test
    void anInfiniteDurationSurvives() {
        PotionEffect infinite = effect("WITHER", -1); // the 1.19.4+ infinite marker
        Player player = player(infinite);

        assertEquals(0, service().cleanse(player).potions());
        verify(player, never()).removePotionEffect(infinite.getType());
    }

    @Test
    void aLongButFiniteDebuffIsStillCleansed() {
        // Vanilla Bad Omen runs 100 minutes and is a genuine landed debuff — the permanent floor sits well above
        // it precisely so this case is not swept up.
        PotionEffect badOmen = effect("BAD_OMEN", 120_000);
        Player player = player(badOmen);

        assertEquals(1, service().cleanse(player).potions());
        verify(player).removePotionEffect(badOmen.getType());
    }

    @Test
    void burningIsExtinguished() {
        Player player = player();
        when(player.getFireTicks()).thenReturn(60);

        CleanseService.Report report = service().cleanse(player);

        assertTrue(report.extinguished());
        verify(player).setFireTicks(0);
    }

    @Test
    void aPlayerWhoIsNotBurningIsLeftAlone() {
        Player player = player();

        assertFalse(service().cleanse(player).extinguished());
        verify(player, never()).setFireTicks(0);
    }

    @Test
    void damageAlreadyBankedAgainstThePlayerIsDropped() {
        // Without this the bless "works" and then the parked hit lands a moment later, which reads as a no-op.
        dotPark.comboStarted(id, UUID.randomUUID(), 0L);
        assertTrue(dotPark.tryPark(id, null, 4.0, 0L), "precondition: damage is banked");

        CleanseService.Report report = service().cleanse(player());

        assertTrue(report.clearedParkedDot());
        assertFalse(dotPark.hasParked(id));
    }

    @Test
    void marksAgainstThePlayerAreLifted() {
        UUID reaper = UUID.randomUUID();
        DamageMarks.mark(id, reaper, 0.25, 60_000L);

        CleanseService.Report report = service().cleanse(player());

        assertTrue(report.clearedMarks());
        assertEquals(0.0, DamageMarks.bonus(id, reaper), "the reaper's +25% is gone");
    }

    @Test
    void aMarkThePlayerHoldsOnSomeoneElseIsNotLifted() {
        // Marks are directional: blessing yourself must not disarm your own offence.
        UUID victim = UUID.randomUUID();
        DamageMarks.mark(victim, id, 0.25, 60_000L);

        CleanseService.Report report = service().cleanse(player());

        assertFalse(report.clearedMarks());
        assertEquals(0.25, DamageMarks.bonus(victim, id), "the mark THIS player holds still stands");
    }

    @Test
    void aCleanPlayerReportsNothingRemoved() {
        assertFalse(service().cleanse(player()).anything());
    }
}
