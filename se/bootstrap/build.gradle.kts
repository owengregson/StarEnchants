import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    `java-library`
}

// ── Cross-version overlay (the OPTIONAL 1.8.9 fork) — docs/legacy-1.8.9-codeshare-design.md §4 ──
// The composition root is ~1.8-clean (no Brigadier/Adventure/PDC — it wires interfaces); only dynamic
// command registration forks (Server.getCommandMap is absent on 1.8, reached via CraftServer). That single
// seam lives under overlay/<target>, selected as a srcDir of main by -Pse.target (default modern). So the
// 1.8 jar is THIS module built with -Pse.target=legacy (which pulls every module's legacy overlay) — there
// is no separate legacy composition-root module; the overlay mechanism shares the bootstrap too.
val legacyTarget = (project.findProperty("se.target") as String?) == "legacy"

if (legacyTarget) {
    repositories {
        mavenLocal {
            content {
                includeGroup("org.bukkit")
                includeGroup("org.spigotmc")
            }
        }
    }
}

sourceSets["main"].java.srcDir(if (legacyTarget) "overlay/legacy" else "overlay/modern")

// ── The OPTIONAL 1.8.9 jar ──
// `-Pse.target=legacy :bootstrap:jar` here is the dual-compile gate + the (Java-17) legacy fat jar: every
// module + the overlay/legacy seams compile against the REAL Spigot 1.8.8 jar, so a 1.8-absent symbol is a
// javac error (Gate 1 + Gate 1b). Lowering it to Java-8 bytecode is a multi-tool step (JvmDowngrader: lower
// 61→52 + shade its stdlib API AND runtime helpers self-contained), so the full pipeline — verified to boot
// + enable on a real craftbukkit-1.8.8 under JDK 8 (Gate 4) — lives in scripts/build-legacy-jar.sh. JDG
// handles the 126 records + sealed + switch-expressions (risk R6); the config compiler falls back to the
// no-arg SnakeYAML on 1.8's older SnakeYAML (YamlNode/LegacyYaml, §6/R2), so nothing is bundled — the legacy
// jar uses the server's libraries, like the modern jar.

// Shipped config packs (ADR-0023): each pack lives in the repo as a REVIEWABLE config tree under
// packs-src/<name>/ and is zipped at build time into packs/<name>.zip inside the jar, so the source is
// diffable in PRs while the shipped/on-disk artifact is the chosen ZIP format. First boot extracts the
// zip via packs/index.txt; /se pack apply swaps it over the live config. Add a pack by registering a
// Zip task here and listing its archive in resources/packs/index.txt. Reproducible (sorted entries,
// zeroed timestamps) so a given source tree yields a byte-identical archive.
val packCosmicPack = tasks.register<Zip>("packCosmicPack") {
    from(layout.projectDirectory.dir("packs-src/cosmic-pack"))
    archiveFileName.set("cosmic-pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated-packs"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

dependencies {
    // Floor API: the plugin loads on 1.17.1 → 26.1.x; the server provides it. The legacy lane swaps in the
    // real Spigot 1.8.8 server jar (org.bukkit + CraftServer) so the composition root is javac-checked on 1.8.
    if (legacyTarget) {
        compileOnly(libs.craftbukkit.legacy) { isTransitive = false }
    } else {
        compileOnly(libs.paper.api.floor)
    }

    // The composition root depends on every runtime layer. feature brings :engine/:item (and thus
    // :compile/:schema/:platform) transitively; they are listed explicitly for clarity of fat-jar contents.
    implementation(project(":feature"))
    implementation(project(":engine"))
    implementation(project(":item"))
    implementation(project(":platform"))
    implementation(project(":compat-folia"))
    implementation(project(":api")) // public events fired at activation/reload points
    implementation(project(":migrate")) // /se migrate imports legacy EE/EA configs
    implementation(project(":pack")) // /se pack export/apply config packs (ADR-0023)
    // §N third-party integrations, bundled + soft (ADR-0027). EXCLUDED from the 1.8 tree: the bridged plugin
    // APIs are modern-Bukkit-typed and cannot dual-compile on 1.8 (docs/legacy-1.8.9-codeshare-design.md gate
    // list), so the composition root reaches them only through the bootstrap.compat.Bridges seam, whose legacy
    // impl needs no integrate dependency. Every bridged plugin is modern-only, so nothing is lost on 1.8.9.
    if (!legacyTarget) {
        implementation(project(":integrate"))
    }

    // The catalog-validation test compiles resources/content/ through the real LibraryLoader +
    // BuiltinEffects registry; the effect kinds reference Bukkit types and YAML is parsed, both
    // server-provided at runtime (compileOnly) — so the test supplies them.
    testImplementation(libs.paper.api.floor)
    testImplementation("org.yaml:snakeyaml:2.2")
    // AddonBridge unit test: a mocked engine Sink verifies the AddonSink facade forwards each intent (ADR-0038).
    testImplementation(libs.mockito.core)
    // FakeEffectCtx (a strict, real EffectCtx) drives the bridge without a server.
    testImplementation(project(":testfx"))
}

// The bootstrap half of `./gradlew regenDocs`: rewrites the docs-site operator surface (commands /
// tiers / items / config.yml) and the content index from the real sources.
tasks.register<Test>("regenDocs") {
    group = "documentation"
    description = "Regenerate website surface.json and content/index.txt from the sources."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter {
        includeTestsMatching("*SurfaceCatalogDriftTest")
        includeTestsMatching("*ContentIndexDriftTest")
    }
    systemProperty("se.doc.regen", "true")
    systemProperty("se.index.regen", "true") // ContentIndexDriftTest's regen flag
    outputs.upToDateWhen { false }
}

// SurfaceCatalogDriftTest compares the rendered surface against the committed website/src/data/surface.json,
// read via a repo-root walk (not the classpath). Declare it as a content-hashed test input so a hand-edited
// golden invalidates the cache instead of being hidden FROM-CACHE under org.gradle.caching (the §M drift
// hole). content/index.txt needs no such declaration — it is a main-resource on the test classpath already.
tasks.named<Test>("test") {
    inputs.files(rootProject.file("website/src/data/surface.json"))
        .withPropertyName("surfaceGolden").optional()
}

// Stamp the build version into plugin.yml's ${version} placeholder, and fold the built config-pack
// archive(s) into the jar under packs/ (alongside the static packs/index.txt manifest).
tasks.named<ProcessResources>("processResources") {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
    from(packCosmicPack) {
        into("packs")
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
