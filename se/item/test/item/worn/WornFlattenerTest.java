package item.worn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.SourceKind;
import java.util.BitSet;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

class WornFlattenerTest {

    // Trigger ids: 0 = attack-side, 1 = defense-side, 2 = block-break (non-combat).
    private static final IntPredicate ATTACK = t -> t == 0;
    private static final IntPredicate DEFENSE = t -> t == 1;
    private static final int TRIGGERS = 3;

    private static Ability ab(int id, int triggerMask) {
        return new Ability(id, 0, SourceKind.ENCHANT, triggerMask, 1, 100.0, 0, 0, 0L,
                null, new CompiledEffect[0], 0, Affinity.CONTEXT_LOCAL, -1, -1, -1, -1, 0);
    }

    private static WornState flatten(int[] activeIds, Ability[] abilities) {
        return WornFlattener.flatten(7, activeIds, abilities, TRIGGERS, new BitSet(),
                new int[0], HeroicStat.NONE, ATTACK, DEFENSE);
    }

    @Test
    void buildsPerTriggerIndexAndCombatDirections() {
        Ability[] abilities = {
                ab(0, 1 << 0),                 // attack only
                ab(1, 1 << 1),                 // defense only
                ab(2, (1 << 0) | (1 << 2)),    // attack + break
                ab(3, 1 << 2),                 // break only (non-combat)
        };
        WornState w = flatten(new int[]{0, 1, 2, 3}, abilities);

        assertArrayEquals(new int[]{0, 2}, w.byTrigger(0)); // fire on trigger 0
        assertArrayEquals(new int[]{1}, w.byTrigger(1));
        assertArrayEquals(new int[]{2, 3}, w.byTrigger(2));
        assertArrayEquals(new int[]{0, 2}, w.combatAttack());  // fire on an attack trigger
        assertArrayEquals(new int[]{1}, w.combatDefense());    // id 3 (break only) is in neither
        assertEquals(7, w.gen());
    }

    @Test
    void preservesMultiplicityForStackedSources() {
        Ability[] abilities = {ab(0, 1 << 0)};
        // The same crystal stacked twice → its ability id appears twice and runs twice.
        WornState w = flatten(new int[]{0, 0}, abilities);
        assertArrayEquals(new int[]{0, 0}, w.byTrigger(0));
        assertArrayEquals(new int[]{0, 0}, w.combatAttack());
    }

    @Test
    void emptyWhenNothingActive() {
        WornState w = flatten(new int[0], new Ability[0]);
        assertEquals(0, w.combatAttack().length);
        assertEquals(0, w.combatDefense().length);
        assertEquals(0, w.byTrigger(0).length);
    }

    @Test
    void emptyFactoryIsConsistent() {
        WornState w = WornState.empty(3);
        assertEquals(3, w.gen());
        assertEquals(0, w.combatAttack().length);
        assertSame(HeroicStat.NONE, w.heroic());
    }
}
