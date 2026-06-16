plugins {
    `java-library`
}

dependencies {
    // Activated as implementation reaches this edge:
    // compileOnly(libs.folia.api)
    // api(project(":platform"))
}

// Folia region/entity/global schedulers, probed by se-platform/sched and gated
// behind the Capabilities probe. Newer than the floor API → quarantined here
// (docs/architecture.md §2, §9).
