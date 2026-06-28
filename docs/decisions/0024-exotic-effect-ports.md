# ADR 0024: Expression-valued effect arguments + the exotic Cosmic Enchants-style effect ports

- **Status:** Accepted
- **Date:** 2026-06-24
- **Deciders:** project owner + agent
- **Relates to:** ADR 0003 (one unified effect engine), ADR 0004 (modern compile-at-load DSL), ADR 0011
  (content-compiler + data-oriented runtime), ADR 0012/0021 (damage fold), ADR 0023 (the EliteEnchantments pack)

## Context

The shipped `cosmic-pack` pack (ADR-0023) left ~121 Cosmic Enchants-style effect occurrences as honest `# TODO` lines —
mechanics the migrator could not translate because StarEnchants had no equivalent primitive. The owner asked
to replicate every one faithfully, creating new engine primitives where genuinely needed (analyzing a
Cosmic Enchants-style reference to learn what each does, never inventing). Reviewing that reference split them into three groups:

1. **Composable** from existing effects + selectors (WRATH = lightning + AoE damage + slow + blind; SHACKLE =
   knockback cancel; FROST = a temporary ice field + debuffs; ROT_DECAY = spawned zombies + wither + armour
   decay; DROP_HEAD = drop a head item; SNIPER = bonus arrow damage; a compound particle).
2. **A dynamic-scaling cluster** — RAGE (stacking combo damage), anti-gank/GANK (more damage per nearby
   enemy), DAMAGE_DISTANCE (melee-vs-ranged falloff), and `DAMAGE_INCREASE:playerHealth*N`. All four want a
   damage contribution scaled by a *live* quantity, which a fixed numeric argument cannot express.
3. **Genuinely new primitives** — REMOVE_ARMOR, TELEBLOCK, IMMUNE, SMELT, TELEPORT_DROPS, AUTO_LOCK (homing
   arrows), plus small extensions (a `set` mode for `MODIFY_HEALTH`, negative `DAMAGE_MOD`, a victim target
   for `REMOVE_SOULS`).

## Decision

**Expression-valued numeric effect arguments.** A numeric effect argument may now be an arithmetic expression
over `%variables%` (e.g. `DAMAGE_MOD:attack:add:%combo% * 10`), evaluated per-activation against the same
primitive `FactBuffer` the condition gate already populates. The expression sublanguage (`schema.grammar.expr`)
gains `+ - * /` and unary minus; the compiled numeric IR (`compile.model.cond.NumExpr`) gains arithmetic
nodes; the lower stage resolves an expression argument's variables to dense fact slots (the same
variable→slot pass conditions use); and a shared `NumExprEval` evaluates it. This is the architecture's
existing handle-resolution seam reused for numbers — schema parses the token to an untyped AST, the compiler
lowers it to slot-resolved IR, the runtime reads it with no parsing on the hot path. Conditions get
arithmetic operands for free. Three facts were added/sourced to feed it: `%combo%` (a per-attacker
`ComboStore` streak — the value a Cosmic Enchants-style RAGE relied on, finally populated), `%distance%` (actor↔victim), and
`%nearbyenemies%` (a small radial scan), all Folia-guarded in `FactPopulator`.

**New primitives** (one `EffectKind` + `ParamSpec` + registry line each, the §13.2 local-add):
`REMOVE_ARMOR` (drop a random worn piece, the armour `DISARM`), `TELEBLOCK` and `IMMUNE` (timed combat-flags
with version-probed listeners, the `KNOCKBACK_CONTROL` shape), `SMELT` / `TELEPORT_DROPS` (inline MINE-side
read-backs the block-break dispatcher applies), and `SEEK` (AUTO_LOCK — an inline BOW_FIRE read-back that
starts a per-projectile homing task on the arrow's own entity scheduler, Folia-correct, best-effort across
regions). Small extensions: `MODIFY_HEALTH:…:set` (REDUCE_HEARTS), negative `DAMAGE_MOD` (the Cosmic Enchants-style negative
DAMAGE_INCREASE self-nerf), and a `@Victim` target for `REMOVE_SOULS` (drain the enemy's gem, via a
`SoulDebit.debitTarget` default the soul service overrides).

With these, the migrator translates the **entire** Cosmic Enchants-style effect + condition vocabulary: the regenerated pack has
**zero `# TODO` lines** (down from ~121) and compiles to 531 abilities clean. `isTargetHolding <GROUP>` also
now maps — the `contains` string operator over `%victim.helditem%` is the item-group test.

## Consequences

- The pack is a faithful, complete port: every enchant's every effect does something real, not a stub.
- Expression arguments are a general capability, not a Cosmic Enchants-style hack: any numeric argument on any effect can scale
  with any fact, and conditions can use arithmetic. Evaluation is allocation-free over the existing fact
  buffer; a divide-by-zero or unresolved placeholder degrades to a finite value rather than poisoning a fold.
- New combat-flag/homing/MINE primitives touch version-specific Bukkit events (ProjectileLaunch, EntityDamage,
  EntityShootBow, BlockBreak) and a Folia-routed per-arrow task, so they are verified on the live matrix; the
  pure pieces (the grammar, `NumExprEval`, the migrator mappings, effect→sink wiring) are unit-tested.
- `DAMAGE_INCREASE:playerHealth*N` faithfully reproduces the Cosmic Enchants-style literal multiplier (`(expr - 1) * 100` percent),
  including that at full health it is a very large multiplier — the source's own (arguably over-tuned) behaviour, not
  reinterpreted. DAMAGE_DISTANCE is a faithful-in-spirit linear falloff (the source's piecewise step is approximated).

## Alternatives considered

- **Flat-bonus approximations** for the dynamic cluster (RAGE → a fixed per-hit %, etc.) — far less code, but
  the lore line ("stacking", "more enemies = more damage") would not actually be true; rejected for fidelity.
- **A dedicated numeric mini-grammar** for effect args instead of extending the shared expression grammar —
  more isolated, but two expression languages drift; the unified grammar is the "one declaration" ethos.
- **Approximating AUTO_LOCK** (extra arrows / accuracy) instead of real homing — loses the defining mechanic;
  the owner chose the homing primitive.
- **Leaving the exotic effects as TODOs** (ADR-0023's state) — the explicit ask was to finish them.
