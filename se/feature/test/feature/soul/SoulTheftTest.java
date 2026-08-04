package feature.soul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.SoulGemConfig;
import engine.interact.SoulPool;
import engine.stores.SoulModeStore;
import feature.compat.Hands;
import feature.compat.Sounds;
import feature.fx.ParticleFx;
import item.codec.SoulCodec;
import item.codec.SoulData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.sched.Scheduling;
import testfx.SyncSchedulerBackend;

/**
 * The two wave-2b soul ops on the service side: {@code SOUL_MODE_DISABLE}'s forced exit and
 * {@code SOUL_TRANSFER}'s steal. Both deliberately sidestep a rule the neighbouring verb enforces — the exit
 * skips the manual toggle's rate limit, the steal skips the soul-mode gate {@code debit} applies — so what they
 * DON'T do is as worth pinning as what they do. A stateful codec/inventory pair, because a drain that writes
 * back through the PDC is only provable by reading it back.
 */
class SoulTheftTest {

    /** One codec over many inventories: a steal reads both players' gems through the same one. */
    private final Map<ItemStack, SoulData> byStack = new HashMap<>();
    private final SoulCodec codec = mock(SoulCodec.class);

    private SoulModeStore modes;
    private SoulPool pool;

    @BeforeEach
    void setUp() {
        Scheduling.install(new SyncSchedulerBackend()); // the steal's credit half hops to the actor's thread
        modes = new SoulModeStore();
        pool = new SoulPool();
        when(codec.read(any())).thenAnswer(call -> byStack.get(call.<ItemStack>getArgument(0)));
        doAnswer(call -> {
            byStack.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(codec).write(any(), any());
    }

    /** A player whose inventory holds one gem per {@code souls} entry, from slot 0 up. */
    private Holder holder(int... souls) {
        ItemStack[] slots = new ItemStack[Math.max(4, souls.length)];
        for (int i = 0; i < souls.length; i++) {
            ItemStack stack = mock(ItemStack.class);
            byStack.put(stack, new SoulData(UUID.randomUUID(), souls[i]));
            slots[i] = stack;
        }
        PlayerInventory inv = mock(PlayerInventory.class);
        when(inv.getHeldItemSlot()).thenReturn(0);
        when(inv.getContents()).thenAnswer(call -> slots.clone());
        when(inv.getItem(anyInt())).thenAnswer(call -> slots[call.<Integer>getArgument(0)]);
        doAnswer(call -> {
            slots[call.<Integer>getArgument(0)] = call.getArgument(1);
            return null;
        }).when(inv).setItem(anyInt(), any());
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inv);
        return new Holder(player, slots);
    }

    private final class Holder {

        private final Player player;
        private final ItemStack[] slots;

        private Holder(Player player, ItemStack[] slots) {
            this.player = player;
            this.slots = slots;
        }

        int totalSouls() {
            int sum = 0;
            for (ItemStack slot : slots) {
                SoulData data = slot == null ? null : byStack.get(slot);
                sum += data == null ? 0 : data.souls();
            }
            return sum;
        }
    }

    private SoulService service() {
        return new SoulService(pool, modes, codec, SoulGemConfig::defaults, () -> true,
                platform.lang.Messages.defaults(), mock(ParticleFx.class), mock(Hands.class), Sounds.NONE);
    }

    @Test
    void aForcedExitDropsSoulModeAndTellsTheVictim() {
        Holder victim = holder(30);
        modes.activate(victim.player.getUniqueId(), victim.player.getUniqueId());
        pool.enable(victim.player.getUniqueId(), 30);

        service().disableSoulMode(victim.player);

        assertFalse(modes.isActive(victim.player.getUniqueId()));
        assertFalse(pool.isActive(victim.player.getUniqueId()), "the pool ledger is dropped with the mode");
        verify(victim.player, atLeastOnce()).sendMessage(anyString());
    }

    @Test
    void aForcedExitOnSomeoneNotInSoulModeSaysNothing() {
        // Soul Trap fires the disable unconditionally; a victim who was never in soul mode must not be told they
        // were kicked out of one — the no-op has to be silent, not merely harmless.
        Holder victim = holder(30);

        service().disableSoulMode(victim.player);

        verify(victim.player, never()).sendMessage(anyString());
    }

    @Test
    void aStealTakesUpToTheCapAndCreditsOnlyTheRatio() {
        Holder victim = holder(40, 30);
        Holder actor = holder(5);

        service().transferSouls(actor.player, victim.player, 50, 0.5, false);

        assertEquals(20, victim.totalSouls(), "min(cap, total) = 50 of 70 left the victim");
        assertEquals(5 + 25, actor.totalSouls(), "floor(0.5 x 50) arrived; the remainder was destroyed");
    }

    @Test
    void aStealNeedsNeitherPartyInSoulMode() {
        // The whole distance between this and REMOVE_SOULS, whose debit() is soul-mode gated and no-ops here.
        Holder victim = holder(20);
        Holder actor = holder(0);

        service().transferSouls(actor.player, victim.player, 100, 1.0, false);

        assertEquals(0, victim.totalSouls(), "the take ran with the switch off on both ends");
        assertEquals(20, actor.totalSouls());
    }

    @Test
    void aGemlessActorUnderDiscardLosesTheCreditRatherThanGainingAGem() {
        // The victim still pays in full: the take happens on their thread before the credit is even attempted,
        // so "the actor had nowhere to put it" can never become "the steal did not happen".
        Holder victim = holder(20);
        Holder gemless = holder();

        service().transferSouls(gemless.player, victim.player, 100, 1.0, false);

        assertEquals(0, victim.totalSouls());
        assertNull(gemless.slots[0], "overflow: discard conjures no gem");
    }

    @Test
    void aDryVictimReachesNoCreditAtAll() {
        // The credit is conditional on a take, not on the overflow flag: a 0-soul victim must not hand out a
        // free minted gem. The mint itself needs a live server ItemFactory, so this pins the branch, not the item.
        Holder victim = holder(0);
        Holder actor = holder(9);

        service().transferSouls(actor.player, victim.player, 100, 1.0, true);

        assertEquals(9, actor.totalSouls(), "nothing was taken, so nothing arrived");
    }

    @Test
    void aZeroRatioIsAPureDrainRatherThanANoOp() {
        // Soul Trap takes the gems and destroys the take, so ratio 0 is a shipped authoring value, not a
        // degenerate one: a guard that refused the whole call on it would leave the trapped victim full.
        Holder victim = holder(40);
        Holder actor = holder(7);

        service().transferSouls(actor.player, victim.player, 30, 0.0, false);

        assertEquals(10, victim.totalSouls(), "min(cap, total) = 30 left the victim");
        assertEquals(7, actor.totalSouls(), "nothing was credited");
    }

    @Test
    void aRatioThatRoundsToZeroStillCostsTheVictim() {
        // floor(0.4 x 1) == 0, and the victim's soul is still gone: the destroyed remainder IS the design, so a
        // credit-first implementation that bailed before the drain would silently refund them.
        Holder victim = holder(1);
        Holder actor = holder(7);

        service().transferSouls(actor.player, victim.player, 10, 0.4, false);

        assertEquals(0, victim.totalSouls());
        assertEquals(7, actor.totalSouls(), "nothing was credited");
    }
}
