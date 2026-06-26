plugins {
    `java-library`
}

// ── Cross-version overlay (the OPTIONAL 1.8.9 fork) — docs/legacy-1.8.9-codeshare-design.md §4 ──
// The feature shells are ~90% version-agnostic; the few 1.9+ touch points (off-hand/main-hand item API,
// the Particle/spawnParticle API, the String playSound overload, the Paper PlayerArmorChangeEvent, the
// 1.8 knockback event shape) live in same-FQN seam classes / whole-file listeners under overlay/<target>,
// added as a srcDir of `main` by -Pse.target (default `modern`). This keeps feature SHARED rather than
// forked wholesale. `-Pse.target=legacy` compiles main + overlay/legacy against the real Spigot 1.8.8.
val legacyTarget = (project.findProperty("se.target") as String?) == "legacy"

if (legacyTarget) {
    repositories {
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
    // Floor API: feature shells touch Bukkit events/entities; the server provides it. The legacy lane swaps
    // in the real 1.8.8 server jar so the legacy feature seams are javac-checked against the 1.8 API.
    if (legacyTarget) {
        compileOnly(libs.craftbukkit.legacy) { isTransitive = false }
    } else {
        compileOnly(libs.paper.api.floor)
    }

    // The runtime + item layers a feature shell calls into. engine brings :compile/:platform/:schema
    // transitively; item brings the codec/ItemView/WornState. A feature shell is thin glue.
    api(project(":engine"))
    api(project(":item"))

    // The SE1 import codec (feature.imports, ADR-0029) parses the JSON envelope + serialises content
    // YAML with SnakeYAML. compileOnly — the server bundles it (same arrangement as :compile, no shading).
    compileOnly("org.yaml:snakeyaml:2.2")

    testImplementation(libs.paper.api.floor)
    testImplementation(libs.mockito.core)
    testImplementation("org.yaml:snakeyaml:2.2")
}

// Thin Bukkit FEATURE shells. The combat shell (feature.combat) bridges Bukkit damage/equip events
// to the engine: EquipListener resolves a player's WornState on equip change into the WornStateStore,
// and CombatListener dispatches EntityDamageByEntityEvent through CombatDispatch — gather the
// attacker/defender candidate abilities, run them through the executor into a Sink, fold the damage
// onto the event once, and flush. No business logic that isn't a call into engine/item (§2, §2.1).
