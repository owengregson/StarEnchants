// Third-party plugin integrations — bundled INTO the core fat jar (via :bootstrap) and active out of the
// box, but SOFT: every bridged plugin's API is compileOnly (never shaded — the plugin provides it at
// runtime) and every bridge only loads when its plugin is actually present, so no integration plugin is ever
// required (docs/decisions/0027, superseding 0017). The bridges implement the first-party SPIs in :platform
// (ProtectionProvider / EconomyProvider); the Integrations registrar selects the present ones at boot.
plugins {
    `java-library`
}

// Each bridged plugin's API repo is declared ONLY here (mavenCentral + papermc come from the root). None of
// these artifacts are bundled — they are compileOnly, so this affects the compile classpath only.
repositories {
    maven { url = uri("https://jitpack.io") }                               // Vault, Lands, FactionsUUID
    maven { url = uri("https://repo.glaremasters.me/repository/towny/") }   // Towny
    maven { url = uri("https://repo.bg-software.com/repository/api/") }      // SuperiorSkyblock2
    maven { url = uri("https://maven.enginehub.org/repo/") }                // WorldGuard (+ WorldEdit)
}

dependencies {
    // Floor API: bridges touch Bukkit events/entities; the server provides it.
    compileOnly(libs.paper.api.floor)
    // The first-party SPIs these bridges implement. Shaded into the core fat jar alongside this module.
    implementation(project(":platform"))

    // Bridged plugin APIs — provided by each plugin at runtime, NEVER bundled (compileOnly). Non-transitive
    // where a plugin's POM drags a messy graph we don't need (we reference only its own classes; Bukkit types
    // come from paper-api).
    compileOnly(libs.worldguard.bukkit)
    compileOnly(libs.vault.api)
    compileOnly(libs.towny.api) { isTransitive = false }
    compileOnly(libs.lands.api) { isTransitive = false }
    compileOnly(libs.superiorskyblock.api) { isTransitive = false }
    compileOnly(libs.factions.api) { isTransitive = false }

    // Unit tests mock each plugin's API to pin the decision wiring (mirrors the former add-on tests).
    testImplementation(project(":platform"))
    testImplementation(libs.paper.api.floor)
    testImplementation(libs.mockito.core)
    testImplementation(libs.worldguard.bukkit)
    testImplementation(libs.vault.api)
    testImplementation(libs.towny.api) { isTransitive = false }
    testImplementation(libs.lands.api) { isTransitive = false }
    testImplementation(libs.superiorskyblock.api) { isTransitive = false }
    testImplementation(libs.factions.api) { isTransitive = false }
}
