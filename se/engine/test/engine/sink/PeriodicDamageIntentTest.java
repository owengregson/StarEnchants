package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.stores.DotSuppressionStore;
import engine.stores.EngineStores;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.sched.Scheduling;
import schema.spec.PotionLoadout;
import testfx.Envs;
import testfx.SyncSchedulerBackend;

/**
 * PERIODIC_DAMAGE's two non-damage halves, both invisible to the effect-kind table (which only sees the intent
 * leave). {@code replace} must CONVERT a vanilla DoT — cancel its damage while leaving the status effect on the
 * victim — where it used to delegate to {@code potionLock} and strip it; and the per-pulse cue must actually
 * reach the world from inside the pulse, which the flushed per-event plan cannot carry.
 */
class PeriodicDamageIntentTest {

    private static final int WITHER_ID = 3;
    private static final int SPEED_ID = 4;

    private EngineStores stores;
    private RecordingSink sink;
    private Player victim;
    private UUID victimId;
    private World world;

    @BeforeEach
    void setUp() {
        Scheduling.install(new SyncSchedulerBackend());
        stores = EngineStores.fresh();
        sink = new RecordingSink(Envs.sink().stores(stores).nowTicks(() -> 0L).build());
        sink.potions.put(WITHER_ID, named("WITHER"));
        sink.potions.put(SPEED_ID, named("SPEED"));
        victimId = UUID.randomUUID();
        world = mock(World.class);
        Location at = mock(Location.class);
        when(at.getWorld()).thenReturn(world);
        victim = mock(Player.class);
        when(victim.getUniqueId()).thenReturn(victimId);
        when(victim.getLocation()).thenReturn(at);
        when(victim.isValid()).thenReturn(true);
    }

    @Test
    void replaceSuppressesTheDotDamageWithoutStrippingOrLockingTheEffect() {
        burn(List.of(PotionLoadout.pack(WITHER_ID, 0)), -1, -1);

        assertTrue(stores.dotSuppression().suppressed(victimId, 0L, DotSuppressionStore.CAUSE_WITHER),
                "the burn holds the wither window for its duration");
        // The old behaviour delegated to potionLock: a strip now, a LockedPotions deny for the window, and a
        // per-tick re-strip task. All three must be gone — the icon and its particles are the point.
        verify(victim, never()).removePotionEffect(any(PotionEffectType.class));
        assertFalse(LockedPotions.isLocked(victimId, "WITHER"), "replace must never register a potion deny");
    }

    @Test
    void replacingANonDotEffectSuppressesNothingAndStillDoesNotStrip() {
        burn(List.of(PotionLoadout.pack(SPEED_ID, 0)), -1, -1);

        assertFalse(stores.dotSuppression().suppressed(victimId, 0L, DotSuppressionStore.CAUSE_WITHER));
        assertFalse(stores.dotSuppression().suppressed(victimId, 0L, DotSuppressionStore.CAUSE_POISON));
        verify(victim, never()).removePotionEffect(any(PotionEffectType.class));
    }

    @Test
    void eachPulsePlaysTheAuthoredTickCueAtTheTarget() {
        Sound cue = mock(Sound.class);
        sink.cueSound = cue;

        burn(List.of(), 7, 9);

        // Distinct volume/pitch/count so a transposed argument cannot pass. One pulse runs inline here
        // (the sync backend fires a repeating task once), so exactly one cue of each.
        verify(world).playSound(victim.getLocation(), cue, 0.6f, 0.8f);
        assertEquals(1, sink.bursts.size());
        assertEquals(new RecordingSink.Burst(victim, 9, 20), sink.bursts.get(0));
    }

    @Test
    void anUnauthoredCueEmitsNothing() {
        sink.cueSound = mock(Sound.class);

        burn(List.of(), -1, -1);

        verify(world, never()).playSound(any(Location.class), any(Sound.class), anyFloat(), anyFloat());
        assertTrue(sink.bursts.isEmpty());
    }

    /** Run one burn on the victim, with the given replaced loadout and cue handle ids ({@code -1} = none). */
    private void burn(List<Integer> replaced, int tickSoundId, int tickParticleId) {
        sink.periodicDamage(victim, 0.0, 20, 100, replaced, "", null,
                tickSoundId, 0.6f, 0.8f, tickParticleId, 20);
        sink.flush();
    }

    private static PotionEffectType named(String name) {
        PotionEffectType type = mock(PotionEffectType.class);
        when(type.getName()).thenReturn(name);
        return type;
    }
}
