package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The falling-block → IMPACT cast registry: bind / claim-once-on-land / forget-on-miss. EVERY cosmetic block is
 * tracked (so the listener cancels its placement); the owner — which may be null — drives the IMPACT abilities.
 */
class FallingBlockCastsTest {

    @AfterEach
    void clean() {
        FallingBlockCasts.clearAll();
    }

    @Test
    void bindThenLandReturnsTheCastOnceThenNothing() {
        UUID block = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        FallingBlockCasts.bind(block, owner, target, 7.0);
        assertTrue(FallingBlockCasts.isTracked(block));

        FallingBlockCasts.Cast cast = FallingBlockCasts.onLand(block);
        assertEquals(owner, cast.owner());
        assertEquals(target, cast.target()); // the aimed entity round-trips so the landing hits only it
        assertEquals(7.0, cast.damage());
        assertFalse(FallingBlockCasts.isTracked(block)); // unbound after landing
        assertNull(FallingBlockCasts.onLand(block));      // a second landing of the same block claims nothing
    }

    @Test
    void forgetUnbindsAMissedBlock() {
        UUID block = UUID.randomUUID();
        FallingBlockCasts.bind(block, UUID.randomUUID(), UUID.randomUUID(), 1.0);
        FallingBlockCasts.forget(block);
        assertFalse(FallingBlockCasts.isTracked(block));
        assertNull(FallingBlockCasts.onLand(block));
    }

    @Test
    void nullOwnerIsTrackedForCancellationButCarriesNoOwner() {
        UUID block = UUID.randomUUID();
        // An owner-less cosmetic (e.g. environment-fired): still tracked so the listener cancels its placement —
        // a FALLING_BLOCK must never stick — but it carries a null owner so no IMPACT fires.
        FallingBlockCasts.bind(block, null, null, 1.0);
        assertTrue(FallingBlockCasts.isTracked(block));

        FallingBlockCasts.Cast cast = FallingBlockCasts.onLand(block);
        assertNull(cast.owner());
        assertEquals(1.0, cast.damage());
    }

    @Test
    void theRehitCeilingIsAFixedBucketSharedByEveryWearerRainingOnOneVictim() {
        UUID victim = UUID.randomUUID();
        UUID block = UUID.randomUUID();
        FallingBlockCasts.bind(block, UUID.randomUUID(), victim, 3.0, 4, 200, -1);
        FallingBlockCasts.Cast cast = FallingBlockCasts.onLand(block);

        // Driven through the CAST, so a ceiling dropped anywhere in the bind → landing plumbing fails here too.
        // Four blocks land at once — conceptually from four DIFFERENT wearers' fields, which a victim-keyed
        // bucket cannot tell apart, and must not: a crowd may not multiply one victim's damage ceiling.
        for (int wearer = 1; wearer <= 4; wearer++) {
            assertTrue(FallingBlockCasts.claimHit(victim, cast.rehitMax(), cast.rehitWindowTicks(), 0L),
                    "impact " + wearer + " is inside the ceiling");
        }
        assertFalse(FallingBlockCasts.claimHit(victim, cast.rehitMax(), cast.rehitWindowTicks(), 0L));
        assertFalse(FallingBlockCasts.claimHit(victim, cast.rehitMax(), cast.rehitWindowTicks(), 199L),
                "the bucket is anchored at the FIRST claim, so the window runs from there...");
        assertTrue(FallingBlockCasts.claimHit(victim, cast.rehitMax(), cast.rehitWindowTicks(), 200L),
                "...and re-anchors only once it has fully elapsed (a fixed bucket, not a sliding one)");
        assertTrue(FallingBlockCasts.claimHit(UUID.randomUUID(), 4, 200, 0L), "another victim has their own bucket");
    }

    @Test
    void theArmingGroupSurvivesTheBindToLandingHop() {
        // ADR-0074: the group is the ONLY thing connecting a landing back to the ability that armed the field —
        // the owner lookup is fresh at each landing and the arm's defId is a different ability's entirely. A
        // plumbing break here would silently restore the over-firing, since -1 means "run them all".
        UUID scoped = UUID.randomUUID();
        FallingBlockCasts.bind(scoped, UUID.randomUUID(), UUID.randomUUID(), 1.0, 0, 0, 42);
        assertEquals(42, FallingBlockCasts.onLand(scoped).sourceGroup());

        UUID ungrouped = UUID.randomUUID();
        FallingBlockCasts.bind(ungrouped, UUID.randomUUID(), UUID.randomUUID(), 1.0); // the pre-scoping bind
        assertEquals(-1, FallingBlockCasts.onLand(ungrouped).sourceGroup(),
                "content authoring no group: stays unscoped, so nothing shipped before this changes behaviour");
    }

    @Test
    void anUnprofiledGridCarriesNoCeilingAndBooksNothing() {
        UUID block = UUID.randomUUID();
        FallingBlockCasts.bind(block, UUID.randomUUID(), UUID.randomUUID(), 1.0); // today's plain grid
        FallingBlockCasts.Cast cast = FallingBlockCasts.onLand(block);
        assertEquals(0, cast.rehitMax(), "an unprofiled grid stays uncapped, exactly as it was before the field");

        UUID victim = UUID.randomUUID();
        for (int landing = 0; landing < 50; landing++) {
            assertTrue(FallingBlockCasts.claimHit(victim, cast.rehitMax(), cast.rehitWindowTicks(), 0L));
        }
    }
}
