plugins {
    `java-library`
}

dependencies {
    // Activated as implementation reaches this module:
    // compileOnly(libs.paper.api.floor)
    // api(project(":compile"))
    // api(project(":platform"))
}

// The item-data service + render. ONE compact record codec over PDC (stable
// string keys), the ItemView content-hash + generation cache, the event-driven
// multi-set WornState resolver, and lore/name rendered from state — never parsed
// back (docs/architecture.md §4.2–4.3, §5).
