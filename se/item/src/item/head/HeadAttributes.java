package item.head;

import org.bukkit.inventory.ItemStack;

/**
 * Copies the WORN-tooltip attribute lines ("When on Head: +2 Armor…") from a real helmet onto the mask
 * illusion's shown head (ADR-0053 §5 follow-up, 1.8.1) — an ADR-0044 era seam. A player head carries no
 * armor attributes, so without this the repainted armor slot loses the helmet's green attribute block.
 * Modern copies the helmet's EXPLICIT modifiers (a heroic piece's real GENERIC_ARMOR, ADR-0031) or, when
 * none exist, the material's vanilla defaults for the HEAD slot (name-probed — the API is absent on the
 * 1.17.1 floor, where this degrades to no attribute lines). 1.8 renders no attribute tooltips at all, so
 * the legacy binding stays {@link #NONE}.
 *
 * <p>Pure item decoration: no entity/world read, safe from any thread.
 */
public interface HeadAttributes {

    /** Inert default — the shown head carries no attribute block (1.8, and unsupported servers). */
    HeadAttributes NONE = (shownHead, realHelmet) -> { };

    /** Decorate {@code shownHead}'s meta with the worn-attribute lines {@code realHelmet} displays. */
    void copyWorn(ItemStack shownHead, ItemStack realHelmet);
}
