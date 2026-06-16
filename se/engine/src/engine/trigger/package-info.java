/**
 * Trigger kinds and the canonical trigger vocabulary (docs/architecture.md §3.7).
 * {@link engine.trigger.TriggerKind} is the pluggable SPI (name + combat direction +
 * held/scans-equipment/needs-target metadata); {@link engine.trigger.TriggerRegistry}
 * assigns the dense ids both the compiler (interning content trigger names) and the
 * runtime (routing events, classifying the {@code WornState} combat arrays) share. The
 * Bukkit event→{@code Activation} listener that uses this vocabulary is server-side.
 */
package engine.trigger;
