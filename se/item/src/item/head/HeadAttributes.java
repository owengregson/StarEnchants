package item.head;

import org.bukkit.inventory.ItemStack;

/**
 * Copies the WORN-tooltip attribute lines ("When on Head: +2 Armor…") and the durability bar from a real
 * helmet onto the mask illusion's shown head (ADR-0053 §5 follow-up, 1.8.1) — an ADR-0044 era seam. A player
 * head carries no armor attributes or durability of its own, so without this the repainted armor slot loses
 * the helmet's green attribute block and reads as pristine to durability-display mods. Modern copies the
 * helmet's EXPLICIT modifiers (a heroic piece's real GENERIC_ARMOR, ADR-0031) or, when none exist, the
 * material's vanilla defaults for the HEAD slot, plus the helmet's max_damage + damage components (both
 * name-probed — absent APIs degrade that piece away; the 1.17.1 floor loses default attribute lines, and
 * pre-1.20.5 loses the durability mirror). 1.8 renders no attribute tooltips and no skull durability at all,
 * so the legacy binding stays {@link #NONE}.
 *
 * <p>Pure item decoration: no entity/world read, safe from any thread.
 */
public interface HeadAttributes {

    /** Inert default — the shown head carries no attribute block or durability (1.8, unsupported servers). */
    HeadAttributes NONE = (shownHead, realHelmet) -> { };

    /** Decorate {@code shownHead}'s meta with the worn-attribute lines and durability {@code realHelmet} displays. */
    void copyWorn(ItemStack shownHead, ItemStack realHelmet);
}
