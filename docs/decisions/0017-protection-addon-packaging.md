# ADR 0017: Protection/region integrations ship as separate add-on plugins

- **Status:** Accepted
- **Date:** 2026-06-16
- **Deciders:** project owner + platform work
- **Relates to:** ADR 0001 (first-party SPIs, drop bundled bridges), `platform.protect.ProtectionProvider`

## Context

StarEnchants gate 2 (`ActivationPipeline.Guard`) asks one question of any land/region plugin:
*may the player identified by `actor` have an ability act at `where`?* The architecture answers
this through a first-party SPI — `platform.protect.ProtectionProvider`, composed by
`ProtectionService`, discovered from Bukkit's `ServicesManager` — explicitly **instead of** the
brittle bundled reflection the legacy plugins (EE/EA/AE) used. A correct WorldGuard bridge also has
two properties that make it a poor fit for the core jar: it must read region state on the firing
region's thread (Folia), and it cannot be exercised on the live matrix (no WorldGuard runs there).

The open question was packaging: where does the concrete WorldGuard bridge live, and how does it
register early enough for StarEnchants to find it?

## Decision

**Protection/region integrations are separate add-on plugins, one per external plugin, that register
a `ProtectionProvider` through the `ServicesManager`. They are never part of the core jar.** The
first is `se/addon-worldguard/` (artifact `StarEnchants-WorldGuard`).

Concretely:

- **Its own plugin jar.** A new Gradle module producing a standalone plugin (its own `plugin.yml`,
  its own default `jar` — *not* a fat jar). It is the first non-`bootstrap`, non-`tester` plugin in
  the build.
- **Compiled against the real WorldGuard API**, from the EngineHub maven repo (declared only in this
  module). `worldguard-bukkit` is `compileOnly` — WorldGuard provides it at runtime, it is never
  bundled. A renamed/removed WorldGuard method is therefore a compile error here, not a silent
  fail-open in production (the legacy bridges' failure mode).
- **`compileOnly(project(":platform"))`** for the `ProtectionProvider` interface — StarEnchants ships
  that class in its fat jar and provides it at runtime. Bundling it would create two distinct
  `Class` objects and the `ServicesManager` lookup (keyed by `Class`) would never find the add-on's
  provider.
- **Load order via `plugin.yml`:** `depend: [WorldGuard]` (load only when WorldGuard is present, after
  it) and `loadbefore: [StarEnchants]` (enable — and register the provider — before StarEnchants runs
  its one-shot boot discovery; `loadbefore` also grants the add-on's classloader access to the
  StarEnchants-owned interface). `ProtectionProviders.discover` documents this register-before-enable
  requirement. Note the limits: `loadbefore` is an ordering *hint* relative to plugins that are actually
  installed (not a hard dependency), and discovery is one-shot — a provider registered *after*
  StarEnchants enables is ignored by design (no live re-discovery).
- **Provider semantics:** for an online actor, a per-player WorldGuard `BUILD` query (region members
  and region-bypassing players are allowed; non-members in a protected region are denied) — i.e. what
  WorldGuard would allow if that player placed a block. An offline/unknown actor (unresolvable to a live
  player) is **allowed**: with no player to wrap there is no way to establish region membership and so no
  way to establish a deny, matching the SPI's "protection only ever denies; allow is the default" stance.
  (Passing a `null` subject to WorldGuard instead would be read as a non-member and wrongly deny build in
  every normal member-owned region.) The method never throws (fails open, logged once).

## Verification

This add-on is **not installed on the live matrix** — WorldGuard does not run there, and (being
Paper-only and not Folia-aware) it is hard-depended on WorldGuard so it would not load on Folia
anyway. Its correctness rests on three legs:

- **Compilation** against the real WorldGuard API (CI's `./gradlew build`) — proves the API usage.
- **A unit test** (`WorldGuardProviderTest`) pinning the BUILD-flag decision against a mocked
  `RegionQuery`.
- **The engine's `ProtectionSuite`** (already in the matrix) — proves a registered `ProtectionProvider`
  actually gates an activation by location through gate 2, with a fake provider standing in for the
  bridge.

End-to-end behaviour with WorldGuard installed is verified on a WorldGuard server, out of this repo.

## Consequences

- Adding another integration (GriefPrevention, Towny, Lands, …) is a parallel module — no core change.
- The core jar stays free of any land-plugin dependency, reflection, or maven repo.
- The matrix gate does not cover the add-on's WorldGuard-specific code; that gap is explicit and
  accepted (the alternative — bundling a WorldGuard server into the matrix — is disproportionate for a
  thin, compile-checked bridge).
