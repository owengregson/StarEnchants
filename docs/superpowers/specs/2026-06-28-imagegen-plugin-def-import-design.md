# imagegen: import item definitions from a plugin content pack

> Status: APPROVED (design). Lets `se/imagegen` render item tooltips imported
> from a real plugin content tree — compiled through the plugin's own pipeline —
> alongside its existing hardcoded fixtures.

## Problem

`se/imagegen/.../fixture/Fixtures.java` hardcodes every preview's name + lore.
That is a maintainability problem: a set's display/lore can change in config and
the committed PNG silently drifts, and adding a preview means hand-transcribing
the lore the plugin would have rendered. We want an *import* path that builds a
preview from the real config the way the game does, while keeping the hardcoded
fixtures for synthetic illustrations.

## Goal

Render the cosmic-pack **spooky**, **clarity**, and **koth** sets — all four
armour pieces and the weapon (15 tooltips) — from their actual `content/*.yml`,
and provide a general, **path-selectable** import mechanism so any pack's items
can be rendered without transcription.

## Key facts this rests on (verified)

- `compile.load.LibraryLoader.load(root, compiler, gen)` compiles a `content/`
  tree into a `compile.load.Library` **server-free** — the same pure path
  `validateContent` reuses (no Bukkit server, no entity/world reads).
- `engine.boot.ContentCompiler.production(resolvers)` builds the production
  compiler. We pass an off-server **stub** `compile.resolve.PlatformResolvers`
  (7 trivial `OptionalInt` methods, all empty). We only need the def catalogs
  (`sets()`, `displayNameOf`, `tierOf`, `tiers()`, `setDefOf`), not a resolved
  runtime `Snapshot`; unresolved effect tokens are warn-and-skipped and never
  throw.
- `item.render.LoreRenderer.lines(CombatState)` is already server-free and emits
  the complete set/enchant lore **when wired** with the library-backed
  `displayNameOf` / per-tier `enchantColorOf` / `SetLore` lookups — exactly the
  wiring `bootstrap.StarEnchantsPlugin` uses on the cold apply path.
- A set member's identity at mint (`feature.apply.ItemEnchanter.mintSetPiece`):
  name = `member.name()` else `def.display()`; material = `member.material()`;
  combat state carries only the set's **custom** enchants + `setKey`
  (armour) / `setWeaponKey` (weapon). Vanilla enchants
  (`PROTECTION`/`SHARPNESS`/`UNBREAKING`) are applied to the real ItemStack and
  rendered by Minecraft — *not* by `LoreRenderer` — so the importer renders them
  itself (see §5).

## Module dependency

`se/imagegen/build.gradle.kts` adds `implementation(project(":engine"))`, which
re-exports `:compile` and `:platform` via `api`. imagegen already depends on
`:item`, `:feature`, `paper-api` (floor), and `snakeyaml`. No other change.

## Components (new package `imagegen.imports`)

Each is a small, independently testable unit.

### 1. `ImportManifest` — what to render, and from where

A committed `se/imagegen/imports.yml`, overridable with
`-Dse.imagegen.imports=<path>`. Resolved against the repo root (the
`renderImages` task already sets `workingDir = rootProject.projectDir`).

```yaml
# Which real plugin items to render as tooltips, and from where.
sources:
  - root: se/bootstrap/packs-src/cosmic-pack/content   # the pack's content/ tree
    sets: [spooky, clarity, koth]                        # explicit list, or "*" for every set
# Add more sources to pull from other packs, e.g. the SE default:
# - root: se/bootstrap/resources/content
#   sets: "*"
```

- `root` (string, required) — a content tree (`enchants/`, `sets/`, `tiers.yml`).
- `sets` (list of keys, or the string `"*"`) — `"*"` expands to every set in the
  compiled library; a list selects by key. Omitted/empty → no sets from this
  source.

Parsed with the snakeyaml already on imagegen's classpath into a record:
`ImportManifest(List<Source>)`, `Source(String root, SetSelection sets)` where
`SetSelection` is either ALL or an explicit `List<String>`.

### 2. `StubResolvers implements compile.resolve.PlatformResolvers`

Every method returns `OptionalInt.empty()`. Off-server, deterministic; the def
catalogs we read do not depend on handle resolution.

### 3. `PackImporter`

Given a `root`: `Library lib = LibraryLoader.load(root, ContentCompiler.production(new StubResolvers()), 1)`.
Builds a library-backed `LoreRenderer` mirroring `StarEnchantsPlugin`:
`displayNameOf = lib::displayNameOf`; `enchantColorOf` = tier → `lib.tiers().tier(t).color()`;
`SetLore` = `lib.setDefOf(key).armorLore()/weaponLore()`. Exposes
`List<ItemFixture> fixturesFor(SetDef def)` returning the 4 armour pieces + the
weapon (if present), and a top-level `List<ItemFixture> fixtures(ImportManifest)`
that compiles each source once and flattens the selected sets. Logs and skips on
a missing root, an empty library, or an unknown set key; never throws.

### 4. `VanillaEnchantLore`

`String line(String enchantName, int level)` → the game-faithful gray enchant
line: a proper-cased display name + a Roman numeral, e.g. `§7Protection IV`,
`§7Sharpness V`, `§7Unbreaking III`. Single-level enchants (e.g. Mending) render
with no numeral. Display names come from a small curated map for the enchants the
packs actually use, falling back to TitleCase of the enum token. Roman numerals
reuse `item.render.Numerals` (the same converter `LoreRenderer` uses), so set
and enchant numerals are consistent.

## Data flow & output

For each selected `SetDef`, for each armour `Member` and the weapon `Member`:

1. `name` = `member.name()` else `def.display()`; `material` = `member.material()`.
2. `state` = `CombatState` with the set's **custom** enchants
   (`enchants/<id>` refs) + `setKey` (armour) or `setWeaponKey` (weapon).
3. `lore` = **vanilla-enchant lines** (from the member's vanilla enchant names,
   in authored order) ++ `LoreRenderer.lines(state)`. This is the game order:
   enchantments directly under the name, then the authored SET BONUS / flavour
   block (whose authored leading blank line, where present, separates them).
4. Emit `ItemFixture(id, material, name, lore)`.

`Main` appends `new PackImporter(...).fixtures(manifest)` to its existing
`tooltips()` + `eeItems()` list; the existing `TooltipRenderer` writes each PNG.

Output filenames: `set-<key>-<member>.png` →
`set-spooky-helmet.png`, `set-spooky-chestplate.png`, `set-spooky-leggings.png`,
`set-spooky-boots.png`, `set-spooky-weapon.png` (× spooky/clarity/koth = 15),
into `website/static/img/renders/`.

## Error handling

The import path is additive and fully degrading — it never breaks the hardcoded
renders or the run:

- Missing/unreadable manifest → log once, render only the hardcoded fixtures.
- Missing or empty `root` → `LibraryLoader` yields an empty library; log + skip.
- Unknown set key in `sets:` → log + skip that key.
- Compile diagnostics → non-fatal (we read defs, not the snapshot); log a count.
- Unknown vanilla enchant name → TitleCase fallback, never throws.

## Testing

None. `se/imagegen` is a tool-only, dev-time utility for producing docs-site
graphics (like `se/tester`'s tooling) — never shipped in the plugin jar and not
on any runtime path. Per the maintainer, it carries no unit tests; the committed
PNGs are the reviewed artifact, regenerated deliberately via
`./gradlew :imagegen:renderImages` when content/display changes. Correctness is
inherited: the import path reuses the plugin's own `LibraryLoader` +
`LoreRenderer`, which are themselves unit-tested in `:compile` / `:item`.

## Scope (YAGNI)

Implements **set import** fully. The manifest reserves room for an `enchants:`
selector, but rendering imported *enchant-book* tooltips is a separate likeness
and is **out of scope** here. No change to the hardcoded fixtures, the tooltip/
sprite renderers, or the `renderImages` task contract.
