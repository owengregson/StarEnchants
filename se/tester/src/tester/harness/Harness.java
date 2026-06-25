package tester.harness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;

/**
 * The in-server suite driver. Launches every {@link Scenario} on the first tick, polls each tick until all
 * declared checks resolve or the deadline hits, then writes FRESH result files and shuts down — so the runner
 * never sees a stale banner. Timing is GAME-TICK anchored, never wall-clock (correct under matrix load); the
 * result map is concurrent because checks resolve from entity/region/async threads on Folia.
 */
public final class Harness {

    /** A live scenario: declare expectations against the harness and start resolving them. */
    public interface Scenario extends Consumer<Harness> {
    }

    private final Plugin plugin;
    private final Logger log;
    private final Path resultsFile;
    private final Path failuresFile;
    private final long deadlineTicks;

    private final Set<String> expected = ConcurrentHashMap.newKeySet();
    private final Map<String, String> results = new ConcurrentHashMap<>();
    private final List<Scenario> scenarios = new ArrayList<>();

    public Harness(Plugin plugin, Path serverRoot, long deadlineTicks) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.resultsFile = serverRoot.resolve("test-results.txt");
        this.failuresFile = serverRoot.resolve("test-failures.txt");
        this.deadlineTicks = deadlineTicks;
    }

    public Harness add(Scenario scenario) {
        scenarios.add(scenario);
        return this;
    }

    /** Declare a check this run must resolve; an unresolved expectation fails at the deadline. */
    public void expect(String name) {
        expected.add(name);
    }

    /** Record a check as passed (first writer wins; an explicit {@link #fail} still overrides). */
    public void pass(String name) {
        results.merge(name, "PASS", (existing, ignored) -> existing.startsWith("FAIL") ? existing : "PASS");
        log.info("[check] PASS " + name);
    }

    /** Record a check as failed with a reason; a failure always sticks. */
    public void fail(String name, String reason) {
        results.put(name, "FAIL: " + reason);
        log.warning("[check] FAIL " + name + " — " + reason);
    }

    /**
     * Run {@code body} on the CALLING thread, recording {@code name} PASS if it returns and FAIL
     * if it throws. Used inside a scheduled callback so a wrong-region/wrong-thread access (which
     * throws on Folia) is captured as a failure rather than a silent stall.
     */
    public void guard(String name, ThrowingRunnable body) {
        try {
            body.run();
            pass(name);
        } catch (Throwable t) {
            fail(name, t.toString());
        }
    }

    /** Begin ticking: launch scenarios, then poll for completion until done or the deadline. */
    public void start() {
        final long[] tick = {0L};
        final TaskHandle[] driver = new TaskHandle[1];
        driver[0] = Scheduling.repeatingGlobal(1L, 1L, () -> {
            if (tick[0] == 0L) {
                log.info("[harness] launching " + scenarios.size() + " scenario(s)");
                for (Scenario scenario : scenarios) {
                    try {
                        scenario.accept(this);
                    } catch (Throwable t) {
                        fail("scenario.launch", t.toString());
                    }
                }
            }
            tick[0]++;
            boolean allResolved = !expected.isEmpty() && results.keySet().containsAll(expected);
            boolean timedOut = tick[0] >= deadlineTicks;
            if (allResolved || timedOut) {
                if (driver[0] != null) {
                    driver[0].cancel();
                }
                finish(timedOut && !allResolved, tick[0]);
            }
        });
    }

    private void finish(boolean timedOut, long ticksUsed) {
        // Any expected check with no recorded result timed out unresolved.
        for (String name : expected) {
            results.putIfAbsent(name, "FAIL: unresolved (timed out after " + ticksUsed + " ticks)");
        }
        boolean allPass = !results.isEmpty() && results.values().stream().allMatch("PASS"::equals);

        StringBuilder report = new StringBuilder();
        report.append(allPass ? "PASS" : "FAIL").append('\n');
        report.append("checks=").append(results.size())
                .append(" ticks=").append(ticksUsed)
                .append(timedOut ? " (deadline reached)" : "").append('\n');
        StringBuilder failures = new StringBuilder();
        results.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    report.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
                    if (!"PASS".equals(e.getValue())) {
                        failures.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
                    }
                });

        try {
            Files.writeString(resultsFile, report.toString(), StandardCharsets.UTF_8);
            if (failures.length() > 0) {
                Files.writeString(failuresFile, failures.toString(), StandardCharsets.UTF_8);
            } else {
                Files.deleteIfExists(failuresFile);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("writing test results", e);
        }

        log.info("[harness] " + (allPass ? "PASS" : "FAIL") + " — wrote " + resultsFile.toAbsolutePath());
        // Shut down on the global thread so the runner sees a clean exit + fresh result file.
        Scheduling.onGlobal(plugin.getServer()::shutdown);
    }
}
