plugins {
    `java-library`
}

// ── Cross-version overlay (the OPTIONAL 1.8.9 fork) — docs/legacy-1.8.9-codeshare-design.md §1, §4 ──
// The version-volatile registry lookup forks: RegistrySupport (Registry/Particle/Attribute on modern;
// 1.8 getByName/valueOf on legacy) and the modern RuntimeHandles live under overlay/<target>, selected as
// a srcDir of `main` by -Pse.target (default `modern`). RenameResolvers/RegistryResolvers/Aliases stay
// shared in src/ (1.8-safe). `-Pse.target=legacy` compiles the whole module against the real Spigot 1.8.8.
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

dependencies {
    // The PlatformResolvers facade this leaf implements lives in :compile.resolve
    // (brings :schema transitively for HandleCategory).
    api(project(":compile"))

    // Floor API (Registry/Material/… land with the Bukkit-backed resolver); the pure
    // resolution core needs none of it. compileOnly — the server provides it. The legacy lane swaps in the
    // real 1.8.8 server jar so the legacy RegistrySupport is javac-checked against the 1.8 API.
    if (legacyTarget) {
        compileOnly(libs.craftbukkit.legacy) { isTransitive = false }
    } else {
        compileOnly(libs.paper.api.floor)
    }

    testImplementation(libs.paper.api.floor)

    // The ProtectionService tests mock Bukkit Player/Location/World to exercise the
    // composed gate-2 check without a server (docs/architecture.md §1.3, §3.3).
    testImplementation(libs.mockito.core)
}

// Version + Folia ABSORPTION. Boot-time resolvers (Material/Sound/Particle/
// Enchantment/PotionEffect/Attribute/EntityType), the Scheduling abstraction,
// the Capabilities probe, and the Protection/Economy SPIs. A domain-free leaf:
// no engine/feature file ever references a renamed constant or names a scheduler
// (docs/architecture.md §2, §9).
