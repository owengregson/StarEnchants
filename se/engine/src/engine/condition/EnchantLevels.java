package engine.condition;

/**
 * The per-activation reader behind {@code %actor.enchlevel.<key>%} / {@code %victim.enchlevel.<key>%}
 * (docs/architecture.md §3.4). The keyed families cannot own fact slots — the enchant vocabulary is the
 * pack's, not the var vocabulary's — so like the PlaceholderAPI and entity-var tokens they resolve LAZILY
 * through this seam and cost nothing until an expression actually reaches the node.
 *
 * <p>Bukkit-free by construction: the {@code key} is the lower-cased enchant stem the compiler lowered, and
 * the value is the highest level that side wears — {@code 0} when the enchant is not worn.
 */
public interface EnchantLevels {

    /** No entities bound: every read is 0 (the default, and what a synthetic activation sees). */
    EnchantLevels NONE = new EnchantLevels() {
        @Override
        public int actorLevel(String key) {
            return 0;
        }

        @Override
        public int victimLevel(String key) {
            return 0;
        }
    };

    int actorLevel(String key);

    int victimLevel(String key);
}
