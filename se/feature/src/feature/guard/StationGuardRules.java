package feature.guard;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * The version-agnostic decision for the vanilla-station guard (G04/G05/G06): whether a smithing / grindstone /
 * anvil operation touching a plugin SET piece or set weapon must be denied, so the vanilla station cannot bolt
 * off-design stats onto gear the pack author balanced as diamond (a netherite upgrade, a cashed-in enchant, a
 * Mending book, a free repair) while the set key / custom enchants / crystals / heroic ride along untouched.
 *
 * <p>Pure logic, floor-safe (only {@link ItemStack}/{@link Material}) so the ONE rules instance is shared by the
 * modern prepare-event guard and the 1.8 anvil-click guard alike. The set-gear test is injected (the combat
 * codec's {@code setKey}/{@code setWeaponKey} read at the composition root); the three station toggles are read
 * LIVE so a {@code /se reload} re-tunes them. Off ⇒ the station behaves vanilla again.
 */
public final class StationGuardRules {

    private final Predicate<ItemStack> isSetGear;
    private final BooleanSupplier anvilGuard;
    private final BooleanSupplier grindstoneGuard;
    private final BooleanSupplier smithingGuard;

    public StationGuardRules(Predicate<ItemStack> isSetGear, BooleanSupplier anvilGuard,
                             BooleanSupplier grindstoneGuard, BooleanSupplier smithingGuard) {
        this.isSetGear = Objects.requireNonNull(isSetGear, "isSetGear");
        this.anvilGuard = Objects.requireNonNull(anvilGuard, "anvilGuard");
        this.grindstoneGuard = Objects.requireNonNull(grindstoneGuard, "grindstoneGuard");
        this.smithingGuard = Objects.requireNonNull(smithingGuard, "smithingGuard");
    }

    /**
     * G04 smithing: deny when ANY station slot holds set gear. Scanning every slot (result included) is version-
     * agnostic across the 1.17 two-input and the 1.20+ template layout with no index maths and cannot false-
     * positive — the result is only ever set gear because an input was.
     */
    public boolean blockSmithing(ItemStack[] contents) {
        if (!smithingGuard.getAsBoolean() || contents == null) {
            return false;
        }
        for (ItemStack slot : contents) {
            if (isSetGear.test(slot)) {
                return true;
            }
        }
        return false;
    }

    /**
     * G05 grindstone: deny when either input slot holds set gear — killing the disenchant XP cash-out, the
     * vanilla-clean relaunder, and the same-material durability-merge repair in one predicate.
     */
    public boolean blockGrindstone(ItemStack top0, ItemStack top1) {
        return grindstoneGuard.getAsBoolean() && (isSetGear.test(top0) || isSetGear.test(top1));
    }

    /**
     * G06 anvil: deny a set piece combined with a sacrifice (book/material/gear repair) OR a set piece sacrificed
     * in the right slot to move its minted vanilla enchants onto other gear. A lone left-slot set piece with an
     * empty right slot is a bare rename — the exact shape of the nametag virtual anvil — so it is left to vanilla.
     */
    public boolean blockAnvil(ItemStack left, ItemStack right) {
        if (!anvilGuard.getAsBoolean()) {
            return false;
        }
        boolean rightPresent = right != null && right.getType() != Material.AIR;
        return (isSetGear.test(left) && rightPresent) || isSetGear.test(right);
    }
}
