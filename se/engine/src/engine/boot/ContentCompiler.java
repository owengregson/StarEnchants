package engine.boot;

import compile.Compiler;
import compile.cond.VarResolver;
import compile.resolve.PlatformResolvers;
import engine.condition.BuiltinVars;
import engine.effect.EffectRegistry;
import engine.effect.kind.BuiltinEffects;
import engine.selector.SelectorRegistry;
import engine.selector.kind.BuiltinSelectors;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import platform.resolve.RegistryResolvers;

/**
 * Wires the production content {@link Compiler} from the built-in registries. Both the shipped
 * {@code bootstrap} plugin and the live suite call {@link #production()}, so a library compiles
 * identically in production and under test. Construction resolves nothing, so it is safe off a server.
 */
public final class ContentCompiler {

    private ContentCompiler() {
    }

    /** Compiler wired with a fresh live Bukkit-backed handle resolver. */
    public static Compiler production() {
        return production(new RegistryResolvers());
    }

    /**
     * Compiler wired with the GIVEN {@code resolvers}. Bootstrap must pass the same
     * {@code RegistryResolvers} it builds the runtime {@code RuntimeHandles} from, or the §9 round-trip
     * breaks: a token interned at compile time must resolve back to its object at runtime, and a fresh
     * resolver would not know the interned ids. Reuse across reloads is safe — the reload path is single-flight.
     */
    public static Compiler production(PlatformResolvers resolvers) {
        return production(resolvers, BuiltinEffects.registry());
    }

    /**
     * As {@link #production(PlatformResolvers)}, but with the GIVEN effect registry rather than the built-in
     * one — the seam the bootstrap uses to compile add-on effect heads (ADR-0038): it passes a registry of
     * the built-ins PLUS the registered add-on effect adapters, rebuilt each reload so a newly registered
     * head becomes compilable. Selectors/triggers/vars stay the built-in set (add-ons contribute effects only).
     */
    public static Compiler production(PlatformResolvers resolvers, EffectRegistry effects) {
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
                resolvers);
    }
}
