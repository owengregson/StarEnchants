package item.worn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.FactMask;
import compile.model.SourceKind;
import item.codec.HeroicStat;
import java.util.BitSet;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

class WornFlattenerTest {

    // Trigger ids: 0 = attack-side, 1 = defense-side, 2 = block-break (non-combat).
    private static final IntPredicate ATTACK = t -> t == 0;
    private static final IntPredicate DEFENSE = t -> t == 1;
    private static final int TRIGGERS = 3;

    private static Ability ab(int id, int triggerMask) {
        return abMask(id, triggerMask, FactMask.ALL);
    }

    private static Ability abMask(int id, int triggerMask, FactMask factMask) {
        return new Ability(id, 0, SourceKind.ENCHANT, triggerMask, 1, 100.0, 0, 0, 0L,
                null, new CompiledEffect[0], 0, Affinity.CONTEXT_LOCAL, -1, -1, -1, -1, 0, factMask);
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

    /** ADR-0039: the per-trigger mask is the UNION of the abilities firing on that trigger; other triggers are independent. */
    @Test
    void perTriggerFactMaskUnionsTheFiringAbilities() {
        Ability[] abilities = {
                abMask(0, 1 << 0, new FactMask(0b0001L, 0L, 0L)), // attack: reads num slot 0
                abMask(1, 1 << 0, new FactMask(0b0100L, 0L, 0L)), // attack: reads num slot 2
                abMask(2, 1 << 1, new FactMask(0L, 0b0010L, 0L)), // defense: reads flag slot 1
        };
        WornState w = flatten(new int[]{0, 1, 2}, abilities);

        assertEquals(new FactMask(0b0101L, 0L, 0L), w.factMask(0)); // slots 0 and 2 unioned
        assertEquals(new FactMask(0L, 0b0010L, 0L), w.factMask(1)); // defense untouched by attack's number slots
        assertEquals(FactMask.NONE, w.factMask(2));                 // no ability fires on trigger 2
    }

    @Test
    void emptyFactoryIsConsistent() {
        WornState w = WornState.empty(3);
        assertEquals(3, w.gen());
        assertEquals(0, w.combatAttack().length);
        assertSame(HeroicStat.NONE, w.heroic());
    }
}
