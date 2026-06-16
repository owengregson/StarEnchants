# ADR 0008: Cross-version resolvers + Folia scheduling abstraction

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

## Context

The range renames registry types (Enchantment/PotionEffectType/Particle at
1.20.5; Attribute/Sound enum→interface at 1.21.3) and flips server mappings at
1.20.5; configs are full of legacy enum names. Folia removes the single main
thread, which the originals assume everywhere (global scheduler + static state).

## Decision

- **Resolvers:** boot-time name→value resolvers for every volatile surface
  (Material/Sound/Particle/Enchantment/PotionEffectType/Attribute/EntityType),
  with bidirectional legacy aliases; resolve once, cache, warn-and-skip on
  unknown. Never reference a volatile constant at compile time.
- **Folia:** a `Scheduling` abstraction (region/entity/global) is the ONLY way
  to touch entities/blocks/world/timers; shared state is concurrent + UUID-keyed;
  cross-region work hops to the owning thread. Verified on a real Folia server.

## Consequences

- Configs authored on any era load on any server; the migrator reuses the alias
  maps.
- A CI lint can ban raw `Bukkit.getScheduler()` in plugin code.

## Alternatives considered

- Version-string branching — rejected in favor of capability/name resolution.
- Shading XSeries — rejected: its old reflection/regex risks breaking on new
  version strings.
