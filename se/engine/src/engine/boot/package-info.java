/**
 * Boot-time wiring that assembles the engine's built-in registries into the artifacts the rest of
 * the plugin needs (docs/architecture.md §2.1, §7). {@link engine.boot.ContentCompiler} is the single
 * place that wires the production content {@link compile.Compiler} from {@code BuiltinEffects} /
 * {@code BuiltinSelectors} / {@code BuiltinTriggers} / {@code BuiltinVars} + the live Bukkit-backed
 * handle resolver, so a content library compiles identically in the shipped plugin and under live test.
 */
package engine.boot;
