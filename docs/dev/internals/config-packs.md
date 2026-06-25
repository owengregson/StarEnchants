# Config packs

A config pack is a portable, swappable ZIP snapshot of a *whole* StarEnchants
configuration surface. An operator can export their entire setup to one file,
hand it to someone, and apply a curated preset — "give me the EliteEnchantments
setup" — in a single command, reversibly. This document maps `se/pack`: the ZIP
codec, the captured surface, the transactional stage-then-swap apply, and how
`/se pack` wires into the live reloader.

It implements [ADR-0023](../../decisions/0023-config-packs.md) (config packs),
building on [ADR-0014](../../decisions/0014-content-loader-and-reload.md)
(content loader + transactional reload) and
[ADR-0016](../../decisions/0016-content-format-v2.md) (content format).

> A pack's apply pairs with the same transactional reloader as `/se reload` —
> see [`docs/architecture.md`](../../architecture.md) §10. The first shipped pack
> is produced by [the migrator](the-migrator.md). For operator-facing pack usage,
> see the [docs site](https://owengregson.github.io/StarEnchants/).

## Where it lives

| Concern | File |
| --- | --- |
| In-memory pack (manifest + bytes) | `se/pack/src/pack/Pack.java` |
| The pure ZIP codec | `se/pack/src/pack/PackArchive.java` |
| The `pack.yml` header | `se/pack/src/pack/PackManifest.java` |
| The captured surface + apply primitives | `se/pack/src/pack/PackSurface.java` |
| On-disk authority over `packs/` | `se/pack/src/pack/PackStore.java` |
| `/se pack` command | `se/bootstrap/src/bootstrap/SeCommand.java` |
| Wiring + first-boot extraction | `se/bootstrap/src/bootstrap/StarEnchantsPlugin.java` |

The whole `se/pack` module is pure JDK — no Bukkit. It defines the format and
owns the on-disk store; it **never reloads**. The composition root pairs
`PackStore#apply` with the transactional reloader.

## The surface

`PackSurface` (`se/pack/src/pack/PackSurface.java`) defines exactly what a pack
captures — the same surface the plugin extracts on first boot and reloads
atomically:

```java
// se/pack/src/pack/PackSurface.java
static final List<String> FILES = List.of("config.yml", "lang.yml");
static final List<String> DIRS  = List.of("content", "items", "menus");
```

So a pack is `config.yml`, `lang.yml`, and the `content/`, `items/`, and
`menus/` trees — nothing else. `collect(Path)` walks the surface into a
`/`-keyed `Map<String, byte[]>` (skipping dotfiles), and `clear` / `promote` /
`deleteRecursively` are the apply primitives.

## The codec

`PackArchive` (`se/pack/src/pack/PackArchive.java`) is the pure codec: a tree of
config files ↔ a ZIP. Two invariants callers rely on:

1. **The manifest is the first entry**, so `peekManifest` reads metadata without
   inflating the whole archive (used for listings).
2. **Entries are sorted and timestamps zeroed**, so a given surface yields a
   byte-identical archive — diffable and reproducible.

```java
// se/pack/src/pack/PackArchive.java#write
Map<String, byte[]> sorted = new TreeMap<>(files);
try (ZipOutputStream zip = new ZipOutputStream(out)) {
    putEntry(zip, PackManifest.ENTRY,
            manifest.withFileCount(sorted.size()).toYaml().getBytes(StandardCharsets.UTF_8));
    for (Map.Entry<String, byte[]> file : sorted.entrySet()) {
        putEntry(zip, file.getKey(), file.getValue());
    }
}
```

```java
// se/pack/src/pack/PackArchive.java#putEntry
ZipEntry entry = new ZipEntry(name);
entry.setTime(0L); // zero timestamps → a given config surface yields a byte-identical archive
```

The file count is re-stamped on write (`manifest.withFileCount(sorted.size())`),
so the manifest's `files:` always reflects the actual entry count.

## The manifest

`PackManifest` (`se/pack/src/pack/PackManifest.java`) is the `pack.yml` header —
a record of `name`, `description`, `author`, `format` (int, bumped only on an
incompatible layout change; currently `CURRENT_FORMAT = 1`), `created` (ISO
timestamp), and `fileCount`. It is a hand-rolled flat-YAML codec to stay
dependency-free; string values are always double-quoted on write so colour
codes, colons, and quotes survive a round trip. `fromYaml` ignores unknown keys
(forward-compatible) and never throws, so a damaged manifest still lists under a
fallback name.

## The transactional apply

`PackStore` (`se/pack/src/pack/PackStore.java`) is the on-disk authority over
`packs/`. Its `apply` is **fail-safe stage-then-swap**: read → stage in a temp
dir → back up the current surface → clear → promote. A failed write never
half-clobbers the live config, because nothing live is touched until staging has
succeeded:

```java
// se/pack/src/pack/PackStore.java#apply
// Stage in a temp dir first — if this throws, nothing live has changed yet.
Path staging = dataRoot.resolve(STAGING);
PackSurface.deleteRecursively(staging);
List<String> skipped = PackSurface.writeAll(pack.files(), staging);

String backupName = null;
Map<String, byte[]> current = PackSurface.collect(dataRoot);
if (!current.isEmpty() && isValidName(backupLabel)) {
    // …write the current surface to packs/<backupLabel>.zip with author "auto"…
    backupName = backupLabel;
}

PackSurface.clear(dataRoot);
PackSurface.promote(staging, dataRoot);
PackSurface.deleteRecursively(staging);
```

Applying a pack therefore always snapshots the current config into a timestamped
backup pack first (unless the live config was empty), so a swap is reversible
with `/se pack apply <backup>`. `apply` returns an `ApplyResult` carrying the
manifest, file count, the backup name (or `null`), and the list of skipped
entries.

`PackStore` also offers `list` (peeks each archive's manifest, tolerating a
corrupt one), `info`, and `export`. A pack name must match
`[A-Za-z0-9_-]+` — no separators or dots, so a name can never escape `packs/`.

## The escape guard

A pack is untrusted input. `PackSurface#writeAll` only ever writes inside the
five surface roots; an entry that escapes — a foreign top-level name, a `..`, an
absolute path — is skipped and reported, never written:

```java
// se/pack/src/pack/PackSurface.java#withinSurface
private static boolean withinSurface(String rel) {
    if (rel.isEmpty() || rel.startsWith("/") || rel.contains("..")) {
        return false;
    }
    if (FILES.contains(rel)) {
        return true;
    }
    int slash = rel.indexOf('/');
    String top = slash < 0 ? rel : rel.substring(0, slash);
    return slash > 0 && DIRS.contains(top);
}
```

`writeAll` adds a defence-in-depth check that the resolved path stays under the
target root (`out.startsWith(base)`), so even a path that slipped the first guard
cannot escape.

## Wiring: `/se pack` and the reloader

The pack module knows nothing about reloading; the composition root joins them.
`StarEnchantsPlugin` (`se/bootstrap/src/bootstrap/StarEnchantsPlugin.java`)
constructs the store with the data folder and hands it, plus the `reloader`, to
the command:

```java
// se/bootstrap/src/bootstrap/StarEnchantsPlugin.java
// Config packs (ADR-0023). /se pack apply pairs the on-disk swap with the transactional reloader.
PackStore packs = new PackStore(getDataFolder().toPath());
```

`SeCommand#packApply` (`se/bootstrap/src/bootstrap/SeCommand.java`) runs the
filesystem work off-thread, generates the backup label and ISO timestamp, applies
the pack, then triggers the *same* atomic reload as `/se reload`:

```java
// se/bootstrap/src/bootstrap/SeCommand.java#packApply
String backupLabel = "backup-" + now.format(BACKUP_STAMP);
PackStore.ApplyResult applied = packs.apply(name, backupLabel, createdIso);
// …
reloader.reload(result -> report(sender, result));   // SAME transactional reload as /se reload
tell(sender, messages.format("command.pack.apply-note"));
```

So a swapped pack takes effect live. A pack with a *content* fault keeps the
previous in-memory state and reports the diagnostics, exactly like a bad
`/se reload` — the transactional reloader guarantees an all-or-nothing swap. One
caveat surfaced by `command.pack.apply-note`: boot-gated wiring (the souls /
slots / scrolls listeners, integration discovery, the command-trigger) binds once
at enable, so a pack that flips those toggles needs a server restart to fully
take effect; live combat and feature values re-read on the reload.

## Shipped packs and first-boot extraction

The repo keeps each shipped pack as a *reviewable* config tree
(`se/bootstrap/packs-src/<name>/`), zipped into the jar at build time, so PRs
diff the YAML while the shipped artifact stays the chosen ZIP. On first boot,
`StarEnchantsPlugin#saveDefaults` extracts the `packs/` tree alongside the config
surface, driven by `packs/index.txt` (one resource path per line) — exactly like
`content/`, and only when a file does not already exist (never overwriting
operator edits).

The first shipped pack, `elite-enchantments`, is the full EliteEnchantments port:
the whole library run through the extended migrator (see
[the-migrator.md](the-migrator.md)), plus the standard surface. A build-time
validity test compiles the entire pack clean before it can ship.

## Adding a pack

Per ADR-0023, adding a content preset is local and touches neither the codec nor
the command:

1. Drop a reviewable tree under `se/bootstrap/packs-src/<name>/`.
2. Register a `Zip` build task that produces `<name>.zip`.
3. List its archive in `packs/index.txt`.

## Gotchas

- **The pack module never reloads.** Apply is an on-disk swap only; the live
  effect comes from the reloader the composition root pairs with it.
- **Apply is transactional in two layers.** The on-disk swap is stage-then-swap
  (fails before clobbering); the in-memory swap is the all-or-nothing reloader
  (a bad pack keeps the previous snapshot live).
- **Treat every pack as untrusted.** The surface guard is the only thing
  standing between a malicious archive and a path traversal — never bypass
  `withinSurface`.
- **Pack names cannot contain dots or separators.** `[A-Za-z0-9_-]+` is enforced
  so a name can never escape `packs/`.
- **Boot-gated toggles need a restart.** A pack that changes which listeners bind
  won't fully apply on a live reload; the apply note says so.
- **Keep archives deterministic.** Sorted entries and zeroed timestamps are what
  make a pack diffable and reproducible — don't introduce non-deterministic
  ordering or real timestamps into `PackArchive`.
