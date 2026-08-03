package engine.condition;

/**
 * The per-activation reader behind {@code %actor.potion.<effect>%} / {@code %victim.potion.<effect>%}
 * (docs/architecture.md §3.4). The keyed families cannot own fact slots — the effect vocabulary is the
 * platform's, not the var vocabulary's — so like the PlaceholderAPI and entity-var tokens they resolve
 * LAZILY through this seam and cost nothing until an expression actually reaches the node.
 *
 * <p>Bukkit-free by construction: the {@code potionEffectId} is the interned handle the compiler already
 * resolved (§9), and the value is {@code amplifier + 1} — {@code 0} when the effect is absent.
 */
public interface PotionLevels {

    /** No entities bound: every read is 0 (the default, and what a synthetic activation sees). */
    PotionLevels NONE = new PotionLevels() {
        @Override
        public int actorLevel(int potionEffectId) {
            return 0;
        }

        @Override
        public int victimLevel(int potionEffectId) {
            return 0;
        }
    };

    int actorLevel(int potionEffectId);

    int victimLevel(int potionEffectId);
}
