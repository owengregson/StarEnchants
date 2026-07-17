package feature.guard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * The pure station-guard truth table (G04/G05/G06): a fake set-gear predicate keyed off a marker material lets
 * this exercise every accept/deny branch and each live toggle without a server. The three modern listeners and
 * the one legacy listener are thin shells over these decisions, so this is the single source of truth for them.
 */
class StationGuardRulesTest {

    // treat one material as "set gear"; everything else is plain; null/AIR is empty
    private static final Material SET = Material.DIAMOND_HELMET;
    private static final Predicate<ItemStack> IS_SET_GEAR =
            stack -> stack != null && stack.getType() == SET;

    private static ItemStack item(Material type) {
        ItemStack stack = mock(ItemStack.class);
        lenient().when(stack.getType()).thenReturn(type);
        return stack;
    }

    private static final ItemStack SET_GEAR = item(SET);
    private static final ItemStack PLAIN = item(Material.DIAMOND);
    private static final ItemStack BOOK = item(Material.ENCHANTED_BOOK);

    private static StationGuardRules rules(boolean anvil, boolean grindstone, boolean smithing) {
        return new StationGuardRules(IS_SET_GEAR, () -> anvil, () -> grindstone, () -> smithing);
    }

    private static StationGuardRules allOn() {
        return rules(true, true, true);
    }

    // ── smithing (G04): any slot holding set gear blocks ──────────────────────────────────────────

    @Test
    void smithingBlocksWhenAnySlotIsSetGear() {
        assertTrue(allOn().blockSmithing(new ItemStack[]{PLAIN, SET_GEAR, null}));
        assertTrue(allOn().blockSmithing(new ItemStack[]{SET_GEAR, PLAIN, PLAIN}));
    }

    @Test
    void smithingPassesWithNoSetGearOrEmptyOrNullContents() {
        assertFalse(allOn().blockSmithing(new ItemStack[]{PLAIN, PLAIN, null}));
        assertFalse(allOn().blockSmithing(new ItemStack[]{null, null}));
        assertFalse(allOn().blockSmithing(null));
    }

    @Test
    void smithingGuardOffPassesEverything() {
        assertFalse(rules(true, true, false).blockSmithing(new ItemStack[]{SET_GEAR, SET_GEAR}));
    }

    // ── grindstone (G05): either input slot holding set gear blocks ────────────────────────────────

    @Test
    void grindstoneBlocksFromEitherSlot() {
        assertTrue(allOn().blockGrindstone(SET_GEAR, null));   // solo disenchant cash-out
        assertTrue(allOn().blockGrindstone(null, SET_GEAR));   // merge-repair from the other slot
        assertTrue(allOn().blockGrindstone(SET_GEAR, PLAIN));
    }

    @Test
    void grindstonePassesWithNoSetGearAndWhenGuardOff() {
        assertFalse(allOn().blockGrindstone(PLAIN, null));
        assertFalse(allOn().blockGrindstone(null, null));
        assertFalse(rules(true, false, true).blockGrindstone(SET_GEAR, SET_GEAR));
    }

    // ── anvil (G06): set gear + sacrifice, or set-gear sacrifice; rename-only allowed ──────────────

    @Test
    void anvilBlocksSetGearWithASacrifice() {
        assertTrue(allOn().blockAnvil(SET_GEAR, BOOK));    // book combine (Mending etc.)
        assertTrue(allOn().blockAnvil(SET_GEAR, PLAIN));   // material repair / gear combine
    }

    @Test
    void anvilBlocksASetGearSacrificeInTheRightSlot() {
        assertTrue(allOn().blockAnvil(PLAIN, SET_GEAR));   // move minted vanilla enchants onto other gear
    }

    @Test
    void anvilAllowsRenameOnlyOfSetGear() {
        // lone set piece, empty right slot — the nametag virtual-anvil shape; must stay allowed
        assertFalse(allOn().blockAnvil(SET_GEAR, null));
        assertFalse(allOn().blockAnvil(SET_GEAR, item(Material.AIR)));
    }

    @Test
    void anvilAllowsPlainCombinesAndWhenGuardOff() {
        assertFalse(allOn().blockAnvil(PLAIN, BOOK));      // plain gear + book is off-limits to us
        assertFalse(rules(false, true, true).blockAnvil(SET_GEAR, BOOK));
    }

    // ── pluginValueGear (ADR-0064): the composition-root predicate, single-sourced ────────────────

    @Test
    void pluginValueGearGuardsMaskCrystalsAndSetsButNotPlainEnchantedGear() {
        java.util.Map<ItemStack, item.codec.CombatState> state = new java.util.IdentityHashMap<>();
        ItemStack masked = item(Material.IRON_HELMET);
        ItemStack crystalled = item(Material.IRON_CHESTPLATE);
        ItemStack setPiece = item(Material.IRON_LEGGINGS);
        ItemStack enchantedOnly = item(Material.IRON_BOOTS);
        state.put(masked, new item.codec.CombatState(java.util.Map.of(), java.util.List.of(), null, null,
                false, item.codec.HeroicStat.NONE, 0, "masks/blaze"));
        state.put(crystalled, new item.codec.CombatState(java.util.Map.of(), java.util.List.of("crystals/dark")));
        state.put(setPiece, new item.codec.CombatState(java.util.Map.of(), java.util.List.of(), "sets/x", false));
        state.put(enchantedOnly, new item.codec.CombatState(java.util.Map.of("enchants/haste", 1), java.util.List.of()));
        Predicate<ItemStack> gear =
                StationGuardRules.pluginValueGear(s -> state.getOrDefault(s, item.codec.CombatState.EMPTY));

        assertTrue(gear.test(masked));
        assertTrue(gear.test(crystalled));
        assertTrue(gear.test(setPiece));
        assertFalse(gear.test(enchantedOnly)); // plain custom-enchant gear stays vanilla-flexible
        assertFalse(gear.test(null));
    }
}
