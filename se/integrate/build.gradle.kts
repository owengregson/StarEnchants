// Third-party plugin integrations — bundled INTO the core fat jar (via :bootstrap) and active out of the
// box, but SOFT: every bridged plugin's API is compileOnly (never shaded — the plugin provides it at
// runtime) and every bridge only loads when its plugin is actually present, so no integration plugin is ever
// required (docs/decisions/0027, superseding 0017). The bridges implement the first-party SPIs in :platform
// (ProtectionProvider / EconomyProvider); the Integrations registrar selects the present ones at boot.
plugins {
    `java-library`
}

// ── Dual-compile gate (the OPTIONAL 1.8.9 fork) — docs/legacy-1.8.9-codeshare-design.md §6, Gate 1b ──
// `-Pse.target=legacy` swaps the Bukkit compileOnly to the real Spigot 1.8.8 jar so the bridges are
// javac-checked on 1.8. NOTE: this module is EXCLUDED from the legacy fat jar (bootstrap gates its dependency
// on `legacyTarget`) because the bridged plugin APIs are compiled against a modern Bukkit — see the gate list
// in the design doc. Kept gated so a future 1.8-safe bridge subset can be re-enabled without new wiring.
val legacyTarget = (project.findProperty("se.target") as String?) == "legacy"

// Each bridged plugin's API repo is declared ONLY here (mavenCentral + papermc come from the root). None of
// these artifacts are bundled — they are compileOnly, so this affects the compile classpath only.
repositories {
    if (legacyTarget) {
        mavenLocal {
            content {
                includeGroup("org.bukkit")
                includeGroup("org.spigotmc")
            }
        }
    }
    maven { url = uri("https://jitpack.io") }                               // Vault, Lands, FactionsUUID
    maven { url = uri("https://repo.glaremasters.me/repository/towny/") }   // Towny
    maven { url = uri("https://repo.bg-software.com/repository/api/") }      // SuperiorSkyblock2
    maven { url = uri("https://maven.enginehub.org/repo/") }                // WorldGuard (+ WorldEdit)
    maven { url = uri("https://repo.extendedclip.com/releases/") }          // PlaceholderAPI
    maven { url = uri("https://mvn.lumine.io/repository/maven-public/") }   // MythicMobs
    maven { url = uri("https://repo.oraxen.com/releases") }                 // Oraxen
    maven { url = uri("https://repo.grim.ac/snapshots") }                   // GrimAC API
}

dependencies {
    // Floor API: bridges touch Bukkit events/entities; the server provides it. The legacy lane swaps in the
    // real 1.8.8 jar so the Bukkit surface is javac-checked on 1.8 (Gate 1b).
    if (legacyTarget) {
        compileOnly(libs.craftbukkit.legacy) { isTransitive = false }
    } else {
        compileOnly(libs.paper.api.floor)
    }
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
    compileOnly(libs.placeholderapi) { isTransitive = false }
    compileOnly(libs.mythicmobs.api) { isTransitive = false }
    compileOnly(libs.itemsadder.api) { isTransitive = false }
    compileOnly(libs.oraxen.api) { isTransitive = false }
    compileOnly(libs.grim.api) { isTransitive = false }

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
    testImplementation(libs.placeholderapi) { isTransitive = false }
}
