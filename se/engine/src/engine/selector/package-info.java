/**
 * Selector kinds: turning an activation into the entities an effect acts on
 * (docs/architecture.md §3.5, §7). {@link engine.selector.SelectorKind} is the SPI,
 * {@link engine.selector.SelectorCtx} the read-only resolution context, and
 * {@link engine.selector.SelectorRegistry} the explicit registry whose
 * {@code specRegistry()} view lets the pure compiler validate inline selector
 * arguments without depending on {@code se-engine}.
 */
package engine.selector;
