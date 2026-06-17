import org.gradle.language.jvm.tasks.ProcessResources

// The WorldGuard protection add-on — a SEPARATE plugin jar (NOT part of the core fat jar) that
// registers a platform.protect.ProtectionProvider through Bukkit's ServicesManager (docs/decisions/0017).
// It is built and compile-verified here, but is NOT installed on the live matrix (no WorldGuard runs
// there); its end-to-end behaviour is verified on a WorldGuard server, and the SPI consumption it plugs
// into is already covered by the engine's ProtectionSuite. See docs/decisions/0017.
plugins {
    `java-library`
}

// WorldGuard's API (and WorldEdit, transitively) come from EngineHub's maven — declared ONLY here, so
// the core modules never pull it.
repositories {
    maven { url = uri("https://maven.enginehub.org/repo/") }
}

dependencies {
    // The floor Bukkit API — the server provides it at runtime.
    compileOnly(libs.paper.api.floor)

    // The first-party SPI this add-on implements. StarEnchants ships the class in its fat jar and
    // provides it at runtime; the add-on declares `loadbefore: StarEnchants` (plugin.yml) both for
    // cross-plugin class access and to register before StarEnchants' boot-time provider discovery.
    // compileOnly — NEVER bundled: two copies of ProtectionProvider would be distinct Class objects and
    // the ServicesManager lookup (keyed by Class) would never find this one.
    compileOnly(project(":platform"))

    // WorldGuard's API — provided by the WorldGuard plugin at runtime; never bundled.
    compileOnly(libs.worldguard.bukkit)

    // The unit test mocks WorldGuard's RegionQuery to pin the BUILD-flag wiring (and proves the add-on
    // compiles against the real API). WorldGuard + the floor API + the SPI module (so the test classpath
    // can resolve WorldGuardProvider's ProtectionProvider supertype) are needed on the test classpath.
    testImplementation(project(":platform"))
    testImplementation(libs.paper.api.floor)
    testImplementation(libs.worldguard.bukkit)
    testImplementation(libs.mockito.core)
}

// Stamp the build version into plugin.yml's ${version} placeholder (mirrors :bootstrap).
tasks.named<ProcessResources>("processResources") {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

// The default `jar` ships ONLY this add-on's own classes + plugin.yml — platform and worldguard are
// compileOnly (provided at runtime), so there is deliberately no fat jar here.
