# ADR 0027: Integrations are bundled in the core jar, soft and optional

- **Status:** Accepted
- **Date:** 2026-06-24
- **Deciders:** project owner + agent
- **Supersedes:** ADR 0017 (protection/region integrations as separate add-on plugins)
- **Relates to:** §N integrations (docs/v3-directives.md), ADR 0026 (Mental knockback), the
  `platform.protect.ProtectionProvider` / `platform.economy.EconomyProvider` SPIs

## Context

ADR 0017 shipped each third-party integration as its own separate plugin jar (the first was
`StarEnchants-WorldGuard`), registering a provider through Bukkit's `ServicesManager`. In practice that
means an operator must download and install a *second* jar per integration, and keep its version in step
with the core — friction for a plugin whose whole point is being one universal artifact.

The project owner's direction: **every integration ships inside the one big plugin jar and works out of the
box when the other plugin is present — but no other plugin is ever required.** One download, integrations
"just work," nothing mandatory.

## Decision

**All third-party integrations live in the core fat jar, in the `se/integrate` module, and are SOFT:** each
bridge compiles against its plugin's real API as `compileOnly` (so the API is never bundled), and only ever
loads when that plugin is actually present. No integration plugin is a hard or soft *requirement* — they are
`softdepend` only (a load-order hint), so StarEnchants runs identically with none of them installed.

Concretely:

- **One module, `se/integrate`**, shaded into the bootstrap fat jar like every other core module. It holds
  the protection bridges (`WorldGuardProvider`, `TownyProvider`, `LandsProvider`, `SuperiorSkyblockProvider`,
  `FactionsProvider`), the economy bridge (`VaultEconomyProvider`), and an `Integrations` registrar.
- **`compileOnly` plugin APIs, never shaded.** Each plugin's API (and its maven repo) is declared only in
  `se/integrate`. The bootstrap fat jar shades `runtimeClasspath`, which excludes `compileOnly` — so the jar
  contains the bridge classes but **zero** plugin-API classes (verified: `com/sk89q`, `net/milkbowl`,
  `com/palmergames`, `me/angeschossen`, `com/bgsoftware`, `com/massivecraft` are all absent from the jar).
  Compiling against the *real* API (not reflection) keeps ADR 0017's best property: a renamed/removed plugin
  method is a compile error here, not a silent fail-open in production.
- **Lazy classloading is the soft mechanism.** A bridge class references its plugin's API types, which exist
  on the classpath only when that plugin is installed. The `Integrations` registrar guards each bridge with
  a string-only presence check (`PluginManager.getPlugin(name)`), and every bridge exposes a static factory
  (`create()` / `fromServices()`) declared to return the first-party SPI *interface*. So the JVM never needs
  to load a bridge class — and thus never resolves an absent plugin's API — to verify or run the registrar; a
  bridge loads only when its guarded factory call executes, i.e. only when its plugin is present. This is
  what lets the one jar carry every integration with no hard dependency and no `NoClassDefFoundError` on a
  server missing a plugin.
- **The SPI stays open.** The composition root unions the bundled providers with any `ProtectionProvider` /
  `EconomyProvider` still registered externally through the `ServicesManager`, so third parties can add their
  own without forking the core.
- **Config + toggles.** `config.yml` `integrations.protection` / `integrations.economy` gate discovery as
  before; `integrations.named.<id>: false` disables an individual bridge (e.g. `mental`, `worldguard`,
  `vault`). All read once at boot (consistent with the other boot-time toggles).
- **Mental** (ADR 0026) follows the same model — bundled, soft, reflective — though it lives in
  `feature.combat` because it shares the live `KnockbackControlStore` rather than implementing an SPI.

## Amendment (2026-07-01): two sanctioned bridge shapes

In practice `se/integrate/Integrations` grew **two** bridge shapes, both soft, differing only in what the
registrar hands back and where the presence guard lives:

1. **SPI shape** — the bridge implements a first-party interface (`ProtectionProvider` / `EconomyProvider`)
   returned from a static factory (`create()` / `fromServices()`). The registrar guards it with the
   string-only `active(plugin, enabled, PluginName, configKey)` presence check *before* touching the
   factory, so the bridge class (which references the plugin's API) only loads when that plugin is present.
2. **Bare functional-interface shape** — for hooks that are not a full SPI, the registrar returns a plain
   `java.util.function` interface with a **no-op / identity absent default**, so the caller can invoke it
   unconditionally. Same lazy-classload principle; the guard just decides *which* value is returned.

Both keep classloading lazy — the difference is only **where** the guard sits per family:

| Family | Bridge shape | Absent default | Guard location |
|---|---|---|---|
| Protection (WorldGuard/Towny/Lands/SuperiorSkyblock/Factions) | SPI (`ProtectionProvider`) | omitted from the list | registrar `active()` (presence + config) |
| Economy (Vault) | SPI (`EconomyProvider`) | `null` | registrar `active()` |
| PlaceholderAPI expansion | SPI-like install (`SePlaceholderExpansion`) | not installed | registrar `active()` |
| PlaceholderAPI passthrough | `BiFunction<Player,String,String>` | identity (`(p,t)->t`) | registrar `active()` |
| Anti-cheat exemption | `Consumer<Player>` | no-op | delegated into `AntiCheat.exemption(plugin, enabled, log)` |
| mcMMO friendly-fire | `BiPredicate<Player,Player>` | constant `false` | registrar `enabled.test("mcmmo")` (then `Mcmmo.sameParty`) |
| MythicMobs mob-type | `Function<Entity,String>` | constant `""` | registrar `enabled.test("mythicmobs")` |
| Custom items (ItemsAdder/Oraxen) | `Function<String,ItemStack>` | `null`-resolver | delegated into `CustomItems.resolver(plugin, enabled)` |

The registrar's own `active()` and the config-toggle checks touch only Strings + the core `Plugin` type, so
verifying/running the registrar never resolves an absent plugin's API; a bridge (or its delegate's inner
class) loads only when its guarded factory actually runs.

## Verification

Per-bridge, three legs (as ADR 0017 established, minus the now-unnecessary separate-jar packaging):

- **Compilation** against each plugin's real API (CI `./gradlew build`).
- **A unit test per bridge** pinning its decision core against the mocked plugin API (BUILD-flag, island
  privilege, Towny world gate, Lands role-flag, Factions zone gate, Vault "no partial charge").
- **The engine's `ProtectionSuite`** (already in the live matrix) proving a registered provider actually
  gates an activation by location through gate 2.

End-to-end behaviour with each plugin installed is verified on a server running that plugin, out of the live
matrix (the matrix runs none of them — the same explicit, accepted gap as ADR 0017).

## Consequences

- One jar to install; integrations are automatic when their plugin is present and invisible when it is not.
- The separate `se/addon-worldguard` module is removed and its `WorldGuardProvider` folded into
  `se/integrate`; the published `StarEnchants-WorldGuard` artifact is discontinued. No other add-on jars are
  produced.
- The core jar still contains no plugin-API bytecode and no land/economy-plugin maven repo leaks into the
  core modules (the repos are confined to `se/integrate`).
- Adding an integration is still local: one bridge class + one guarded line in `Integrations` (+ a
  `softdepend` entry). No new module, no new jar.

## Alternatives considered

- **Keep separate add-on jars (ADR 0017).** Correct but operationally heavier (N+1 downloads, version
  skew). The owner explicitly chose the single-jar model.
- **Reflection instead of `compileOnly` APIs.** Avoids the compile dependency entirely, but loses compile-time
  verification of every bridge — the exact silent-fail-open failure mode ADR 0017 was created to avoid. The
  `compileOnly` + lazy-load pattern keeps verification *and* optionality. (Reflection is still the right tool
  for a plugin with no usable compile artifact — e.g. Mental, ADR 0026.)
- **Hard `depend`/required plugins.** Rejected outright — no integration may be mandatory.
