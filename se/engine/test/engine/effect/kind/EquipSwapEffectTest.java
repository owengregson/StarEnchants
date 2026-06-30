package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import engine.sink.Sink;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import testfx.FakeEffectCtx;

/** EQUIP_SWAP maps each slot name to its getArmorContents index and forwards to the Sink. */
class EquipSwapEffectTest {

    private static void slotMapsTo(String slot, int index) {
        Player p = mock(Player.class);
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("slot", slot).with("material", 7).with("duration", 60).targets("who", p);
        Sink sink = mock(Sink.class);
        new EquipSwapEffect().run(ctx, sink);
        verify(sink).swapEquipment(p, index, 7, 60);
    }

    @Test
    void eachArmourSlotMapsToItsArmorContentsIndex() {
        slotMapsTo("helmet", 3);
        slotMapsTo("chestplate", 2);
        slotMapsTo("leggings", 1);
        slotMapsTo("boots", 0);
    }
}
