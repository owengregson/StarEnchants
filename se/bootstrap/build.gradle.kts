import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    `java-library`
}

dependencies {
    // Floor API: the plugin loads on 1.17.1 → 26.1.x; the server provides it.
    compileOnly(libs.paper.api.floor)

    // The composition root depends on every runtime layer. engine brings :compile/:schema/:platform
    // transitively; they are listed explicitly for clarity of the fat-jar contents.
    implementation(project(":engine"))
    implementation(project(":item"))
    implementation(project(":platform"))
    implementation(project(":compat-folia"))
}

// Stamp the build version into plugin.yml's ${version} placeholder.
tasks.named<ProcessResources>("processResources") {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

// Fat jar: bundle the project classes the plugin needs (engine/item/platform/compat-folia/compile/
// schema). paper-api, folia-api and SnakeYAML are compileOnly (server-provided), so they are NOT
// shaded. The short single-segment package roots never collide, so no relocation is needed
// (docs/architecture.md §11). The shipped default content/ ships in this jar's resources and is
// copied to the data folder on first enable.
tasks.named<Jar>("jar") {
    val runtimeClasspath = configurations.runtimeClasspath
    dependsOn(runtimeClasspath)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
}

// The StarEnchants JavaPlugin — the composition root (ADR-0014): probe capabilities, init Scheduling,
// wire the production Compiler, load content/ into the published ContentHolder, and serve /se reload
// with the transactional ContentReloader. The first real plugin main (the tester is test-only).
