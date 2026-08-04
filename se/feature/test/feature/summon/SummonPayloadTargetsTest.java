package feature.summon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.sink.SummonFlags;
import feature.trigger.TriggerDispatch;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * The payload's target box: its extents, the shared selector filter vocabulary, and the max-targets cap. All
 * three are silent when wrong — a payload with the wrong box or an unapplied filter still detonates, it just
 * hits the wrong set, so nothing downstream would fail.
 */
class SummonPayloadTargetsTest {

    private static final Location CENTER = mock(Location.class);

    private static SummonFlags payload(double radius, double height, String filter, int maxTargets) {
        return SummonFlags.NONE.withPayload(
                SummonFlags.PHASE_DETONATE, 40, radius, height, filter, maxTargets, 0);
    }

    private static Entity summon(List<Entity> nearby, double rx, double ry) {
        Entity summon = mock(Entity.class);
        lenient().when(summon.getLocation()).thenReturn(CENTER);
        when(summon.getNearbyEntities(rx, ry, rx)).thenReturn(nearby);
        return summon;
    }

    private static LivingEntity at(double distSq) {
        LivingEntity e = mock(LivingEntity.class);
        Location l = mock(Location.class);
        lenient().when(l.distanceSquared(CENTER)).thenReturn(distSq);
        lenient().when(e.getLocation()).thenReturn(l);
        return e;
    }

    @Test
    void aZeroHeightBoxFallsBackToTheRadiusOnBothAxes() {
        Entity summon = summon(List.of(), 4.0, 4.0);

        SummonPayloadService.select(summon, mock(Player.class), payload(4.0, 0.0, "ALL", 0));

        verify(summon).getNearbyEntities(4.0, 4.0, 4.0);
    }

    @Test
    void anExplicitHeightGivesTheBoxItsOwnYExtent() {
        // Plague Carrier's 5x4x5: a box that reused the radius vertically would reach a floor above and below.
        Entity summon = summon(List.of(), 2.5, 2.0);

        SummonPayloadService.select(summon, mock(Player.class), payload(2.5, 2.0, "ALL", 0));

        verify(summon).getNearbyEntities(2.5, 2.0, 2.5);
    }

    @Test
    void theFilterIsTheSameVocabularyTheAreaSelectorsUse() {
        Player owner = mock(Player.class);
        Monster hostile = mock(Monster.class);
        Player bystander = mock(Player.class);
        Entity summon = summon(List.of(hostile, bystander), 4.0, 4.0);

        assertEquals(List.of(hostile),
                SummonPayloadService.select(summon, owner, payload(4.0, 0.0, "MONSTERS", 0)));
    }

    @Test
    void theOwnerIsNeverTheirOwnPayloadTarget() {
        Player owner = mock(Player.class);
        LivingEntity other = mock(LivingEntity.class);
        Entity summon = summon(List.of(owner, other), 4.0, 4.0);

        assertEquals(List.of(other), SummonPayloadService.select(summon, owner, payload(4.0, 0.0, "ALL", 0)));
    }

    @Test
    void maxTargetsKeepsTheNearestAndNothingElse() {
        // Spirits pulses one or two allies out of a crowd; an uncapped pulse would heal the whole field.
        Player owner = mock(Player.class);
        LivingEntity near = at(1.0);
        LivingEntity mid = at(9.0);
        LivingEntity far = at(64.0);
        Entity summon = summon(List.of(far, near, mid), 8.0, 8.0);

        assertEquals(List.of(near, mid), SummonPayloadService.select(summon, owner, payload(8.0, 0.0, "ALL", 2)));
    }

    @Test
    void everyTargetGetsItsOwnActivation() {
        // One activation per target is what makes the payload's DAMAGE/IGNITE/VELOCITY ordinary authored
        // effects; two targets so a loop that fires only the first (or only the last) is visible.
        TriggerDispatch dispatch = mock(TriggerDispatch.class);
        Entity summon = mock(Entity.class);
        Player owner = mock(Player.class);
        LivingEntity a = mock(LivingEntity.class);
        LivingEntity b = mock(LivingEntity.class);

        new SummonPayloadService(dispatch).dispatch(owner, summon, List.of(a, b));

        verify(dispatch).fireSummonPayload(owner, summon, a);
        verify(dispatch).fireSummonPayload(owner, summon, b);
        verifyNoMoreInteractions(dispatch);
    }

    @Test
    void zeroMaxTargetsIsUnlimited() {
        Player owner = mock(Player.class);
        LivingEntity a = mock(LivingEntity.class);
        LivingEntity b = mock(LivingEntity.class);
        Entity summon = summon(List.of(a, b), 4.0, 4.0);

        assertEquals(List.of(a, b), SummonPayloadService.select(summon, owner, payload(4.0, 0.0, "ALL", 0)));
    }
}
