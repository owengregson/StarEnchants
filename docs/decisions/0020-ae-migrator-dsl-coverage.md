# ADR 0020: AdvancedEnchantments migrator — selector, condition, and effect DSL coverage

- **Status:** Accepted
- **Date:** 2026-06-16
- **Deciders:** project owner + engine work
- **Relates to:** ADR 0006 (config migration), ADR 0016 (content format v2 — the migrator emits v2)

## Context

The AdvancedEnchantments (AE) importer (added with the v2 migrator) mapped only a confident effect core
(`DAMAGE`, `ADD_HEALTH`, `POTION`, `MESSAGE`, `ACTIONBAR`, money) and **dropped AE's condition DSL
entirely** (`MigratedLevel.condition` was always null), TODO-ing every selector with args. AE's signature
features — its `@Selector{args}` targeting and its `Fractor` condition engine — were therefore lost on
import, forcing operators to re-author them by hand with no machine help.

The exact AE syntax was reverse-engineered from the decompiled source (`deobf/`, local-only; informs WHAT
the config looks like, never HOW to build the engine). That archaeology **corrected several assumptions**:
AE has no `FEED`/`GIVE_EXP`/`KNOCKBACK`/`IGNITE`/`COMMAND` heads — fire is `BURN:<seconds>` (×20 → ticks),
food is `ADD_FOOD`, xp is `EXP`, `LIGHTNING` takes a boolean `real` (not damage), commands are
`CONSOLE_COMMAND`/`PLAYER_COMMAND`, the AoE filter value is `MOBS` (not `MONSTERS`), and condition
variables use **spaces not underscores** (`%victim health%`).

## Decision

Expand the AE importer to translate the confident overlap and, as before, TODO everything unverified —
**never a silently-wrong value**. The migrator stays cold-path and is pinned by `MigratorTest`, which
compiles its own output through the real production compiler.

### 1. More effect heads (source-verified arg shapes)

`ADD_FOOD:n`→`FEED:n`, `EXP:n`→`GIVE_EXP:n`, `BURN:s`→`IGNITE:s*20` (seconds→ticks), argless
`REPAIR`/`KILL`/`EXTINGUISH`/`DISARM`→same, `LIGHTNING` (visual)→`LIGHTNING`, `CONSOLE_COMMAND:cmd`→
`RUN_COMMAND:cmd`. Each carries the AE-specified target as the `who:` selector. Demoted to TODO (not
mapped) when the shape would be lossy: `LIGHTNING:true` (real vanilla damage ≠ a fixed StarEnchants damage
value), `PLAYER_COMMAND` (runs as the player; `RUN_COMMAND` is console-only), a command body containing
`:` (the v2 lowerer would re-split it), and an unexpected arg on an argless head.

### 2. Direction-aware selectors (and area/mining selectors stay TODO)

AE's `@Victim`/`@Attacker` name **different entities by trigger direction** (verified from the AE triggers):
on an ATTACK enchant `@Attacker` is the wielder and `@Victim` is the foe; on a DEFENSE enchant `@Victim`
is the wielder and `@Attacker` is the foe. StarEnchants is role-fixed (`@Self`=wielder; `@Victim`=the other
entity; `@Attacker` only resolves on defence), so the migrator maps **direction-aware** to preserve the
*targeted entity*, not the literal token: ATTACK → AE `@Self`/`@Attacker`→`@Self`, AE `@Victim`→`@Victim`;
DEFENSE → AE `@Self`/`@Victim`→`@Self`, AE `@Attacker`→`@Attacker`. The reader derives the direction from the
enchant's mapped trigger. (This also corrects a pre-existing bug: the original importer mapped tokens 1:1,
silently retargeting every DEFENSE enchant and resolving ATTACK `@Attacker` to nobody.)

Area/mining selectors are **not** mapped (TODO): StarEnchants `@Aoe` differs from AE's in default radius
(4 vs AE's 1) and has no entity cap (AE always caps at 20), and `@Nearest` is any-living, not the
player-only `@NearestPlayer` — so `@Aoe{…}`, `@NearestPlayer`, `@Trench`/`@Tunnel`/`@Veinmine` have no
faithful equivalent and would change the target set silently.

### 3. Condition DSL → allow-gate

AE conditions are per-level `LEFT : RESULT` lines. The migrator now maps each to a StarEnchants
`condition:` allow-gate: `%allow%`/`%continue%` → the gate as-is; `%stop%` → the negated gate `!(LEFT)`.
Multiple mappable lines combine with `&&`. LEFT is translated token-by-token: each `%var%` → a StarEnchants
`%fact%` (`victim health`→`victim.health`; `attacker/player/bare health`→`actor.health`; `combo`, `damage`;
activator pose flags `is sneaking/blocking/flying`→`sneaking/blocking/flying`), word joiners `and`/`or`→
`&&`/`||`, and AE's single `=` → `==`. A `%force%` or `±N %chance%` result (no StarEnchants equivalent), a
`contains`/`matchesregex` operator, or any variable without a StarEnchants fact makes the whole line a
`# TODO condition` comment — carried, never silently dropped. The emitted gate is additionally **validated
for type-coherence** (each `&&`/`||` clause must be a numeric fact with a numeric comparison, or a boolean
flag used as a boolean) — a clause that would not type-check (e.g. a flag compared numerically, which AE
treats as degenerate-false) is TODO'd, so a migrated condition can never produce a blocking `E_COND_TYPE`
that fails the whole transactional load. `MESSAGE`/`ACTIONBAR` bodies keep their free text (a non-selector
`@word` like `@admin` is preserved), but a body that names a real `@selector` target → TODO, since
StarEnchants messages the actor and the recipient would otherwise change silently. The variable scope
mapping assumes the ATTACK direction (activator = attacker); DEFENSE imports are flagged by the file header.

## Verification

- `MigratorTest` (unit): the new effect heads, the AoE radius selector, and `Mappings.aeCondition`
  translate to the exact expected tokens; the lossy cases are asserted to be TODOs (not mapped).
- An integration test migrates a full AE enchant carrying a mappable + an unmappable condition and
  **compiles the output through the real loader** — proving the emitted `condition: "%sneaking% == true"`
  is valid `Expr` and the TODO comment is present.

## Consequences

- AE configs import with far more carried over (conditions especially), and every remaining gap is an
  explicit, reviewable `# TODO` — consistent with the migrator's existing discipline.
- Migrator-only change: the runtime, the compiled model, and the live combat path are untouched.
- The mining selectors, filtered/limited AoE, inline `<random>`/`<chance>`/`<condition>` effect tags, and
  chance-modifying conditions remain TODO — they need engine features StarEnchants does not have today.
