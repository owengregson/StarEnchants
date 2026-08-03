package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.stores.HeadTrophyStore;
import feature.compat.Hands;
import item.head.TexturedHeads;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

/**
 * The HEAD_TROPHY death half: which deaths spend an arm, and what the templates resolve against. The token fill
 * is the part a real bug hides in — the lore names a killer, a date and a place that only exist at the death.
 */
class HeadTrophyListenerTest {

    private final HeadTrophyStore store = new HeadTrophyStore();
    private final Hands hands = mock(Hands.class);

    /** A heads seam that mints a bare stack, so the test asserts the listener's own decisions, not Bukkit's. */
    private final TexturedHeads heads = new TexturedHeads() {
        @Override
        public ItemStack head(String base64) {
            return null;
        }

        @Override
        public ItemStack playerHead(UUID owner, String ownerName) {
            ItemStack stack = mock(ItemStack.class);
            when(stack.getItemMeta()).thenReturn(null); // no meta ⇒ the listener still drops the bare head
            return stack;
        }
    };

    private PlayerDeathEvent deathOf(UUID victimId, boolean keeping, List<ItemStack> drops, Player killer) {
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        Player victim = mock(Player.class);
        when(victim.getUniqueId()).thenReturn(victimId);
        when(victim.getName()).thenReturn("Loser");
        when(victim.getKiller()).thenReturn(killer);
        when(event.getEntity()).thenReturn(victim);
        when(event.getKeepInventory()).thenReturn(keeping);
        when(event.getDrops()).thenReturn(drops);
        return event;
    }

    @Test
    void anArmedDeathAddsTheHeadAndSpendsTheArm() {
        UUID victimId = UUID.randomUUID();
        store.arm(victimId, "Skull of {VICTIM}", "", 0L);
        List<ItemStack> drops = new ArrayList<>();

        new HeadTrophyListener(store, heads, hands).onDeath(deathOf(victimId, false, drops, null));

        assertEquals(1, drops.size(), "the head joins the drops");
        assertNull(store.consume(victimId), "and the arm is spent");
    }

    @Test
    void anUnarmedDeathAddsNothing() {
        List<ItemStack> drops = new ArrayList<>();
        new HeadTrophyListener(store, heads, hands).onDeath(deathOf(UUID.randomUUID(), false, drops, null));
        assertTrue(drops.isEmpty());
    }

    @Test
    void aKeptDeathSpendsNoArm() {
        UUID victimId = UUID.randomUUID();
        store.arm(victimId, "Skull of {VICTIM}", "", 0L);
        List<ItemStack> drops = new ArrayList<>();

        new HeadTrophyListener(store, heads, hands).onDeath(deathOf(victimId, true, drops, null));

        // Nothing drops on a kept death, so burning the trophy there would lose it to a death nobody collects.
        assertTrue(drops.isEmpty());
        assertNotNull(store.consume(victimId), "the arm survives for the next real death");
    }

    @Test
    void tokensResolveAgainstTheDeathNotTheArm() {
        Player killer = killer("Winner", 12, 64, -30, null);
        String filled = HeadTrophyListener.fill(
                "{VICTIM} by {KILLER} at {X}, {Y}, {Z} with {ITEM}", "Loser", killer, hands);
        assertTrue(filled.startsWith("Loser by Winner at 12, 64, -30 with "), filled);
        assertTrue(filled.endsWith("Fists"), "an empty hand reads as Fists, never a blank");
    }

    @Test
    void theWeaponPrefersTheHeldDisplayNameThenAPrettifiedMaterial() {
        ItemStack named = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(named.getType()).thenReturn(org.bukkit.Material.DIAMOND_SWORD);
        when(named.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("Excalibur");
        assertEquals("Excalibur", HeadTrophyListener.fill("{ITEM}", "Loser",
                killer("Winner", 0, 0, 0, named), hands));

        ItemStack plain = mock(ItemStack.class);
        when(plain.getType()).thenReturn(org.bukkit.Material.DIAMOND_SWORD);
        when(plain.getItemMeta()).thenReturn(null);
        assertEquals("Diamond Sword", HeadTrophyListener.fill("{ITEM}", "Loser",
                killer("Winner", 0, 0, 0, plain), hands));
    }

    @Test
    void aKillerLessDeathLeavesTheKillerTokensEmpty() {
        String filled = HeadTrophyListener.fill("[{KILLER}][{X}]", "Loser", null, hands);
        assertEquals("[][]", filled, "which is exactly why a killer-less head ships with no lore at all");
    }

    @Test
    void theDateTokensAreFilled() {
        String filled = HeadTrophyListener.fill("{MONTH} {DAY}, {YEAR}", "Loser", null, hands);
        assertFalse(filled.contains("{"), filled);
    }

    @Test
    void prettifyTitleCasesEveryWord() {
        assertEquals("Diamond Sword", HeadTrophyListener.prettify("DIAMOND_SWORD"));
        assertEquals("Stick", HeadTrophyListener.prettify("STICK"));
    }

    private Player killer(String name, int x, int y, int z, ItemStack held) {
        Player killer = mock(Player.class);
        when(killer.getName()).thenReturn(name);
        Location at = mock(Location.class);
        when(at.getBlockX()).thenReturn(x);
        when(at.getBlockY()).thenReturn(y);
        when(at.getBlockZ()).thenReturn(z);
        when(killer.getLocation()).thenReturn(at);
        when(hands.mainHand(killer)).thenReturn(held);
        return killer;
    }
}
