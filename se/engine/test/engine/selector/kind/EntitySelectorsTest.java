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
        return areaCtx(actor, r, nearby, false);
    }

    private static SelectorCtx areaCtx(Player actor, double r, List<LivingEntity> nearby, boolean allies) {
        SelectorCtx ctx = mock(SelectorCtx.class);
        when(ctx.location()).thenReturn(CENTER);
        lenient().when(ctx.actor()).thenReturn(actor);
        lenient().when(ctx.dbl("r")).thenReturn(r);
        lenient().when(ctx.args()).thenReturn(Args.empty().with("allies", allies));
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

    // R-QC17: the last targeting paths that were blind to the ONE installed alliance predicate. The default
    // skips a party-mate the damage gate already spares; `allies: true` is the audience escape hatch.
    @Test
    void allPlayersSkipsAlliesByDefaultAndTakesThemBackOnRequest() {
        Player actor = mock(Player.class);
        Player ally = mock(Player.class);
        Player foe = mock(Player.class);
        Allies.resolver((a, b) -> a == actor && b == ally);
        try {
            assertEquals(List.of(foe), new AllPlayersSelector().resolve(areaCtx(actor, 32.0, List.of(ally, foe))));
            assertEquals(List.of(ally, foe),
                    new AllPlayersSelector().resolve(areaCtx(actor, 32.0, List.of(ally, foe), true)));
        } finally {
            Allies.resolver(null); // restore the no-alliance default so other tests are unaffected
        }
    }

    @Test
    void nearestPlayerWalksPastACloserAllyToTheNearestEnemy() {
        // The closer body wins on distance alone, so an ally at 2 and a foe at 100 is the case that catches a
        // filter applied after the min() instead of inside it.
        Player actor = mock(Player.class);
        Player ally = playerAt(2.0);
        Player foe = playerAt(100.0);
        Allies.resolver((a, b) -> a == actor && b == ally);
        try {
            assertEquals(List.of(foe),
                    new NearestPlayerSelector().resolve(areaCtx(actor, 16.0, List.of(ally, foe))));
            assertEquals(List.of(ally),
                    new NearestPlayerSelector().resolve(areaCtx(actor, 16.0, List.of(ally, foe), true)));
        } finally {
            Allies.resolver(null);
        }
    }

    @Test
    void entityInSightSkipsAnAlliedPlayerButNeverAMob() {
        Player actor = mock(Player.class);
        Player ally = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class);
        Allies.resolver((a, b) -> a == actor && b == ally);
        try {
            assertTrue(new EntityInSightSelector().resolve(sightCtx(actor, ally, false)).isEmpty());
            assertEquals(List.of(ally), new EntityInSightSelector().resolve(sightCtx(actor, ally, true)));
            // A mob is nobody's ally: filtering by hostility instead would have stopped Smite working on a cow.
            assertEquals(List.of(mob), new EntityInSightSelector().resolve(sightCtx(actor, mob, false)));
        } finally {
            Allies.resolver(null);
        }
    }

    private static SelectorCtx sightCtx(Player actor, LivingEntity hit, boolean allies) {
        SelectorCtx ctx = mock(SelectorCtx.class);
        lenient().when(ctx.actor()).thenReturn(actor);
        lenient().when(ctx.args()).thenReturn(Args.empty().with("allies", allies));
        when(ctx.dbl("r")).thenReturn(16.0);
        when(ctx.entityInSight(16.0)).thenReturn(hit);
        return ctx;
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
        assertEquals(List.of(hit), new EntityInSightSelector().resolve(sightCtx(actor, hit, false)));
        assertTrue(new EntityInSightSelector().resolve(sightCtx(actor, null, false)).isEmpty());
    }
}
