package bootstrap.wire;

import java.util.Objects;
import org.bukkit.event.Listener;

/**
 * One ordered registration step (ADR-0047). The fold concatenates every enabled module's wires — that
 * concatenation IS the listener registration order (RegistryWiringTest pins it golden), the one order the
 * reordering lemma proves must be engineered.
 */
public sealed interface Wire {

    /** {@code registerEvents(listener, plugin)}. */
    record Events(Listener listener) implements Wire {
        public Events {
            Objects.requireNonNull(listener, "listener");
        }
    }

    /**
     * A registration that manages its own mechanics (a knockback probe, the Mental bridge, the anvil-rename
     * preview, a suppression hook) — runs in sequence, names itself for {@code /se modules}.
     */
    record Install(String what, Runnable run) implements Wire {
        public Install {
            Objects.requireNonNull(what, "what");
            Objects.requireNonNull(run, "run");
        }
    }

    /**
     * Materialized BY THE FOLD as the vanilla-guard listener over the OR of every module's plugin-item
     * contribution — a module cannot see the whole registry, the fold can.
     */
    record PluginItemGuard() implements Wire { }

    /**
     * Materialized BY THE FOLD as the quit sweeper over {@code EngineStores} plus every module's declared
     * player stores (the structural quit-cleanup authority).
     */
    record QuitSweep() implements Wire { }
}
