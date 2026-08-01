package feature.soul;

import compile.load.ParticleSpec;
import compile.load.SoulGemConfig;
import compile.load.SoundCue;
import engine.stores.SoulModeStore;
import engine.stores.VarStore;
import feature.compat.Sounds;
import feature.fx.ParticleFx;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;
import platform.text.Colors;

/**
 * Pack-configured, held-enchant Soul Mode upkeep. Cosmic ran this globally every five ticks, summed costs by
 * enchant presence (not level), enforced a four-soul reserve before charging, and refreshed the same one-second
 * soul-removal marker consulted by proc costs. This driver keeps that transaction Folia-safe by enumerating on
 * the global scheduler and performing inventory/feedback work on each holder's entity scheduler.
 */
public final class SoulDrainDriver {

    private static final int SHARED_COST_WINDOW_TICKS = 20;

    private final SoulService souls;
    private final SoulModeStore modes;
    private final Supplier<SoulGemConfig> config;
    private final Function<Player, Map<String, Integer>> heldEnchants;
    private final VarStore vars;
    private final LongSupplier nowTicks;
    private final Sounds sounds;
    private final ParticleFx particles;
    private TaskHandle task;

    public SoulDrainDriver(SoulService souls, SoulModeStore modes, Supplier<SoulGemConfig> config,
                           Function<Player, Map<String, Integer>> heldEnchants, VarStore vars,
                           LongSupplier nowTicks, Sounds sounds, ParticleFx particles) {
        this.souls = Objects.requireNonNull(souls, "souls");
        this.modes = Objects.requireNonNull(modes, "modes");
        this.config = Objects.requireNonNull(config, "config");
        this.heldEnchants = Objects.requireNonNull(heldEnchants, "heldEnchants");
        this.vars = Objects.requireNonNull(vars, "vars");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.particles = Objects.requireNonNull(particles, "particles");
    }

    public void start() {
        if (task == null) {
            task = Scheduling.repeatingGlobal(1L, 1L, this::tick);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    void tick() {
        SoulGemConfig.Drain drain = config.get().drain();
        long now = nowTicks.getAsLong();
        if (!drain.enabled() || Math.floorMod(now, drain.periodTicks()) != 0) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (modes.active(id).isPresent()) {
                Scheduling.onEntity(player, () -> drain(player));
            }
        }
    }

    void drain(Player player) {
        UUID id = player.getUniqueId();
        if (modes.active(id).isEmpty()) {
            return;
        }
        SoulGemConfig.Drain drain = config.get().drain();
        souls.maintain(player);
        if (modes.active(id).isEmpty()) {
            return;
        }
        int before = souls.carriedTotal(player);
        if (before < drain.reserve()) {
            souls.disableEmpty(player);
            return;
        }

        int cost = drain.costFor(heldEnchants.apply(player));
        if (cost <= 0) {
            spawnUnlessSpectating(player, drain.idleParticle());
            return;
        }

        souls.debit(player, id, cost);
        long now = nowTicks.getAsLong();
        vars.set(id, "last-soul-remove", "1", now, SHARED_COST_WINDOW_TICKS);
        spawnUnlessSpectating(player, drain.particle());
        SoundCue cue = drain.sound();
        if (cue != null) {
            sounds.play(player, player.getLocation(), cue.name(), cue.volume(), cue.pitch());
        }
        int remaining = souls.carriedTotal(player);
        if (!drain.milestoneMessage().isBlank() && remaining % 100 == 0) {
            player.sendMessage(Colors.translate(drain.milestoneMessage().replace("{SOULS}", Integer.toString(remaining))));
        }
    }

    private void spawnUnlessSpectating(Player player, ParticleSpec particle) {
        if (player.getGameMode() != GameMode.SPECTATOR && particle != null && !particle.isEmpty()) {
            particles.spawn(player, particle);
        }
    }
}
