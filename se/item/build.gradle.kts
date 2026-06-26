plugins {
    `java-library`
}

// ── Cross-version overlay (the OPTIONAL 1.8.9 fork) — docs/legacy-1.8.9-codeshare-design.md §1, §4 ──
// `main` is the shared, version-agnostic core. The platform SEAMS that differ between the modern
// (1.17.1→26.1.x) and legacy (1.8.9) targets live in same-FQN classes under overlay/modern and
// overlay/legacy. The active overlay is added as a srcDir of `main` (selected by -Pse.target, default
// `modern`) so it compiles AS PART OF the module — exposed to downstream compile/runtime automatically,
// and dual-compiled in one pass: `-Pse.target=legacy` compiles main+overlay/legacy directly against the
// REAL Spigot 1.8.8 server jar, so a 1.8-absent platform symbol is a javac error (Gate 1+1b), not a
// runtime NoSuchMethodError. The default `./gradlew build` uses overlay/modern + paper-api and never
// touches the 1.8 jar.
val legacyTarget = (project.findProperty("se.target") as String?) == "legacy"

if (legacyTarget) {
    repositories {
        // CraftBukkit 1.8.8 (+ NMS v1_8_R3) is BuildTools-local (~/.m2), not on any public repo. Scope
        // mavenLocal to those groups so it can never shadow a real (paper/central) dependency.
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
    // Floor API only (PDC/ItemStack land with the codec); compileOnly — the server provides it. The legacy
    // lane swaps in the real 1.8.8 server jar so the overlay's NMS seams are javac-checked, not assumed.
    if (legacyTarget) {
        compileOnly(libs.craftbukkit.legacy) { isTransitive = false }
    } else {
        compileOnly(libs.paper.api.floor)
    }

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
