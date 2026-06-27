# ADR 0016: Content format v2 — readable authoring (verbose effects, scaling, tier folders, items)

- **Status:** Accepted
- **Date:** 2026-06-16
- **Deciders:** project owner + engine work
- **Supersedes:** the v1 ad-hoc content format (still loadable — see Backward compatibility)

> **[Update 2026-06-27]** The terse colon-string effect form is **no longer a supported user-authoring
> syntax** — the content loader rejects a scalar effect item with `E_TERSE_EFFECT`, and all authored
> content, generated docs, and examples use the block `{ HEAD: { param: value } }` form only. Terse is
> still **read by the migrator** when importing AE/EE/EA configs (it re-renders them as block). The
> "alongside terse" / "v1 terse still loads" statements below are historical.

## Context

The v1 content files (`se/bootstrap/resources/content/{enchants,crystals,sets}/`) store
each enchant/crystal/set as a YAML mapping with effects written as cryptic positional
colon-strings (`"IGNITE:80:@Attacker"`, `"SOUND:ENTITY_GENERIC_EXPLODE:1:1"`) and levels
copy-pasted (`thunderstrike` repeats its `SOUND` line in all three levels). There is no
rarity/tier concept and every enchant lives flat in one folder. At catalog scale (70+
files today, hundreds intended) this is hard to author, review, and migrate into.

The format was redesigned by a multi-agent workshop (4 independent proposals → synthesis →
adversarial validation). The validation found that the first synthesis rested on two false
premises — it claimed to reuse an arithmetic expression grammar that **does not exist**
(the `Expr` grammar has only boolean/comparison operators, no `+ - * /`, no ternary, no
bare `level` variable), and it lowered verbose effects via `toPositional → join(":") →
reparse`, which is **lossy** for any string argument containing `:` or starting with `@`
(MESSAGE/TITLE/ACTIONBAR/RUN_COMMAND). This ADR is the hardened design that fixes both.

## Decision

v2 is a **backward-compatible superset** of v1: every existing v1 file still loads
byte-for-byte. New capability is opt-in and lowers, inside the loader/compiler, to the
exact `AbilityDef`/`EffectLine`/`CompiledEffect` shapes the runtime already consumes. The
compiled model (`Ability`, `Snapshot`, `Affinity`, the gates) is **unchanged**.

### 1. Tier subfolders — organisation without identity change

Enchants (and optionally crystals/sets) live in tier subfolders:
`content/enchants/<tier>/<name>.yml` (e.g. `enchants/mythic/thunderstrike.yml`).

**The tier folder is NOT part of the stable key.** The stable key is `<root>/<filename>`
(`enchants/thunderstrike`), independent of any intermediate folder. This is the critical
fix: baking the tier into the key would change the PDC-stamped key of every already-enchanted
item the moment a file moves into a tier folder (or is re-tiered), bricking live gear. By
deriving the key from the source root + filename only:

- the v1→v2 catalog move is purely organisational — keys are identical before and after;
- re-tiering an enchant (moving its file) never changes its identity or breaks stamped items;
- two files that would yield the same key (same filename under different tiers) are a
  hard `E_DUPLICATE_KEY` load error (names must be globally unique within a source root).

The tier is **derived from the immediate subfolder** under the source root if that folder
is a registered tier; an in-file `tier:` overrides it (a folder/file mismatch warns,
`W_TIER_FOLDER_MISMATCH`); otherwise the registry default applies silently (the common case for a flat
file, so it is not warned).
Tier is presentation/gating metadata only (lore colour, glint, GUI sort weight) — it never
affects compilation. Tiers are declared in `content/tiers.yml` (order, colour, glint,
weight); absent → a built-in default registry.

### 2. Verbose effects — named, typed, colon-safe (alongside terse)

Each `effects:` entry is **either** a terse string (v1, unchanged) **or** a verbose
single-key map whose key is the effect head and whose value is a map of named params plus
the reserved keys `who:` (target selector) and `wait:` (a lead-in WAIT):

```yaml
effects:
  - "IGNITE:80:@Attacker"                                   # terse (v1)
  - IGNITE:  { duration: 80, who: "@Attacker" }             # verbose (v2)
  - MESSAGE: { text: "Combo x2: keep it up!" }              # colons in a string: SAFE
  - LIGHTNING: { damage: 3, who: "@Victim", wait: 20 }      # ⇒ WAIT:20 then LIGHTNING
```

**Lowering is colon-safe by construction.** The reader carries the named map on a new
optional `EffectLine` payload (it does NOT join into a string). `LineCompiler`, which holds
the `ParamSpec`, orders the named values into positional order *as a list* (each value one
element — a `:` inside a string value can never be re-split) and validates:

- unknown param key → `E_UNKNOWN_EFFECT_PARAM` (listing the valid parameter names);
- missing **required** param → `E_MISSING_ARG` naming the param (not a positional slot);
- the `who:` value is set as the line's explicit selector (so a string param whose value
  starts with `@` can never be mis-detected as a selector).

`who:` is the reserved selector key because `who` is the literal target-slot name in the
engine (`.target("who", …)`). Omitting it falls to the kind's declared default target.
`who:` on a target-less kind (SOUND/PARTICLE/MESSAGE…) is accepted but inert at runtime
(those kinds never read a target) — we do not hand-maintain a target-less inventory in the
config layer (the earlier synthesis got it wrong: EXPLODE *has* a `who` slot).

### 3. Level scaling — no copy-paste, no new grammar

The varying numbers across levels are tabulated once in a `scale:` block and referenced as
`$token` in effect params and knobs; the shared effect lines are written **once**. There is
**no formula expression language** (the `Expr` grammar has no arithmetic — see Context). A
scale token is one of:

- a **constant** scalar (same value every level): `burn: 80`
- an explicit **level-map** (non-linear allowed): `bolts: { 1: 2, 2: 4, 3: 6 }`
- a **linear** form (integer-exact, no rounding): `bolts: { from: 2, step: 2 }`
  → level *L* = `from + (L-1)*step`

`$token` resolves in any knob (`chance`/`cooldown`/`soul-cost`/`condition`) and any **numeric**
effect arg, per level, **at compile time**; the resolved literal then range-checks through the
normal `ParamSpec` path (a scale yielding an out-of-range value is a load-time error, not a
runtime surprise). A knob may also carry an inline level-map directly
(`chance: { 1: 15, 2: 22, 3: 30 }`). A level-map missing a level clamps to its last entry
silently (clamping to the nearest lower entry is the intended default).

`levels:` remains for genuine per-level behaviour changes, deep-merging **over** the scaled
defaults: scalar knobs at a level win; `effects:` replaces the scaled list; `effects+:`
appends to it. Emitted levels = `1..max-level` when `scale`/`max-level` is present, else the
union of declared `levels` keys (v1 behaviour). Levels expand in ascending order so dense
ability ids stay byte-stable dev-vs-prod.

### 4. Identity-item def type — `items/<kind>/<name>.yml` (zero-ability)

A carrier item (book/scroll/dust/gem) is a new `ItemDef` — pure PDC/presentation metadata,
never compiled to an `AbilityDef`, never on the combat hot path. `kind:` drives the allowed
sub-schema (a `book`/`tome` grants an enchant/crystal/set; a `scroll` carries a role; a
`dust` carries a modifier), so the shapes don't overload one ambiguous `grants:` map. The
loader walks `items/`, builds `List<ItemDef>` on the `Library`, and the def drives the carrier
application economy (`feature.carrier` — mint a carrier item, drag it onto gear to apply its grant
with a success/destroy roll; guard scrolls spare a failed apply). Dust (success-bonus combining) is
the one deferred carrier kind.

### 5. Misc

- `description:` accepts a string **or** a list of strings (joined with `\n`).
- v1 key names are kept (`display/description/trigger/applies-to/group/condition/soul-cost/
  disabled-worlds/repeat/pieces/max-level`) — no renames; muscle memory and the migrators
  are preserved.
- Unknown top-level keys warn (`W_UNKNOWN_KEY`) and are ignored — so a future knob is a
  clean additive change.

### Explicitly out of scope (deferred, documented)

`charges`, per-enchant `slot-cost`, TYPE-scoped cooldowns (`cdScopeType`), and a custom
per-ability `suppressKey` are **not** in the runtime model (`AbilityDef`) today, so v2 does
not express them. They are deferred; when the engine grows them, adding the keys is a local
additive change (the `W_UNKNOWN_KEY` warn means an early use is diagnosed, not silently lost).

## Consequences

- **Implementation surface** (this ADR's PR A): a `named` payload + factory on `EffectLine`;
  a verbose branch in `LineCompiler`; `YamlNode` sequence/scalar/own-entries accessors;
  `ContentParse` verbose + scale + `$token` + tier helpers; scale/levels logic in
  `EnchantDefReader`; `tier` on the Def records; `LibraryLoader` key-stability +
  `TierRegistry`/`tiers.yml` + `items/` walk + duplicate-key detection; a new
  `ItemDef`/`ItemDefReader`; new diagnostics. The grammar's `Expr`, every compile stage past
  lowering, `Ability`/`CompiledEffect`, and the runtime are untouched.
- **Backward compatibility:** every v1 file still loads. The catalog adoption (PR B) is a
  separate, purely-organisational rewrite that keeps every stable key identical.
- **Migrators** (EE/EA/AE) emit v2 (verbose effects + tier folder + `# TODO` review comments).
- **Residual risk:** the verbose lowerer is the most intricate new code; it is pinned by unit
  tests (colon-bearing strings, `@`-leading strings, missing/unknown params, who→selector)
  and by the live `CatalogSuite` compiling the real v2 catalog on every matrix server.

## Alternatives considered

- **Formula scaling (`"40 + level*30"`).** Rejected: the `Expr` grammar has no arithmetic;
  honouring it means a whole new expression evaluator. Level-maps + a linear `from/step`
  cover the real cases with zero new grammar.
- **Lower verbose effects via `toPositional`+join+reparse.** Rejected: lossy for `:`/`@` in
  string args. We build the positional list without re-lexing instead.
- **Tier in the stable key (folder-derived identity).** Rejected: bricks live gear on any
  file move/re-tier. The key is root+filename; the tier is separate metadata.
- **Rename keys to `name/lore/when/do`.** Rejected: breaks muscle memory and forces the
  migrators to pick a dialect, for no compiler benefit.
