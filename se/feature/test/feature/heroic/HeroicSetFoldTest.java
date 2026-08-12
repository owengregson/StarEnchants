package feature.heroic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.Snapshot;
import org.junit.jupiter.api.Test;
import schema.spec.Args;
import testfx.Abilities;
import testfx.Snapshots;

/**
 * ADR-0073 Decision 3: a set whose completion bonus IS its heroic wall folded in cannot also take the stamp,
 * or {@code DamageFold} bills the one wall from both channels. The rule is derived from the bonus rather than
 * declared per set, so these pin what "already folds it" means.
 */
class HeroicSetFoldTest {

    private static CompiledEffect damageMod(String side) {
        return new CompiledEffect("DAMAGE_MOD",
                Args.empty().with("side", side).with("mode", "add").with("amount", 45.0),
                CompiledSelector.SELF, 0, Affinity.CONTEXT_LOCAL);
    }

    private static CompiledEffect potion() {
        return new CompiledEffect("POTION", Args.empty(), CompiledSelector.SELF, 0, Affinity.CONTEXT_LOCAL);
    }

    @Test
    void aDefensiveFoldOnTheCompletionBonusRefusesTheStamp() {
        Snapshot snapshot = Snapshots.snapshot()
                .abilities(Abilities.ability().id(0).effects(damageMod("defense")).build())
                .stableKeys("sets/architect")
                .build();

        assertTrue(HeroicSetFold.foldsDefensiveWall(snapshot, "sets/architect"));
    }

    @Test
    void aFoldOnAnyOfTheSetsArmourBonusesCounts() {
        // Armour bonuses key <set>, /a1, /a2, … — the fold is not always the completion one.
        Snapshot snapshot = Snapshots.snapshot()
                .abilities(Abilities.ability().id(0).effects(potion()).build(),
                        Abilities.ability().id(1).effects(damageMod("defense")).build())
                .stableKeys("sets/architect", "sets/architect/a1")
                .build();

        assertTrue(HeroicSetFold.foldsDefensiveWall(snapshot, "sets/architect"));
    }

    @Test
    void anAttackSideModIsNotAHeroicWall() {
        // Only the DEFENSE side feeds the channel the heroic stamp writes into; an outgoing bonus is unrelated.
        Snapshot snapshot = Snapshots.snapshot()
                .abilities(Abilities.ability().id(0).effects(damageMod("attack")).build())
                .stableKeys("sets/koth")
                .build();

        assertFalse(HeroicSetFold.foldsDefensiveWall(snapshot, "sets/koth"));
    }

    @Test
    void aPieceOutsideAnySetIsNeverRefused() {
        Snapshot snapshot = Snapshots.snapshot().build();

        assertFalse(HeroicSetFold.foldsDefensiveWall(snapshot, null));
        assertFalse(HeroicSetFold.foldsDefensiveWall(snapshot, ""));
        assertFalse(HeroicSetFold.foldsDefensiveWall(snapshot, "sets/nothing-here"));
    }
}
