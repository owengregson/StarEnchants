package feature.soul;

import compile.load.ParticleSpec;
import compile.load.SoulGemConfig;
import engine.stores.SoulModeStore;
import feature.fx.ParticleFx;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;

/**
 * §D soul-gem while-active periodic tick. One global repeating task (not per-player) reading the live
 * {@link SoulModeStore} each tick. For every player in soul mode it (1) enforces the active gem — auto-disabling
 * soul mode when that gem has left the inventory or drained to zero ({@link SoulService#enforceActiveGem}) — then
 * (2) spawns the while-active aura if one is configured. Folia-correct: runs on the global thread (where
 * enumerating players is safe), then hops to each active player's region via {@link Scheduling#onEntity}.
 */
public final class SoulParticleDriver {

    // ambient aura + gem-presence check: a coarse period keeps the per-tick cost low (sub-second responsiveness)
    private static final int PERIOD_TICKS = 10;

    private final SoulService souls;
    private final SoulModeStore modes;
    private final Supplier<SoulGemConfig> config;
    private final ParticleFx fx;
    private TaskHandle task;

    public SoulParticleDriver(SoulService souls, SoulModeStore modes, Supplier<SoulGemConfig> config, ParticleFx fx) {
        this.souls = Objects.requireNonNull(souls, "souls");
        this.modes = Objects.requireNonNull(modes, "modes");
        this.config = Objects.requireNonNull(config, "config");
        this.fx = Objects.requireNonNull(fx, "fx");
    }

    /** Start the aura loop (idempotent). */
    public void start() {
        if (task == null) {
            task = Scheduling.repeatingGlobal(PERIOD_TICKS, PERIOD_TICKS, this::tick);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        ParticleSpec idle = config.get().particles().idle();
        for (Player player : Bukkit.getOnlinePlayers()) {
            // maintain() runs for EVERY player: the zero-gem cleanup invariant is global, not soul-mode-only. It
            // then (in soul mode) re-points the drain target at the least-souls gem or auto-disables when none
            // remain. Hop to each player's region thread (where reading/mutating their inventory is Folia-safe).
            Scheduling.onEntity(player, () -> {
                souls.maintain(player);
                // re-check: maintain() may have just auto-disabled; only aura a still-active player
                if (!idle.isEmpty() && modes.active(player.getUniqueId()).isPresent()) {
                    fx.spawn(player, idle);
                }
            });
        }
    }
}
