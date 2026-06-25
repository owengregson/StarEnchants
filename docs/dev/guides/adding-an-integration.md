# Adding an integration

A **bundled soft integration** is a bridge to a third-party plugin that ships
inside the one core jar, works when that plugin is present, and is never required
(see [ADR-0027](../../decisions/0027-bundled-soft-integrations.md)). Adding one is
local: a single bridge class, one guarded line in the registrar, a `compileOnly`
dependency, and a `softdepend` entry. No new module, no new jar.

This guide walks the real **WorldGuard** protection bridge — a `ProtectionProvider`
— end to end. Everything lives in `se/integrate`; the SPI interfaces live in
`se/platform`.

## The contract (ADR-0027)

Every bridge satisfies five things:

1. **`compileOnly` plugin API.** The third-party API (and its maven repo) is
   declared only in `se/integrate/build.gradle.kts`. The bootstrap fat jar shades
   `runtimeClasspath` and excludes `compileOnly`, so the jar contains the bridge
   bytecode but zero plugin-API classes. Compiling against the real API (not
   reflection) means a renamed upstream method is a build error, not a silent
   production fail-open.
2. **A string-only guarded presence check.** The registrar checks
   `PluginManager.getPlugin(name)` and the toggle — touching only Strings and the
   core `Plugin` type, so it can never load an absent plugin's API.
3. **A static factory returning the SPI interface.** `create()` is *declared to
   return* `ProtectionProvider`, so the JVM resolves the bridge class (and its
   plugin's API types) only when the guarded factory call actually runs.
4. **The provider SPI.** Bridges implement `platform.protect.ProtectionProvider`
   or `platform.economy.EconomyProvider`. The composition root unions bundled
   providers with any registered externally via Bukkit's `ServicesManager`.
5. **A `integrations.named.<id>` toggle.** Read once at boot; an unlisted id
   defaults to enabled.

## The layers, by example

| Layer | WorldGuard file | What it owns |
| --- | --- | --- |
| SPI | `se/platform/src/platform/protect/ProtectionProvider.java` | the first-party interface |
| Bridge | `se/integrate/src/integrate/protect/WorldGuardProvider.java` | wraps the real API |
| Registrar | `se/integrate/src/integrate/Integrations.java` | guarded line + factory call |
| Build dep | `se/integrate/build.gradle.kts` + `gradle/libs.versions.toml` | `compileOnly` API |
| Toggle | `se/compile/src/compile/load/MasterConfig.java` (`IntegrationsSection`) | `enabled(id)` |
| Load hint | `se/bootstrap/resources/plugin.yml` | `softdepend` |
| Wiring | `se/bootstrap/src/bootstrap/StarEnchantsPlugin.java` | collect + adapt to a gate |

## Step 1 — pick (or define) the SPI

The protection SPI is a functional interface — `ProtectionProvider`:

```java
public interface ProtectionProvider {
    boolean allows(UUID actor, Location where);   // true = allow; protection only ever denies
    default String name() { return getClass().getSimpleName(); }
    ProtectionProvider ALLOW = /* allow-all default */;
}
```

The actor is a `UUID`, not a live `Player` — on Folia the actor may be owned by a
different region thread, so a bridge must never reach for a live entity. The
economy SPI (`platform.economy.EconomyProvider`) is the same shape: `balance`,
`withdraw` (no partial charge), `deposit`, `name`. Reuse an existing SPI if your
integration fits it; only add a new SPI interface in `se/platform` for a genuinely
new capability.

## Step 2 — write the bridge

The bridge implements the SPI, imports the real plugin API directly, and is
fail-safe: it catches `Throwable`, warns once, and degrades to allow. The static
factory returns the SPI type. `WorldGuardProvider`:

```java
final class WorldGuardProvider implements ProtectionProvider {

    private final AtomicBoolean warned = new AtomicBoolean();

    /** Registrar factory; returns the SPI type so referencing it never eagerly loads this class. */
    public static ProtectionProvider create() {
        return new WorldGuardProvider();
    }

    @Override public boolean allows(UUID actor, Location where) {
        try {
            // … query WorldGuard's BUILD flag …
            return buildAllowed(query, at, subject);
        } catch (Throwable wgFailure) {
            if (warned.compareAndSet(false, true)) {
                log.log(System.Logger.Level.WARNING,
                        "WorldGuard protection query failed; allowing this and future actions (logged once)",
                        wgFailure);
            }
            return true;
        }
    }

    // pure decision, split out so a unit test needs no live server
    static boolean buildAllowed(RegionQuery query, Location at, LocalPlayer subject) {
        return query.testState(at, subject, Flags.BUILD);
    }
}
```

Split the pure decision into a `static` method so a unit test can assert it
against a mocked API with no server. Most factories are no-arg `create()`; a
bridge whose API needs the plugin instance takes it (`LandsProvider.create(Plugin)`),
and an economy bridge resolves lazily because Vault often registers after the
plugin enables (`VaultEconomyProvider.fromServices()`).

## Step 3 — register it (the one guarded line)

`Integrations` is the registrar. The guard is string-only:

```java
private static boolean active(Plugin plugin, Predicate<String> enabled, String pluginName, String configKey) {
    Plugin found = plugin.getServer().getPluginManager().getPlugin(pluginName);
    return found != null && found.isEnabled() && enabled.test(configKey);
}
```

Add one line to the matching registrar list. For protection that is
`Integrations#protectionProviders`:

```java
public static List<ProtectionProvider> protectionProviders(Plugin plugin, Predicate<String> enabled) {
    List<ProtectionProvider> out = new ArrayList<>();
    if (active(plugin, enabled, "WorldGuard", "worldguard")) { out.add(WorldGuardProvider.create()); }
    if (active(plugin, enabled, "Towny", "towny"))           { out.add(TownyProvider.create()); }
    // …
    return out;
}
```

The two string args are the exact Bukkit plugin name passed to `getPlugin` and
the lowercase `integrations.named.<id>` key. This chain *is* the registry — there
is no central provider list to also edit. An economy bridge goes in
`Integrations#economyProvider` instead.

## Step 4 — the build dependency

Declare the API `compileOnly` (and its repo, if new) in
`se/integrate/build.gradle.kts`, plus a matching `testImplementation` so the unit
test can mock it:

```kotlin
compileOnly(libs.worldguard.bukkit)
// …
testImplementation(libs.worldguard.bukkit)
```

Pin the version in the catalog `gradle/libs.versions.toml`:

```toml
[versions]
worldguard = "7.0.9"

[libraries]
worldguard-bukkit = { module = "com.sk89q.worldguard:worldguard-bukkit", version.ref = "worldguard" }
```

`compileOnly` keeps the API out of the runtime classpath, so the shaded jar never
carries it. Most APIs add `{ isTransitive = false }` to avoid dragging in a whole
server.

## Step 5 — the load hint

Add the plugin name to `softdepend` in `se/bootstrap/resources/plugin.yml`. This
is a load-order hint only — never a hard dependency:

```yaml
softdepend: [Mental, WorldGuard, Vault, Towny, Lands, /* … */]
```

## Step 6 — the toggle (already wired)

The `integrations.named.<id>` toggle needs no new code. `IntegrationsSection` in
`se/compile/src/compile/load/MasterConfig.java` reads it, defaulting an unlisted
id to enabled:

```java
public boolean enabled(String id) {
    if (id == null) return true;
    Boolean flag = named.get(id.toLowerCase(Locale.ROOT));
    return flag == null || flag;   // absent ⇒ enabled (default-on)
}
```

The composition root passes `master.config().integrations()::enabled` into the
registrar as the `Predicate<String>`, so `active(...)` calls
`enabled.test("worldguard")` → reads `integrations.named.worldguard`. An operator
can then disable just your bridge with:

```yaml
integrations:
  protection: true
  named:
    worldguard: false
```

## How it is consumed (no change needed)

The boot wiring already picks up whatever the registrar returns. In
`StarEnchantsPlugin`:

```java
List<ProtectionProvider> protectionProviders = new ArrayList<>();
if (master.config().integrations().protection()) {
    protectionProviders.addAll(Integrations.protectionProviders(this, master.config().integrations()::enabled));
    protectionProviders.addAll(ProtectionProviders.discover(getServer(), /* logger */));  // external SPI union
}
ProtectionService protection = new ProtectionService(protectionProviders);
ActivationPipeline.Guard protectionGuard = (ability, activation) -> {
    Location where = activation.location();
    return where == null || protection.allows(activation.actor(), where);
};
```

`ProtectionService#allows` ANDs all providers (first deny wins; a thrown provider
is treated as allow and logged once). The engine never calls a provider directly —
the adapter above turns the SPI into the pipeline's gate-2 `Guard`, reading the
activation's captured `actor()`/`location()` so there is no live-player
cross-region read.

## Step 7 — the unit test

Mock the plugin API and assert the pure decision, mirroring
`se/integrate/test/integrate/protect/WorldGuardProviderTest.java`
(`allowsWhenBuildFlagAllows` / `deniesWhenBuildFlagDenies` call
`WorldGuardProvider.buildAllowed(...)`). The fail-safe `Throwable` path matters —
assert that a thrown API call yields allow.

## Checklist

1. `se/integrate/src/integrate/protect/<Name>Provider.java` — `implements
   ProtectionProvider`, fail-safe `allows`, static `create()` returning the SPI,
   a `static` pure-decision method.
2. `se/integrate/src/integrate/Integrations.java` — one guarded line + the import.
3. `se/integrate/build.gradle.kts` — the repo (if new) + `compileOnly` +
   `testImplementation`.
4. `gradle/libs.versions.toml` — a `[versions]` pin + a `[libraries]` alias.
5. `se/bootstrap/resources/plugin.yml` — the `softdepend` entry.
6. `se/integrate/test/integrate/protect/<Name>ProviderTest.java` — mock + assert.

The toggle, the `ProtectionService` union, the gate adapter, and the boot wiring
all pick up the new bridge automatically.

## Verify

```bash
./gradlew build
```

A bridge is pure logic with a mocked API, so the unit gate is enough — the live
matrix is for code that touches Folia threading or version-specific server
internals, which a soft integration bridge does not. Confirm the shaded jar does
**not** carry the third-party API classes (that is what `compileOnly` guarantees);
if you accidentally use `implementation`, the build still passes but the jar
bloats and may clash with the real plugin.

## See also

- [ADR-0027](../../decisions/0027-bundled-soft-integrations.md) — the bundled soft
  integration decision (the full rationale).
- [Effect engine internals](../internals/effect-engine.md) — the activation
  pipeline and where the protection `Guard` sits in the gate sequence.
- [Adding a config option](adding-a-config-option.md) — for an integration that
  needs more than the built-in `named.<id>` toggle.
- [Decision records](../../decisions/) — ADR-0017 (protection addon packaging,
  superseded by 0027).
