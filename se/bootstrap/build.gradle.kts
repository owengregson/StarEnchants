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

// ── §7 "lowers" gate: downgrade the assembled legacy jar to Java-8 bytecode ──
// The legacy modules emit Java-17 class files (records/sealed/switch-expressions); a 1.8.9 server runs on
// JDK 8, so the jar is lowered 61→52 by JvmDowngrader, which also shades its stdlib API stubs for the JDK-9+
// calls. Produces StarEnchants-<ver>-legacy.jar. Run: `./gradlew -Pse.target=legacy downgradeLegacyJar`.
// (Verified to handle the 126 records + sealed + switch-expressions — risk R6.)
if (legacyTarget) {
    val jdgCli by configurations.creating
    dependencies { jdgCli("xyz.wagyourtail.jvmdowngrader:jvmdowngrader:1.3.6:all") }
    tasks.register<JavaExec>("downgradeLegacyJar") {
        group = "build"
        description = "Lower the legacy jar 61→52 (JvmDowngrader) + shade its stdlib API → a JDK-8-loadable jar."
        dependsOn("jar")
        classpath = files(jdgCli)
        mainClass.set("xyz.wagyourtail.jvmdg.cli.Main")
        val input = layout.buildDirectory.file("libs/bootstrap-${project.version}.jar")
        val output = layout.buildDirectory.file("libs/StarEnchants-${project.version}-legacy.jar")
        // org.bukkit/NMS/Paper are server-provided externals — JDG need not (and cannot) resolve them.
        argumentProviders.add {
            listOf("-c", "52", "-i", "org.bukkit", "-i", "net.minecraft", "-i", "com.destroystokyo",
                "downgrade", "--target", input.get().asFile.path, "-",
                "shade", "--prefix", "se_jdg", "--target", "-", output.get().asFile.path)
        }
    }
}

// Shipped config packs (ADR-0023): each pack lives in the repo as a REVIEWABLE config tree under
// packs-src/<name>/ and is zipped at build time into packs/<name>.zip inside the jar, so the source is
// diffable in PRs while the shipped/on-disk artifact is the chosen ZIP format. First boot extracts the
// zip via packs/index.txt; /se pack apply swaps it over the live config. Add a pack by registering a
// Zip task here and listing its archive in resources/packs/index.txt. Reproducible (sorted entries,
// zeroed timestamps) so a given source tree yields a byte-identical archive.
val packEliteEnchantments by tasks.registering(Zip::class) {
    from(layout.projectDirectory.dir("packs-src/elite-enchantments"))
    archiveFileName.set("elite-enchantments.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated-packs"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

dependencies {
    // Floor API: the plugin loads on 1.17.1 → 26.1.x; the server provides it. The legacy lane swaps in the
    // real Spigot 1.8.8 server jar (org.bukkit + CraftServer) so the composition root is javac-checked on 1.8.
    if (legacyTarget) {
        compileOnly(libs.craftbukkit.legacy) { isTransitive = false }
        // 1.8.8 bundles SnakeYAML ~1.15 (no LoaderOptions, the §6/R2 library skew); ship a modern SnakeYAML
        // inside the legacy jar so the config compiler's 2.x API resolves (plugin-classloader precedence).
        implementation("org.yaml:snakeyaml:2.2")
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
    implementation(project(":integrate")) // §N third-party integrations, bundled + soft (ADR-0027)

    // The catalog-validation test compiles resources/content/ through the real LibraryLoader +
    // BuiltinEffects registry; the effect kinds reference Bukkit types and YAML is parsed, both
    // server-provided at runtime (compileOnly) — so the test supplies them.
    testImplementation(libs.paper.api.floor)
    testImplementation("org.yaml:snakeyaml:2.2")
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

// Stamp the build version into plugin.yml's ${version} placeholder, and fold the built config-pack
// archive(s) into the jar under packs/ (alongside the static packs/index.txt manifest).
tasks.named<ProcessResources>("processResources") {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
    from(packEliteEnchantments) {
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
