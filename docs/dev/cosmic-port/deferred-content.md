# Deferred content — the authoritative stop list

Every matrix entry NOT yet authored in `cosmic-pack`, with its blocking
dependency. An entry leaves this table only when its file lands. Batch verifiers
treat a missing file as unauthorized unless it has a row here.

| Entry | Batch | Blocked on | Unblocks at |
| --- | --- | --- | --- |
| `enchants/blood-lust` | 01 | doc-04 bleed-stack publisher + tagged-effect PROXIMITY_EVENT | after batch 4 + wave 2 |
| `enchants/ghost` | 01 | matrix-UNRESOLVED external metadata consumer — owner decision pending | owner ruling |
| `enchants/enchant-reflect` | 02 | PROC_REBOUND (incoming-direction family; #276 STOP analysis is the design input) | wave 2 |
| `enchants/phoenix` | 02 | ESCALATING_SOUL_COST (everything else expressible today — drop-in) | wave 2 |
| `enchants/self-destruct` | 02 | SUMMON_PAYLOAD (phase=detonate + scatter + payload effects) | wave 2 |
| `enchants/spirits` | 02 | SUMMON_PAYLOAD (periodic phase: pulsing ally heal on the summon) | wave 2 |
| `enchants/undead-ruse` | 02 | SUMMON_PAYLOAD (named ring minions with per-level buff amplifiers); VIEWER_HIDE half already shipped | wave 2 |
| `enchants/plague-carrier` | 02 | SUMMON_PAYLOAD (detonate burst, terrain=none); legacy note: CREEPER_HISS alias unverified | wave 2 |
| `enchants/nutrition` | 02 | MODIFY_FOOD `mode=absolute` (matrix ships measured absolute semantics, not delta) + EAT is a held-only trigger (worn leggings never walk it) — both need rulings | wave 1f/2 |
| `enchants/repair-guard` | 02 | `%item.durabilitypercent%` fact (the EQUIP_CHANGE gap's unshipped half); authoring without it reproduces the jar's fatal proc-on-every-unequip | wave 1f |
| `enchants/metaphysical` | 02 | SHIPPED as a roster marker (effect-less PASSIVE, boots); its consumers (Trap/Snare/Pummel) need a victim worn-enchant-level fact for the reduction clauses | wave 1f (consumer side) |
| `enchants/sticky` | 02 | shipped as a roster marker (same victim worn-level fact for its consumers) | wave 1f (consumer side) |

## Engine follow-up pool fed by these rows

- **Wave 2 critical path:** SUMMON_PAYLOAD (5 consumers above + sets/masks later),
  ESCALATING_SOUL_COST, PROC_REBOUND.
- **Wave 1f (small):** victim worn-enchant-level fact (Metaphysical/Sticky
  consumers, Hero Killer's heroic-piece counting — the orphaned WORN_GEAR_FACT
  from clustering); `%item.durabilitypercent%`; MODIFY_FOOD `mode=absolute`;
  EAT worn-scan ruling; per-block cooldown-scope opt-out (Rocket Escape's FALL
  companion is cooldown-starved by its own launch); GUARD/SPAWN_ENTITY
  per-level potion amplifiers.
- **Legacy-sweep alias rows:** `DIG_STONE→BLOCK_STONE_BREAK`,
  `FIREWORK_LAUNCH→ENTITY_FIREWORK_ROCKET_LAUNCH`,
  `FIREWORK_TWINKLE2→ENTITY_FIREWORK_ROCKET_TWINKLE_FAR`, verify `CREEPER_HISS`.
