plugins {
    `java-library`
}

dependencies {
    // Activated as implementation reaches this module:
    // compileOnly(libs.paper.api.floor)
    // api(project(":schema")) // ParamSpec + Affinity are part of the SPI
}

// PUBLIC surface ONLY: events, the registration SPI (effect/condition/trigger/
// selector/source), and read-only item/enchant queries. Add-ons compile against
// this module (docs/architecture.md §2, §7).
