# ADR 0006: Fresh unified config schema + migrator

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

## Context

The originals have partial reloads (some files need a restart) and three config
dialects. We want a clean schema, but existing servers have AE/EE/EA configs and
in-world items carrying legacy NBT.

## Decision

A fresh, modular, exhaustively-commented unified config schema, loaded into one
immutable snapshot swapped atomically (one reload reloads everything). Ship a
**migrator** that imports AdvancedEnchantments AND EliteEnchantments/EliteArmor
config files into the new schema, AND lazily migrates legacy item NBT on
existing items so servers keep their gear.

## Consequences

- No drop-in config compatibility; migration is the bridge.
- The migrator reuses the cross-version alias maps (ADR 0008).
- Synergy crystals are generated from set definitions, not shipped as 132 files.

## Alternatives considered

- Drop-in config compatibility — rejected by the user in favor of a clean schema.
- Config-only migration — rejected: existing player items would be orphaned.
