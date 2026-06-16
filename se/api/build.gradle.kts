plugins {
    `java-library`
}

dependencies {
    // The public surface third-party plugins compile against: Bukkit events fired at activation/
    // reload points, plus the engine's registration SPI (EffectKind/EffectSpec/Sink) re-exposed via
    // `api` so an add-on sees it through this one module. Floor API only (server-provided).
    compileOnly(libs.paper.api.floor)
    api(project(":engine"))

    testImplementation(libs.paper.api.floor)
}

// PUBLIC surface ONLY: events, the registration SPI (effect/condition/trigger/
// selector/source), and read-only item/enchant queries. Add-ons compile against
// this module (docs/architecture.md §2, §7).
