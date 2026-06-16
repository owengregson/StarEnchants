plugins {
    `java-library`
}

dependencies {
    // Activated as implementation reaches this leaf:
    // compileOnly(libs.paper.api.floor)
    // api(project(":schema")) // implements the PlatformResolvers facade
}

// Version + Folia ABSORPTION. Boot-time resolvers (Material/Sound/Particle/
// Enchantment/PotionEffect/Attribute/EntityType), the Scheduling abstraction,
// the Capabilities probe, and the Protection/Economy SPIs. A domain-free leaf:
// no engine/feature file ever references a renamed constant or names a scheduler
// (docs/architecture.md §2, §9).
