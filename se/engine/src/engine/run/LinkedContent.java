package engine.run;

import engine.effect.EffectKind;
import engine.effect.EffectRegistry;
import engine.selector.SelectorKind;
import engine.selector.SelectorRegistry;
import java.util.Objects;

/**
 * The effect + selector kinds the {@link AbilityExecutor} dispatches against, bound as ONE atomic reference
 * (ADR-0039). A {@link compile.model.CompiledEffect}/{@code CompiledSelector} carries a dense {@code kindId}
 * stamped against a registry build; dispatching it needs the {@link EffectKind}/{@link SelectorKind} arrays
 * from that SAME build, so they are swapped together and never as two independent {@code volatile} fields — a
 * reader can never see a torn effects-from-build-N / selectors-from-build-N+1 mix.
 *
 * <p>The registries are kept for the head-fallback path: a {@code kindId} of {@code -1} (a hand-built test
 * effect, or a brand-new add-on head in the sub-millisecond window before the executor is rebound) resolves
 * by head instead — warn-and-skip if still absent, exactly the pre-ADR-0039 behaviour.
 */
public record LinkedContent(EffectRegistry effects, SelectorRegistry selectors,
                            EffectKind[] effectsById, SelectorKind[] selectorsById) {

    public static LinkedContent of(EffectRegistry effects, SelectorRegistry selectors) {
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(selectors, "selectors");
        return new LinkedContent(effects, selectors, effects.kindsById(), selectors.selectorsById());
    }

    /** The effect kind for {@code effect}: array index on a stamped id, else a head lookup ({@code null} if unregistered). */
    EffectKind effectFor(compile.model.CompiledEffect effect) {
        int id = effect.kindId();
        if (id >= 0 && id < effectsById.length) {
            return effectsById[id];
        }
        return effects.lookup(effect.head()).orElse(null);
    }

    /** The selector kind for {@code selector}: array index on a stamped id, else a head lookup ({@code null} if unregistered). */
    SelectorKind selectorFor(compile.model.CompiledSelector selector) {
        int id = selector.kindId();
        if (id >= 0 && id < selectorsById.length) {
            return selectorsById[id];
        }
        return selectors.lookup(selector.head()).orElse(null);
    }
}
