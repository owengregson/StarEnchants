# ADR 0005: Item-data on PersistentDataContainer; drop NBT-API

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

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
