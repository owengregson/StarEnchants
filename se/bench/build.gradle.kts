import groovy.json.JsonSlurper

plugins {
    `java-library`
    id("me.champeau.jmh") version "0.7.2"
}

// TOOL-ONLY — NEVER shipped in the plugin jar (like :tester and :imagegen; bootstrap depends on none of them).
// The JMH microbenchmarks that turn the combat hot-path budget into a build gate (docs/architecture.md §8,
// performance-hot-paths): the ActivationPipeline gate walk and AbilityExecutor effect execution over a
// synthetic in-memory Snapshot built with testfx's Defs builders + the real erase stage. `:bench:jmhCheck`
// runs a short pass and FAILS on a throughput floor or a per-op allocation budget from the GC profiler.

// Flat layout like the rest of the repo: benchmarks live in jmh/ (single-segment package `bench`), not the
// jmh plugin's default src/jmh/java, so they never overlap the convention's main srcDir of src/.
sourceSets["jmh"].java.setSrcDirs(listOf("jmh"))

jmh {
    jmhVersion.set("1.37")
    // Short mode: one quick warmup + a few timed iterations, single fork — enough for a stable-enough number
    // to gate a regression without a multi-minute run. throughput (ops/s) is the headline metric.
    warmupIterations.set(1)
    warmup.set("1s")
    iterations.set(3)
    timeOnIteration.set("1s")
    fork.set(1)
    benchmarkMode.set(listOf("thrpt"))
    // The GC profiler yields gc.alloc.rate.norm (bytes/op) — the allocation budget jmhCheck enforces.
    profilers.set(listOf("gc"))
    resultFormat.set("JSON")
    resultsFile.set(layout.buildDirectory.file("results/jmh/results.json"))
}

dependencies {
    // The runtime under test: pipeline + executor + Sink (brings :compile/:schema/:platform transitively).
    jmhImplementation(project(":engine"))
    // Defs / the erase stage seam used to build a synthetic Snapshot without a server.
    jmhImplementation(project(":testfx"))
    // Bukkit SPI types on the classpath (DispatchSink / EffectCtx signatures reference LivingEntity/Location).
    jmhImplementation(libs.paper.api.floor)
}

// ── The gate: a short pass that fails on a throughput floor OR a per-op allocation budget ──────────────────
// Calibrated once locally on 2026-07-01 (short mode: gateWalk ~129M ops/s ≈0 B/op, effectExecution ~17M ops/s
// ≈0 B/op — the ctx objects scalar-replace). The allocation budget is the reliable regression signal (a per-hit
// clone / NBT / gson parse spikes it by hundreds-to-thousands of bytes); the throughput floor is deliberately
// well under 50% of local because short-mode variance + a shared CI runner are far slower/noisier, yet the
// floors still trip on the order-of-magnitude drop a hot-path parse or map-lookup regression causes.
val throughputFloors = mapOf(
    "bench.PipelineBenchmark.gateWalk" to 3_000_000.0,      // ops/s; measured ~129M
    "bench.ExecutorBenchmark.effectExecution" to 500_000.0, // ops/s; measured ~17M (extra margin: noisier, entity path)
)
val allocBudgets = mapOf(
    "bench.PipelineBenchmark.gateWalk" to 16.0,          // bytes/op; measured ~0 (allocates nothing)
    "bench.ExecutorBenchmark.effectExecution" to 256.0,  // bytes/op; measured ~0 (headroom if the ctx objects don't scalar-replace on CI)
)

tasks.register("jmhCheck") {
    group = "verification"
    description = "Run the JMH bench (short mode) and fail on a throughput floor or per-op allocation budget."
    dependsOn("jmh")
    val resultsFile = layout.buildDirectory.file("results/jmh/results.json")
    inputs.file(resultsFile)
    doLast {
        val json = JsonSlurper().parse(resultsFile.get().asFile) as List<*>
        val failures = mutableListOf<String>()
        for (entry in json) {
            val row = entry as Map<*, *>
            val name = row["benchmark"] as String
            val primary = row["primaryMetric"] as Map<*, *>
            val score = (primary["score"] as Number).toDouble()
            val floor = throughputFloors[name]
            if (floor != null && score < floor) {
                failures += "$name throughput ${"%,.0f".format(score)} ops/s < floor ${"%,.0f".format(floor)}"
            }
            // The GC profiler reports allocation as the secondary metric "·gc.alloc.rate.norm" (bytes/op).
            val budget = allocBudgets[name]
            val secondaryMetrics = row["secondaryMetrics"] as? Map<*, *>
            val allocNorm = (secondaryMetrics?.get("·gc.alloc.rate.norm")
                ?: secondaryMetrics?.get("gc.alloc.rate.norm")) as? Map<*, *>
            if (budget != null && allocNorm != null) {
                val bytes = (allocNorm["score"] as Number).toDouble()
                if (bytes > budget) {
                    failures += "$name allocation ${"%.1f".format(bytes)} B/op > budget ${"%.1f".format(budget)}"
                }
            }
        }
        if (failures.isNotEmpty()) {
            throw GradleException("JMH gate failed:\n  " + failures.joinToString("\n  "))
        }
        logger.lifecycle("JMH gate passed: throughput floors + allocation budgets met.")
    }
}
