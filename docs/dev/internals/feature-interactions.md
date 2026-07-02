# Feature interactions

The hard part of an enchant engine is not running one effect — it is running
*many* effects from *many* sources against the same hit and getting the
arithmetic, the suppression, and the resource accounting right. StarEnchants
resolves every interaction with **contribute-then-resolve arbiters**: effects
*contribute* deltas through the [`Sink`](effect-engine.md#the-sink--the-only-door-to-the-world),
and a small set of arbiters in [`se/engine/interact`](../../../se/engine/src/engine/interact)
commit **once**. Because [source erasure](compiler-and-config.md#erase--source-erasure-into-one-abilityarray)
makes all five sources (enchant, set, weapon, crystal, heroic) the same `Ability`
record, they all feed every arbiter *uniformly* — there is no per-source special
case to get wrong.

An arbiter is **per-event primitive scratch owned by the single firing thread** —
not a long-lived object threaded across capabilities (that would race on Folia).
The gate walk that drives the contributions is
[the effect engine](effect-engine.md); this page is how the contributions
combine.

## The arbiters at a glance

| Concern | Arbiter | Rule |
| --- | --- | --- |
| Damage / reduction | `DamageFold` | Effects contribute deltas; one fold commits. No `event.setDamage` from an effect. |
| `DISABLE_*` suppression | `SuppressionSet` | Interned-id `O(1)` membership at gate 5; role-correct (defender vs activator). |
| Souls | `SoulLedger` | The only code that debits souls; atomic, keyed by gem UUID. |
| Slots | `SlotLedger` | `max = base + added`, `used`, `remaining`; persisted in PDC. |
| Crystals | list semantics | Crystals are a **list** of keys; N crystals → N abilities. No last-of-type collapse. |
| Omni / multi-set | `WornState` resolver | Resolved once per equip; `activeSets` is a set; omni is read-time wildcard. |

## Damage: fully additive, one fold

The approved policy (ADR [0012](../../decisions/0012-damage-stacking.md)) is **no
multiplicative stacking across sources**. Every same-side source sums into one
additive outgoing bucket and one additive reduction bucket, so the result is
order-independent by construction:

```text
final = base × (1 + Σ outgoing%) × (1 − Σ reduction%)
```

`DamageFold` (`se/engine/src/engine/interact/DamageFold.java`) holds the buckets
and applies them once. The full formula, including the flat terms and the bounded
heroic stage:

```text
folded = max(0, (base × (1 + Σ outgoing%) + Σ flatDamage) × (1 − Σ reduction%) − Σ flatReduction)
final  = max(0, folded × clamp(1 + Σ heroicOut%, 0, 4) × clamp(1 − Σ heroicRed%, 0, 1))
```

Each contribution is a plain `+=` into a bucket; the fold runs in `apply`:

```java
public double apply(double base) {
    double cappedOutgoing = Math.min(outgoingPercent, maxBonusOutgoing);
    double cappedReduction = Math.min(reductionPercent, maxBonusReduction);
    double outgoing = base * Math.max(0.0, 1.0 + cappedOutgoing) + flatDamage;
    double mitigated = outgoing * Math.max(0.0, 1.0 - cappedReduction) - flatReduction;
    double folded = Math.max(0.0, mitigated);
    double ceiling = Math.max(1.0, maxOutgoingFactor.getAsDouble());
    double heroicOut = Math.min(ceiling, Math.max(0.0, 1.0 + heroicOutgoing));
    double heroicRed = Math.max(0.0, Math.min(1.0, 1.0 - heroicReduction));
    return Math.max(0.0, folded * heroicOut * heroicRed);
}
```

The percentages are summed first, *then* capped against the per-event combat
ceilings (`combat.max-bonus-damage` / `max-bonus-reduction`, `+inf` when
uncapped). Every factor is floored at `0.0`, so over-100% reduction contributes
nothing rather than healing the victim. Heroic flat stats + set
`DAMAGE`/`REDUCTION` + crystal + weapon all feed the *same* accumulators — there
is no per-source weighting, so the result cannot depend on which order the
abilities fired. Because the accumulator stores compiled expression contributions
(not just constants), an equation-capable `DAMAGE_INCREASE` still evaluates
per-hit yet folds once.

### Heroic percents

Heroic percents feed the **same additive fold** as any enchant contribution (ADR
[0037](../../decisions/0037-heroic-additive-fold.md), which superseded the retired
bounded-multiplicative stage of ADR
[0021](../../decisions/0021-heroic-multiplicative-stage.md)): a heroic weapon adds
its outgoing% into `Σ outgoing%`, heroic armour its reduction% into `Σ reduction%`.
The percents come from a heroic *stat* stamped onto gear by `HeroicService#applyTo`
(`se/feature/src/feature/heroic/HeroicService.java`).

Heroic is not set-bound — any armour or weapon may be upgraded. The stat rides on
the item's `CombatState`, the [worn-set resolver](performance-hot-paths.md) lifts
it into `WornState#heroic`, and the firing system feeds it into the fold via
`Sink#addOutgoingDamage`/`addDamageReduction` — exactly like any enchant
`DAMAGE_MOD`, so heroic dilutes additively with other buffs.

### Where the fold is committed

`CombatDispatch#onDamage`
(`se/feature/src/feature/combat/CombatDispatch.java`) is the orchestrator. It
builds one `DispatchSink` (which owns the `DamageFold`), applies the live combat
caps, runs the attacker pass then the victim pass — each contributing into the
*same* fold — and commits exactly once:

```java
DispatchSink sink = new DispatchSink(handles, economy, souls, vars, suppression, knockback, keepOnDeath,
        teleblock, immune, nowTicks, maxHeroicOutgoing);
sink.fold().caps(maxBonusDamage.getAsDouble(), maxBonusReduction.getAsDouble()); // live combat caps
…
// Fold every damage contribution onto the event ONCE; honour a cancel; flush deferred work.
event.setDamage(sink.fold().apply(event.getDamage()));
if (sink.armorIgnored()) { /* IGNORE_ARMOR: zero ARMOR + MAGIC modifiers */ }
if (sink.cancelled()) event.setCancelled(true);
sink.flush();
```

The fold scratch is owned by this one event on this one thread, and the event
belongs to the firing region — so this is always correct on Folia. The `%damage%`
fact is captured *before* the fold mutates the event, so a condition reading
`%damage%` sees the activation-time value. `CombatListener`
(`feature/combat/CombatListener.java`) is the thin Bukkit bridge — `HIGH` priority
with `ignoreCancelled` so SE folds after the base damage calculation and skips a
cancelled hit.

## Suppression

`DISABLE_ENCHANT` / `DISABLE_GROUP` / `DISABLE_TYPE` effects silence other
abilities. The membership test is gate 5 — an `O(1)` `contains` against a
`BitSet` of interned ids, with no string compares because the keys are case-folded
to interned ids at compile time. `SuppressionSet`
(`se/engine/src/engine/interact/SuppressionSet.java`) is the whole structure:

```java
private final BitSet ids = new BitSet();
public void add(int id)      { if (id >= 0) ids.set(id); }   // negative = "no key", ignored
public boolean contains(int id) { return id >= 0 && ids.get(id); }
```

The subtlety is **role-correctness**, which the lowering bakes into each op:

- **`DISABLE_ENCHANT` keys the defender.** "Turn off the victim's enchant." The
  key matches `equalsIgnoreCase` → interned.
- **`DISABLE_GROUP` keys the activator.** "Turn off this group on me." The key
  matches `equals` → interned.

So an activation carries one suppression set *per role*, and each ability checks
the set matching the role its `suppressKey` was lowered against. Because crystals
are first-class `Ability` sources, a crystal-`DISABLE_ENCHANT` works — dead in a
Cosmic Enchants-style design. A cancellable `PreActivate` event still exists at
gate 9 for add-on interception. Suppression can also be *timed* (a
`SuppressionStore` per-player TTL across the three cooldown scopes), checked in the
same gate.

## Souls

`SoulLedger` (`se/engine/src/engine/interact/SoulLedger.java`) is the **only code
that debits souls**. Soul cost is gate 10, charged only when a gem is active. The
debit is atomic under a fixed array of stripe locks keyed by the gem's stable
UUID:

```java
public boolean tryConsume(UUID gemId, Balance balance, int cost) {
    if (cost <= 0) return true;
    synchronized (stripeFor(gemId)) {
        int current = currentLocked(gemId, balance);
        if (current < cost) return false;
        int next = current - cost;
        authoritative.put(gemId, next);
        balance.setSouls(next); // write through to the durable copy
        return true;
    }
}
```

The authority is kept **in memory keyed by the gem UUID**, not in the PDC,
because on Folia an `ItemStack`/PDC is a per-thread *copy* — serializing PDC
writes would not stop a double-spend. The in-memory count is seeded from the
durable `Balance` on first touch and written through on every change. `Balance`
is the caller's PDC proxy; `SoulService` (`se/feature/src/feature/soul/SoulService.java`,
which implements `SoulDebit`) supplies it and bridges the ledger to durable
storage with deferred, identity-located write-throughs so the gem is found
wherever it sits in the inventory. Deposits are **on any kill** (ADR-frame
directive §D, read live so a reload can flip it) while spending stays gated by
soul mode. `SoulBinding` (`feature/soul/SoulBinding.java`) is the tiny record
carrying the active gem id and its `Balance` into `Activation.soulMode` for gate
10.

## Slots

`SlotLedger` (`se/engine/src/engine/interact/SlotLedger.java`) is an immutable
value computed from the item's `CombatState` and persisted in PDC. The capacity
rule is `max = base + added`:

```java
public record SlotLedger(int base, int added, int used) {
    public int max() { return base + added; }
    /** Free capacity, never negative — an over-filled item reports 0. */
    public int remaining() { return Math.max(0, max() - used); }
    public boolean canApply(int count) { return count <= remaining(); }
    public SlotLedger withAddedSlots(int extra) { return new SlotLedger(base, added + Math.max(0, extra), used); }
}
```

`base` is the unified default (9 slots), `added` is bought with slot-increase
orbs, `used` is the enchant count. `SlotService#applyTo`
(`se/feature/src/feature/slot/SlotService.java`) raises `added`, clamped so the
**total** never exceeds `slots.hardCap` (`maxAdded = hardCap − base`), and only
consumes the orb when it actually raises the count. The ledger is consumed on the
cold apply path by `ItemEnchanter#checkSlots`
(`se/feature/src/feature/apply/ItemEnchanter.java`), which lets a `removes-required`
upgrade cost `1 − freed` net slots (a non-positive cost always fits). Crystals use
a **separate** ledger (`crystalSlots`, default 1), so crystal capacity never eats
enchant capacity.

## Crystals are a list

Crystals **stack** (ADR-frame directive §E): they are a `List<String>` of keys,
order-preserving, **never collapsed** to last-of-type. N crystals contribute N
abilities, and the additive fold sums any overlaps. The list lives in
`CombatState`; `ItemEnchanter#applyCrystalEntry` adds one entry per application:

```java
List<String> crystals = new ArrayList<>(current.crystals());
crystals.add(String.join(CrystalItemData.DELIMITER, keys)); // ONE entry = ONE slot
CombatState next = new CombatState(current.enchants(), crystals,
        current.setKey(), current.omni(), current.heroic(), current.added());
```

A *multi-crystal* — two singles merged by `CrystalService#merge`
(`se/feature/src/feature/crystal/CrystalService.java`, pairs only) — is stored as
one `"a+b"` entry that occupies one slot yet contributes *both* abilities. Two
caps apply: the per-item `crystalSlots`, and a hard `maxCrystals` stack cap
(default 16) that always holds to keep PDC from bloating. Extraction pops the
most-recently-applied entry whole and re-mints it as a crystal.

## Omni and multi-set completion

A player can wear more than one set at once. The [worn-set resolver](performance-hot-paths.md)
runs **once per equip change** and produces an immutable `WornState` whose
`activeSets` is a `BitSet` — a *set*, not a single active set — so multiple set
bonuses coexist and each contributes its abilities to the pre-flattened arrays.
**Omni** is a wildcard set membership resolved synchronously, read-time, inside
that one resolver; an omni piece counts toward whatever set the rest of the armour
forms. A set's *weapon member* (the extra bonus while the set is complete and
held) is a separate `setWeaponKey` on `CombatState`, minted by
`ItemEnchanter#mintSetPiece`. Set membership, omni, enchants, crystals, and the
heroic stat are **orthogonal fields on one `CombatState`** — every apply/remove
reconstructs the record preserving the others.

## Custom-enchant stamping

Stamping a custom enchant onto gear is **synchronous, one write path** —
`ItemEnchanter` is the single cold mutation path, never fire-and-forget. It splits
validation (`check*`) from mutation (`apply*`): `checkApplicable`, `checkSlots`,
and `checkRelationships` (§G requires + bidirectional blacklist) all run before a
single `CombatState` write and a lore re-render. Lore is always rendered *from*
state, never the source of truth.

## Where it lives

| Concern | File |
| --- | --- |
| Damage fold | `se/engine/src/engine/interact/DamageFold.java` |
| Suppression set | `se/engine/src/engine/interact/SuppressionSet.java` |
| Soul ledger | `se/engine/src/engine/interact/SoulLedger.java` |
| Slot ledger | `se/engine/src/engine/interact/SlotLedger.java` |
| Combat orchestration | `se/feature/src/feature/combat/CombatDispatch.java`, `combat/CombatListener.java` |
| Heroic | `se/feature/src/feature/heroic/HeroicService.java`, `heroic/HeroicResult.java` |
| Crystals | `se/feature/src/feature/crystal/CrystalService.java` |
| Slots | `se/feature/src/feature/slot/SlotService.java` |
| Souls | `se/feature/src/feature/soul/SoulService.java`, `soul/SoulBinding.java` |
| Cold apply path | `se/feature/src/feature/apply/ItemEnchanter.java` |

## Gotchas and invariants

- **An effect contributes; it never commits.** Call `sink.addOutgoingDamage(...)`,
  not `event.setDamage`. The fold commits once on the firing thread.
- **The fold is order-independent.** Same-side sources sum into one bucket; do not
  add per-source weighting that reintroduces order sensitivity.
- **Heroic folds additively** (ADR-0037): a heroic weapon's outgoing% and armour's
  reduction% sum into the ordinary buckets, like any enchant. There is no
  multiplicative stage.
- **Suppression is role-correct.** `DISABLE_ENCHANT` → defender, `DISABLE_GROUP`
  → activator; match the right per-role set.
- **`SoulLedger` is the sole debit authority.** Never write souls to PDC directly
  on a spend; go through the ledger so the in-memory authority stays consistent on
  Folia.
- **Crystals never collapse.** They are a list; preserve order and multiplicity.
- **`WornState` is multi-set.** `activeSets` is a set and is resolved once per
  equip — never per hit. See [the hot path](performance-hot-paths.md).
- **One cold write path for items.** Mutate gear through `ItemEnchanter`; render
  lore from state.

Adjacent reading: [the effect engine](effect-engine.md) for the gate walk that
drives contributions; [the compiler](compiler-and-config.md) for how suppression
keys and effects are interned; [the hot path](performance-hot-paths.md) for the
`WornState` resolve; and [architecture spec](../../architecture.md) §6 plus ADRs
[0012](../../decisions/0012-damage-stacking.md) and
[0037](../../decisions/0037-heroic-additive-fold.md) for the rationale.
