package engine.condition;

/**
 * The per-activation reader behind {@code %actor.crystals.<key>%} / {@code %victim.crystals.<key>%}
 * (docs/architecture.md §3.4) — the {@link EnchantLevels} seam exactly, for the same reason: the crystal
 * vocabulary is the pack's, not the var vocabulary's, so the keyed family cannot own fact slots and resolves
 * LAZILY here instead, costing nothing until an expression reaches the node.
 *
 * <p>The value is how many of that side's WORN ARMOUR pieces carry the crystal — a per-piece count, which is
 * what "+N% per socketed piece" needs and what the flattened crystal ability list cannot answer.
 */
public interface CrystalCounts {

    /** No entities bound: every read is 0 (the default, and what a synthetic activation sees). */
    CrystalCounts NONE = new CrystalCounts() {
        @Override
        public int actorCount(String key) {
            return 0;
        }

        @Override
        public int victimCount(String key) {
            return 0;
        }

        @Override
        public int countOf(java.util.UUID id, String key) {
            return 0;
        }
    };

    int actorCount(String key);

    int victimCount(String key);

    /** The worn-piece count of {@code key} on ANY id — the {@link EnchantLevels#levelOf} seam, for the same
     *  reason: the subject cursor re-points this reader instead of re-populating anything (ADR-0076). */
    int countOf(java.util.UUID id, String key);
}
