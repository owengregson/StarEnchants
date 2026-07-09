package feature.trak;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.TraksConfig;
import feature.compat.Hands;
import item.codec.AppliedSlot;
import item.codec.TrakCodec;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.item.ItemGroups;
import platform.lang.Messages;
import platform.sched.Scheduling;
import testfx.SyncSchedulerBackend;

/**
 * Unit-pins the F19 guard in {@link TrakService#trackKill}: after the kill hop, the credited main-hand tool
 * must still match the blow-time snapshot, or the increment is dropped (never mis-credit a weapon swapped in
 * after the fatal blow). The scheduler hop runs inline via {@link SyncSchedulerBackend}.
 */
class TrakServiceKillCreditTest {

    private final TrakCodec codec = mock(TrakCodec.class);
    private final AppliedSlot slot = mock(AppliedSlot.class);
    private final ItemGroups groups = mock(ItemGroups.class);
    private final Hands hands = mock(Hands.class);
    @SuppressWarnings("unchecked")
    private final Consumer<ItemStack> reRender = mock(Consumer.class);
    private final TrakService service =
            new TrakService(codec, slot, groups, TraksConfig::defaults, mock(Messages.class), reRender, hands);

    @BeforeEach
    void setUp() {
        Scheduling.install(new SyncSchedulerBackend());
        when(groups.matches(any(), any())).thenReturn(true); // any eligible weapon
    }

    private ItemStack tool() {
        ItemStack tool = mock(ItemStack.class);
        when(tool.getType()).thenReturn(Material.DIAMOND_SWORD);
        return tool;
    }

    @Test
    void creditsWhenTheFatalWeaponStillMatchesTheMainHand() {
        Player killer = mock(Player.class);
        ItemStack held = tool();
        when(hands.mainHand(killer)).thenReturn(held);
        ItemStack fatal = mock(ItemStack.class);
        when(fatal.isSimilar(held)).thenReturn(true);

        service.trackKill(killer, false, fatal);

        verify(codec).increment(held, TrakCodec.Kind.MOB);
    }

    @Test
    void dropsTheCreditWhenTheMainHandNoLongerMatches() {
        Player killer = mock(Player.class);
        ItemStack held = tool();
        when(hands.mainHand(killer)).thenReturn(held);
        ItemStack fatal = mock(ItemStack.class);
        when(fatal.isSimilar(held)).thenReturn(false); // a different weapon was swapped in after the blow

        service.trackKill(killer, true, fatal);

        verify(codec, never()).increment(any(), any());
        verify(hands, never()).setMainHand(any(), any());
    }
}
