plugins {
    `java-library`
}

dependencies {
    // The PlatformResolvers facade this leaf implements lives in :compile.resolve
    // (brings :schema transitively for HandleCategory).
    api(project(":compile"))

    // Floor API (Registry/Material/… land with the Bukkit-backed resolver); the pure
    // resolution core needs none of it. compileOnly — the server provides it.
    compileOnly(libs.paper.api.floor)

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
