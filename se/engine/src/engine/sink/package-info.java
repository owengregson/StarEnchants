/**
 * The single mutation boundary (docs/architecture.md §3.5, §3.6). Effects emit
 * intents through {@link engine.sink.Sink}; the implementation batches them and routes
 * by declared {@link compile.model.Affinity} onto the correct Folia thread. Removing
 * the scheduler door from effect authors is what makes Folia-correctness structural
 * rather than a matter of discipline.
 */
package engine.sink;
