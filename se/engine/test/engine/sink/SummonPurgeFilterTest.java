package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * The SUMMON_PURGE ownership decision — the one thing a sweep can get wrong in a way nothing else catches:
 * purging your own summons, or sparing an enemy's. Every (rung x owner) cell, hand-computed against the
 * ladder: every rung spares the actor's own, and each rung up spares one more group.
 */
class SummonPurgeFilterTest {

    /** {@code (ownedByActor, ownerOnline, allied)} — the three facts a sweep can establish about an owner. */
    private static DynamicTest row(String label, String filter, boolean ownedByActor, boolean ownerOnline,
                                   boolean allied, boolean purged) {
        return DynamicTest.dynamicTest(label, () ->
                assertEquals(purged, SummonPurgeFilter.purges(filter, ownedByActor, ownerOnline, allied)));
    }

    @TestFactory
    List<DynamicTest> everyOwnerCasePerRung() {
        return List.of(
                // not-own: the widest sweep — only the actor's own survive.
                row("not-own spares mine", SummonPurgeFilter.NOT_OWN, true, true, false, false),
                row("not-own purges an ally's", SummonPurgeFilter.NOT_OWN, false, true, true, true),
                row("not-own purges an online enemy's", SummonPurgeFilter.NOT_OWN, false, true, false, true),
                row("not-own purges an offline owner's", SummonPurgeFilter.NOT_OWN, false, false, false, true),

                // not-own-or-ally: an ONLINE ally is spared; offline is not an online ally, so it still goes.
                row("not-own-or-ally spares mine", SummonPurgeFilter.NOT_OWN_OR_ALLY, true, true, false, false),
                row("not-own-or-ally spares an ally's", SummonPurgeFilter.NOT_OWN_OR_ALLY, false, true, true, false),
                row("not-own-or-ally purges an online enemy's",
                        SummonPurgeFilter.NOT_OWN_OR_ALLY, false, true, false, true),
                row("not-own-or-ally purges an offline owner's",
                        SummonPurgeFilter.NOT_OWN_OR_ALLY, false, false, false, true),

                // not-own-or-ally-or-offline: the authored rung — an abandoned summon is left to its own TTL.
                row("not-own-or-ally-or-offline spares mine",
                        SummonPurgeFilter.NOT_OWN_OR_ALLY_OR_OFFLINE, true, true, false, false),
                row("not-own-or-ally-or-offline spares an ally's",
                        SummonPurgeFilter.NOT_OWN_OR_ALLY_OR_OFFLINE, false, true, true, false),
                row("not-own-or-ally-or-offline purges an online enemy's",
                        SummonPurgeFilter.NOT_OWN_OR_ALLY_OR_OFFLINE, false, true, false, true),
                row("not-own-or-ally-or-offline spares an offline owner's",
                        SummonPurgeFilter.NOT_OWN_OR_ALLY_OR_OFFLINE, false, false, false, false));
    }
}
