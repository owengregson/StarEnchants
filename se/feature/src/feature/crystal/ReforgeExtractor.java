package feature.crystal;

import feature.apply.GestureOutcome;
import org.bukkit.inventory.ItemStack;

/**
 * The reforge family's extraction hook (ADR-0070): on gear holding BOTH a reforge and crystals the ONE
 * Item Extractor pops the reforge FIRST, intact, back as a reforge item — the crystal extract runs only
 * when this declines. Keeps the extractor cursor claimed by {@link CrystalListener} alone (no second
 * listener on one cursor) while the reforge module owns the reforge-side economy.
 */
public interface ReforgeExtractor {

    /** Whether {@code gear} carries an applied reforge the extractor must pop first. */
    boolean carries(ItemStack gear);

    /** Pop the reforge off {@code gear}, minting it back intact; spends the extractor cursor. */
    GestureOutcome extract(ItemStack cursor, ItemStack gear);

    /** No reforge family wired (fixtures/tests): never claims, extract is an inert no-op. */
    ReforgeExtractor NONE = new ReforgeExtractor() {
        @Override
        public boolean carries(ItemStack gear) {
            return false;
        }

        @Override
        public GestureOutcome extract(ItemStack cursor, ItemStack gear) {
            return GestureOutcome.noop(null);
        }
    };
}
