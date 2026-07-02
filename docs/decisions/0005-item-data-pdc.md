# ADR 0005: Item-data on PersistentDataContainer; drop NBT-API

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

> **[Update 2026-07-01]** The legacy item-NBT **reader was descoped**. The plan below (and ADR-0006 §4.3
> of docs/architecture.md) once called for an isolated reflective reader that would decode another
> plugin's on-item NBT into the modern PDC record on first touch, during a deprecation window. That reader
> was **never built and is dropped.** Migration is **config-only** (`se/migrate` translates other plugins'
> configs to reviewable StarEnchants YAML). A live item written by a *different* plugin is read as vanilla
> — StarEnchants recognises only its own `se:*` PDC keys, so an unrecognised item simply carries no
> StarEnchants state. This is deliberate: StarEnchants is a fresh plugin with a config migrator, not a
> drop-in binary upgrade for another plugin's items. StarEnchants' own PDC format still versions its keys
> and reads forward-compatibly across the whole version range.

## Context

Both originals store all item state via the shaded de.tr7zw NBT-API, whose
version table caps at MC 1.21.11 and breaks at/above 1.22 ("26.x"); they also
parse lore for some state and re-read/clone NBT on every combat tick.

## Decision

One item-data service over **PersistentDataContainer** under versioned
`NamespacedKey`s owns ALL item state (enchants, slots, souls, crystals, set
identity, heroic, economy markers). Lore/name are **rendered from state**, never
parsed. Reads are cached; nothing parses YAML/DSL on the hot path.

## Consequences

- Survives the 1.20.5 flip and ≥1.22; no NMS reflection for item data.
- A migration reader understands legacy NBT keys for a deprecation window
  (ADR 0006).

## Alternatives considered

- Keep NBT-API — rejected: hard version ceiling + NMS reflection.
