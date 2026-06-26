package tester.legacy;

import java.nio.file.Path;
import org.bukkit.plugin.java.JavaPlugin;
import platform.caps.Capabilities;
import platform.sched.Scheduling;
import tester.harness.Harness;

/**
 * The entry point for the OPTIONAL 1.8.9 lane's reduced smoke gate (docs/legacy-1.8.9-codeshare-design.md §11).
 * This is the plugin {@code main} ONLY in the legacy build (tester/build.gradle.kts swaps it in via the
 * {@code ${mainClass}} token when {@code -Pse.target=legacy}); the modern matrix uses {@link tester.SeTesterPlugin}.
 *
 * <p>It exists separately because {@code SeTesterPlugin} and the ~38 modern suites reference modern-only seams
 * ({@code ServerLoadEvent}, {@code RuntimeHandles}, …) that neither exist nor link on craftbukkit-1.8.8 — so the
 * legacy source set excludes them entirely. There is no {@code ServerLoadEvent} on 1.8 to anchor the start, so
 * the suite begins after a short warm-up delay through the {@code Scheduling} abstraction (a plain Bukkit
 * delayed task on 1.8).
 */
public final class LegacySmokePlugin extends JavaPlugin {

    /** Game-tick budget before unresolved checks are failed (20 s at 20 tps) — matches the modern harness. */
    private static final long DEADLINE_TICKS = 400L;

    @Override
    public void onEnable() {
        Capabilities caps = Capabilities.probe(getServer());
        Scheduling.init(this, caps);
        getLogger().info("[legacy-smoke] " + caps + " — scheduling backend "
                + Scheduling.backend().getClass().getSimpleName());

        // The server's working directory is the smoke run dir; test-results.txt lands there (read by legacy-smoke.sh).
        Path serverRoot = Path.of(System.getProperty("user.dir", "."));
        Harness harness = new Harness(this, serverRoot, DEADLINE_TICKS).add(new LegacySmokeSuite(this));
        // No ServerLoadEvent on 1.8: start after ~2s so the world is fully up before a fake player spawns.
        Scheduling.onGlobalLater(40L, harness::start);
    }
}
