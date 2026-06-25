package feature.soul;

import compile.load.SoulGemConfig;
import engine.stores.SoulModeStore;
import feature.fx.ParticleFx;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;

/**
 * §D soul-gem while-active particle aura. One global repeating task (not per-player) so it needs no
 * toggle/join/quit bookkeeping — it reads the live {@link SoulModeStore} each tick. Folia-correct: the task
 * runs on the global thread (where enumerating players is safe), then hops to each active player's region via
 * {@link Scheduling#onEntity} to spawn at them.
 */
public final class SoulParticleDriver {

    // ambient aura: a coarse period keeps the per-tick cost low
    private static final int PERIOD_TICKS = 10;

    private final SoulModeStore modes;
    private final Supplier<SoulGemConfig> config;
    private final ParticleFx fx;
    private TaskHandle task;

    public SoulParticleDriver(SoulModeStore modes, Supplier<SoulGemConfig> config, ParticleFx fx) {
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
        List<String> tokens = config.get().particlesActive();
        if (tokens.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (modes.active(player.getUniqueId()).isPresent()) {
                Scheduling.onEntity(player, () -> fx.spawn(player, tokens, 1));
            }
        }
    }
}
