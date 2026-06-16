/**
 * Condition evaluation: the variable vocabulary and the primitive {@link
 * engine.condition.FactBuffer} the runtime evaluates a compiled condition over
 * (docs/architecture.md §3.4). {@link engine.condition.VarVocabulary} is the single
 * source of truth for slot assignment — its {@code asResolver()} view lets the pure
 * compiler lower variables to slots, and it sizes the matching {@code FactBuffer}.
 * {@link engine.condition.ConditionEvaluator} walks the lowered IR to a
 * {@link engine.condition.ConditionResult} with no parsing or allocation on the hot
 * path.
 */
package engine.condition;
