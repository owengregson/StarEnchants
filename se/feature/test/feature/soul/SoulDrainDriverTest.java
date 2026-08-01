package feature.soul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.ParticleSpec;
import compile.load.SoulGemConfig;
import compile.load.SoundCue;
import engine.stores.SoulModeStore;
import engine.stores.VarStore;
import feature.compat.Sounds;
import feature.fx.ParticleFx;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Pins Cosmic's source-code held-enchant Soul Mode billing transaction independently from proc costs. */
class SoulDrainDriverTest {

    private static SoulGemConfig config() {
        SoulGemConfig d = SoulGemConfig.defaults();
        SoulGemConfig.Drain drain = new SoulGemConfig.Drain(5, 4,
                Map.of("divine-immolation", 5, "soul-trap", 2, "hero-killer", 1, "sabotage", 2),
                new SoundCue("EAT", 0.4f, 0.2f),
                new ParticleSpec("SPELL", 0, 0, 0, 65, 0.0, 1.0, 0.5),
                new ParticleSpec("ENCHANTMENT_TABLE", 0, 0, 0, 80, 0.0, 1.0, 1.5),
                "&e&l** SOULS: &n{SOULS}&e&l **");
        return new SoulGemConfig(d.material(), d.name(), d.lore(), d.soulsPerKill(), d.soulsPerMob(),
                d.colorTiers(), d.emptyColor(), d.sounds(), d.particles(), drain);
    }

    @Test
    void addsHeldEnchantCostsAndRefreshesTheSharedOneSecondMarker() {
        UUID id = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLocation()).thenReturn(mock(Location.class));

        SoulModeStore modes = new SoulModeStore();
        modes.activate(id, id);
        SoulService souls = mock(SoulService.class);
        when(souls.carriedTotal(player)).thenReturn(100, 93);
        VarStore vars = new VarStore();
        Sounds sounds = mock(Sounds.class);
        ParticleFx particles = mock(ParticleFx.class);
        SoulDrainDriver driver = new SoulDrainDriver(souls, modes, SoulDrainDriverTest::config,
                ignored -> Map.of("divine-immolation", 3, "soul-trap", 1), vars, () -> 50L,
                sounds, particles);

        driver.drain(player);

        verify(souls).debit(player, id, 7);
        assertNotNull(vars.get(id, "last-soul-remove", 50L));
        verify(particles).spawn(player, config().drain().particle());
        SoundCue cue = config().drain().sound();
        verify(sounds).play(player, player.getLocation(), cue.name(), cue.volume(), cue.pitch());
        assertEquals(7, config().drain().costFor(Map.of("divine-immolation", 99, "soul-trap", 1)));
    }

    @Test
    void disablesBeforeChargingWhenTheFourSoulReserveIsNotMet() {
        UUID id = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        SoulModeStore modes = new SoulModeStore();
        modes.activate(id, id);
        SoulService souls = mock(SoulService.class);
        when(souls.carriedTotal(player)).thenReturn(3);
        SoulDrainDriver driver = new SoulDrainDriver(souls, modes, SoulDrainDriverTest::config,
                ignored -> Map.of("soul-trap", 1), new VarStore(), () -> 50L,
                mock(Sounds.class), mock(ParticleFx.class));

        driver.drain(player);

        verify(souls).disableEmpty(player);
        verify(souls, never()).debit(player, id, 2);
    }
}
