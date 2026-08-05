package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import compile.model.Ability;
import org.junit.jupiter.api.Test;
import testfx.Abilities;

/**
 * IMPACT source scoping (ADR-0074): a landing runs only the abilities carrying the {@code group:} that armed the
 * cast. The identity is the authored group and NOT {@code defId}, because a field's arm and its payload are two
 * separate authored bonuses with two different ids — a defId filter would match nothing at all, which is why the
 * first reading was disqualified rather than deferred.
 */
class ImpactGroupScopeTest {

    /** Four IMPACT abilities: two in group 3 (the arming feature), one in group 7, one ungrouped. */
    private static Ability[] roster() {
        return new Ability[] {
            Abilities.ability().id(0).cooldownScope(-1, 3, -1).build(),
            Abilities.ability().id(1).cooldownScope(-1, 7, -1).build(),
            Abilities.ability().id(2).cooldownScope(-1, 3, -1).build(),
            Abilities.ability().id(3).cooldownScope(-1, -1, -1).build(),
        };
    }

    @Test
    void aScopedLandingKeepsOnlyItsOwnGroup() {
        // The measured collision: a Dimensional Traveler wearer who also carries Tombstone fired Tombstone's
        // whole-set armour damage on each of ~142 landing blocks, because the landing ran every IMPACT ability
        // the owner wore rather than the one that armed the field.
        assertArrayEquals(new int[] {0, 2}, TriggerRunner.withGroup(roster(), new int[] {0, 1, 2, 3}, 3));
    }

    @Test
    void anUnmatchedGroupRunsNothingRatherThanEverything() {
        // The failure mode that matters most: a filter that fell back to the whole roster on no match would
        // restore the exact over-firing it exists to stop, and silently.
        assertEquals(0, TriggerRunner.withGroup(roster(), new int[] {0, 1, 2, 3}, 99).length);
    }

    @Test
    void anUngroupedAbilityIsNeverClaimedByAScopedLanding() {
        // -1 means "declares no group". It must not collide with a real group id, and it must not be swept up
        // by any of them — an ungrouped payload belongs to no feature in particular.
        assertArrayEquals(new int[] {3}, TriggerRunner.withGroup(roster(), new int[] {0, 1, 2, 3}, -1));
    }

    @Test
    void aNarrowedPayloadFollowsItsOwnGroupAndNotTheFamilyMatchKey() {
        // R-QC40 (ADR-0074 amendment). Ability 4 sits in family `3` — the key a family-wide
        // SUPPRESS_INCOMING{scope: GROUP} names — but sources IMPACT under its own `11`. A group-3 cast must
        // NOT claim it (that is the mastery cross-fire: three enchants' payloads under one family word), and a
        // group-11 cast must. The two identities are read from two fields, so neither can move the other.
        Ability[] roster = new Ability[] {
            Abilities.ability().id(0).cooldownScope(-1, 3, -1).build(),
            Abilities.ability().id(1).cooldownScope(-1, 3, -1).sourceGroup(11).build(),
        };
        assertArrayEquals(new int[] {0}, TriggerRunner.withGroup(roster, new int[] {0, 1}, 3));
        assertArrayEquals(new int[] {1}, TriggerRunner.withGroup(roster, new int[] {0, 1}, 11));
        assertEquals(3, roster[1].cdScopeGroup(), "the narrowed payload keeps the family match key");
    }

    @Test
    void anAllMatchingWalkHandsBackTheWornStatesOwnArray() {
        // The hot path: a field that lands ~142 blocks must not allocate a copy per landing when the filter
        // changes nothing.
        Ability[] abilities = roster();
        int[] candidates = {0, 2};
        assertSame(candidates, TriggerRunner.withGroup(abilities, candidates, 3));
    }
}
