package engine.selector.kind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.selector.SelectorCtx;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The area selectors {@code @Aoe} and {@code @Nearest}, scanning a mocked region. */
class AreaSelectorsTest {

    @Test
    void aoeReturnsEveryNearbyLivingExceptTheActor() {
        Player actor = mock(Player.class);
        LivingEntity a = mock(LivingEntity.class);
        LivingEntity b = mock(LivingEntity.class);
        Location center = mock(Location.class);

        SelectorCtx ctx = mock(SelectorCtx.class);
        when(ctx.location()).thenReturn(center);
        when(ctx.actor()).thenReturn(actor);
        when(ctx.dbl("r")).thenReturn(4.0);
        when(ctx.nearbyLiving(center, 4.0)).thenReturn(List.of(a, actor, b));

        assertEquals(List.of(a, b), new AoeSelector().resolve(ctx)); // actor excluded, order kept
    }

    @Test
    void nearestPicksTheClosestEntity() {
        LivingEntity a = mock(LivingEntity.class);
        LivingEntity b = mock(LivingEntity.class);
        Location center = mock(Location.class);
        Location la = mock(Location.class);
        Location lb = mock(Location.class);
        when(la.distanceSquared(center)).thenReturn(9.0);
        when(lb.distanceSquared(center)).thenReturn(4.0);
        when(a.getLocation()).thenReturn(la);
        when(b.getLocation()).thenReturn(lb);

        SelectorCtx ctx = mock(SelectorCtx.class);
        when(ctx.location()).thenReturn(center);
        when(ctx.actor()).thenReturn(null);
        when(ctx.dbl("r")).thenReturn(10.0);
        when(ctx.nearbyLiving(center, 10.0)).thenReturn(List.of(a, b));

        assertEquals(List.of(b), new NearestSelector().resolve(ctx)); // b is closer
    }

    @Test
    void nearestIsEmptyWhenNothingInRange() {
        Location center = mock(Location.class);
        SelectorCtx ctx = mock(SelectorCtx.class);
        when(ctx.location()).thenReturn(center);
        when(ctx.actor()).thenReturn(null);
        when(ctx.dbl("r")).thenReturn(5.0);
        when(ctx.nearbyLiving(center, 5.0)).thenReturn(List.of());

        assertTrue(new NearestSelector().resolve(ctx).isEmpty());
    }
}
