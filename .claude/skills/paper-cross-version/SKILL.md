---
name: paper-cross-version
description: Use when writing or changing code that must run across StarEnchants' whole Paper range (1.17.1 → 26.1.x) — API selection, version-gated features, reflection, the 1.20.5 mapping flip, enum→registry breaks, or Java toolchains.
---

# Cross-version Paper development (1.17.1 → 26.1.x)

## The compilation model

- `core` compiles against the **floor API** (paper-api 1.17.1) so the common
  path is binary-safe everywhere. Anything needing a newer API lives in a
  `compat-*` module loaded behind runtime feature detection (a `Capabilities`
  probe), e.g. `compat-folia`, `compat-brigadier`.
- One universal shaded jar serves the whole range. `api-version: '1.17'` in
  plugin.yml.
- Class-file target: Java 17 — a deliberate choice (1.17.1 itself only required
  Java 16, but Java 17 runs everything ≤ 1.20.4). 1.20.5+ requires Java 21 (CI
  runs 25). The integration build provisions per-version toolchains.

## Rules of thumb

- **Feature-detect at runtime; never branch behavior on a parsed version
  string** when a capability probe works.
- Decide version-dependent behavior **ONCE at load/enable**, not per event or
  per item. Cache the resolved choice.
- Reflection helpers try the Mojang name through the remapper FIRST, then the
  raw/legacy name, and degrade gracefully when a member genuinely doesn't
  exist on a version.
- Treat "constant/field stopped existing" as a candidate **rename or
  relocation**, not a deletion — confirm with `nms-archaeology`, never guess.

## Known binary breaks in the range (absorbed by resolvers)

- **1.20.5 — the mapping flip.** Runtimes are **Mojang-mapped from 1.20.5**,
  spigot-mapped before. Any reflection must route names through a
  reflection-remapper (identity on modern, reobf data parsed ONCE per JVM —
  expensive). See `nms-archaeology`.
- **1.20.5 — registry renames** of Enchantment / PotionEffectType / Particle /
  many enums to match vanilla (e.g. `DAMAGE_ALL`→sharpness,
  `PROTECTION_ENVIRONMENTAL`→protection, `CONFUSION`→nausea,
  `FAST_DIGGING`→haste, `VILLAGER_HAPPY`→happy_villager). EE/EA configs use the
  OLD names — resolve by name with legacy aliases. See `cross-version-item-api`.
- **1.21.3 — `Attribute`** changed from an enum with `GENERIC_*` constants to a
  registry-backed interface with unprefixed constants — a break in BOTH
  directions. Resolve constants by name once at class load (modern spelling
  first, then legacy).
- **Type-shape changes (exact versions).** `Enchantment` became an abstract
  class (registry-backed) at **1.20.5**; `Attribute` and `Sound` became
  interfaces at **1.21.3** (`Attribute` also dropped its `GENERIC_` prefix).
  `Particle` was RENAMED at **1.20.5** but is still a plain enum. Never store
  any of these as a compile-time constant in a field — resolve by name through
  the platform resolver (`cross-version-item-api`).
- **PDC** (`PersistentDataContainer`) has existed since **1.14** — present
  across our entire 1.17.1+ range and the item-state storage of record. Avoid
  the `ItemMeta` enchant helpers whose signatures shifted at the 1.20.5 flip;
  go through PDC + the Enchantment resolver.

## When adding a matrix version

Update `integrationTestVersions` in gradle.properties, check the Java boundary
in `core/build.gradle.kts`, and expect the gradle task to download/cache the
paperclip jar the integration matrix reuses. See `matrix-gate`.
