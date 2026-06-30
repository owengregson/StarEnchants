package engine.selector.kind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.selector.SelectorCtx;
import engine.sink.DamageMarks;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import schema.spec.Args;

class EntitySelectorsTest {

    @AfterEach
    void cleanMarks() {
        DamageMarks.clearAll();
    }

    private static LivingEntity withId(UUID id) {
        LivingEntity e = mock(LivingEntity.class);
        lenient().when(e.getUniqueId()).thenReturn(id);
        return e;
    }

    private static final Location CENTER = mock(Location.class);

    private static LivingEntity at(double distSq) {
        LivingEntity e = mock(LivingEntity.class);
        Location l = mock(Location.class);
        lenient().when(l.distanceSquared(CENTER)).thenReturn(distSq);
        lenient().when(e.getLocation()).thenReturn(l);
        return e;
    }

    private static Player playerAt(double distSq) {
        Player p = mock(Player.class);
        Location l = mock(Location.class);
        lenient().when(l.distanceSquared(CENTER)).thenReturn(distSq);
        lenient().when(p.getLocation()).thenReturn(l);
        return p;
    }

    private static SelectorCtx areaCtx(Player actor, double r, List<LivingEntity> nearby) {
        SelectorCtx ctx = mock(SelectorCtx.class);
        when(ctx.location()).thenReturn(CENTER);
        lenient().when(ctx.actor()).thenReturn(actor);
        lenient().when(ctx.dbl("r")).thenReturn(r);
        when(ctx.nearbyLiving(CENTER, r)).thenReturn(nearby);
        return ctx;
    }

    @Test
    void allPlayersKeepsOnlyPlayersExceptTheActor() {
        Player actor = mock(Player.class);
        Player other = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class);
        SelectorCtx ctx = areaCtx(actor, 32.0, List.of(actor, other, mob));

        assertEquals(List.of(other), new AllPlayersSelector().resolve(ctx));
    }

    @Test
    void nearestPlayerPicksTheClosestPlayer() {
        Player far = playerAt(100.0);
        Player near = playerAt(2.0);
        LivingEntity nearerMob = at(1.0); // closer, but not a player → ignored
        SelectorCtx ctx = areaCtx(null, 16.0, List.of(far, nearerMob, near));

        assertEquals(List.of(near), new NearestPlayerSelector().resolve(ctx));
    }

    @Test
    void nearestPlayerEmptyWhenNoPlayerInRange() {
        SelectorCtx ctx = areaCtx(null, 16.0, List.of(at(1.0), at(2.0)));
        assertTrue(new NearestPlayerSelector().resolve(ctx).isEmpty());
    }

    @Test
    void playerFromNameReturnsTheNamedPlayerOrEmpty() {
        Player steve = mock(Player.class);
        SelectorCtx ctx = mock(SelectorCtx.class);
        when(ctx.args()).thenReturn(Args.empty().with("name", "Steve"));
        when(ctx.playerByName("Steve")).thenReturn(steve);
        assertEquals(List.of(steve), new PlayerFromNameSelector().resolve(ctx));

        SelectorCtx absent = mock(SelectorCtx.class);
        when(absent.args()).thenReturn(Args.empty().with("name", "Ghost"));
        when(absent.playerByName("Ghost")).thenReturn(null);
        assertTrue(new PlayerFromNameSelector().resolve(absent).isEmpty());
    }

    @Test
    void markedKeepsOnlyNearbyEntitiesTheActorHasMarked() {
        Player actor = mock(Player.class);
        UUID actorId = UUID.randomUUID();
        when(actor.getUniqueId()).thenReturn(actorId);
        LivingEntity marked = withId(UUID.randomUUID());
        LivingEntity unmarked = withId(UUID.randomUUID());
        DamageMarks.mark(marked.getUniqueId(), actorId, 0.25, 60_000L); // the actor has marked exactly one of them
        SelectorCtx ctx = areaCtx(actor, 32.0, List.of(marked, unmarked));

        assertEquals(List.of(marked), new MarkedSelector().resolve(ctx)); // the unmarked nearby entity is dropped
    }

    @Test
    void markedIsEmptyWhenTheActorHasMarkedNobody() {
        Player actor = mock(Player.class);
        lenient().when(actor.getUniqueId()).thenReturn(UUID.randomUUID());
        SelectorCtx ctx = mock(SelectorCtx.class);
        lenient().when(ctx.actor()).thenReturn(actor); // no marks → returns before any nearby scan
        assertTrue(new MarkedSelector().resolve(ctx).isEmpty());
    }

    @Test
    void entityInSightReturnsTheRaytraceHitOrEmpty() {
        Player actor = mock(Player.class);
        LivingEntity hit = mock(LivingEntity.class);
        SelectorCtx ctx = mock(SelectorCtx.class);
        lenient().when(ctx.actor()).thenReturn(actor);
        when(ctx.dbl("r")).thenReturn(16.0);
        when(ctx.entityInSight(16.0)).thenReturn(hit);
        assertEquals(List.of(hit), new EntityInSightSelector().resolve(ctx));

        SelectorCtx nothing = mock(SelectorCtx.class);
        lenient().when(nothing.actor()).thenReturn(actor);
        when(nothing.dbl("r")).thenReturn(16.0);
        when(nothing.entityInSight(16.0)).thenReturn(null);
        assertTrue(new EntityInSightSelector().resolve(nothing).isEmpty());
    }
}
