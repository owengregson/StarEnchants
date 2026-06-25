/**
 * Trigger kinds and the canonical trigger vocabulary (docs/architecture.md §3.7).
 * {@link engine.trigger.TriggerKind} is the pluggable SPI; {@link
 * engine.trigger.TriggerRegistry} assigns the dense ids shared by the compiler (interning
 * content trigger names) and the runtime (routing events, classifying {@code WornState}
 * combat arrays). The Bukkit event→{@code Activation} listener is server-side.
 */
package engine.trigger;
