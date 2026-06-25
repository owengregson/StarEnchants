/**
 * The single mutation boundary (docs/architecture.md §3.5, §3.6). Effects emit intents through
 * {@link engine.sink.Sink}; the implementation batches them and routes onto the owning Folia thread.
 * Removing the scheduler door from effect authors makes Folia-correctness structural, not disciplinary.
 */
package engine.sink;
