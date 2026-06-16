plugins {
    `java-library`
}

dependencies {
    // Floor API only (PDC/ItemStack land with the codec); compileOnly — the server
    // provides it. Pure WornState resolution needs none of it yet.
    compileOnly(libs.paper.api.floor)

    // The compiled world: Ability (for trigger masks during worn-state flattening),
    // the Snapshot's stable-key map (for the codec later). Brings :schema transitively.
    api(project(":compile"))

    testImplementation(libs.paper.api.floor)
    testImplementation(libs.mockito.core)
}

// The item-data service + render. ONE compact record codec over PDC (stable
// string keys), the ItemView content-hash + generation cache, the event-driven
// multi-set WornState resolver, and lore/name rendered from state — never parsed
// back (docs/architecture.md §4.2–4.3, §5).
