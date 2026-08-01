package feature.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import item.codec.CombatState;
import item.view.ItemView;
import item.view.ItemViewCache;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class CosmicSetWeaponTest {

    @Test
    void matchesTheDedicatedSetWeaponField() {
        ItemStack held = mock(ItemStack.class);
        when(held.getType()).thenReturn(Material.DIAMOND_SWORD);
        ItemViewCache views = mock(ItemViewCache.class);
        ItemView view = mock(ItemView.class);
        when(views.of(held)).thenReturn(view);
        when(view.combat()).thenReturn(CombatState.weaponMember("sets/mother-of-yijki"));

        assertTrue(CosmicSetCombatListener.weapon(held, "sets/mother-of-yijki", views));
        assertFalse(CosmicSetCombatListener.weapon(held, "sets/yeti", views));
    }

    @Test
    void armorMembershipDoesNotMasqueradeAsASetWeapon() {
        ItemStack held = mock(ItemStack.class);
        when(held.getType()).thenReturn(Material.DIAMOND_SWORD);
        ItemViewCache views = mock(ItemViewCache.class);
        ItemView view = mock(ItemView.class);
        when(views.of(held)).thenReturn(view);
        when(view.combat()).thenReturn(new CombatState(
                java.util.Map.of(), java.util.List.of(), "sets/mother-of-yijki", false));

        assertFalse(CosmicSetCombatListener.weapon(held, "sets/mother-of-yijki", views));
    }
}