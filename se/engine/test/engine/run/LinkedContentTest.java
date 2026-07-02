package engine.run;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import engine.effect.EffectRegistry;
import engine.effect.kind.DamageEffect;
import engine.effect.kind.IgniteEffect;
import engine.selector.SelectorRegistry;
import engine.selector.kind.SelfSelector;
import engine.selector.kind.VictimSelector;
import schema.spec.Args;
import org.junit.jupiter.api.Test;

/**
 * The atomic effect+selector pair the executor dispatches against (ADR-0039). Dispatch is by dense id (array
 * index) with zero head lookup; a {@code -1} sentinel or an out-of-range id falls back to a head lookup, so a
 * hand-built or window-torn effect still resolves (or warn-and-skips) exactly as before.
 */
class LinkedContentTest {

    private static final EffectRegistry EFFECTS = EffectRegistry.builder()
            .register(new DamageEffect()).register(new IgniteEffect()).build();
    private static final SelectorRegistry SELECTORS = SelectorRegistry.builder()
            .register(new SelfSelector()).register(new VictimSelector()).build();

    private static CompiledEffect effect(String head, int kindId) {
        return new CompiledEffect(head, Args.empty(), CompiledSelector.SELF, 0, Affinity.CONTEXT_LOCAL, kindId);
    }

    @Test
    void ofPairsBothKindArraysFromTheSameBuild() {
        LinkedContent lc = LinkedContent.of(EFFECTS, SELECTORS);
        assertSame(EFFECTS.lookup("IGNITE").orElseThrow(), lc.effectsById()[EFFECTS.idOf("IGNITE")]);
        assertSame(SELECTORS.lookup("SELF").orElseThrow(), lc.selectorsById()[SELECTORS.idOf("SELF")]);
    }

    @Test
    void stampedKindIdDispatchesByIndexNotHead() {
        LinkedContent lc = LinkedContent.of(EFFECTS, SELECTORS);
        int igniteId = EFFECTS.idOf("IGNITE");

        // Head says DAMAGE but the stamped id points at IGNITE — dispatch must follow the INDEX (no head read).
        assertSame(lc.effectsById()[igniteId], lc.effectFor(effect("DAMAGE", igniteId)));
    }

    @Test
    void sentinelIdFallsBackToHeadLookup() {
        LinkedContent lc = LinkedContent.of(EFFECTS, SELECTORS);
        assertSame(EFFECTS.lookup("IGNITE").orElseThrow(), lc.effectFor(effect("IGNITE", -1)));
    }

    @Test
    void outOfRangeIdFallsBackToHeadLookupThenNullWhenAbsent() {
        LinkedContent lc = LinkedContent.of(EFFECTS, SELECTORS);
        // A brand-new add-on id in the pre-rebind window: out of this build's range → head fallback.
        assertSame(EFFECTS.lookup("DAMAGE").orElseThrow(), lc.effectFor(effect("DAMAGE", 99)));
        assertNull(lc.effectFor(effect("NOT_REGISTERED", 99))); // unknown head → warn-and-skip null
    }

    @Test
    void selectorDispatchesByIndexWithHeadFallback() {
        LinkedContent lc = LinkedContent.of(EFFECTS, SELECTORS);
        int victimId = SELECTORS.idOf("VICTIM");
        assertSame(lc.selectorsById()[victimId], lc.selectorFor(new CompiledSelector("SELF", Args.empty(), victimId)));
        assertSame(SELECTORS.lookup("SELF").orElseThrow(), lc.selectorFor(CompiledSelector.SELF)); // -1 sentinel
    }
}
