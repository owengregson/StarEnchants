# Decomposition matrix — 03: sword & weapon enchants

36 entries, codex order (`03-enchants-swords.md`). Conventions:

- Defensive entries record SINGLE-PASS intended values (`deviations.md` D-001);
  the jar's defensive double-fire (`00-MECHANICS.md` §3) is never replicated.
- `D3-nn` are provisional ledger ids for this doc's deviations, to be renumbered
  into `deviations.md` when the ledger is consolidated.
- Decompositions use only primitives in `docs/reference/authoring-surface.txt`
  at HEAD, written `KIND(param=value, …) @Selector wait=N`. Ability-level
  `chance:`/`cooldown:` knobs and `±N %chance%` condition clauses are part of
  the authoring surface (dsl-reference §Flow / chance clauses).
- `L` = enchant level. Ticks unless stated; cosmic ms windows are converted at
  20 t/s. Strings are verbatim; runtime values become `{brace}` tokens.
- Acquisition rows (`base`/`interval`/`max`, table weight, tier) are recorded
  under **numbers** for the pack's acquisition config; they do not affect the
  runtime decomposition.

---

## Entries

### Assassin (`enchants/assassin`)

- **codex:** `03-enchants-swords.md § Assassin`
- **activation:** trigger `ATTACK`; swords only; no chance roll; condition rows
  on `%distance%` (net bands below). Cross-world hits cannot occur in-engine
  (no condition needed).
- **decomposition:** one condition-gated row per net distance band, all
  `DAMAGE_MOD(side=attack, mode=add, amount=<percent>)`:
  1. `%distance% < 0.25` → `DAMAGE_MOD(side=attack, mode=add, amount=5·L)`
  2. `%distance% >= 0.25 && %distance% < 0.5` → `amount=4·L`
  3. `%distance% >= 0.5 && %distance% < 0.75` → `amount=3·L`
  4. `%distance% >= 0.75 && %distance% < 1.0` → `amount=2·L`
  5. `%distance% >= 1.0 && %distance% < 1.5` → `amount=1·L`
  6. `%distance% > 2.0 && %distance% <= 2.5` → `amount=-1·L`
  7. `%distance% > 2.5 && %distance% <= 3.0` → `amount=-2.25·L`
  8. `%distance% > 3.0` → `amount=-4.75·L`
- **interactions:** heroic upgrade path to Shadow Assassin (07 doc). Composes
  with every other attack modifier through the engine's additive damage fold
  (measured jar composed multiplicatively in unspecified order — engine-model
  difference covered by the fold design, not a per-item deviation).
- **strings:** none.
- **numbers:** net multipliers per codex table (L5: ×1.25 / ×1.20 / ×1.15 /
  ×1.10 / ×1.05 / ×1.00 / ×0.95 / ×0.8875 / ×0.7625). Bands in the jar are
  cumulative independent `if`s; the net-band rows above reproduce the same
  totals exactly. Penalty past 2 blocks is real and kept. Acquisition:
  max 5, base 15.0, interval 6.0, weight 5, tier 4.
- **era:** none (pure damage math).

---

### Blind (`enchants/blind`)

- **codex:** `03-enchants-swords.md § Blind`
- **activation:** trigger `ATTACK` (+ `BOW` — item set swords_and_bow);
  chance `10·L`%.
- **decomposition:**
  1. `POTION(effect=BLINDNESS, level=L, duration=30·L) @Victim`
- **interactions:** Clarity (armor, 01 doc) blocks blindness whose amplifier ≤
  `(clarity==3 ? 3 : clarity−1)` — interaction-layer rule
  `clarity-blocks-blindness`, player victims only (mobs always blinded).
- **strings:** none.
- **numbers:** chance 10/20/~30% (L3 measured `0.30000000000000004`); duration
  30/60/90 t; amplifier `L−1` (Blindness I–III). Non-force application: an
  existing stronger/longer blindness wins (engine potion semantics match).
  The jar's declared recently-blinded marker is dead — not ported. Acquisition:
  max 3, base 15.0, interval 6.0, weight 5, tier 3.
- **era:** BLINDNESS exists on 1.8.9; no hazard.

---

### Block (`enchants/block`)

- **codex:** `03-enchants-swords.md § Block`
- **activation:** trigger `DEFENSE`; condition `%blocking% == true` (defender
  actively blocking); chance `10·L`%.
- **decomposition:**
  1. `DAMAGE_MOD(side=defense, mode=add, amount=<reduction-percent>)`
  2. `SOUND(sound=ENTITY_ITEM_BREAK, volume=0.7, pitch=0.2)`
- **interactions:** stacks with vanilla blocking reduction (applied by the
  server after the fold), as measured.
- **strings:** none.
- **numbers:** measured (integer division `0.5·(L/2)`): L1 ×1.0 (**no-op**),
  L2 ×0.5, L3 ×0.5 — as-intended (real division `0.5·(L/2.0)`): 25% / 50% /
  75% reduction → ledger `D3-01`. Defensive double-fire member (left column,
  00 §3.4) → single-pass values per D-001. Chance 10/20/~30%. Acquisition:
  max 3, base 17.0, interval 4.0, weight 2, tier 4.
- **era:** sword right-click blocking is a 1.8-era mechanic; on modern Paper
  `%blocking%` resolves to shield-raise. Sound `ITEM_BREAK` (legacy) ↔
  `ENTITY_ITEM_BREAK` (modern) via the boot-time sound resolver.

---

### Deep Wounds (`enchants/deep-wounds`)

- **codex:** `03-enchants-swords.md § Deep Wounds`
- **activation:** trigger `ATTACK`; no roll, no guards — every qualifying hit.
- **decomposition:**
  1. `SET_VAR(name=deepwounds, value=1, ttl=30·L) @Victim` (1500·L ms window)
  2. `DAMAGE_MOD(side=attack, mode=add, amount=1·L)` (the flat `+0.01·L` term)
  3. bleed-stack term → gap (below); as-authored today the `+0.5%` per bleed
     stack cannot ride the fold.
- **gaps:** `VAR_SCALED_DAMAGE — contribute per×⌈var value⌉ percent (or flat)
  to the damage fold, clamped to cap; params: var name, side, mode, per, cap;
  consumers: Deep Wounds (+0.5%/bleed stack), Rage (combo×5·L% capped +150%),
  cross-doc stack-scaled enchants. DAMAGE_SCALE counts a selector's resolved
  targets, not a variable's value — no existing primitive reads a numeric var
  into the fold (verified against the engine reference: DAMAGE_SCALE
  contributes per resolved selector target — total = per × count — so it
  cannot read a variable's value).`
- **interactions:** reads the Bleed stack counter (axes doc; counter authored
  there via `COUNTER_VAR`, see §Rage). Blessed (axes) is blocked while its own
  wielder's `%deepwounds%` var is live — Blessed-side condition
  `!%deepwounds%`, recorded here because this enchant arms it; Blessed's
  refusal string `§4** DEEP WOUNDS §7(NO BLESS)§4 **` belongs to the 04 doc.
- **strings:** none of its own.
- **numbers:** multiplier `1 + 0.01·L + 0.005·bleedstacks` (Bleed cap 20 → max
  ×1.11/×1.12/×1.13); window 1500·L ms = 30·L t. Only unconditional attack
  multiplier in the sword pool. Acquisition: max 3, base 15.0, interval 15.0,
  weight 2, tier 2.
- **era:** none.

---

### Demonforged (`enchants/demonforged`)

- **codex:** `03-enchants-swords.md § Demonforged`
- **activation:** trigger `ATTACK`; condition `%victim.type% == PLAYER`;
  chance `4·L`%.
- **decomposition:** blocked on the slot gap — the surface's
  `DURABILITY(target=armor)` addresses the whole armor set uniformly, not one
  random piece:
  1. `ARMOR_SLOT_DURABILITY(slot=random, amount=1, mode=damage,
     skip-undamaged=true) @Victim` (gap)
- **gaps:** `ARMOR_SLOT_DURABILITY — durability damage/restore addressed to a
  single worn-armor slot; params: slot(helmet|chestplate|leggings|boots|random),
  amount, mode, skip-undamaged(bool: a piece at full durability is left
  untouched); consumers: Demonforged (random slot, skip-undamaged),
  Disintegrate (per-slot profile), armor-doc durability attacks.`
- **interactions:** none.
- **strings:** none.
- **numbers:** chance 4/8/~12/16% (L3 measured `0.12000000000000001`); 1 point,
  uniform 25% per slot. Measured target is the **attacker's own** armor
  (verified in bytecode) → as-intended the victim's armor → ledger `D3-02`.
  `skip-undamaged` mirrors the jar's undamaged-piece guard (a fresh piece is
  never scratched) — kept as measured. Non-player wielder CCE in the jar is a crash
  bug, not behavior; engine is player-scoped. Acquisition: max 4, base 15.0,
  interval 3.0, weight 2, tier 3.
- **era:** none.

---

### Disarmor (`enchants/disarmor`)

- **codex:** `03-enchants-swords.md § Disarmor`
- **activation:** trigger `ATTACK`; conditions `%victim.type% == PLAYER` and
  `%victim.health% <= 10.0`; chance `0.25·L`% minus the Sticky rebate (see
  interactions).
- **decomposition:**
  1. `REMOVE_ARMOR() @Victim` (strips one random worn piece)
  2. `SOUND(sound=BLOCK_ANVIL_BREAK, volume=1.0, pitch=0.6)`
  3. `PARTICLE(particle=BLOCK_CRACK, block=DIAMOND_ORE, count=24, spread=0.4,
     spread-y=1.5)` (the jar's three stacked 2001 bursts at y+0/+1/+2)
  4. `MESSAGE(text=<victim block below>) @Victim` (eight lines)
  5. `MESSAGE(text="§a§l* DISARMED {VICTIM} *") @Self`
- **interactions:** Sticky (armor, 02 doc) subtracts `0.25` pp per Sticky level
  — interaction-layer clause `-0.25·sticky %chance%`; Sticky ≥ Disarmor level
  → never procs (kept).
- **strings:** victim receives, in order: three blank lines,
  `§6§l* DISARMORED *§7 (by: {ATTACKER})`,
  `§7A random piece of your armor has been unequipped. Re-equip it quickly before you get KO'd!`,
  three blank lines. Attacker receives `§a§l* DISARMED {VICTIM} *`.
- **numbers:** chance 0.25/0.5/0.75/1.0/1.25/1.5/~1.75/2.0% (L7 measured
  `0.017500000000000002`). Measured slot draw: a 0–3 roll vs branches 1–4 →
  25% of procs remove **nothing** (messages+sound still fire) and boots are
  unreachable; as-intended: uniform over all four slots → ledger `D3-03`.
  `REMOVE_ARMOR` drops the piece; the jar tried inventory first and dropped at
  the victim's target block only on overflow — cosmetic placement delta, noted
  for the primitive's param review. Acquisition: max 8, base 15.0,
  interval 3.0, weight 2, tier 5.
- **era:** `ANVIL_BREAK` (legacy) ↔ `BLOCK_ANVIL_BREAK` (modern); world-effect
  2001 block-crack maps to `BLOCK_CRACK` + block data, present on 1.8.9.

---

### Disintegrate (`enchants/disintegrate`)

- **codex:** `03-enchants-swords.md § Disintegrate`
- **activation:** trigger `ATTACK`; condition `%victim.type% == PLAYER`; chance
  `2·L`% (jar uses `<=`, an extra ULP of chance — immaterial at these
  magnitudes, recorded here once for the corpus).
- **decomposition:** per-slot profile → gap:
  1. `ARMOR_SLOT_DURABILITY(slot=helmet, amount=1, mode=damage,
     skip-undamaged=false) @Victim`
  2. `ARMOR_SLOT_DURABILITY(slot=chestplate, amount=2, …) @Victim`
  3. `ARMOR_SLOT_DURABILITY(slot=leggings, amount=2, …) @Victim`
  4. `ARMOR_SLOT_DURABILITY(slot=boots, amount=1, …) @Victim`
  5. `SOUND(sound=BLOCK_ANVIL_BREAK, volume=0.3, pitch=0.8)`
- **gaps:** `ARMOR_SLOT_DURABILITY` — defined at §Demonforged; consumer here
  for the −1/−2/−2/−1 slot profile with `skip-undamaged=false`.
- **interactions:** none.
- **strings:** none.
- **numbers:** chance 2/4/6/8%; durability loss level-independent. Jar plays
  the sound to the attacker only (attacker-scoped, at the victim's location);
  `SOUND` is location-scoped — cosmetic audibility delta, noted.
  Unlike Demonforged, undamaged pieces ARE affected (kept). Acquisition:
  max 4, base 15.0, interval 6.0, weight 5, tier 4.
- **era:** sound rename as §Disarmor.

---

### Divine Immolation (`enchants/divine-immolation`)

- **codex:** `03-enchants-swords.md § Divine Immolation`
- **activation:** trigger `ATTACK`; conditions: `%actor.helditem% contains
  SWORD`; held-switch gate `!%heldswitch%` (armed by a companion rule:
  `HELD` → `SET_VAR(name=heldswitch, value=1, ttl=5) @Self`); souls armed
  (soul-gem god-mode + balance > 0) and Soul Trap expired — both
  interaction-layer conditions against the engine soul system; global souls
  toggle is a pack/policy switch. No chance roll.
- **decomposition:**
  1. `REMOVE_SOULS(amount=20) @Self` — paced by a 20 t (1 s) cooldown bucket
     on this spend row (the jar throttles only the spend; see numbers)
  2. `DAMAGE(amount=⌊1.25·L⌋) @AOE{r=L, filter=ENEMIES}` (primary victim is
     inside the radius and included)
  3. `POTION(effect=WITHER, level=2, duration=⌊1.25·L⌋·20) @AOE{r=L,
     filter=ENEMIES}` (the visual/status layer)
  4. `PARTICLE(particle=FLAME, count=30, spread=0.15) @AOE` and
     `PARTICLE(particle=LAVA, count=20, spread=0.5) @AOE`
  5. `SOUND(sound=ENTITY_FIREWORK_ROCKET_BLAST, volume=1.0, pitch=0.3)` and
     `SOUND(sound=ENTITY_ZOMBIFIED_PIGLIN_ANGRY, volume=0.8, pitch=0.5)`
  6. wither-conversion burn → gap (below): `PERIODIC_DAMAGE(amount=min(7,4+L),
     period=20, duration=(4+L)·20, replace=WITHER) @AOE` (port choice: the
     codex is self-contradictory on wither cadence; 20 t is the plausible
     reading) with per-tick
     feedback `§c§l** DIVINE IMMOLATION **` + `ENTITY_ZOMBIFIED_PIGLIN_ANGRY`
     0.6/0.8 + FLAME×20/LAVA×15
- **gaps:** `PERIODIC_DAMAGE — actor-attributed flat damage every period ticks
  for duration ticks, optionally replacing (cancelling) the ticks of a named
  vanilla DoT it converts, with per-tick message/sound/particle hooks; params:
  amount, period, duration, replace(potion-effect|none), tick feedback;
  consumers: Divine Immolation's wither conversion; other converted-DoT
  enchants across the corpus. FREEZE's dot is frost-themed (powder-snow
  visual) and cannot carry a fire/wither identity.`
- **interactions:** shares the full soul-gate chain with Sabotage (souls
  system, Soul Trap debuff, held-switch window); AoE ally/vanish/gamemode/PvP
  filtering is the engine's `filter=ENEMIES` + region policy layer.
- **strings:** `§c§l** DIVINE IMMOLATION **` to a burning player on each
  converted tick.
- **numbers:** radius `1·L`; hit damage `⌊1.25·L⌋` = 1/2/3/**5** (the L3→L4
  step is +2); WITHER II for `⌊1.25·L⌋·20` t = 20/40/60/100; conversion window
  `(4+L)` s, replacement damage `min(7, 4+L)` = 5/6/7/7 (capped); souls 20 per
  spend. Measured: the 1 s throttle guards ONLY the soul spend — the AoE burst
  fires free on every gated hit; as-intended: burst and spend share the 1 s
  pace → ledger `D3-13`. Jar leaves the divine-fire mark forever (any later
  wither source inside the window converts) — the gap's window is owned and
  expires; recorded as the intended scoping. Acquisition: max 4, base 10.0,
  interval 10.0, weight 2, tier 6 (soul tier).
- **era:** `ZOMBIE_PIG_ANGRY` (legacy) ↔ `ENTITY_ZOMBIFIED_PIGLIN_ANGRY`
  (renamed 1.16); `FIREWORK_BLAST` ↔ `ENTITY_FIREWORK_ROCKET_BLAST`; FLAME /
  LAVA particles exist on 1.8.9.

---

### Dominate (`enchants/dominate`)

- **codex:** `03-enchants-swords.md § Dominate`
- **activation:** trigger `ATTACK`; chance `4·L`%.
- **decomposition:**
  1. `WEAKEN(percent=5·L, duration=2·L·20) @Victim` (intended value; measured
     is a no-op — see numbers)
  2. `MESSAGE(text="§c§l* DOMINATED [§c-{level·5}% DMG for {level·2}s§c§l] *")
     @Victim`
  3. `PARTICLE(particle=ENCHANTMENT_TABLE, count=32, spread=0.8) @Victim`
- **interactions:** WEAKEN is non-stacking; a live stronger application is not
  downgraded and a refresh is silent (jar messaged only on first application —
  matches WEAKEN's non-stacking refresh; message row is authored on the same
  ability, first-application gating is the effect's own semantics, noted).
- **strings:** `§c§l* DOMINATED [§c-{level·5}% DMG for {level·2}s§c§l] *`
  (rendered per level as literal `-5% DMG for 2s` … `-20% DMG for 8s`).
- **numbers:** chance 4/8/~12/16%; duration 40/80/120/160 t. Measured debuff:
  `level·5/100` **integer division** → ×1.0 always, Dominate never reduces any
  damage (message + particles only); as-intended: −5·L% outgoing for 2·L s →
  ledger `D3-04`. Jar cleans metadata lazily on the victim's next attack;
  WEAKEN expires on schedule (intended scoping). Acquisition: max 4,
  base 15.0, interval 15.0, weight 2, tier 4.
- **era:** `ENCHANTMENT_TABLE` particle (legacy) ↔ `ENCHANT` (1.20.5 registry
  rename) via resolver.

---

### Double Strike (`enchants/double-strike`)

- **codex:** `03-enchants-swords.md § Double Strike`
- **activation:** trigger `ATTACK`; chance `2·L`%.
- **decomposition:**
  1. `ECHO_STRIKE()`
  2. `PARTICLE(particle=REDSTONE, count=20, spread=0.5) @Victim`
- **interactions:** Rage: the jar's exclusion guard reads a misspelled marker
  key that is never written, so it never fires and measured Rage
  multiplies the follow-up AND double-increments the combo; intended
  behavior — echo hits excluded from Rage — is the engine default (Rage's
  counter increments once per real swing) → ledger `D3-06`.
- **strings:** none.
- **numbers:** chance 2/4/6%. Measured mechanism: a scheduled, attacker-
  attributed re-hit for the first hit's final damage, 2 t later, re-entering
  the whole pipeline — re-procs every enchant, can recurse without a depth
  guard, feeds post-armor damage back in
  as raw, and can be swallowed by vanilla invulnerability; shipped: single
  same-event `ECHO_STRIKE` fold (one re-run, damage folds into the one event,
  no recursion, no invuln interaction) → ledger `D3-05`. The liveness /
  Phoenix-revival edge cases of the 2 t delay disappear with the fold.
  Acquisition: max 3, base 10.0, interval 10.0, weight 2, tier 5.
- **era:** `RED_DUST` (legacy) ↔ `REDSTONE`/`DUST` particle via resolver.

---

### Enrage (`enchants/enrage`)

- **codex:** `03-enchants-swords.md § Enrage`
- **activation:** trigger `ATTACK`; no roll; cumulative condition rows on
  `%actor.healthpercent%`.
- **decomposition:**
  1. `%actor.healthpercent% <= 75` → `DAMAGE_MOD(side=attack, mode=add,
     amount=15)`
  2. `%actor.healthpercent% <= 50` → `DAMAGE_MOD(side=attack, mode=add,
     amount=15)`
  3. `%actor.healthpercent% <= 25` → `DAMAGE_MOD(side=attack, mode=add,
     amount=15)`
- **interactions:** none.
- **strings:** none.
- **numbers:** ×1.0 / ×1.15 / ×1.30 / ×1.45 at >75 / ≤75 / ≤50 / ≤25% health
  (measured doubles `1.2999999999999998` / `1.4499999999999997`); thresholds
  cumulative, all three fire at exactly 25%. **Level-independent** — L1–L3
  identical (quirk, kept as measured; three levels exist only in acquisition).
  Acquisition: max 3, base 15.0, interval 6.0, weight 5, tier 4.
- **era:** none.

---

### Epicness (`enchants/epicness`)

- **codex:** `03-enchants-swords.md § Epicness`
- **activation:** trigger `ATTACK`; chance flat **50%**, level-independent.
- **decomposition:** per-level cosmetic pair (level-selected params):
  - L1: `SOUND(sound=ENTITY_CHICKEN_HURT, volume=1.0, pitch=0.85)` +
    `PARTICLE(particle=CLOUD, count=20, spread=0.25) @Victim` (eye height)
  - L2: `SOUND(sound=ENTITY_MAGMA_CUBE_SQUISH, volume=1.2, pitch=0.8)` +
    `PARTICLE(particle=SLIME, count=20, spread=1.0) @Victim`
  - L3: `SOUND(sound=ENTITY_GHAST_SCREAM, volume=1.0, pitch=1.25)` +
    `PARTICLE(particle=SPELL_WITCH, count=15, spread=0.95) @Victim`
- **interactions:** jar plays the sound only to non-truce players within 16
  blocks of the victim (and never to the victim); `SOUND` is location-scoped
  and audible to anyone in range — the ally-exclusion audio targeting is a
  purely cosmetic delta, dropped (noted; not gap-worthy).
- **strings:** none.
- **numbers:** 50% at every level; only the sound/particle pair changes.
  Acquisition: max 3, base 10.0, interval 10.0, weight 2, tier 1.
- **era:** `CHICKEN_HURT`/`MAGMACUBE_WALK2`/`GHAST_SCREAM` (legacy) ↔
  `ENTITY_CHICKEN_HURT`/`ENTITY_MAGMA_CUBE_SQUISH`/`ENTITY_GHAST_SCREAM`
  (modern); `WITCH_MAGIC` (legacy) ↔ `SPELL_WITCH`/`WITCH` particle.

---

### Execute (`enchants/execute`)

- **codex:** `03-enchants-swords.md § Execute`
- **activation:** trigger `ATTACK`; conditions `%victim.type% == PLAYER`,
  `%victim.health% <= 8.0` (pre-hit health, as measured) and
  `!%victim.var.raged%` (gap below); chance `4·L`%.
- **decomposition:**
  1. `DAMAGE_MOD(side=attack, mode=add, amount=200)` (×3.0)
  2. `PARTICLE(particle=BLOCK_CRACK, block=REDSTONE_WIRE, count=8) @Victim`
- **gaps:** `TARGET_VAR_FACT` — defined at §Inquisitive; consumer here for the
  `!%victim.var.raged%` gate. Rage writes `raged` victim-scoped
  (`SET_VAR(name=raged, …) @Victim`, §Rage) and Execute must read that other
  entity's var — a cross-entity read the surface cannot express today.
- **interactions:** mutually exclusive with Rage inside Rage's 200 ms (4 t)
  victim window — interaction-layer rule `execute-blocked-while-victim-raged`,
  riding on the `TARGET_VAR_FACT` gap above rather than on any existing
  condition fact (Rage arms the window; see §Rage). The jar's winner on a
  same-hit collision was iteration-order roulette; the rule makes the exclusion
  deterministic (Rage's mark from a PRIOR hit blocks Execute, same-hit both
  apply is impossible single-pass).
- **strings:** none.
- **numbers:** chance 4/8/~12/16/20/~24/28% (L3 `0.12000000000000001`,
  L6 `0.24000000000000002`); multiplier ×3.0 level-independent. Acquisition:
  max 7, base 15.0, interval 3.0, weight 2, tier 3.
- **era:** `REDSTONE_WIRE` as a particle block-data handle: legacy id 55;
  modern `REDSTONE_WIRE` block state — resolver-mapped.

---

### Featherweight (`enchants/featherweight`)

- **codex:** `03-enchants-swords.md § Featherweight`
- **activation:** trigger `ATTACK`; chance `25·L`%; gate: skip while the
  attacker already has Haste from any source → gap fact below.
- **decomposition:**
  1. condition `!%actor.potion.FAST_DIGGING%` (gap) →
     `POTION(effect=FAST_DIGGING, level=L, duration=20·L) @Self`
- **gaps:** `POTION_STATE_FACT — condition facts
  %actor.potion.<effect>%/%victim.potion.<effect>% (BOOL, optionally the
  active amplifier as NUM); params: potion-effect handle; consumers:
  Featherweight (skip while hasted), IceAspect (skip while victim slowed),
  corpus-wide skip-while-active gates.`
- **interactions:** any external Haste source (beacon, Haste enchant) starves
  it, as measured.
- **strings:** none.
- **numbers:** chance 25/50/75% (jar `<=`); Haste I/II/III for 20/40/60 t.
  Without the gap fact, plain `POTION` non-force semantics cover the
  no-downgrade half but would refresh an equal-level effect the jar skips.
  Acquisition: max 3, base 15.0, interval 10.0, weight 2, tier 2.
- **era:** `FAST_DIGGING` (legacy) ↔ `HASTE` (modern registry) via resolver.

---

### Greatsword (`enchants/greatsword`)

- **codex:** `03-enchants-swords.md § Greatsword`
- **activation:** trigger `ATTACK`; condition `%victim.helditem% == BOW`; no
  roll.
- **decomposition:**
  1. `DAMAGE_MOD(side=attack, mode=add, amount=5·L)`
- **interactions:** none.
- **strings:** none.
- **numbers:** ×1.05/×1.1/×1.15/×1.2/×1.25 vs a bow-holder
  (L3 measured `1.1500000000000001`), ×1.0 otherwise. Exact-match `"BOW"`
  only, as measured (crossbows/tridents excluded on modern). Acquisition:
  max 5, base 20.0, interval 5.0, weight 7, tier 3.
- **era:** none.

---

### Headless (`enchants/headless`)

- **codex:** `03-enchants-swords.md § Headless`
- **activation:** trigger `ATTACK`; condition `%victim.type% == PLAYER`;
  chance `3·L`%.
- **decomposition:** the flag + any-cause-death consumption + owned-skull item
  with templated lore has no primitive combination → gap:
  1. `HEAD_TROPHY_DROP(name="§fSkull of {VICTIM}", lore=<template below>)
     @Victim` (arms the trophy state; consumed by the victim's next death from
     any cause, adding the head to their drops)
- **gaps:** `HEAD_TROPHY_DROP — arm an on-death player-head trophy on the
  target: on their next death (any cause) a skull item owned by the target,
  with templated display/lore ({VICTIM},{KILLER},{DATE},{X},{Y},{Z},{ITEM}
  tokens resolved at death time), joins their death drops, then the state
  clears; params: name template, lore template lines, killer-less fallback
  (plain head, no lore); consumers: head-collection enchants. SET_VAR can arm
  a flag but no primitive drops a custom owned-skull item, and DEATH-side
  consumption fires on the victim, not the enchant holder.`
- **interactions:** CosmicContests console hook (`contests add {KILLER}
  headsCollected 1`, deduped per victim) is an external-plugin integration —
  out of port scope, recorded verbatim for completeness.
- **strings:** display `§fSkull of {VICTIM}`; lore (4 lines, trailing spaces
  on 1–3 verbatim):
  <!-- markdownlint-disable MD038 -- the trailing spaces inside these code spans are load-bearing verbatim lore -->

  `§7Defeated by §f{KILLER}§7 on ` /
  `§f{MONTH} {DAY}, {YEAR}§7 at ` /
  `§f{X}, {Y}, {Z}§7 with a(n) ` /
  `§f{ITEM}!`
  (`{ITEM}` = held display name, else capitalized material, `Fists` for none);
  console `HEADLESS ENCH -- Dropping skull of {VICTIM}!`.
- **numbers:** chance 3/6/9%; everything downstream level-independent.
  Measured: a static date stamp initialised at class load — every skull shows
  the server BOOT date; as-intended: the kill date → ledger `D3-07`. Jar flag
  is name-keyed, unbounded, survives relogs and is consumed by the next death
  from any cause (kept as the gap's semantics, minus the unbounded-static
  leak). Killer-less deaths drop the plain lore-less head (kept). Acquisition:
  max 3, base 30.0, interval 10.0, weight 7, tier 1.
- **era:** `SKULL_ITEM` data 3 (legacy) ↔ `PLAYER_HEAD` (modern); the gap's
  implementation must resolve the head item per era.

---

### IceAspect (`enchants/ice-aspect`)

- **codex:** `03-enchants-swords.md § IceAspect`
- **activation:** trigger `ATTACK`; conditions: `!%victim.potion.SLOW%`
  (POTION_STATE_FACT, §Featherweight) and `%victim.type% == PLAYER`; chance
  `7.5·L`%. Registered display name has **no space**: `IceAspect`.
- **decomposition:**
  1. `POTION(effect=SLOW, level=6, duration=2·L·20) @Victim` (Slowness VI)
  2. `PARTICLE(particle=BLOCK_CRACK, block=DIAMOND_BLOCK, count=8) @Victim`
- **gaps:** `POTION_STATE_FACT` — defined at §Featherweight; consumer here for
  the no-re-slow gate.
- **interactions:** Dragon Slayer set (`immune_freeze`) blocks outright — no
  leak chance — with the set-side message
  `§8§l* DRAGON SLAYER [§7Ice Aspect blocked!§8§l] *` (interaction-layer rule
  `dragon-slayer-blocks-freeze`, shared with §Trap); the jar's cancellable
  proc event maps to the engine's native activation-veto pipeline.
- **strings:** block message above (authored on the armor-set side, 10 doc).
- **numbers:** chance 7.5/15/~22.5% (L3 `0.22500000000000003`); duration
  40/80/120 t; amplifier hard-coded 5 (Slowness VI ≈ −90% speed, an effective
  stop) at every level. Acquisition: max 3, base 15.0, interval 3.0,
  weight 2, tier 4.
- **era:** `SLOW` (legacy) ↔ `SLOWNESS` (modern registry); block id 57 raw
  literal ↔ `DIAMOND_BLOCK` handle.

---

### Inquisitive (`enchants/inquisitive`)

- **codex:** `03-enchants-swords.md § Inquisitive`
- **activation:** trigger `ATTACK`; condition `%victim.type% != PLAYER` (mobs
  only); chance `7.5·L`%.
- **decomposition:**
  1. `SET_VAR(name=inquisitive, value=L, ttl=0) @Victim` (the mark; a later
     hit overwrites with that hit's level, as measured)
  2. companion rule — trigger `KILL`; condition `%victim.var.inquisitive% > 0`
     (gap) → `SET_VAR(name=inq.window, value=1, ttl=40) @Self`
  3. companion rule — trigger `EXP_GAIN`; condition `%inq.window%` →
     `EXP_MULTIPLY(factor=1 + 0.25·L)`
- **gaps:** `TARGET_VAR_FACT — read another entity's SET_VAR state in
  conditions as %victim.var.<name>% / %attacker.var.<name>%; params: var name,
  scope; consumers: Inquisitive (mark read-back at kill time), Execute (reads
  the victim's raged var), Rage (the victim-scoped writer of that var),
  cross-entity state gates across the corpus. SET_VAR is only readable
  actor-scoped (%name%) today.`
- **interactions:** none (self-contained mark → XP chain).
- **strings:** none.
- **numbers:** mark chance 7.5/15/~22.5/~30%; XP ×1.25/×1.5/×1.75/×2.0;
  measured truncates to whole XP (5 XP → 6/7/8/10). Two measured quirks the
  pickup-window decomposition narrows: the jar boosts the XP even when a
  DIFFERENT player or the environment kills the marked mob (holder-agnostic;
  lost — only the holder's own kill opens the window), and the 40 t window
  technically catches unrelated XP picked up in the same 2 s (new; orb pickup
  from the kill dominates). Recorded as decomposition deltas, not ledger rows
  (no codex-marked bug). Acquisition: max 4, base 25.0, interval 5.0,
  weight 7, tier 5.
- **era:** none.

---

### Insomnia (`enchants/insomnia`)

- **codex:** `03-enchants-swords.md § Insomnia`
- **activation:** trigger `ATTACK`; chance `2.5·L`%.
- **decomposition:**
  1. `POTION(effect=SLOW_DIGGING, level=min(L,3), duration=(L==1 ? 40 : 60))
     @Self` — the wielder debuffs THEMSELF
  2. L ≥ 6 only: `POTION(effect=CONFUSION, level=2, duration=60) @Self`
  3. `DAMAGE_MOD(side=attack, mode=add, amount=5·L)`
- **interactions:** none.
- **strings:** none.
- **numbers:** chance 2.5/5/7.5/10/12.5/15/~17.5% (L7
  `0.17500000000000002`); Fatigue I@40 t (L1), II@60 t (L2), III@60 t (L3+);
  Nausea II 60 t at L6–7; damage ×1.05…×1.35 (L3 `1.1500000000000001`, L4
  `1.2000000000000002`). Self-debuff-for-damage trade kept exactly.
  Non-force potion: an existing longer Fatigue is not refreshed (engine
  matches). Acquisition: max 7, base 15.0, interval 15.0, weight 2, tier 1.
- **era:** `SLOW_DIGGING` ↔ `MINING_FATIGUE`, `CONFUSION` ↔ `NAUSEA` (modern
  registry names) via resolver.

---

### Inversion (`enchants/inversion`)

- **codex:** `03-enchants-swords.md § Inversion`
- **activation:** trigger `DEFENSE` (held sword active while defending —
  WornState held-item source); chance `5·L`%.
- **decomposition:** (heal branch; Corrupt branch is an interaction) — the
  1/2/3 draw is an exact chance ladder with 1 t scratch vars:
  1. chance-row A (33.334%): `SET_VAR(name=inv.done, value=1, ttl=1) @Self` +
     `MODIFY_HEALTH(amount=1, mode=give) @Self`
  2. chance-row B (50%, condition `!%inv.done%`): `SET_VAR(inv.done,1,ttl=1)`
     plus `MODIFY_HEALTH(amount=2, mode=give) @Self`
  3. row C (condition `!%inv.done%`): `MODIFY_HEALTH(amount=3, mode=give)
     @Self`
  4. `CANCEL()` (zeroes and cancels the incoming hit)
  5. `PARTICLE(particle=SPELL, count=20, spread=0.45) @Self`
  6. `SOUND(sound=BLOCK_PISTON_EXTEND, volume=0.8, pitch=2.0)`
- **gaps:** `HURT_TRIGGER — targetless defensive trigger firing on ANY damage
  cause with the %damagecause% fact bound; params: none (cause filtering via
  condition); consumers: Inversion's all-cause inversion (fall, fire,
  drowning, poison ticks all invert in the jar). Today DEFENSE needs an
  attacking target and only FALL/FIRE exist as targetless defensive triggers;
  %damagecause% exists as a fact, suggesting the trigger is the only missing
  piece.` (`RANDOM_PARAM` — §Poison — would collapse rows 1–3 to one.)
- **interactions:** Corrupt (armor/other doc): while the defender is corrupted,
  `corrupt·20`% of Inversion procs invert against them — the heal branch
  is replaced by `DAMAGE(amount=<same 1–3 draw>) @Self` + message, and the
  incoming damage lands uncancelled. Interaction-layer rule
  `corrupt-inverts-inversion` (chance 20/40/~60/~80% by Corrupt level;
  float-measured `0.6000000238418579`/`0.800000011920929`).
- **strings:** corrupted branch: `§5* CORRUPTED [§c{AMOUNT}§5 DMG] *`
  (jar renders `{AMOUNT}` as a double: `1.0`/`2.0`/`3.0`).
- **numbers:** chance 5/10/15/20% (float-cast, exact at these values); heal or
  self-damage uniformly 1.0/2.0/3.0 (⅓ each), level-independent. The jar's
  guard on the 1–3 draw is dead code (always true) — not ported. Defensive
  double-fire member → single-pass per D-001. Jar CCEs on non-player
  defenders (crash bug; engine is player-scoped). Heal clamped at max health,
  refused when dead (MODIFY_HEALTH matches). Acquisition: max 4, base 17.0,
  interval 4.0, weight 2, tier 5.
- **era:** `PISTON_EXTEND` (legacy) ↔ `BLOCK_PISTON_EXTEND` (modern); SPELL
  particle exists on 1.8.9.

---

### Kill Aura (`enchants/kill-aura`)

- **codex:** `03-enchants-swords.md § Kill Aura`
- **activation:** trigger `ATTACK`; condition: victim carries the CosmicMasks
  monster-summon state (interaction-layer fact from the masks family); chance
  `25`% at L5 handled by the ladder below, else `5·L`%.
- **decomposition:** (L1–4: single row, chance `5·L`% → step 3 only with
  value 2. L5 exact ladder for the 5% upgrade:)
  1. row A — chance 25%: `SET_VAR(name=ka.proc, value=1, ttl=1) @Self`
  2. row B — condition `%ka.proc%`, chance 5%:
     `SET_VAR(name=killaura, value=3, ttl=0) @Victim` +
     `SET_VAR(name=ka.up, value=1, ttl=1) @Self`
  3. row C — condition `%ka.proc% && !%ka.up%`:
     `SET_VAR(name=killaura, value=2, ttl=0) @Victim`
  4. `PARTICLE(particle=EXPLOSION_LARGE, count=3, spread=0.55) @Victim`
     (condition `%ka.proc%`)
- **interactions:** gate on the masks family's summon state (11 doc);
  the `killaura` value's consumer is **UNRESOLVED in the jar corpus** (nothing
  in the source tree reads it) — the var is written for parity and the
  consumer left to the masks/pets port to claim or retire.
- **strings:** none.
- **numbers:** chance 5/10/~15/20/25% (L3 `0.15000000000000002`); L5 hit
  distribution exactly 75% nothing / 23.75% value 2 / 1.25% value 3
  (ladder reproduces: 0.25×0.05 and 0.25×0.95). Acquisition: max 5,
  base 15.0, interval 6.0, weight 2, tier 5.
- **era:** `LARGE_EXPLODE` (legacy) ↔ `EXPLOSION_LARGE`/`EXPLOSION_EMITTER`
  via resolver.

---

### Lifesteal (`enchants/lifesteal`)

- **codex:** `03-enchants-swords.md § Lifesteal`
- **activation:** trigger `ATTACK`; chance `min(30, 10·L)`%; blocked outright
  when the victim is a player wearing the NECROMANCER mask (interaction).
- **decomposition:**
  1. `MODIFY_HEALTH(amount=L, mode=give) @Self`
- **interactions:** NECROMANCER mask negates entirely (interaction-layer rule
  `necromancer-blocks-lifesteal`, shared with §Vampire; masks family, 11 doc).
- **strings:** none.
- **numbers:** chance 10/20/~30/30/30% (L3 measured `0.30000000000000004`, one
  ULP above L4–5's exact 0.3 — the ladder's authored values keep 30/30/30).
  Measured heal: health SET to `⌊health⌋ + L` — truncation eats up to
  0.999 HP of the nominal heal; as-intended: flat `+L` HP heal, clamped →
  ledger `D3-08`. Heal is damage-independent (kept). Acquisition: max 5,
  base 10.0, interval 8.0, weight 2, tier 5.
- **era:** none.

---

### Obliterate (`enchants/obliterate`)

- **codex:** `03-enchants-swords.md § Obliterate`
- **activation:** trigger `ATTACK`; item set **weapons** (swords + axes +
  bow); chance `10·L`%. Jar-side world gates (victim not in The End, not
  duel-flagged, KOTH suppresses only the knockback after the particle) are
  server-policy conditions for the interaction/policy layer, not enchant
  behavior.
- **decomposition:**
  1. `PARTICLE(particle=EXPLOSION_LARGE, count=3, spread=1.0) @Victim`
  2. `VELOCITY(mode=away, strength=1.8 + 0.5·L) @Victim`
- **interactions:** policy layer: End-world disable (global tier rule), duel
  flag, KOTH world knockback suppression (particle still plays — ordering kept
  if the policy is authored between rows 1 and 2).
- **strings:** none.
- **numbers:** chance 10/20/~30/40/50% (L3 `0.30000000000000004`); push speed
  2.3/2.8/3.3/3.8/4.3 along the normalized attacker→victim vector (VELOCITY
  mode=away matches, including the zero-length skip). Acquisition: max 5,
  base 15.0, interval 6.0, weight 2, tier 1.
- **era:** particle rename as §Kill Aura.

---

### Paralyze (`enchants/paralyze`)

- **codex:** `03-enchants-swords.md § Paralyze`
- **activation:** trigger `ATTACK`; chance `L==3 ? 5 : 1.75·L`%.
- **decomposition:**
  1. `LIGHTNING(damage=0) @Victim` (cosmetic strike, no fire/damage)
  2. `POTION(effect=SLOW, level=(L>2 ? 2 : 1), duration=100) @Victim`
  3. L4 only: `POTION(effect=SLOW_DIGGING, level=2, duration=100) @Victim`
  4. `DAMAGE(amount=1+L) @Victim` (separate flat hit, as in the jar — not
     folded into the melee event)
- **interactions:** none.
- **strings:** none.
- **numbers:** chance 1.75/3.5/5 (special-cased DOWN from 5.25)/7%; Slowness
  I/I/II/II 100 t; Fatigue II 100 t at L4; extra damage 2/3/4/5. Measured:
  the bolt strikes at the **attacker's** location (verified bytecode);
  as-intended: at the victim → ledger `D3-09`. No cooldown — each proc stacks
  another 5 s slow + flat hit (kept). `interval = 0.0`: the vanilla table
  always rolled max level — acquisition-model note (max 4, base 25.0,
  interval 0.0, weight 2, tier 3).
- **era:** the jar's effect-only bolt ↔ `LIGHTNING(damage=0)`; potion renames as
  §Insomnia/§IceAspect.

---

### Poison (`enchants/poison`)

- **codex:** `03-enchants-swords.md § Poison`
- **activation:** trigger `ATTACK`; chance `10·L`%.
- **decomposition:**
  1. `POTION(effect=POISON, level=L, duration=<uniform 0 .. 20·L−1>) @Victim`
     — the uniform per-proc duration draw needs the gap below.
- **gaps:** `RANDOM_PARAM — bind a uniform random draw (min..max, fresh per
  activation) to a numeric effect param; params: min, max, integer|double;
  consumers: Poison (duration 0..20·L−1), Inversion (the 1–3 draw, currently
  a chance ladder), corpus random-magnitude effects.`
- **interactions:** none.
- **strings:** none.
- **numbers:** chance 10/20/~30% (L3 `0.30000000000000004`); duration uniform
  0–19/0–39/0–59 t (truncation caps at `20·L−1`, mean ≈ 10·L t); amplifier
  `L−1` (Poison I–III). Sub-tick-interval procs that do nothing are part of
  the measured envelope and kept. Acquisition: max 3, base 15.0,
  interval 6.0, weight 5, tier 3.
- **era:** POISON exists on 1.8.9; no hazard.

---

### Rage (`enchants/rage`)

- **codex:** `03-enchants-swords.md § Rage`
- **activation:** trigger `ATTACK` (melee only — projectile damagers excluded
  by trigger choice, matching the jar); item set **weapons** but gated
  `%actor.helditem% contains _SWORD` (axes and bows never proc, as measured);
  condition `%damage% > 0`; no chance roll.
- **decomposition:** (order matters — multiplier reads the PRE-increment
  count, so the first hit of a chain is always ×1.0)
  1. `VAR_SCALED_DAMAGE(var=counter.rage.<bucket>, side=attack, mode=add,
     per=5·L, cap=150)` (gap §Deep Wounds) where `<bucket>` = `pvp` when
     `%victim.type% == PLAYER`, else `pve` — two condition-split rows
  2. on a fold contribution > 0 (i.e. count ≥ 1) and victim not Devour-marked
     (interaction): `SET_VAR(name=raged, value=1, ttl=4) @Victim` (the 200 ms
     Execute-exclusion window) + `PARTICLE(particle=BLOCK_CRACK,
     block=REDSTONE_BLOCK, count=8) @Victim` (eye height)
  3. `COUNTER_VAR increment` on the bucket matching the victim type (gap)
- **gaps:** `COUNTER_VAR — authorable named per-player counter:
  increment/decrement-by-delta effect + %counter.<name>% condition fact;
  params: name, delta, floor(0), cap, reset-on(damage-taken|death|
  held-change), ttl; consumers: Rage's independent pvp/pve hit-combo buckets
  (both reset when the HOLDER takes any damage — reset-on=damage-taken,
  exactly the jar's reset-on-any-damage rule), Bleed stacks (04 doc), masks monster
  counters (11 doc). The engine's native %combo%/%ragestacks% facts are
  single-bucket and not authorable per-enchant.` Plus `VAR_SCALED_DAMAGE`
  (defined §Deep Wounds).
- **interactions:** Devour (axes) suppresses the damage multiplier and the
  victim mark but NOT the counter increment (200 ms window) — layer rule
  `devour-suppresses-rage-multiplier`. Pacify (bow) → `SUPPRESS(scope=ENCHANT,
  key=rage, duration=15·L) @Victim`-equivalent authored on Pacify (05 doc;
  jar window `750·L` ms = 15·L t). Execute refuses to fire inside the `raged`
  window (§Execute). Double Strike echo exclusion → ledger `D3-06` (guard typo
  made the jar multiply echo hits and double-increment; intended exclusion is
  the engine default).
- **strings:** none.
- **numbers:** per-stack gain `0.05·L` (L3 `0.15000000000000002`, L6
  `0.30000000000000004`); total multiplier `1 + count·0.05·L` capped ×2.5
  (cap reached at combo 30/15/10/8/6/5 for L1–6); combo counted before this
  hit's increment. Counters reset when the holder takes ANY damage (no decay
  timer — kept). The jar's combo-decrement API has no callers — not ported.
  Acquisition: max 6, base 10.0, interval 8.0, weight 2, tier 5.
- **era:** block id 152 ↔ `REDSTONE_BLOCK`; block-crack particle fine on
  1.8.9.

---

### Sabotage (`enchants/sabotage`)

- **codex:** `03-enchants-swords.md § Sabotage`
- **activation:** trigger `ATTACK`; gates identical to §Divine Immolation
  (held sword contains `_SWORD`, `!%heldswitch%` 5 t window, souls armed +
  balance > 0, Soul Trap expired — souls interaction layer); **no chance
  roll** — every gated hit marks.
- **decomposition:**
  1. `SET_VAR(name=sabotage, value=L, ttl=20) @Victim` (the 1000 ms window)
  2. `REMOVE_SOULS(amount=8) @Self` — 20 t cooldown bucket on this row (the
     jar's spend throttle; the mark itself is never throttled, as measured)
- **interactions:** consumer: Rocket Escape / Heroic Rocket Escape (armor
  docs) — within the 20 t window their lethal-save rolls `L·10`% to be
  blocked; on a block the wearer sees
  `§c§l ** §7Rocket Escape:§c§l SABOTAGED **` and dies. Interaction-layer
  rule `sabotage-blocks-rocket-escape` (Rocket Escape side reads its wearer's
  own `%sabotage%` var — actor-scoped, no gap needed).
- **strings:** none of its own; consumer string above (armor-doc authored).
- **numbers:** block chance 10/20/~30/40/50% by mark level (L3
  `0.30000000000000004`); souls 8 per spend, level-independent (vs Divine
  Immolation's 20); mark on every gated hit, spend at most 1/s (kept as
  measured — the free-mark cadence is the mechanism, unlike D3-13's free
  damage). Acquisition: max 5, base 10.0, interval 10.0, weight 2, tier 6
  (soul tier).
- **era:** none.

---

### Shackle (`enchants/shackle`) — registered copy

- **codex:** `03-enchants-swords.md § Shackle (weapons variant, registered)`
- **activation:** trigger `ATTACK`; item set weapons; conditions:
  `%victim.fromspawner%` (gap) and per-level mob gates —
  L1: `%victim.mobtype% != BLAZE && %victim.mobtype% != MAGMA_CUBE`;
  L2: `%victim.mobtype% != MAGMA_CUBE`; L3: none. No chance roll.
- **decomposition:**
  1. `KNOCKBACK_CONTROL(multiplier=0, duration=2) @Victim` (cancels the hit's
     knockback — the jar zeroed velocity 1 t later; the engine primitive
     cancels the same knockback without the teleport-fighting race)
- **gaps:** `SPAWN_ORIGIN_FACT — %victim.fromspawner% BOOL condition fact
  (entity spawn provenance: spawner vs natural); params: none; consumers:
  spawner-mob-gated effects (Shackle; grinder-oriented enchants in other
  docs).`
- **interactions:** none.
- **strings:** none.
- **numbers:** no scaling beyond the two mob gates. Measured: the jar reads a
  Bukkit metadata key nothing ever writes (the real flag is an NMS field) —
  Shackle is **likely inert in the shipped jar**; as-intended: spawner-gated
  anti-knockback as specified → ledger `D3-12`. Acquisition: max 3,
  base 15.0, interval 6.0, weight 2, tier 3.
- **era:** none (BLAZE/MAGMA_CUBE exist on 1.8.9).

---

### Shackle (dead copy — not ported)

- **codex:** `03-enchants-swords.md § Shackle (swords variant, unregistered)`
- **activation:** none — this copy never registers (the jar instantiates only
  the registered copy; name-keyed registration would have collided).
- **decomposition:** none. Dead code; byte-for-byte identical to the registered
  copy apart from where it lives in the jar. No pack entry is produced for it.
- **interactions:** none.
- **strings:** none.
- **numbers:** identical constants to the registered copy; nothing to ship.
- **era:** n/a.

---

### Silence (`enchants/silence`)

- **codex:** `03-enchants-swords.md § Silence`
- **activation:** trigger `ATTACK` (+ `BOW` — swords_and_bow set); condition
  `%victim.type% == PLAYER`; base chance `2·L`% boosted by the co-held
  Solitude level via condition clauses
  `%solitude% == 1 : +2 %chance%` / `== 2 : +4 %chance%` / `== 3 : +6
  %chance%` (`%solitude%` maintained by §Solitude).
- **decomposition:** duration rows laddered on `%solitude%` (S = 0..3):
  1. condition `%solitude% == S` → `SUPPRESS(scope=TYPE, key=DEFENSE,
     duration=(L+S)·20, mode=timed) @Victim`
  2. `SOUND(sound=ENTITY_WITHER_HURT, volume=1.0, pitch=0.25)`
  3. `PARTICLE(particle=ENCHANTMENT_TABLE, count=80, spread=0.4) @Victim` +
     `PARTICLE(particle=PORTAL, count=60, spread=0.4) @Victim`
  4. `MESSAGE(text="§5§l* SILENCED §7[{SECONDS}s] §5§l*") @Victim`
     (`{SECONDS}` = `(L+S)`, integer)
  5. expiry burst: repeat row 3 with `wait=(L+S)·20`
- **interactions:** Phoenix is the sole exempt defensive enchant — modeled as
  Phoenix (armor doc) carrying `SUPPRESS_IMMUNE(chance=100)` scoped to itself
  (layer rule `phoenix-immune-to-defense-suppression`). Dragon Slayer set
  `immune_silence` → `SUPPRESS_IMMUNE(chance=75)` on the set (the measured
  25% leak is exactly the primitive's per-suppression roll); on a block the
  ATTACKER sees `§c§l* SILENCE BLOCKED [§7{VICTIM}§c§l] *`. Mastery Tombstone
  (07 doc) shares the suppression channel. Solitude coupling per §Solitude.
- **strings:** `§5§l* SILENCED §7[{SECONDS}s] §5§l*` (victim);
  `§c§l* SILENCE BLOCKED [§7{VICTIM}§c§l] *` (attacker, immunity block).
- **numbers:** chance `(L+S)·2`% (2–14%), duration `(L+S)·20` t (20–140 t)
  per the codex matrices. Single-pass model (D-001): the jar's pass-2 50%
  environmental-damage leak through a Silence is a double-fire artifact —
  suppression is unconditional for the window. Measured: the jar also gives
  the silenced victim ×0.75 INCOMING damage (a 25% reward for being
  silenced — likely an inverted sign); shipped: suppression only, no damage
  modifier → ledger `D3-10`. Jar's overlapping-proc bug (first expiry task
  ends a refreshed silence early) is replaced by SUPPRESS's timed-window
  refresh. Acquisition: max 4, base 20.0, interval 5.0, weight 2, tier 5.
- **era:** `WITHER_HURT` ↔ `ENTITY_WITHER_HURT`; `ENCHANTMENT_TABLE` particle
  rename as §Dominate; PORTAL fine on 1.8.9.

---

### Skill Swipe (`enchants/skill-swipe`)

- **codex:** `03-enchants-swords.md § Skill Swipe`
- **activation:** trigger `ATTACK`; condition `%victim.type% == PLAYER`;
  chance `20·L`% (L5 = 100%, always).
- **decomposition:**
  1. `MODIFY_EXP(amount=6·L, mode=transfer) @Victim` (moves at most the
     victim's held XP to the attacker — the primitive's clamp IS the jar's
     `j - i < 0` clamp)
  2. `SPAWN_ENTITY(type=EXPERIENCE_ORB, count=1) @Self` (the jar's bonus orb,
     kept as measured; jar placed it at the attacker's target block ≤ 1 away —
     placement delta is cosmetic)
- **interactions:** none.
- **strings:** none.
- **numbers:** chance 20/40/~60/80/100% (L3 `0.6000000000000001`; L5's 1.0
  makes the roll always succeed — natural formula endpoint, kept, no
  ledger row); steal 6/12/18/24/30 XP points, clamped to the victim's total.
  Jar skipped the whole proc (including the orb) against a zero-XP victim;
  transfer-clamp makes the steal a no-op but the orb still spawns — recorded
  delta (no victim-XP condition fact exists; not gap-worthy). The orb makes
  the pair non-XP-conserving in the jar too (kept). Acquisition: max 5,
  base 15.0, interval 5.0, weight 2, tier 2.
- **era:** none.

---

### Solitude (`enchants/solitude`)

- **codex:** `03-enchants-swords.md § Solitude`
- **activation:** trigger `HELD` (maintained while the Silence-bearing item is
  held — the jar requires Solitude on the SAME held item as Silence).
- **decomposition:**
  1. `SET_VAR(name=solitude, value=L, ttl=0) @Self` on take-hold; cleared on
     un-hold (HELD-source lifecycle, §B) — pure data marker, zero runtime
     behavior of its own (the jar's combat hook is an empty body).
- **interactions:** consumed by §Silence's `±N %chance%` clauses and duration
  ladder. Same-item constraint: HELD-source vars drop when the item leaves
  the hand, so a Solitude-only sword in the hotbar contributes nothing —
  matching the jar. A Solitude sword with no Silence does absolutely nothing
  (kept).
- **strings:** none.
- **numbers:** +2 pp chance and +20 t duration to Silence per Solitude level
  (S = 1..3). Acquisition: max 3, base 15.0, interval 15.0, weight 2, tier 3.
- **era:** none.

---

### Thundering Blow (`enchants/thundering-blow`)

- **codex:** `03-enchants-swords.md § Thundering Blow`
- **activation:** trigger `ATTACK`; chance `L==3 ? 5 : 1.75·L`%; per-victim
  cooldown 50 t (2500 ms) — a per-target cooldown bucket, checked after the
  roll as in the jar (a proc that hits the cooldown is consumed silently).
- **decomposition:**
  1. `LIGHTNING(damage=0) @Victim` (visual + thunder only)
  2. `DAMAGE(amount=5.0) @Victim` (separate flat hit, not folded)
- **interactions:** none.
- **strings:** none.
- **numbers:** chance 1.75/3.5/5 (special-cased from 5.25)%; damage 5.0 and
  cooldown level-independent. Jar registered the cooldown metadata under a
  DIFFERENT plugin instance — an ownership leak with no behavioral
  intent; the engine cooldown bucket owns it. `interval = 0.0` acquisition
  note as §Paralyze (max 3, base 25.0, interval 0.0, weight 2, tier 1); same
  `L==3 ? 0.05` expression as Paralyze but a different resulting table.
- **era:** none.

---

### Training (`enchants/training`)

- **codex:** `03-enchants-swords.md § Training`
- **activation:** trigger `EXP_GAIN`; item set weapons (held or equipped scan
  per trigger table).
- **decomposition:**
  1. `EXP_MULTIPLY(factor=1 + 0.035·L)`
- **interactions:** the jar multiplies **mcMMO combat-skill XP**
  (mcMMO's XP-gain hook, combat skills only) and is inert without mcMMO;
  the port re-targets the engine's `EXP_GAIN` (vanilla XP) — an external-
  integration re-scope recorded here, not a ledger row (no mcMMO in the
  StarEnchants stack). No combat hook at all otherwise (jar class body is
  empty — kept: no other rules).
- **strings:** none.
- **numbers:** ×1.035 per level: 1.035/1.07/1.105/1.14 (measured
  `1.1400000000000001`)/1.175/1.21/1.245/1.28/1.315/1.35 (+3.5% … +35%).
  Acquisition: max 10, base 15.0, interval 6.0 (XP levels 15–69), weight 2,
  tier 2.
- **era:** none.

---

### Trap (`enchants/trap`)

- **codex:** `03-enchants-swords.md § Trap`
- **activation:** trigger `ATTACK`; conditions: `%victim.type% == PLAYER`;
  attacker not themself frozen — `!%trap.frozen%` (the var this enchant sets
  on its victims; the attacker reads their own copy); chance `4·L`% minus the
  Metaphysical rebate (interactions).
- **decomposition:**
  1. `MOVEMENT_SPEED(speed=-0.2, ticks=35) @Victim` (walk speed to 0 for
     35 t; the engine's timed modifier restores the PRIOR speed, fixing the
     jar's hard-coded 0.2F restore clobber)
  2. `SET_VAR(name=trap.frozen, value=1, ttl=35) @Victim`
  3. `MESSAGE(text="§c§l(!) §cYou have been trapped by {ATTACKER}!") @Victim`
  4. `MESSAGE(text="§a§l(!) §aYou are no longer trapped!") @Victim wait=35`
  5. `PARTICLE(particle=BLOCK_CRACK, block=SNOW_BLOCK, count=8) @Victim`
- **interactions:** Metaphysical / Heroic Metaphysical (armor docs) subtract
  2.5 pp per level via layer clause `-2.5·meta %chance%`, with the block
  message `§8§l** METAPHYSICAL (§8Trap blocked!§l) **` to the victim on a
  would-be proc; Dragon Slayer set (`immune_freeze`) blocks outright (no
  leak) with `§8§l* DRAGON SLAYER [§7Trap blocked!§8§l] *` — rule shared with
  §IceAspect; engine activation-veto replaces the jar's cancellable proc
  event; Bleed's walk-speed slow (axes doc) made bleeding attackers
  (`stacks ≥ 2`) fail the jar's walk-speed < 0.2 attacker gate — layer note for the
  Bleed port (`%counter.bleed% >= 2` extends the attacker gate).
- **strings:** trap/release/block messages as above, verbatim.
- **numbers:** base chance 4/8/~12% (L3 `0.12000000000000001`); freeze 35 t
  and full stop at every level. Measured: Metaphysical ≥ 2 vs Trap I gives a
  NEGATIVE chance (−1%) — can never land, yet the "blocked" message still
  shows on the would-be proc; as-intended: clamp the effective chance at a 1%
  floor (Heroic Trap parity: a 0.01 chance floor) → ledger `D3-11`. Overlapping
  traps: jar's independent release tasks freed early; the timed modifier
  refreshes instead. Jar's quit-reset and op-exempt particle echo are
  plumbing artifacts, not ported. Acquisition: max 3, base 15.0,
  interval 3.0, weight 2, tier 3.
- **era:** block id 80 ↔ `SNOW_BLOCK`; walk-speed freezes behave identically
  on 1.8.9 (sprint-jump creep noted in codex is vanilla-era physics, not
  ours to replicate).

---

### Vampire (`enchants/vampire`)

- **codex:** `03-enchants-swords.md § Vampire`
- **activation:** trigger `ATTACK`; chance `5·L`%; blocked when the victim is
  a player wearing the NECROMANCER mask (interaction, shared rule with
  §Lifesteal).
- **decomposition:**
  1. `SOUND(sound=ENTITY_GENERIC_DRINK, volume=0.75, pitch=2.0)` (immediate
     cue)
  2. `MODIFY_HEALTH(amount=L, mode=give) @Self wait=60` (the 3 s deferred
     heal; the engine's deferred-write liveness gate matches the jar's
     alive-and-valid check — a wielder dead at t+60 gets nothing)
  3. `SOUND(sound=ENTITY_GENERIC_DRINK, volume=0.75, pitch=2.0) wait=60`
     (jar suppressed this one only when the heal failed; with the liveness
     gate the mismatch window is the same death case — cosmetic)
- **interactions:** NECROMANCER mask blocks (layer rule shared with
  Lifesteal). Jar ordering trivia (roll before mask check) has no observable
  effect — not modeled.
- **strings:** none.
- **numbers:** chance 5/10/~15% (L3 `0.15000000000000002`); heal 1/2/3 HP,
  clamped at max health; delay 60 t. Acquisition: max 3, base 25.0,
  interval 0.0 (always rolls max — acquisition note), weight 2, tier 3.
- **era:** `DRINK` (legacy) ↔ `ENTITY_GENERIC_DRINK` (modern) via resolver.

---

## Gap index (this doc)

| Gap | Consumers here |
| --- | --- |
| `ARMOR_SLOT_DURABILITY` | Demonforged, Disintegrate |
| `POTION_STATE_FACT` | Featherweight, IceAspect |
| `RANDOM_PARAM` | Poison (Inversion optional) |
| `VAR_SCALED_DAMAGE` | Deep Wounds, Rage |
| `COUNTER_VAR` | Rage (pvp/pve buckets); Bleed/masks cross-doc |
| `PERIODIC_DAMAGE` | Divine Immolation |
| `HEAD_TROPHY_DROP` | Headless |
| `TARGET_VAR_FACT` | Inquisitive, Execute (reader), Rage (victim-scoped writer) |
| `SPAWN_ORIGIN_FACT` | Shackle |
| `HURT_TRIGGER` | Inversion |

## Provisional deviation rows (→ `deviations.md`)

| Prov. id | Item | Measured → intended |
| --- | --- | --- |
| D3-01 | Block | L1 ×1.0 integer-division no-op; L2=L3 → 25/50/75% reduction (`0.5·L/2` real division) |
| D3-02 | Demonforged | damages the attacker's own armor → the victim's armor |
| D3-03 | Disarmor | 25% no-op procs, boots unreachable → uniform over all 4 slots |
| D3-04 | Dominate | `level·5/100` integer division, ×1.0 always → −5·L% outgoing for 2·L s |
| D3-05 | Double Strike | re-entrant scheduled second hit (re-procs, recursion, invuln absorption) → single-event ECHO_STRIKE fold |
| D3-06 | Rage × Double Strike | misspelled-key exclusion guard never fires → echo hits excluded from Rage |
| D3-07 | Headless | static date stamp: skull lore shows server boot date → kill date |
| D3-08 | Lifesteal | integer-truncation set-heal loses up to 0.999 HP → flat +L HP heal |
| D3-09 | Paralyze | lightning at the attacker's location → at the victim |
| D3-10 | Silence | silenced victim takes ×0.75 incoming (inverted reward) → suppression only, no damage modifier |
| D3-11 | Trap | Metaphysical ≥2 vs Trap I: negative chance, never lands, message still shows → 1% floor clamp (Heroic Trap parity) |
| D3-12 | Shackle | gate reads never-written metadata; likely inert in the jar → spawner-gated anti-knockback as specified |
| D3-13 | Divine Immolation | 1 s throttle guards only the soul spend; AoE fires free every gated hit → burst and spend share the 1 s pace |
