plugins {
    `java-library`
}

// ── Dual-compile gate (the OPTIONAL 1.8.9 fork) — docs/legacy-1.8.9-codeshare-design.md §6, Gate 1b ──
// No version-forked seam here (no overlay): the public event surface is FLOOR-TYPED and 1.8-safe (§5 note 1 —
// both events extend org.bukkit.event.Event, present since 1.8). `-Pse.target=legacy` swaps the compileOnly to
// the real Spigot 1.8.8 jar so that constraint is javac-ENFORCED, not merely asserted: a modern-only type
// slipping onto an accessor becomes a compile error on the legacy lane, not a runtime break on 1.8.
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

dependencies {
    // The public surface third-party plugins compile against: Bukkit events fired at activation/
    // reload points, plus the engine's registration SPI (EffectKind/EffectSpec/Sink) re-exposed via
    // `api` so an add-on sees it through this one module. Floor API only (server-provided); the legacy
    // lane swaps in the real 1.8.8 jar so the event surface is javac-checked on 1.8.
    if (legacyTarget) {
        compileOnly(libs.craftbukkit.legacy) { isTransitive = false }
    } else {
        compileOnly(libs.paper.api.floor)
    }
    api(project(":engine"))

    testImplementation(libs.paper.api.floor)
}

// PUBLIC surface ONLY: events, the registration SPI (effect/condition/trigger/
// selector/source), and read-only item/enchant queries. Add-ons compile against
// this module (docs/architecture.md §2, §7).
