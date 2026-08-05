# ADR 0075: The TYPE suppression scope, the DAMAGE_CAP arming-hit basis, and consume-time feedback

- **Status:** Accepted
- **Date:** 2026-08-05
- **Deciders:** project owner + agent
- **Closes:** ADR-0016's deferral of content-declarable TYPE cooldown/suppression scoping
- **Relates to:** ADR-0049 (Neutralize one-shots, the Diminish cap), ADR-0050 R4 (group/type ids are suppression match keys, never cooldown scopes), ADR-0053 (`scope: KIND`), ADR-0012/§6.1 (the one damage fold)
- **Rulings:** R-QC3, R-QC19, R-QC34 (declined), R-QC41 (R-QC24 unchanged)

## Context

Three defects with one thing in common: an authored surface existed, and the value it needed to read did not.

**1. TYPE suppression was authorable and dead.** `SUPPRESS { scope: TYPE, key: DEFENSE }` compiles, interns a
key and arms a window. But every content reader passed `cdScopeType = null` — ADR-0016 shipped the scope's
three-way *plumbing* and deferred the half that fills it — so gate 5 compared the window's id against `-1` on
every ability and never matched. 26 authored windows across cosmic-pack's `silence.yml` and `tombstone.yml`
were no-ops, as was signature-pack's `neutralize.yml`. Silence is a mastery enchant whose entire mechanic is
this window; it shipped doing nothing, and every layer's own test passed.

**2. `DAMAGE_CAP` priced off the wrong hit.** The cap is documented — in the shipped enchant descriptions — as
"cap your next incoming hit at *the damage of the hit that armed it*". The arm ran inside the defence walk and
read `lastTaken`, which the dispatch wrote *below* the walks at the fold commit, so a cap armed on hit N
carried a factor of hit N−1. A pinned test documented the lag deliberately, pending this ruling. It made
Vengeful Diminish's measured double halving untrue in play and armed nothing at all on the first hit of an
engagement.

**3. Consume-time feedback could not describe a block.** Two surfaces were missing, and the Death Knight
mask's two verbatim block lines needed both: a `mode: next-hit` charge stored no `Feedback` record at all, and
the consume path sent the authored string raw, so the party NAMES both lines are built around could not bind.

## Decision

### 1. An ability's TYPE scope is its combat DIRECTION, stamped by the compiler (R-QC3)

`TriggerRegistry.suppressionTypes()` maps each non-neutral trigger to its `TriggerKind.Direction` name; the
erase stage stamps an ability that declared no type of its own with the direction its triggers imply
(`DEFENSE` wins a mixed-direction ability — the ruled semantics is silencing what a victim DOES, and one
interned slot names one side). `SUPPRESS { scope: TYPE, key: DEFENSE }` therefore silences the whole defender
side — DEFENSE, HURT, FALL, FIRE — of whoever it lands on, across every source, with nothing authored.

**Implicit, not hand-authored.** The alternative was a `type:` knob written onto every defensive file in a
library. That is a transcription job restating what the trigger already says, on hundreds of files, where one
missed file is an enchant that silently survives Silence — and it would have to be repeated by every pack
author for the mechanic to work at all. The trigger vocabulary already holds the answer; the compiler reads it.

The knob still exists, because closing ADR-0016 means the scope must be *declarable*: **`suppress-type:`** on
any ability (the enchant reader scopes it ability → level → root like its siblings; every other reader takes it
off the ability node). It REPLACES the implicit stamp rather than adding to it. TYPE keys are case-folded at
erase — the vocabulary is compiler-stamped, so `defense` and `DEFENSE` must reach one scope; ENCHANT and GROUP
keep their authored spelling, where folding would merge two distinct names.

A worn PASSIVE belongs to no combat direction and so carries no type — which is exactly the studied plugin's
behaviour (silence never dropped worn passives) and the standing justification for the eight maintained-passive
enchants' `suppress-immune: true`. Those flags STAY: `suppress-immune` also holds against ENCHANT- and
GROUP-scoped windows, which a TYPE stamp says nothing about. The END-SUPPRESS contract is untouched.

### 2. R-QC34's mastery re-key is DECLINED

Rift's and Dragon Slayer's mastery negation stays on `scope: GROUP`. The measured TYPE there is `mastery` — an
enchant *tier* vocabulary, not the defence direction — and one interned slot names one type, so declaring
`suppress-type: mastery` on the ten mastery files would surrender their implicit `DEFENSE` stamp and make every
defender-side mastery ability silence-proof, breaking the END-SUPPRESS rule that exists to keep tier > 5
enchants reachable. It would buy nothing for it: all ten carry `group: "mastery"` 1:1 with `tier: mastery`, so
GROUP already names the identical set. Carrying both meanings would mean widening `cdScopeType` past a single
slot — a hot-path change for no behavioural difference. Recorded on `masks/rift.yml` and the ledger row.

### 3. `DAMAGE_CAP` is priced at the fold commit (R-QC19)

The arm records a PENDING price (`factor`, `reflect`, the window, the line); `CombatDispatch` calls
`DamageCapStore.price(...)` at the fold commit with the value this event committed against the holder, and the
window opens there. §3.3's gate order and §3.5's intent-only rule are untouched — the effect still emits one
intent and the sink still owns the write; only the moment the number is READ moved to where the number exists.
The claim of an already-armed cap still leads the walks, so an arming hit never spends its own window.

The `feedback` line moves with it, announced at the commit at the ceiling the cap actually carries — the rule
`VULNERABILITY`'s and `REFLECT`'s lines already follow, and the first point at which `{damage}` has a true
value. A pending arm with no committed hit against its holder has no arming hit to price off and never
materialises. `lastTaken` is gone: there is one basis now, not two.

Vengeful Diminish keeps its semantics on the new basis and gains the accuracy: its −50 % `DAMAGE_MOD` folds
into the committed figure the cap then halves, so the measured "about a quarter of the arming swing" is now
exact. Overflow reflection is unchanged — it prices off the committed value, as before.

### 4. Consume-time feedback works on one-shots and substitutes names (R-QC41)

A one-shot `Charge` carries a `Feedback` record like a timed window. The BURN is blind (every armed key at
once) but the BLOCK is not: gate 5 asks whether one ability's own scope is armed, so the charge that stopped it
can name itself — which is the only moment a consume cue ever described.

The emit substitutes MESSAGE's name vocabulary: `{ATTACKER}` is whoever ARMED the suppression, `{VICTIM}` the
player it just silenced. That is the pairing `consumed-message-actor` / `consumed-message-victim` already name,
so a line and its recipient cannot disagree; on a defender-keyed `SUPPRESS_INCOMING` the armer is the protected
holder, which its doc states. The armer's NAME is captured at the arm and stored on the record, so the block
reads back a string instead of resolving a UUID on whatever region the suppressed player fires from.

R-QC24 is unchanged: MARK still carries no feedback. The surface exists for suppression alone.

## Consequences

- Silence, Mastery Tombstone and Neutralize do what they advertise for the first time. Anything defender-side
  is now silenceable by a TYPE window — the intended blast radius, and the reason the eight `suppress-immune`
  flags are load-bearing rather than decorative.
- A pack can key a bespoke type with `suppress-type:`, at the cost of the implicit stamp on that ability.
- `TypeSuppressionEndToEndTest` walks loader → compiler → gate for both halves; the compiler-fuzz round-trip
  assertion learned the TYPE fold.
- The pinned `theArmedCapIsPricedOffThePreviousHitNotTheArmingOne` is flipped to
  `theArmedCapIsPricedOffTheArmingHit`, per its own Javadoc. Deviations D-01-07 and D-06-18 note the basis
  correction.
- `Sink.suppress`/`suppressIncoming` take the armer's name; the deferred-content rows for `masks/death-knight`
  and `masks/rift` close.
