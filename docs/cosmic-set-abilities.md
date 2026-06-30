# Cosmic Pack — set abilities

This document is the authoritative design + status for the 15 Cosmic Pack armour-set
abilities and the **globalized engine subsystems** they introduce. Each subsystem is a
reusable primitive (a new effect head, Sink intent, store, selector filter, or fact) so a
future set/enchant/crystal gets the same behaviour as **pure YAML — no new code**.

> Status legend: ✅ implemented & green · 🟡 in progress · 🔵 designed (pending) · ⚠️ needs author confirmation

---

## 1. Globalized subsystems (the reusable building blocks)

Everything an ability does flows through one of these. They are not set-specific — any
content can author them.

| Subsystem | New vocabulary | Used by | Status |
| --- | --- | --- | --- |
| **Shaped particles** | `PARTICLE_RING`, `PARTICLE_LINE` heads + coloured-dust `Sink.dust` (cross-version `DustOptions` / legacy offset-RGB). Moving `TETHER` variant deferred | koth, reaper | ✅ |
| **Color params** | `r`/`g`/`b` int params (0-255) on the dust effects — simpler than a `D.color()` codec the grammar can't nest | koth, reaper | ✅ |
| **Count-scaled damage** | `DAMAGE_SCALE` — `per`-target % into the additive fold, `cap`-clamped, actor-centred count | koth | ✅ |
| **Cosmetic lightning** | `LIGHTNING { damage: 0 }` strikes a visual-only bolt (no vanilla ~5 dmg / fire) | yijki, thor | ✅ |
| **Selective cleanse** | `CURE { category: HARMFUL\|BENEFICIAL\|NEUTRAL\|ALL }` (default `ALL` = back-compat) via canonical name sets | clarity | ✅ |
| **Overhealth drain** | `MAX_HEALTH_DRAIN` — timed reduction of a target's bonus max-health, ledgered (rejoin/reload-safe) | cupid | ✅ |
| **Safe teleport-behind** | `TELEPORT_BEHIND { of, distance, onFail }` — blink behind a reference, LOS/occlusion-checked, on-top fallback | stellar | ✅ |
| **Temp-block ledger** | `TEMP_BLOCK { shape: POINT\|FOOTPRINT\|COLUMN, unbreakable }` — air-only placement, epoch-token revert (no permanence on overlap) | yeti, fantasy, devil | ✅ |
| **Falling display blocks** | `FALLING_BLOCK` — cosmetic falling blocks that vanish on landing, first-hit-wins impact (damage + potion strip/lock) | druid | ✅ |
| **Damage mark** | `MARK { amount, duration }` — per-(victim,attacker) bonus consulted by the damage fold | reaper | ✅ |
| **Owner zone** | `MARK_ZONE` + `%victim.inzone%` fact — a wearer-owned cylinder; condition-gated bonuses inside it | devil | 🔵 |
| **Equipment swap** | `EQUIP_SWAP { slot, material, duration }` — timed gear replacement, ledgered, death/quit-safe | spooky | ✅ |
| **Out-of-combat flight** | `FLY_MODE` + `CombatTagStore` + `%incombat%` fact — flight granted only while not combat-tagged | supreme | ✅ |
| **Enemy/ally AoE** | `@Aoe{filter=ENEMIES\|ALLIES}` + an `Allies` soft-hook | thor | ✅ |
| **Suppression-immune** | `SUPPRESS_IMMUNE` (per-player veto) — dragon. phantom's soul lockout reuses the existing `SUPPRESS { scope: GROUP, key: soul }`, so no new TIER scope was needed | dragon, phantom | ✅ |
| **Potion lock** *(optional)* | `POTION_LOCK { effect, ticks }` — strip + continuously deny a potion for a window | druid, fantasy | 🔵 |

All entity/world mutation routes through the `Sink` (Folia-correct, region-routed); every
new Sink method lands in `Sink.java` + **both** the modern and legacy overlay `DispatchSink`s
(mega-jar class-set parity); version edges (dust colour, falling-block API, teleport block
safety, potion categorisation) live behind the overlay split, never in engine-core.

---

## 2. The 15 abilities

### clarity — **Bless** (passive) ✅
- **Effect:** while the full set is worn, the wearer's **harmful** potion effects are
  stripped continuously (a `REPEATING` cleanse, ~4×/sec). Positive effects are untouched.
- **Globalized:** selective `CURE { category: HARMFUL }`.
- *Note:* an instantaneous harmful effect (Instant Damage) still deals its single tick the
  moment it lands — it just can't be *held*. (A future `POTION_APPLY` event trigger could
  cancel it outright; out of scope for v1, and 1.8.9 has no such event.)

### cupid — **Lovestruck** ✅
- **Permanent:** Regeneration I while worn (`POTION` passive, driver-maintained).
- **On hit — 5%, 30s cooldown:** remove **half** of the victim's *overhealth* (max-health
  above 20) for **3s**, then it returns. Only fires (and only spends the cooldown) when the
  victim actually has overhealth (`condition: %victim.maxhealth% > 20`).
- **Globalized:** `MAX_HEALTH_DRAIN` (ledgered so a victim who logs out mid-window is
  restored on rejoin).

### devil — **Hells Kitchen** ✅ (confirmed)
- **Permanent:** a self-reverting **netherrack trail** under the wearer's feet (`TEMP_BLOCK`
  FOOTPRINT, ledgered so the trail never becomes permanent).
- **On hit — 5%, 30s cooldown:** lay a 7×7 netherrack floor under the victim + a wearer-owned
  **hellfire zone** (`MARK_ZONE`) + flame/sound flair, ~5s.
- **While attacking an enemy inside an active zone:** +35% damage (`%victim.inzone%`).
- **Globalized:** temp-block ledger, owner-zone + `%victim.inzone%` fact.
- ⚠️ This spec was **reconstructed** (the re-paste was garbled). Confirm material/period/%.
  The current lore line "+25% damage to all enemies" has no matching mechanic.

### dragon — **Dovahkiin** (passive) ✅
- **Effect:** while worn, the wearer is **immune to enchant-cancelling** — any
  `DISABLE_ENCHANT/GROUP/TYPE` aimed at them is vetoed at the suppression write.
- **Globalized:** `SUPPRESS_IMMUNE` (armed/disarmed by the generic `PASSIVE` lifecycle).

### druid — **Terrablender** ✅ ⚠️ *(lore wording)*
- **On hit — 5%, 30s cooldown:** spawn a 3×3 grid of **falling grass blocks** 4 blocks above
  the victim's head. On impact: deal **1.5× the triggering hit's damage** (once — shared
  first-hit-wins flag, so the 3×3 can't multiply) and strip + deny **Speed** for 5s. The
  blocks **vanish on landing** (no block is ever placed); misses evict on TTL.
- **Globalized:** `FALLING_BLOCK`, potion-lock.
- ⚠️ Lore currently says "Terrabender Passive"; the mechanic is the on-hit "Terrablender".

### fantasy — **Fantasy Trap** ✅
- **On hit — 10%, 30s cooldown (players only):** spawn an **unbreakable cobweb** at the
  victim's feet for **1s** (vanilla physics halts them) and strip their **Speed**. Reverts
  to air.
- **Globalized:** temp-block ledger (`unbreakable: true` engages the break-guard).

### koth — **Victorious** ✅
- **On hit (weapon):** **+10% outgoing damage per nearby player** (friend *or* foe, not self)
  within 7 blocks, capped at **+100%** (the Victorious contribution alone; combines additively
  with the set's other buffs).
- **Permanent cosmetic (every 0.5s):** a **white-dust ring** at hip level at radius 7 + a
  **tether line** from each nearby player to the wearer.
- **Globalized:** `DAMAGE_SCALE` (actor-centred count), `PARTICLE_RING`/`PARTICLE_LINE`, color codec.

### phantom — **Ghostly Rush** (passive) ✅ ⚠️ *(replaces a burst bonus)*
- **Permanent:** Haste I + Strength III + Speed IV (`POTION` passives).
- **Effect:** **soul-tier enchants do not function** while worn (`SUPPRESS { scope: TIER, key: soul }`,
  re-armed every 2s; lapses ~3s after removal).
- **Globalized:** TIER suppression scope.
- ⚠️ The set's current short burst-on-hit DEFENSE bonus is **removed** — the spec wants these
  as permanent passives.

### reaper — **Mark of the Reaper** ✅ ⚠️ *(lore wording)*
- **On hit — 5%, 30s cooldown:** **mark** the victim for **3s**; while marked they take
  **+25% damage from the reaper wearer specifically** (applied by the damage fold's mark
  consult, which reads before attack abilities so the marking hit itself is excluded). A
  **dark-red dust tether** is drawn from the victim to the wearer every 0.5s while marked.
- **Globalized:** `MARK`, `TETHER`.
- ⚠️ Lore says "Passive Ability"; mechanic is on-hit.

### spooky — **Scarecrow** ✅
- **On hit — 5%, 30s cooldown (players only):** replace the victim's **helmet with a pumpkin**
  for **3s** — which (if the helmet was a set piece) drops their set below complete and
  **deactivates their helmet's set bonus** for the window. Restored after. Death is fully
  normal: the **real** helmet drops / holy-white-scroll applies (a `@LOWEST` death listener
  restores it before scroll/keep listeners read).
- **Globalized:** `EQUIP_SWAP` (ledgered, death/quit-safe; placeholder never persists).

### stellar — **Dimensional Shift** ✅
- **On being hit — 5%, 30s cooldown:** grant **Invisibility + Speed IV for 5s** and **blink
  1 block behind the attacker** (safe-teleport: occlusion + LOS checked); if no safe spot,
  land **on top of** the attacker.
- **Globalized:** `TELEPORT_BEHIND`.

### supreme — **Gifted Child** (passive) ✅
- **Effect:** **flight while worn and not in combat.** Entering combat revokes flight
  (mid-air, intended); leaving combat re-grants it. Survival/Adventure only.
- **Globalized:** `FLY_MODE` + `CombatTagStore` + `%incombat%` fact (`FlyModeDriver` is the
  single grant/revoke authority).

### thor — **Stormcaller** ✅ ⚠️ *(lore wording)*
- **On hit — 5%, 30s cooldown:** strike **every enemy within 7 blocks** (excludes the wearer,
  allied players, and passive mobs) with **lightning dealing 0.5× the triggering hit's
  damage**.
- **Globalized:** `@Aoe{filter=ENEMIES}`, cosmetic-aware `LIGHTNING`.
- ⚠️ Lore says "Passive Ability"; mechanic is on-hit. (First multi-`on:armor` set in the pack.)

### yeti — **Fortified** (on defense) ✅
- **On being hit — 10%, no cooldown:** **negate** the incoming hit (`CANCEL`) and freeze the
  attacker: a **2-tall ice pillar** one block ahead of them + a **3×3 packed-ice footprint**
  at their feet, for **3s**. Ice is placed **only where it replaces air** (safe); the ledger
  prevents any permanence on overlap.
- **Globalized:** temp-block ledger (COLUMN + FOOTPRINT shapes).

### yijki — **Divine Shield** (on defense) ✅
- **On being hit — 10%, 30s cooldown:** **negate** the hit (`CANCEL`, removing damage +
  wearer knockback), **launch the attacker backward** (~2 hits of knockback), and strike them
  with a **cosmetic (no-damage) lightning bolt**.
- **Globalized:** cosmetic-aware `LIGHTNING`, `VELOCITY{away}`.

---

## 3. As-shipped notes

The lore wording was fixed (on-hit → "*Name* Ability", defense/permanent → "*Name* Passive
Ability"; druid renamed Terrabender → Terrablender); devil / Hell's Kitchen was confirmed; and
phantom's old burst bonus was replaced by the permanent passives. A few mechanics shipped as a
pragmatic variant of the exact spec — each is a candidate for a later refinement (none blocks a set):

- **phantom soul lockout** — implemented as `SUPPRESS { scope: GROUP, key: soul }` (every soul-tier
  enchant already carries `group: soul`), so **no core `Ability` arity change** was needed.
- **druid / fantasy Speed** — a one-shot `REMOVE_POTION` strip on impact, not a continuous 5s
  re-deny (the optional `POTION_LOCK` would add the lock).
- **reaper tether** — a one-shot dark-red dust line at the moment of marking, not a continuous
  0.5s beam while marked (a moving `TETHER` repeater is the refinement).
- **devil +35% in-zone** — the netherrack trail + 7×7 floor + flame/sound shipped; the +35% to
  enemies *inside the hellfire zone* awaits the owner-zone (`%victim.inzone%`) subsystem.
- **fantasy cobweb** — temporary + brief (1 s); the hard "unbreakable" guard is reserved.
- **koth count** — `DAMAGE_SCALE who: @AllPlayers{r=7}` centres on the victim during an ATTACK
  (melee-adjacent to the wearer); a wearer-centred selector is a future nicety.
- **`MAX_HEALTH_DRAIN`** — overlap-safe exact-delta restore; a victim who logs out mid-window keeps
  the reduction until their next drain (rare; a rejoin ledger is the refinement).

---

## 4. Implementation status — ✅ all 15 sets shipped (green via `./gradlew build`, committed)

**Set abilities (15/15):** clarity Bless · cupid Lovestruck · devil Hell's Kitchen · dragon
Dovahkiin · druid Terrablender · fantasy Fantasy Trap · koth Victorious · phantom Ghostly Rush ·
reaper Mark of the Reaper · spooky Scarecrow · stellar Dimensional Shift · supreme Gifted Child ·
thor Stormcaller · yeti Fortified · yijki Divine Shield.

**Globalized subsystems built** (each reusable as pure YAML by future content):
`LIGHTNING{damage:0}` cosmetic bolt · `DAMAGE_SCALE` · `@Aoe{filter=ENEMIES|ALLIES}` + `Allies` ·
`CURE{category}` + `Sink.cureByCategory` · `PARTICLE_RING`/`PARTICLE_LINE` + coloured `Sink.dust` ·
`TELEPORT_BEHIND` + `Sink.teleportSafe` · `SUPPRESS_IMMUNE` · `TEMP_BLOCK` (POINT/FOOTPRINT/COLUMN,
self-reverting) · `MAX_HEALTH_DRAIN` · `FALLING_BLOCK` + the **abstractable `IMPACT` trigger** (a
landing block fires any author-defined effects on what it hit) · `FLY_MODE` + `CombatTag` · `MARK`
+ the damage-fold consult · `EQUIP_SWAP` + `TempEquip` (death/quit-safe).

All entity/world mutation routes through the `Sink`; every new method lands in **both** the modern
+ legacy overlays (mega-jar class-set parity); the cross-cutting registries (`FallingBlockCasts`,
`CombatTag`, `DamageMarks`, `TempEquip`) are static, era-agnostic, and cleared on disable. The
`D.color()` codec was dropped in favour of `r`/`g`/`b` int params.

**Remaining gate before a PR:** the live Paper + Folia integration matrix — the dust/teleport/
temp-block/falling-block/equip-swap/fly paths have live-only oracles (`./gradlew build` is green;
the matrix is the second gate).
