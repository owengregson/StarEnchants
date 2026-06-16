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
    // Activated as the harness grows to cover the runtime + item data:
    // implementation(project(":engine"))
    // implementation(project(":item"))
}

// Stamp the build version into plugin.yml's ${version} placeholder.
tasks.named<ProcessResources>("processResources") {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
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
