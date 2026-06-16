/**
 * The worn-equipment resolution: the immutable, multi-set, pre-flattened
 * {@link item.worn.WornState} (docs/architecture.md §5.5), the omni-aware
 * {@link item.worn.SetResolver} (§6.6 — the catalog's subtlest correctness rule), the
 * {@link item.worn.WornFlattener} that pre-merges all sources into the per-trigger and
 * combat-direction arrays, and {@link item.worn.HeroicStat}. Pure: the server-bound
 * resolver (reading equipment on {@code PlayerArmorChangeEvent}) collects the inputs;
 * these turn them into the snapshot the hot path reads.
 */
package item.worn;
