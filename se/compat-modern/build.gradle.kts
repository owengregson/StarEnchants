plugins {
    `java-library`
}

dependencies {
    // Activated as implementation reaches this edge:
    // compileOnly(libs.paper.api.modern)
    // api(project(":platform"))
}

// Anything newer than the floor API, behind the Capabilities probe: profile/head
// API, component commands, BlockData sends, Brigadier + completions, trident/
// dispenser events (docs/architecture.md §2, §9).
