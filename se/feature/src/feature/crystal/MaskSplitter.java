package feature.crystal;

import feature.apply.GestureOutcome;
import org.bukkit.inventory.ItemStack;

/**
 * The mask family's extraction hook (ADR-0074): the ONE Item Extractor, applied to a COMPOSITE mask item, pops
 * its topmost child back off — the Multi Crystal split (ADR-0035 §3) for masks. Folding is a 100 %-commit
 * gesture that spends the cursor, so without this a mis-fold would be permanent.
 *
 * <p>A seam for the same reason {@link ReforgeExtractor} is one: the extractor cursor stays claimed by
 * {@link CrystalListener} alone (no second listener fighting over one cursor) while the masks module keeps its
 * own economy. It answers only for mask ITEMS — a masked HELMET is popped by the mask's own right-click
 * gesture, which returns the composite whole.
 */
public interface MaskSplitter {

    /** Whether {@code stack} is a mask carrying more than one child — the only thing there is to split. */
    boolean carriesComposite(ItemStack stack);

    /** Pop the topmost child off {@code maskItem}: the item becomes the remainder, the child comes back. */
    GestureOutcome split(ItemStack maskItem);

    /** No mask family wired (fixtures/tests): never claims, split is an inert no-op. */
    MaskSplitter NONE = new MaskSplitter() {
        @Override
        public boolean carriesComposite(ItemStack stack) {
            return false;
        }

        @Override
        public GestureOutcome split(ItemStack maskItem) {
            return GestureOutcome.noop(null);
        }
    };
}
