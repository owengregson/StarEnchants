package tester;

import java.nio.file.Path;
import org.bukkit.plugin.java.JavaPlugin;
import platform.caps.Capabilities;
import platform.sched.Scheduling;
import tester.harness.Harness;
import tester.suite.CapabilitiesSuite;
import tester.suite.SchedulingSuite;

/**
 * The live matrix harness plugin (docs/architecture.md §11; live-server-testing, matrix-gate
 * skills). Booted inside a real Paper/Folia server by {@code scripts/run-matrix.sh}, it probes the
 * platform, installs the scheduling backend, runs the in-server suites across game ticks, writes a
 * fresh {@code test-results.txt} (PASS/FAIL), and shuts the server down. The runner then reads that
 * file and fails the gate on anything but a fresh PASS.
 *
 * <p>It deliberately self-tests the {@code platform} layer directly (no separate StarEnchants
 * plugin yet) — Scheduling/Capabilities are the foundation every later layer rides on, so they are
 * the first thing the matrix must prove on both Paper and Folia.
 */
public final class SeTesterPlugin extends JavaPlugin {

    /** Game-tick budget before unresolved checks are failed (20 s at 20 tps). */
    private static final long DEADLINE_TICKS = 400L;

    @Override
    public void onEnable() {
        Capabilities caps = Capabilities.probe(getServer());
        Scheduling.init(this, caps);
        getLogger().info("[tester] " + caps + " — scheduling backend "
                + Scheduling.backend().getClass().getSimpleName());

        // The server's working directory is the matrix run dir; results land there.
        Path serverRoot = Path.of(System.getProperty("user.dir", "."));

        Harness harness = new Harness(this, serverRoot, DEADLINE_TICKS)
                .add(new CapabilitiesSuite(this, caps))
                .add(new SchedulingSuite(this));
        harness.start();
    }
}
