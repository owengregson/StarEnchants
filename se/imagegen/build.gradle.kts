plugins {
    `java-library`
}

// Tool-only source set (like :tester): NOT shaded into the plugin jar. It renders item tooltips and GUIs to
// committable PNGs by REUSING the plugin's own server-free render code (item.render.LoreRenderer), so a preview
// can never drift from what the plugin actually draws (docs/screenshot-rendering.md).
dependencies {
    // paper-api on the RUNTIME classpath (not just compileOnly): the generator loads org.bukkit.Material and
    // calls LoreRenderer.lines headless — no server. Safe because this jar never ships; it is run by Gradle.
    implementation(libs.paper.api.floor)

    // The real render path the screenshots reuse: LoreRenderer / LoreStyle / Colors / CombatState (brings the
    // pure compile/schema/platform layers transitively).
    implementation(project(":item"))
    // MenuLayout — the base-9 GUI geometry the chest renderer composites against (the plugin's own defaults).
    implementation(project(":feature"))

    // Parse vanilla model JSON (JSON is a YAML subset) when resolving item/block models from a client jar.
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation(libs.paper.api.floor)
}

// Render every fixture to PNG under website/static/img/renders/. Opt-in (NOT wired into `./gradlew build`) so the
// core build stays hermetic — sprite/GUI accuracy needs vanilla textures, fetched at run time (see
// imagegen.assets.VanillaAssets): set MC_ASSETS_DIR to a local assets dir/client jar, or let it download a pinned
// client jar into build/imagegen/assets. Without assets it still renders tooltips (font is bundled) and falls back
// to flat placeholders for sprites.
tasks.register<JavaExec>("renderImages") {
    group = "documentation"
    description = "Render item-tooltip and GUI preview PNGs (reuses the plugin's own LoreRenderer)."
    mainClass.set("imagegen.Main")
    classpath = sourceSets["main"].runtimeClasspath
    // Resolve the default out path (website/static/img/renders) against the repo root, not this subproject.
    workingDir = rootProject.projectDir
    // Forward MC_ASSETS_DIR / mc version overrides to the tool JVM.
    environment("MC_ASSETS_DIR", System.getenv("MC_ASSETS_DIR") ?: "")
    System.getProperties().stringPropertyNames()
        .filter { it.startsWith("se.imagegen.") }
        .forEach { systemProperty(it, System.getProperty(it)) }
}
