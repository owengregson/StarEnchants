plugins {
    `java-library`
}

dependencies {
    // The SchedulerBackend contract this leaf implements (brings :compile/:schema
    // transitively, harmlessly — only the sched interface is referenced).
    implementation(project(":platform"))

    // Folia's threaded-regions scheduler API. compileOnly: it is provided by a Folia
    // server at runtime and ABSENT on Paper — but FoliaSchedulerBackend is only ever
    // instantiated when the Folia marker is present, so its references never link on
    // Paper (docs/architecture.md §9; folia-scheduling skill).
    compileOnly(libs.folia.api)
}

// Folia region/entity/global schedulers, the Folia half of platform.sched. Loaded
// reflectively by Scheduling on a threaded-regions server, gated by the Capabilities
// probe. Newer than the floor API → quarantined here (docs/architecture.md §2, §9).
