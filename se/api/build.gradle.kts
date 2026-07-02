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
    // The pure DSL language definition (ParamSpec/ParamType/D) an add-on declares its effect signatures
    // with — the documented four-ways single source (§7). `api` (not implementation) so an add-on sees the
    // schema types through this one module. This is the ONLY intra-repo dependency :api has: the add-on SPI
    // is hand-curated here, NOT a re-export of :engine (ADR-0038), so :api ↮ engine has no cycle and the
    // public surface can never accidentally widen to an internal engine type. ApiBoundaryArchTest enforces it.
    api(project(":schema"))

    // Floor API: the events + SPI reference Bukkit Event/Player/Location/ItemStack; the server provides it.
    // The legacy lane swaps in the real 1.8.8 jar so the surface is javac-checked on 1.8.
    if (legacyTarget) {
        compileOnly(libs.craftbukkit.legacy) { isTransitive = false }
    } else {
        compileOnly(libs.paper.api.floor)
    }

    testImplementation(libs.paper.api.floor)
}

// PUBLIC surface ONLY: the activation/reload events (api.event), the add-on registration SPI (api.spi:
// AddonEffect/AddonSpec/AddonSink/AddonEffectCtx/AddonAffinity), and the StarEnchantsApi service with its
// read-only item/enchant queries. The SPI is a CURATED facade built on :schema, adapted to the engine by the
// bootstrap — :api depends on NOTHING else in the repo (ADR-0038; docs/architecture.md §2, §7).
