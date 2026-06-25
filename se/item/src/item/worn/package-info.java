/**
 * Worn-equipment resolution (§5.5): the immutable multi-set {@link item.worn.WornState}, the
 * omni-aware {@link item.worn.SetResolver} (§6.6), and the {@link item.worn.WornFlattener} that
 * pre-merges all sources into the per-trigger and combat-direction arrays the hot path reads. Pure;
 * the server-bound resolver reads equipment on {@code PlayerArmorChangeEvent} and feeds these.
 */
package item.worn;
