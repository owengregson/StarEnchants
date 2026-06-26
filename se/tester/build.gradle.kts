import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    `java-library`
}

dependencies {
    // Floor API: the harness plugin loads on 1.17.1 → 26.1.x; the server provides it.
    compileOnly(libs.paper.api.floor)

    // The layers under test. platform brings :compile/:schema transitively; compat-folia
    // is shaded in too so the Folia backend is present to load on a threaded-regions server.
    implementation(project(":platform"))
    implementation(project(":compat-folia"))
    implementation(project(":item"))
    // The runtime under test: the Sink dispatcher + effect/pipeline core (brings :platform/:compile
    // /:schema transitively, but they are listed explicitly above for clarity of the fat-jar contents).
    implementation(project(":engine"))
    // The combat feature shell (CombatDispatch/CombatListener) for the end-to-end combat suite.
    implementation(project(":feature"))
    // The public api (EnchantActivateEvent) for the end-to-end event check.
    implementation(project(":api"))

    // Netty for the fake-player harness's clientless connection channel (the one genuinely risky NMS
    // edge — written in real code, not reflection). compileOnly: the server bundles netty at runtime,
    // so it is NOT shaded; any 4.1.x compiles against the stable Channel/EmbeddedChannel API.
    compileOnly("io.netty:netty-transport:4.1.100.Final")
}

// The OPTIONAL 1.8.9 lane (docs/legacy-1.8.9-codeshare-design.md). The dependency modules swap to their legacy
// overlays under -Pse.target=legacy, but the ~38 modern-API suites + SeTesterPlugin reference modern-only seams
// (ServerLoadEvent, RuntimeHandles, the RuntimeHandles-arg sink factory) absent from those overlays — so they do
// not compile against 1.8.8. The legacy build therefore compiles ONLY the 1.8-safe subset (harness + fake player
// + the tester/legacy reduced smoke), driven by tester.legacy.LegacySmokePlugin; the modern build excludes that
// legacy-seam package. The tester itself stays floor-compiled + reflective (FakePlayers); the live boot is its
// 1.8-API net (scripts/legacy-smoke.sh), and the legacy DEPENDENCY jars on the classpath provide the seams.
val legacyTarget = (project.findProperty("se.target") as String?) == "legacy"
val testerMainClass = if (legacyTarget) "tester.legacy.LegacySmokePlugin" else "tester.SeTesterPlugin"
sourceSets["main"].java {
    if (legacyTarget) {
        setIncludes(listOf("tester/harness/**", "tester/fake/**", "tester/legacy/**"))
    } else {
        exclude("tester/legacy/**")
    }
}

// Stamp the build version into plugin.yml's ${version} placeholder, and bundle the bootstrap's
// shipped content catalog (incl. index.txt) so CatalogSuite can validate it live with the REAL
// cross-version resolver on each matrix server (catching handle-name typos a unit test cannot).
tasks.named<ProcessResources>("processResources") {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    inputs.property("mainClass", testerMainClass)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion, "mainClass" to testerMainClass)
    }
    from(rootProject.file("se/bootstrap/resources/content")) {
        into("content")
    }
}

// Fat jar: bundle the project classes the harness needs (platform/compat-folia/compile/
// schema). paper-api and folia-api are compileOnly, so Bukkit/Folia are NOT shaded — the
// server provides them. The short single-segment package roots (platform, compatfolia, …)
// never collide, so no relocation is needed for this test-only jar (docs/architecture.md §11).
tasks.named<Jar>("jar") {
    val runtimeClasspath = configurations.runtimeClasspath
    dependsOn(runtimeClasspath) // wire the producing :platform/:compat-folia/:compile/:schema jar tasks
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
}

// Live Paper + Folia in-server matrix harness. Boots a real server per (platform, version)
// via scripts/run-matrix.sh, installs this jar, runs in-server suites, writes a fresh
// PASS/FAIL, shuts down. A green Paper run says nothing about Folia
// (docs/architecture.md §2, §11; see the live-server-testing + matrix-gate skills).
