package engine.boot;

import compile.Compiler;
import compile.cond.VarResolver;
import engine.condition.BuiltinVars;
import engine.effect.EffectRegistry;
import engine.effect.kind.BuiltinEffects;
import engine.selector.SelectorRegistry;
import engine.selector.kind.BuiltinSelectors;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import platform.resolve.RegistryResolvers;

/**
 * Wires the production content {@link Compiler} from the engine's built-in registries
 * (docs/architecture.md §2.1, §7). This is the single place that assembles the effect-spec registry
 * + per-effect affinities + default target selectors, the selector-spec registry, the condition
 * variable vocabulary, the canonical trigger order, and the live Bukkit-backed cross-version handle
 * resolver into one compiler. The shipped {@code bootstrap} plugin and the live content suite both
 * call {@link #production()}, so a content library is compiled <em>identically</em> in production and
 * under test — the live suite is then a faithful check of what the server will actually run.
 *
 * <p>Constructing the compiler resolves nothing (resolution happens during {@code compile}), so this
 * is safe to call off a server; the resolver only touches Bukkit registries when a handle token is
 * actually resolved during a load.
 */
public final class ContentCompiler {

    private ContentCompiler() {
    }

    /** A compiler wired with every built-in kind + the live Bukkit-backed handle resolver. */
    public static Compiler production() {
        EffectRegistry effects = BuiltinEffects.registry();
        SelectorRegistry selectors = BuiltinSelectors.registry();
        TriggerRegistry triggers = BuiltinTriggers.registry();
        VarResolver vars = BuiltinVars.vocabulary().asResolver();
        return Compiler.of(
                effects.specRegistry(),
                effects.affinityOf(),
                selectors.specRegistry(),
                effects.defaultSelectorOf(),
                vars,
                triggers.names(),
                new RegistryResolvers());
    }
}
