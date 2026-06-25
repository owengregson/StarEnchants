# Adding a config option

A `config.yml` setting is a small but cross-cutting change: it touches the typed
config record, the loader that applies its default, the bundled `config.yml`
(whose comments feed the generated docs surface), and the subsystem that reads it.
Read live behind a `Supplier`, a setting re-reads on `/se reload` with no restart;
the snapshot is compiled once and swapped by reference.

This guide walks the real `souls.deposit-on-any-kill` toggle — the cleanest
live-read boolean — end to end.

## The layers, by example

| Layer | File | What it owns |
| --- | --- | --- |
| Config record | `se/compile/src/compile/load/MasterConfig.java` | the typed field + `defaults()` |
| Loader | `se/compile/src/compile/load/MasterConfigLoader.java` | reads the key, applies the default |
| Snapshot holder | `se/compile/src/compile/load/MasterConfigHolder.java` | `AtomicReference` swap |
| Bundled YAML | `se/bootstrap/resources/config.yml` | the shipped default + docs comment |
| Wiring | `se/bootstrap/src/bootstrap/StarEnchantsPlugin.java` | the `Supplier` lambda |
| Consumer | `se/feature/src/feature/soul/SoulService.java` | reads the supplier live |

## Step 1 — the record field

`MasterConfig` is one record with a nested section record per area. Add the field
to the relevant section and set its default in that section's `defaults()`. The
worked example's section:

```java
public record SoulsSection(boolean depositOnAnyKill) {
    public static SoulsSection defaults() {
        return new SoulsSection(true);          // the default
    }
}
```

The top-level `MasterConfig` record has one component per section plus
`diagnostics`, and `MasterConfig.defaults()` builds the whole thing from each
section's `defaults()` (used when `config.yml` is absent). If you need a brand-new
section, add the component to the top-level record, its null-check, and a line in
`MasterConfig.defaults()`.

Document in the field's Javadoc whether it is read **live** (re-read every use, so
a reload flips it) or **boot-only** (read once at enable) — this is the contract a
reader of `MasterConfig` relies on.

## Step 2 — read it in the loader

`MasterConfigLoader#load(Path configFile)` reads each top-level key via a
`read*` helper. It never throws: an absent or unreadable file falls back to
defaults with a diagnostic. The matching reader applies the section default per
field:

```java
private static MasterConfig.SoulsSection readSouls(YamlNode n, Diagnostics diags) {
    MasterConfig.SoulsSection d = MasterConfig.SoulsSection.defaults();
    return new MasterConfig.SoulsSection(
            parseBool(n.string("deposit-on-any-kill"), d.depositOnAnyKill()));
}
```

YAML keys are kebab-case (`deposit-on-any-kill`), record components camelCase
(`depositOnAnyKill`). Use the lenient parse helpers at the bottom of the loader —
`parseBool` (true/yes/on/1 vs false/no/off/0), `parseInt`, `parseDouble`,
`orDefault` (strings) — each takes the section default as its fallback. A new
section also needs a `read*` method wired into both `load(...)` and the
IOException-fallback constructor.

## Step 3 — the bundled `config.yml` (feeds the docs)

Add the kebab-case key under its section in `se/bootstrap/resources/config.yml`,
**with a comment** that explains what it does and whether it is live or boot-only:

```yaml
souls:
  # Whether souls deposit into a carried gem on ANY kill. false disables the deposit-on-kill mechanic entirely
  # (the give/combine/split commands and soul-cost spending still work).
  deposit-on-any-kill: true
```

The comment is not cosmetic. The entire annotated `config.yml` is embedded
verbatim — comments preserved — into the docs-site operator surface
(`website/src/data/surface.json`, per ADR-0028) by
`se/bootstrap/test/bootstrap/SurfaceCatalogDriftTest.java`. So after editing
`config.yml` you must regenerate (Step 6) or the committed surface drifts and the
drift test fails the build.

## Step 4 — wire it as a live `Supplier`

`se/bootstrap/src/bootstrap/StarEnchantsPlugin.java` is the composition root. The
snapshot holder is built once at boot:

```java
MasterConfigHolder master = new MasterConfigHolder(MasterConfigLoader.load(configFile));
```

Pass the value to the consuming service as a `Supplier`/`BooleanSupplier`/
`IntSupplier` that closes over the holder, so each call re-reads the current
snapshot:

```java
SoulService soulService = new SoulService(souls, soulModes,
        new SoulCodec(ItemKeys.of(this).soul()),
        () -> items.config().soulGemOrDefault(),
        () -> master.config().souls().depositOnAnyKill(),    // live read — a reload flips it
        messages, particleFx);
```

A boot-only value reads `master.config()...` once at enable instead of capturing a
supplier (feature gating and integration discovery do this). Choose deliberately
and match the Javadoc from Step 1.

## Step 5 — read it at the point of use

The subsystem stores the supplier and calls it where the decision is made, never
caching the value. `SoulService`:

```java
private final java.util.function.BooleanSupplier depositOnAnyKill; // read live so a reload can flip it

public void onKill(Player killer, EntityType victimType) {
    if (!depositOnAnyKill.getAsBoolean()) {
        return;   // toggle off
    }
    // … deposit a soul …
}
```

## The reload swap (already wired)

`/se reload` rebuilds the snapshot off-thread and swaps it by reference. The
holder is just an `AtomicReference`:

```java
public final class MasterConfigHolder {
    private final AtomicReference<MasterConfig> current = new AtomicReference<>();
    public MasterConfig config()            { return current.get(); }   // live read
    public void publish(MasterConfig config){ current.set(config); }    // reference swap
}
```

In the reload transaction the config step parses off-thread and commits
all-or-nothing; the commit action is the swap:

```java
() -> { var c = MasterConfigLoader.load(configFile);
        return new ReloadStep.Built(c.diagnostics(), () -> master.publish(c)); },
```

After the swap, the next `depositOnAnyKill.getAsBoolean()` reads the new snapshot.
No restart, no per-subsystem reload hook — that is the whole point of the live
supplier.

## Step 6 — regenerate the docs

```bash
./gradlew regenDocs
```

This re-runs `SurfaceCatalogDriftTest` with `-Dse.doc.regen=true`, rewriting
`website/src/data/surface.json` from the sources (including your `config.yml`
edit). Commit the regenerated file or `./gradlew build` fails the drift guard. A
git pre-commit hook also runs `regenDocs` on source changes.

## Step 7 — tests

`se/compile/test/compile/load/MasterConfigLoaderTest.java` asserts every section.
Add an assertion for your field in the defaults test and the
`parsesEverySection` test, plus a line in that test's inline YAML fixture.

## Checklist

1. `MasterConfig.java` — add the component to the section record; set its default
   in that section's `defaults()` (new section ⇒ top-level component + null-check +
   `MasterConfig.defaults()` line).
2. `MasterConfigLoader.java` — read it in the matching `read*` helper with a parse
   helper (new section ⇒ a `read*` method wired into `load` and the fallback ctor).
3. `se/bootstrap/resources/config.yml` — the kebab-case key, with a comment.
4. `StarEnchantsPlugin.java` — a `() -> master.config().<section>().<field>()`
   supplier (or a one-time read for boot-only).
5. The consuming subsystem — store the supplier, call it at the point of use.
6. `MasterConfigLoaderTest.java` — assertion + YAML fixture line.
7. `./gradlew regenDocs` — commit the updated `website/src/data/surface.json`.

## Verify

```bash
./gradlew build
```

`build` runs the loader unit tests and the drift guard, so it both checks your
parse/default and forces the doc regen above. A config option is pure logic — the
unit gate is enough. Only reach for the live Paper + Folia matrix if the toggle
gates Folia-threaded or version-specific behaviour you want to see exercised
end to end.

## See also

- [Compiler and config internals](../internals/compiler-and-config.md) — the
  compile/snapshot/transactional-reload model in full.
- [Adding a command](adding-a-command.md) — `/se reload` and the docs surface.
- [Adding an item type](adding-an-item-type.md) — gating a feature behind a toggle.
- [Decision records](../../decisions/) — ADR-0006 (config and migration), ADR-0014
  (content loader and reload), ADR-0028 (documentation site).
