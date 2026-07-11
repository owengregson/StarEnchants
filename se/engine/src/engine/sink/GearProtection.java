package engine.sink;

import org.bukkit.inventory.ItemStack;

/**
 * The sink's view of on-item scroll protection (ADR-0052 {@code STRIP_SCROLL}) — a seam wired at the
 * composition root from the item-data layer's marker codec + the ADR-0040 recompose, so the engine never
 * names {@code AppliedSlot}/{@code CarrierCodec} directly. Both calls mutate/read the stack IN PLACE; the
 * caller owns the equipment write-back (worn arrays may be copies).
 */
public interface GearProtection {

    /** The inert default for non-root construction sites (tests, tester suites): nothing is protected. */
    GearProtection NONE = new GearProtection() {
        @Override
        public boolean isProtected(ItemStack stack, boolean holy) {
            return false;
        }

        @Override
        public boolean strip(ItemStack stack, boolean holy) {
            return false;
        }
    };

    /** Whether {@code stack} carries the holy ({@code true}) / white ({@code false}) protection marker. */
    boolean isProtected(ItemStack stack, boolean holy);

    /**
     * Remove the holy/white protection from {@code stack} (marker + the white guard byte) and recompose its
     * lore. Returns whether a protection was actually removed.
     */
    boolean strip(ItemStack stack, boolean holy);
}
