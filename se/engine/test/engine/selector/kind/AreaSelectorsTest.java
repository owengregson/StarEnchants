package engine.selector.kind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.selector.SelectorCtx;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import schema.spec.Args;

class AreaSelectorsTest {

    private static final Location CENTER = mock(Location.class);

    private static SelectorCtx ctx(Player actor, double r, String filter, int limit, List<LivingEntity> nearby) {
        SelectorCtx ctx = mock(SelectorCtx.class);
        when(ctx.location()).thenReturn(CENTER);
        lenient().when(ctx.actor()).thenReturn(actor);
        when(ctx.dbl("r")).thenReturn(r);
        when(ctx.args()).thenReturn(Args.empty().with("filter", filter));
        lenient().when(ctx.integer("limit")).thenReturn(limit);
        when(ctx.nearbyLiving(CENTER, r)).thenReturn(nearby);
        return ctx;
    }

    private static LivingEntity at(double distSq) {
        LivingEntity e = mock(LivingEntity.class);
        Location l = mock(Location.class);
        lenient().when(l.distanceSquared(CENTER)).thenReturn(distSq);
        lenient().when(e.getLocation()).thenReturn(l);
        return e;
    }

    @Test
    void aoeReturnsEveryNearbyLivingExceptTheActor() {
        Player actor = mock(Player.class);
        LivingEntity a = mock(LivingEntity.class);
        LivingEntity b = mock(LivingEntity.class);

        SelectorCtx ctx = ctx(actor, 4.0, "ALL", 0, List.of(a, actor, b));

        assertEquals(List.of(a, b), new AoeSelector().resolve(ctx)); // actor dropped; scan order preserved
    }

    @Test
    void aoeFilterKeepsOnlyPlayers() {
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class);

        SelectorCtx ctx = ctx(null, 5.0, "PLAYERS", 0, List.of(p1, mob, p2));

        assertEquals(List.of(p1, p2), new AoeSelector().resolve(ctx));
    }

    @Test
    void aoeLimitKeepsTheNearestN() {
        LivingEntity far = at(100.0);
        LivingEntity near = at(1.0);
        LivingEntity mid = at(4.0);

        SelectorCtx ctx = ctx(null, 10.0, "ALL", 2, List.of(far, near, mid));

        assertEquals(List.of(near, mid), new AoeSelector().resolve(ctx)); // limit truncates after sorting by distance, not scan order
    }

    @Test
    void nearestPicksTheClosestEntity() {
        LivingEntity a = at(9.0);
        LivingEntity b = at(4.0);

        SelectorCtx ctx = ctx(null, 10.0, "ALL", 0, List.of(a, b));

        assertEquals(List.of(b), new NearestSelector().resolve(ctx));
    }

    @Test
    void nearestFilterPicksTheNearestPlayer() {
        LivingEntity nearMob = at(1.0);
        Player farPlayer = mock(Player.class);
        Location pl = mock(Location.class);
        lenient().when(pl.distanceSquared(CENTER)).thenReturn(25.0);
        lenient().when(farPlayer.getLocation()).thenReturn(pl);

        SelectorCtx ctx = ctx(null, 10.0, "PLAYERS", 0, List.of(nearMob, farPlayer));

        assertEquals(List.of(farPlayer), new NearestSelector().resolve(ctx)); // closer mob skipped: filter runs before distance pick
    }

    @Test
    void nearestIsEmptyWhenNothingInRange() {
        SelectorCtx ctx = ctx(null, 5.0, "ALL", 0, List.of());
        assertTrue(new NearestSelector().resolve(ctx).isEmpty());
    }
}
