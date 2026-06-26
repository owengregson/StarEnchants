plugins {
    `java-library`
}

// ── Cross-version overlay (the OPTIONAL 1.8.9 fork) — docs/legacy-1.8.9-codeshare-design.md §1, §4 ──
// `main` is the shared, version-agnostic core. The platform SEAMS that differ between the modern
// (1.17.1→26.1.x) and legacy (1.8.9) targets live in same-FQN classes under overlay/modern and
// overlay/legacy; exactly ONE is selected into the assembled module by -Pse.target (default `modern`).
// The legacy overlay compiles against the REAL Spigot 1.8.8 server jar, so a 1.8-absent platform symbol is
// a javac error (Gate 1), never a runtime NoSuchMethodError. The default `./gradlew build` never compiles
// the legacy overlay (it is not wired into `assemble`/`check`), so the modern build needs no 1.8 jar.
val seTarget = (project.findProperty("se.target") as String?) ?: "modern"
val activeOverlay = if (seTarget == "legacy") "overlayLegacy" else "overlayModern"

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

sourceSets {
    create("overlayModern") { java.setSrcDirs(listOf("overlay/modern")) }
    create("overlayLegacy") { java.setSrcDirs(listOf("overlay/legacy")) }
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

    // Each overlay's seam impls compile against THEIR target's platform API only — that is the wall.
    "overlayModernCompileOnly"(libs.paper.api.floor)
    "overlayLegacyCompileOnly"(libs.craftbukkit.legacy) { isTransitive = false }
}

// main (and its tests) see the active overlay's seam classes; the module jar bundles them so bootstrap
// (modern) / :compat-legacy (legacy) get one coherent output.
val activeOut = sourceSets[activeOverlay].output
sourceSets["main"].compileClasspath += activeOut
sourceSets["main"].runtimeClasspath += activeOut
sourceSets["test"].compileClasspath += activeOut
sourceSets["test"].runtimeClasspath += activeOut
tasks.named<JavaCompile>("compileJava") {
    dependsOn("compile${activeOverlay.replaceFirstChar { it.uppercase() }}Java")
}
tasks.named<Jar>("jar") { from(activeOut) }

// ── Gate 1b: type-check shared item/main against the real 1.8.8 jar (no artifact) ──
// Gate 1 is the auto `compileOverlayLegacyJava` (overlay/legacy vs craftbukkit). Gate 1b extends the
// compile wall to shared `main`: any 1.8-absent symbol that slips into main becomes a javac error here,
// closing the gap between "the overlay links on 1.8" and "the shared core links on 1.8".
tasks.register<JavaCompile>("verifyLegacyMain") {
    group = "verification"
    description = "Gate 1b: type-check shared item/main against Spigot 1.8.8 (org.bukkit + v1_8_R3); no artifact."
    dependsOn("compileOverlayLegacyJava")
    source = sourceSets["main"].java
    classpath = files(
        // :compile (+ :schema) are pure (no Bukkit); keep them, drop the modern paper-api.
        configurations["compileClasspath"].filter { !it.name.contains("paper-api") },
        sourceSets["overlayLegacy"].output,                 // the legacy seam impls main calls
        configurations["overlayLegacyCompileClasspath"]     // craftbukkit 1.8.8 (org.bukkit + nms)
    )
    destinationDirectory.set(layout.buildDirectory.dir("legacy-verify/item-main"))
    options.release.set(17) // language level only; the 61→52 bytecode floor is a later, separate step
}

// The item-data service + render. ONE compact record codec over PDC (stable
// string keys), the ItemView content-hash + generation cache, the event-driven
// multi-set WornState resolver, and lore/name rendered from state — never parsed
// back (docs/architecture.md §4.2–4.3, §5).
