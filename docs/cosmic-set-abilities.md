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
| **Shaped particles** | `PARTICLE_RING`, `PARTICLE_LINE`, `TETHER` heads + colored-dust Sink draw (cross-version `DustOptions` / legacy offset-RGB) | koth, reaper | 🔵 |
| **Color codec** | `D.color()` param type — `{ r,g,b }` / `#RRGGBB` / `&c` → packed `0xRRGGBB` | koth, reaper | 🔵 |
| **Count-scaled damage** | `DAMAGE_SCALE` — `per`-target % into the additive fold, `cap`-clamped, actor-centred count | koth | 🔵 |
| **Cosmetic lightning** | `LIGHTNING { damage: 0 }` strikes a visual-only bolt (no vanilla ~5 dmg / fire) | yijki, thor | 🔵 |
| **Selective cleanse** | `CURE { category: HARMFUL\|BENEFICIAL\|NEUTRAL\|ALL }` (default `ALL` = back-compat) via canonical name sets | clarity | 🔵 |
| **Overhealth drain** | `MAX_HEALTH_DRAIN` — timed reduction of a target's bonus max-health, ledgered (rejoin/reload-safe) | cupid | 🔵 |
| **Safe teleport-behind** | `TELEPORT_BEHIND { of, distance, onFail }` — blink behind a reference, LOS/occlusion-checked, on-top fallback | stellar | 🔵 |
| **Temp-block ledger** | `TEMP_BLOCK { shape: POINT\|FOOTPRINT\|COLUMN, unbreakable }` — air-only placement, epoch-token revert (no permanence on overlap) | yeti, fantasy, devil | 🔵 |
| **Falling display blocks** | `FALLING_BLOCK` — cosmetic falling blocks that vanish on landing, first-hit-wins impact (damage + potion strip/lock) | druid | 🔵 |
| **Damage mark** | `MARK { amount, duration }` — per-(victim,attacker) bonus consulted by the damage fold | reaper | 🔵 |
| **Owner zone** | `MARK_ZONE` + `%victim.inzone%` fact — a wearer-owned cylinder; condition-gated bonuses inside it | devil | 🔵 |
| **Equipment swap** | `EQUIP_SWAP { slot, material, duration }` — timed gear replacement, ledgered, death/quit-safe | spooky | 🔵 |
| **Out-of-combat flight** | `FLY_MODE` + `CombatTagStore` + `%incombat%` fact — flight granted only while not combat-tagged | supreme | 🔵 |
| **Enemy/ally AoE** | `@Aoe{filter=ENEMIES\|ALLIES}` + an `Allies` soft-hook | thor | 🔵 |
| **Suppression extensions** | `SUPPRESS_IMMUNE` (per-player veto) + `SUPPRESS { scope: TIER }` (lock a whole rarity tier) | dragon, phantom | 🔵 |
| **Potion lock** *(optional)* | `POTION_LOCK { effect, ticks }` — strip + continuously deny a potion for a window | druid, fantasy | 🔵 |

All entity/world mutation routes through the `Sink` (Folia-correct, region-routed); every
new Sink method lands in `Sink.java` + **both** the modern and legacy overlay `DispatchSink`s
(mega-jar class-set parity); version edges (dust colour, falling-block API, teleport block
safety, potion categorisation) live behind the overlay split, never in engine-core.

---

## 2. The 15 abilities

### clarity — **Bless** (passive) 🔵
- **Effect:** while the full set is worn, the wearer's **harmful** potion effects are
  stripped continuously (a `REPEATING` cleanse, ~4×/sec). Positive effects are untouched.
- **Globalized:** selective `CURE { category: HARMFUL }`.
- *Note:* an instantaneous harmful effect (Instant Damage) still deals its single tick the
  moment it lands — it just can't be *held*. (A future `POTION_APPLY` event trigger could
  cancel it outright; out of scope for v1, and 1.8.9 has no such event.)

### cupid — **Lovestruck** 🔵
- **Permanent:** Regeneration I while worn (`POTION` passive, driver-maintained).
- **On hit — 5%, 30s cooldown:** remove **half** of the victim's *overhealth* (max-health
  above 20) for **3s**, then it returns. Only fires (and only spends the cooldown) when the
  victim actually has overhealth (`condition: %victim.maxhealth% > 20`).
- **Globalized:** `MAX_HEALTH_DRAIN` (ledgered so a victim who logs out mid-window is
  restored on rejoin).

### devil — **Hells Kitchen** ⚠️ *(reconstructed — please confirm)*
- **Permanent:** a self-reverting **netherrack trail** under the wearer's feet (`TEMP_BLOCK`
  FOOTPRINT, ledgered so the trail never becomes permanent).
- **On hit — 5%, 30s cooldown:** lay a 7×7 netherrack floor under the victim + a wearer-owned
  **hellfire zone** (`MARK_ZONE`) + flame/sound flair, ~5s.
- **While attacking an enemy inside an active zone:** +35% damage (`%victim.inzone%`).
- **Globalized:** temp-block ledger, owner-zone + `%victim.inzone%` fact.
- ⚠️ This spec was **reconstructed** (the re-paste was garbled). Confirm material/period/%.
  The current lore line "+25% damage to all enemies" has no matching mechanic.

### dragon — **Dovahkiin** (passive) 🔵
- **Effect:** while worn, the wearer is **immune to enchant-cancelling** — any
  `DISABLE_ENCHANT/GROUP/TYPE` aimed at them is vetoed at the suppression write.
- **Globalized:** `SUPPRESS_IMMUNE` (armed/disarmed by the generic `PASSIVE` lifecycle).

### druid — **Terrablender** 🔵 ⚠️ *(lore wording)*
- **On hit — 5%, 30s cooldown:** spawn a 3×3 grid of **falling grass blocks** 4 blocks above
  the victim's head. On impact: deal **1.5× the triggering hit's damage** (once — shared
  first-hit-wins flag, so the 3×3 can't multiply) and strip + deny **Speed** for 5s. The
  blocks **vanish on landing** (no block is ever placed); misses evict on TTL.
- **Globalized:** `FALLING_BLOCK`, potion-lock.
- ⚠️ Lore currently says "Terrabender Passive"; the mechanic is the on-hit "Terrablender".

### fantasy — **Fantasy Trap** 🔵
- **On hit — 10%, 30s cooldown (players only):** spawn an **unbreakable cobweb** at the
  victim's feet for **1s** (vanilla physics halts them) and strip their **Speed**. Reverts
  to air.
- **Globalized:** temp-block ledger (`unbreakable: true` engages the break-guard).

### koth — **Victorious** 🔵
- **On hit (weapon):** **+10% outgoing damage per nearby player** (friend *or* foe, not self)
  within 7 blocks, capped at **+100%** (the Victorious contribution alone; combines additively
  with the set's other buffs).
- **Permanent cosmetic (every 0.5s):** a **white-dust ring** at hip level at radius 7 + a
  **tether line** from each nearby player to the wearer.
- **Globalized:** `DAMAGE_SCALE` (actor-centred count), `PARTICLE_RING`/`PARTICLE_LINE`, color codec.

### phantom — **Ghostly Rush** (passive) 🔵 ⚠️ *(replaces a burst bonus)*
- **Permanent:** Haste I + Strength III + Speed IV (`POTION` passives).
- **Effect:** **soul-tier enchants do not function** while worn (`SUPPRESS { scope: TIER, key: soul }`,
  re-armed every 2s; lapses ~3s after removal).
- **Globalized:** TIER suppression scope.
- ⚠️ The set's current short burst-on-hit DEFENSE bonus is **removed** — the spec wants these
  as permanent passives.

### reaper — **Mark of the Reaper** 🔵 ⚠️ *(lore wording)*
- **On hit — 5%, 30s cooldown:** **mark** the victim for **3s**; while marked they take
  **+25% damage from the reaper wearer specifically** (applied by the damage fold's mark
  consult, which reads before attack abilities so the marking hit itself is excluded). A
  **dark-red dust tether** is drawn from the victim to the wearer every 0.5s while marked.
- **Globalized:** `MARK`, `TETHER`.
- ⚠️ Lore says "Passive Ability"; mechanic is on-hit.

### spooky — **Scarecrow** 🔵
- **On hit — 5%, 30s cooldown (players only):** replace the victim's **helmet with a pumpkin**
  for **3s** — which (if the helmet was a set piece) drops their set below complete and
  **deactivates their helmet's set bonus** for the window. Restored after. Death is fully
  normal: the **real** helmet drops / holy-white-scroll applies (a `@LOWEST` death listener
  restores it before scroll/keep listeners read).
- **Globalized:** `EQUIP_SWAP` (ledgered, death/quit-safe; placeholder never persists).

### stellar — **Dimensional Shift** 🔵
- **On being hit — 5%, 30s cooldown:** grant **Invisibility + Speed IV for 5s** and **blink
  1 block behind the attacker** (safe-teleport: occlusion + LOS checked); if no safe spot,
  land **on top of** the attacker.
- **Globalized:** `TELEPORT_BEHIND`.

### supreme — **Gifted Child** (passive) 🔵
- **Effect:** **flight while worn and not in combat.** Entering combat revokes flight
  (mid-air, intended); leaving combat re-grants it. Survival/Adventure only.
- **Globalized:** `FLY_MODE` + `CombatTagStore` + `%incombat%` fact (`FlyModeDriver` is the
  single grant/revoke authority).

### thor — **Stormcaller** 🔵 ⚠️ *(lore wording)*
- **On hit — 5%, 30s cooldown:** strike **every enemy within 7 blocks** (excludes the wearer,
  allied players, and passive mobs) with **lightning dealing 0.5× the triggering hit's
  damage**.
- **Globalized:** `@Aoe{filter=ENEMIES}`, cosmetic-aware `LIGHTNING`.
- ⚠️ Lore says "Passive Ability"; mechanic is on-hit. (First multi-`on:armor` set in the pack.)

### yeti — **Fortified** (on defense) 🔵
- **On being hit — 10%, no cooldown:** **negate** the incoming hit (`CANCEL`) and freeze the
  attacker: a **2-tall ice pillar** one block ahead of them + a **3×3 packed-ice footprint**
  at their feet, for **3s**. Ice is placed **only where it replaces air** (safe); the ledger
  prevents any permanence on overlap.
- **Globalized:** temp-block ledger (COLUMN + FOOTPRINT shapes).

### yijki — **Divine Shield** (on defense) 🔵
- **On being hit — 10%, 30s cooldown:** **negate** the hit (`CANCEL`, removing damage +
  wearer knockback), **launch the attacker backward** (~2 hits of knockback), and strike them
  with a **cosmetic (no-damage) lightning bolt**.
- **Globalized:** cosmetic-aware `LIGHTNING`, `VELOCITY{away}`.

---

## 3. Needs your confirmation ⚠️

The design verifiers flagged these — they don't block the build but I want your call:

1. **devil / Hells Kitchen** — the spec was garbled in the re-paste and I **reconstructed** it
   (netherrack trail + 7×7 zone + 35% in-zone). Confirm the materials, radius, durations, and
   the "+35%" target. Also: the set lore has an orphan "+25% damage to all enemies" line with
   no mechanic.
2. **Lore wording** — druid ("Terrabender Passive"), thor / reaper / spooky lore say
   "Passive Ability" but the mechanics are **on-hit**. Want me to reword the lore to match?
3. **druid / fantasy Speed denial** — spec says "remove Speed *while within*"; I implement a
   strip + short lock (`POTION_LOCK`). Confirm a continuous re-deny is wanted vs a one-shot strip.
4. **phantom** — confirm removing the existing burst-on-hit DEFENSE bonus in favour of the
   three permanent passives.

---

## 4. Implementation status

Build order (dependency-sorted), each landing as a green, committed increment:

1. Color codec · 2. Cosmetic lightning · 3. Shaped particles · 4. Count-scaled damage ·
5. Selective cleanse · 6. Overhealth drain · 7. Teleport-behind · 8. Temp-block ledger ·
9. Owner zone · 10. Falling blocks · 11. Potion lock · 12. Damage mark · 13. Suppression
extensions · 14. Enemy/ally AoE · 15. Equipment swap · 16. Out-of-combat flight ·
17. Per-set YAML + compile/content tests.

_(This section is updated as each subsystem lands.)_
