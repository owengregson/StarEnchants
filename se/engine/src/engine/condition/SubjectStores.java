package engine.condition;

import java.util.UUID;

/**
 * The UUID-keyed subject-cursor reads the populator sources from its stores — {@code %target.var.<name>%},
 * {@code %target.souls%}, {@code %target.heroicpieces%} (ADR-0076). The victim-scoped twins of all three are
 * already store reads keyed by {@code getUniqueId()}, so asking them about a third id is a map get, never an
 * entity access: this is what makes a per-target fact cheap AND Folia-safe at the same time.
 *
 * <p>Installed once per activation beside the other lazy bindings ({@link EnchantLevels}, {@link CrystalCounts});
 * the cursor only re-points WHICH id is asked about, so binding a body allocates nothing.
 */
public interface SubjectStores {

    /** No stores bound: every read is the zero value (the default, and what a synthetic activation sees). */
    SubjectStores NONE = new SubjectStores() {
        @Override
        public String var(UUID id, String name) {
            return null;
        }

        @Override
        public double souls(UUID id) {
            return 0;
        }

        @Override
        public int heroicPieces(UUID id) {
            return 0;
        }
    };

    /** A dynamic var {@code SET_VAR} wrote on {@code id}; {@code null} when unset. */
    String var(UUID id, String name);

    /** {@code id}'s cached cross-gem soul total; {@code 0} for a mob. */
    double souls(UUID id);

    /** How many of {@code id}'s four worn armour pieces carry a heroic upgrade. */
    int heroicPieces(UUID id);
}
