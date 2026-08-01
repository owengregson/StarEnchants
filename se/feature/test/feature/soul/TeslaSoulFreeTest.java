package feature.soul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.load.SoulGemConfig;
import engine.interact.SoulPool;
import engine.stores.SoulModeStore;
import engine.stores.VarStore;
import feature.compat.Hands;
import feature.compat.Sounds;
import feature.fx.ParticleFx;
import item.codec.SoulCodec;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class TeslaSoulFreeTest {

    @Test
    void timedTeslaWindowWaivesNativeSpendsAndDebits() {
        UUID id = UUID.randomUUID();
        AtomicLong now = new AtomicLong(100);
        VarStore vars = new VarStore();
        vars.set(id, "soul-free", "1", now.get(), 40);
        SoulService souls = service(vars, now);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);

        assertTrue(souls.costFree(player));
        assertTrue(souls.trySpend(id, 8_000),
                "a native mechanic can activate without soul mode or a balance");
        assertEquals(8_000, souls.drainUpTo(player, 8_000),
                "the caller sees its requested cost as satisfied while no gems are touched");
        souls.debit(player, id, 8_000); // returns before any inventory access

        now.set(140);
        assertFalse(souls.costFree(player), "the VarStore TTL is the authoritative end of the buff");
        assertFalse(souls.trySpend(id, 1), "after expiry the ordinary soul-mode/balance gate is restored");
    }

    private static SoulService service(VarStore vars, AtomicLong now) {
        return new SoulService(new SoulPool(), new SoulModeStore(), mock(SoulCodec.class),
                SoulGemConfig::defaults, () -> true, platform.lang.Messages.defaults(),
                mock(ParticleFx.class), mock(Hands.class), Sounds.NONE,
                null, stack -> java.util.List.of(), vars, now::get);
    }
}
