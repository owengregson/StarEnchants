package tester.suite;

import org.bukkit.plugin.Plugin;
import platform.caps.Capabilities;
import platform.sched.Scheduling;
import tester.harness.Harness;

/** Asserts the boot-time platform probe and the installed {@link Scheduling} backend agree. */
public final class CapabilitiesSuite implements Harness.Scenario {

    private final Plugin plugin;
    private final Capabilities caps;

    public CapabilitiesSuite(Plugin plugin, Capabilities caps) {
        this.plugin = plugin;
        this.caps = caps;
    }

    @Override
    public void accept(Harness h) {
        h.expect("caps.version");
        h.expect("caps.foliaProbe");
        h.expect("caps.backendMatchesProbe");

        h.guard("caps.version", () -> {
            if (caps.major() <= 0) {
                throw new IllegalStateException("version did not parse: " + caps);
            }
            plugin.getLogger().info("[caps] " + caps + " bukkit=" + plugin.getServer().getBukkitVersion());
        });

        h.guard("caps.foliaProbe", () -> {
            if (caps.folia() != Capabilities.foliaPresent()) {
                throw new IllegalStateException("folia flag " + caps.folia()
                        + " disagrees with marker presence " + Capabilities.foliaPresent());
            }
        });

        h.guard("caps.backendMatchesProbe", () -> {
            String backend = Scheduling.backend().getClass().getSimpleName();
            String want = caps.folia() ? "FoliaSchedulerBackend" : "BukkitSchedulerBackend";
            if (!want.equals(backend)) {
                throw new IllegalStateException("expected " + want + " for folia=" + caps.folia()
                        + " but Scheduling installed " + backend);
            }
        });
    }
}
