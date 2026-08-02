# Matrix 06 — tool & heroic enchantments

Decomposition of codex doc `06-enchants-tools-heroic.md` (36 items: the shared
heroic-application contract, 10 tool enchants, 25 heroics) onto the StarEnchants
authoring surface at HEAD (`docs/reference/authoring-surface.txt`). Defensive
entries record SINGLE-PASS intended values per spec §6 and deviation `D-001`
(`00-MECHANICS.md` §3: the jar's victim defensive pass runs twice per melee hit;
we never replicate the double-fire). Chance/cooldown/condition are the ability's
own fields; `KIND(...)` sequences are the `effects:` list in order. `L` = enchant
level. Strings are verbatim jar output with placeholders as `{brace}` tokens.

---

## Entries

### Heroic application rules (`heroic/_application-contract`)

- **codex:** `06-enchants-tools-heroic.md § heroic base behaviour (the base-class
  section)`
- **activation:** none — item-application contract, not a runtime ability.
- **decomposition:** no effect sequence. Authored as enchant-definition metadata +
  interaction-layer rules: each heroic declares its non-heroic partner; the apply
  pipeline enforces the gate below. All 25 heroics render at tier 7 (`§d`,
  `LIGHT_PURPLE`), level as Roman numeral, heroic line sorts above other enchant lines.
- **interactions:**
  - APPLY-GATE: a heroic book applies only to an item already carrying its non-heroic
    partner at that partner's max level; otherwise silent no-op (book flow shows the
    prerequisite string).
  - REPLACE: applying the heroic removes the non-heroic's line — the two never both
    run on one item. (Jar mechanism is lore-line deletion; ours is item-state.)
  - REVERSE-BLOCK: a non-heroic can never be applied over its heroic version.
  - SLOT-EXEMPT: applying a heroic over its non-heroic consumes no additional
    enchant slot.
  - DOWNGRADE-GUARD: applying at a level below the currently-held level is a no-op.
  - NO-RANDOMIZE / NO-DUST / NO-COMBINE: Randomization Scrolls, Magic Dust and
    book-combining all refuse heroic books.
  - BLACK-SCROLL: heroics are excluded from black-scroll extraction; a picked heroic
    extracts as its non-heroic partner instead.
  - END-SUPPRESS: all tier > 5 enchants are inert while the holder is in The End —
    every heroic (tier 7) is suppressed there (`SUPPRESS`-scope world rule).
- **strings:**
  - `§c§l(!)§c This is a heroic enchantment book. In order to apply it to an item, the item must have the max level of the non-heroic version ({non_heroic_name}) first.`
  - `§c§l(!)§c This item already has the heroic version of {name} ({heroic_name}) on it.`
  - `§c§l(!) §cYou are not skilled enough to add another enchantment to this item.`
  - `§7Purchase a Rank (or an Enchantment Orb) at §cbuy.cosmicpvp.com§7 to increase your max. custom enchants per item!`
  - `§c§l(!) §cThat item already has {name} {level}!`
  - `§c§l(!) §cYou cannot use Randomization Scrolls on Heroic Enchantment Books.`
  - `§c§l(!) §cYou cannot apply Magic Dust to Heroic Enchantment Books.`
  - `§cYou can only apply a Black Scroll to an item that contains non heroic enchantments!`
- **numbers:** heroic lore colour `§d` for all 25; heroic book weight quirks recorded
  per entry (Reflective Block / Ghostly Ghost / Guided Rocket Escape / Bidirectional
  Teleportation table weight 0; Infinite Luck re-rolled 50% of the time in tier-7
  draws). Jar wires partners 2 ticks after construction — a boot-time artifact, not
  ported behavior.
- **era:** none (item-flow only). The buy-URL line ships verbatim per the
  strings rule; flag to the owner before release.

---

## Tool enchantments

### Auto Smelt (`tools/auto-smelt`)

- **codex:** `06-enchants-tools-heroic.md § Auto Smelt`
- **activation:** trigger `MINE`; condition `%block.type% == IRON_ORE` (rule A) /
  `%block.type% == GOLD_ORE` (rule B); chance `34×L`%; no cooldown.
- **decomposition:** per ore rule:
  1. `SMELT()` — ore drop becomes the ingot for this MINE activation
  2. `TELEPORT_DROPS()` — ingots to inventory (jar adds to inventory, ground-drop at
     the player only when full)
  3. `GIVE_ITEM(material=IRON_INGOT|GOLD_INGOT, count=L-1)` — jar output amount is
     the enchant level; omit this step at L1
  - Block XP: jar hands the block's XP drop to the player directly; engine MINE
    keeps the vanilla orb — same value, no step needed.
- **interactions:**
  - Fully disabled while the same tool carries Detonate or Atomic Detonate
    (interaction rule: `DISABLE_ENCHANT auto-smelt when detonate|atomic-detonate present`);
    inside those volumes smelting instead requires Auto Smelt AND Fuse (see Fuse).
  - Auto Sell (mastery, doc 07): a sellable smelted drop is sold for
    `price × count` instead of being given; ordering rule lives on Auto Sell.
- **strings:** none.
- **numbers:** max 3; table weight 6; item set = 5 pickaxes; base 10.0, interval
  10.0. Chance `L×0.34`: L1 34%, L2 68%, L3 1.02 → always (known bug, see
  ledger; intended 100%). Ingots per proc = L (1/2/3). No durability cost, no
  region check of its own.
- **era:** legacy material names (`GOLD_INGOT` et al.) resolve via the boot-time
  alias resolver; no 1.8.9 hazard.

### Detonate (`tools/detonate`)

- **codex:** `06-enchants-tools-heroic.md § Detonate`
- **activation:** trigger `MINE`; no chance roll (randomness is in the depth roll);
  no cooldown; origin-block deny: `%block.type%` not a comparator/sponge.
- **decomposition:** per level, ordered rules (first match wins via chance):
  1. [upper-depth rule, chance = per-level roll] `BREAK_BLOCK(drops=true,
     at=@FaceBox{w=3,h=3,depth=<hi>})` — gap selector, see gaps
  2. [fallback rule] `BREAK_BLOCK(drops=true, at=@FaceBox{w=3,h=3,depth=<lo>})`
     (levels 1–2: the fallback is *nothing* — only the origin's vanilla break)
  3. `PARTICLE(particle=EXPLOSION_LARGE, count=1)` per broken block (offset +0.5 y)
  4. `DURABILITY(amount=1, mode=damage, target=item)` once per explosion, only when
     at least one volume block broke silently and the tool is a pickaxe
  - Per-block filtering (22-material deny list incl. OBSIDIAN/BEDROCK/fluids/doors/
    hopper/anvil/comparators; pickaxe-only 15-material list; spade-only 6-material
    list; comparator/diode-above guard) → `BLOCK_MATERIAL_FILTER` gap.
  - Volume orientation is the struck block face (jar caches the last interacted
    block face; we orient from the mined face directly — ledger).
- **gaps:**
  - `FACE_ORIENTED_BOX_SELECTOR` — block-volume selector: a w×h cross-section
    marched `depth` layers into the struck face from the activation block, with
    per-axis (optionally asymmetric) extents; params `w,h,depth`
    (or `left/right/up/down,depth`), orientation = mined face. TRENCH is a single
    perpendicular layer (symmetric radius) and TUNNEL a 1×1 line — no combination
    yields a 3×3×depth slab (or Atomic's asymmetric cubes). Consumers: excavation
    volumes (this, Atomic Detonate).
  - `BLOCK_MATERIAL_FILTER` — per-block filter on block-volume selectors/effects:
    deny list, allow list, tool-class-conditional sublists (list applies only when
    `%actor.helditem%` matches a pattern), void-drops list (break listed materials
    without drops). Conditions gate whole activations, not individual blocks of a
    resolved volume, and `BREAK_BLOCK.drops` is all-or-nothing. Consumers:
    excavation deny lists, Atomic Detonate bulk-drop voiding.
- **interactions:**
  - Volume drop treatment composes with the tool's other enchants: Telepathy →
    drops to inventory; Auto Smelt AND Fuse → iron/gold ore smelted in-volume
    (amount = Auto Smelt level); Auto Sell → sellable drops sold at
    `price × amount`, paid once after the loop.
  - Faction/region: per-block build-permission check + war-zone veto → engine
    protection hooks (interaction layer, not enchant config).
  - Spawner-break bookkeeping fires per broken spawner (engine break pipeline).
  - Heroic partner: Atomic Detonate (application contract above).
- **strings:** none player-visible. Console tool-destroy line not ported.
- **numbers:** max 9; table weight 6; item set = 21 tools; base 10.0, interval
  10.0. Depth roll per level (probability → depth): L1 33%→1 else 0;
  L2 66%→1 else 0; L3 always 1; L4 33%→2 else 1; L5 66%→2 else 1;
  L6 always 2; L7 33%→3 else 2; L8 66%→3 else 2;
  L9 always 3. Layer = 9 blocks; max blocks touched 10/10/10/19/19/19/28/28/28
  (incl. origin). Durability: +1 per explosion (destroy at max-1 with
  `ITEM_BREAK` 1.0/1.0). Known bugs (ledger): origin re-processed → duplicate
  drop every swing; only `drops[0]` kept per block (rails voided); null cached
  face NPEs.
- **era:** legacy list names (`SMOOTH_BRICK`→STONE_BRICKS, `MYCEL`→MYCELIUM,
  `*_SPADE`→`*_SHOVEL`, comparator/diode splits collapse post-flattening) — alias
  sweep required; `LARGE_EXPLODE`→`EXPLOSION_LARGE` particle rename; 1.8 has no
  off-hand — face orientation identical.

### Detonate (legacy) (`tools/detonate-legacy`)

- **codex:** `06-enchants-tools-heroic.md § Detonate (legacy) — UNREGISTERED / DEAD`
- **activation:** none — this revision is never registered, never instantiated,
  never referenced; the registered Detonate above is the live implementation.
- **decomposition:** NOT PORTED. Dead code in the reference jar (per-swing
  `0.1×L` proc, 3×3×3 budgeted cube, "Anti Smelt" gate on an enchant that does not
  exist anywhere). No shipped StarEnchants config derives from it.
- **interactions:** none.
- **strings:** none.
- **numbers:** recorded for provenance only: proc `L×0.1` (10%…90%), budget
  `level` blocks in a 3×3×3 cube, 13-entry deny list. None ship.
- **era:** n/a.

### Experience (`tools/experience`)

- **codex:** `06-enchants-tools-heroic.md § Experience`
- **activation:** trigger `EXP_GAIN`; condition `%isblock% == true` (block-sourced
  XP only — jar hook is the block-break event); chance 100%; no cooldown.
- **decomposition:**
  1. `EXP_MULTIPLY(factor=1.0+0.25×L)`
- **interactions:** jar ordering quirk — the boosted block XP is what
  Telepathy/Auto Smelt then hand to the player, so those award the boosted amount;
  engine equivalence: EXP_MULTIPLY applies to the activation's XP before any
  drops-to-player effect reads it (same net result, no extra rule).
- **strings:** none.
- **numbers:** max 5; table weight 6; item set = 21 tools; base 10.0, interval
  10.0. Factor 1.25 / 1.50 / 1.75 / 2.00 / 2.25; result `(int)`-truncated
  (base 7 → 8/10/12/14/15); 0-XP blocks stay 0. No proc roll, no region check,
  fires even on breaks the player was not allowed to make (engine: normal
  cancelled-event handling applies).
- **era:** none.

### Fuse (`tools/fuse`)

- **codex:** `06-enchants-tools-heroic.md § Fuse`
- **activation:** none — the enchant body is empty; it is a pure marker.
- **decomposition:** no effect sequence. Ships as an enchant with no abilities;
  its entire meaning is the interaction rule below.
- **interactions:** in-volume smelting for Detonate and Atomic Detonate requires
  BOTH `Auto Smelt` and `Fuse` on the tool (`iron/gold ore → ingot × the tool's
  Auto Smelt level` inside the volume). Authored on the Detonate entries' smelt
  rule as `requires-enchant: [auto-smelt, fuse]`.
- **strings:** none.
- **numbers:** max 1; table weight 6; item set = 5 pickaxes (NOT the 21 tools — a
  table-rolled Detonate spade/axe can never receive Fuse; acquisition quirk
  kept); base 10.0, interval 10.0.
- **era:** none.

### Haste (`tools/haste`)

- **codex:** `06-enchants-tools-heroic.md § Haste`
- **activation:** triggers `MINE` + `INTERACT_LEFT` (jar refreshes from both the
  break and the block-damage/mining-start hooks); no condition, no chance, no
  cooldown.
- **decomposition:**
  1. `POTION(effect=FAST_DIGGING, level=L, duration=40)`
- **interactions:** jar force-applies (overwrites any stronger Haste from beacons/
  potions on every hit); engine potion tracking never downgrades a stronger
  effect — ledger.
- **strings:** none.
- **numbers:** max 3; table weight 2; item set = 21 tools; base 15.0, interval
  10.0. Haste I/II/III (amplifier `L-1`), duration constant 40t (2.0 s),
  refreshed continuously while mining → effectively permanent during digging.
- **era:** `FAST_DIGGING`→`HASTE` potion-type rename (alias resolver); jar's
  block-damage refresh path fired only while holding an iron/diamond pickaxe —
  dispatch quirk not replicated (MINE+INTERACT_LEFT refresh covers the felt
  behavior on all 21 tools).

### Obsidian Destroyer (`tools/obsidian-destroyer`)

- **codex:** `06-enchants-tools-heroic.md § Obsidian Destroyer`
- **activation:** trigger `INTERACT_LEFT`; condition `%block.type% == OBSIDIAN`
  (and not in The End — world condition `%actor.world%`, see base entry
  END-SUPPRESS which already covers tier gating; the jar also hard-checks here);
  chance `20×L`%; no cooldown.
- **decomposition:**
  1. `BREAK_BLOCK(drops=true)` — at the clicked block (`at=@Block{distance=5}`);
     drops naturally, respecting the held pickaxe
- **interactions:** Factions ownership + OBBY-destroy permission + war-zone veto →
  engine protection hooks. Single block per click, no radius.
- **strings:** none.
- **numbers:** max 5; table weight 6; item set = 5 pickaxes; base 10.0, interval
  10.0. Chance 20/40/60/80/100%. No durability cost. Jar quirks not ported:
  async Factions lookup race and the null-faction NPE path (engine protection
  checks are synchronous).
- **era:** none (obsidian/left-click identical in 1.8.9).

### Oxygenate (`tools/oxygenate`)

- **codex:** `06-enchants-tools-heroic.md § Oxygenate`
- **activation:** trigger `MINE`; no chance; no cooldown.
- **decomposition:**
  1. `AIR_TICKS_RESTORE(amount=20)` — gap; `FILL_OXYGEN` is a full refill and has
     no amount param
- **gaps:** `AIR_TICKS_RESTORE` — restore N air ticks to the target, clamped to
  max air; params `amount` (ticks), optional `skip-if-overflow` to reproduce
  measured no-op inside the last `amount` ticks. No existing primitive restores a
  partial amount (`FILL_OXYGEN` = full). Consumers: incremental breath effects.
- **interactions:** retired — excluded from all random enchant pools and mystery
  books (acquisition metadata, not runtime).
- **strings:** none.
- **numbers:** max 1; table weight 6; item set = 21 tools; base 10.0, interval
  10.0. +20 air ticks (1.0 s; max 300t). Level is never read. Measured guard
  `remaining+20 <= max` skips the final 0–19 ticks entirely → intended clamped
  top-up (ledger).
- **era:** none.

### Skilling (`tools/skilling`)

- **codex:** `06-enchants-tools-heroic.md § Skilling`
- **activation:** jar hook is a raw mcMMO skill-XP-gain listener
  (bypasses every dispatch gate — works in The End and through outpost tier
  suppression); gathering skills only (Excavation, Fishing, Herbalism, Mining,
  Woodcutting); no chance, no cooldown.
- **decomposition:**
  1. `EXTERNAL_SKILL_XP_MULTIPLIER(factor=1.0+0.04×L, categories=GATHERING)` — gap
- **gaps:** `EXTERNAL_SKILL_XP_MULTIPLIER` — multiply XP awarded by a registered
  third-party skill system (soft-depend), filtered to skill categories; params
  `factor`, `categories`; consumers: skill-XP tool enchants. `EXP_GAIN`/
  `EXP_MULTIPLY` cover only vanilla player XP; no primitive can observe another
  plugin's XP events. Scope call (drop vs soft-depend) belongs to the spec owner.
- **interactions:** none in-engine. The jar's gate-bypass (End/outposts) is an
  artifact of the raw listener; if ported, route through normal dispatch so END-
  SUPPRESS applies (flag to owner: felt change in The End).
- **strings:** none.
- **numbers:** max 10; table weight 2; item set = 21 tools; base 15.0, interval
  6.0. Multiplier `1.0+0.04×L`: +4% … +40% (L1…L10, linear).
- **era:** none beyond the third-party dependency itself.

### Telepathy (`tools/telepathy`)

- **codex:** `06-enchants-tools-heroic.md § Telepathy`
- **activation:** trigger `MINE`; condition `%block.type% != SPONGE`; chance
  `25×L`%; no cooldown.
- **decomposition:**
  1. `TELEPORT_DROPS()` — the block's drops straight to the breaker's inventory
     (jar ground-drops at the player only when full; engine effect covers)
  - Block XP handed to the player directly in the jar — vanilla orb equivalent,
    no step.
- **interactions:**
  - Stands down on IRON_ORE/GOLD_ORE when the tool also has Auto Smelt (interaction
    rule: Auto Smelt owns those two ores; condition
    `%block.type% != IRON_ORE && %block.type% != GOLD_ORE` added when auto-smelt
    present).
  - Auto Sell (doc 07) wins outright for any sellable drop (ordering rule on Auto
    Sell).
  - Inside Detonate/Atomic volumes, Telepathy's presence routes volume drops to
    inventory (recorded on those entries).
- **strings:** none.
- **numbers:** max 4; table weight 6; item set = 21 tools; base 10.0, interval
  10.0. Chance 25/50/75/100% (L4 exactly 1.00 → always; by-design cap, not
  ledgered). Measured drop handling (first drop stack only; custom Fortune floor
  table over COAL/DIAMOND/EMERALD/QUARTZ/LAPIS ore — F I: 1 roll @0.25
  (EV 1.25), F II: 3 rolls @0.25 (EV 1.75), F ≥III: 4 rolls @0.20 (EV 1.80,
  max 5); silk-touch re-derivation; double-slab → 2 slabs; dead RAILS /
  never-true drop-count
  guards) is replaced by full vanilla drops incl. vanilla Fortune/Silk Touch —
  ledger.
- **era:** `QUARTZ_ORE`→`NETHER_QUARTZ_ORE` alias; 1.8.9 has no sweeping drop
  API differences for these ores.

---

## Heroic enchantments

### Alien Implants (`heroic/alien-implants`)

- **codex:** `06-enchants-tools-heroic.md § Alien Implants`
- **activation:** trigger `REPEATING` while worn (helmet), period = per-level
  cooldown 120t (L1) / 40t (L2, L3); plus a passive hunger lock.
- **decomposition:** three abilities:
  1. `REPEATING` → `MODIFY_FOOD(amount=1, mode=give)` — engine clamps to 20 (jar
     guard `food < 20` equivalent)
  2. `REPEATING` → `INVERT_VAR(name=implant-phase)` then, ordered after it, ability
     with condition `%implant-phase% == 1` → `MODIFY_HEALTH(amount=2, mode=give)` —
     the 0↔1 toggle reproduces "heal on every second fire" exactly
  3. `PASSIVE` → `FOOD_DRAIN_CANCEL()` — gap (hunger never decreases while worn;
     eating still works)
- **gaps:** `FOOD_DRAIN_CANCEL` — cancel food-level decreases on the wearer while
  the flag is armed (equip → arm, unequip → lift); increases unaffected; params:
  none (or `chance`); consumers: sustain wearables. `MODIFY_FOOD` can only
  give/take discrete points; no primitive vetoes the drain event.
- **interactions:** counterpart `Implants` (heal +1 vs +2, no hunger lock) —
  replaced on application per the contract entry.
- **strings:** none.
- **numbers:** max 3; table weight 2; item set = 5 helmets; base 10.0, interval
  5.0. Heal +2.0 HP every second fire; food +1 every fire. Measured periods
  (integer division `40×(3/L)`): L1 120t, L2 40t, L3 40t → heal cadence
  12.0 s / 4.0 s / 4.0 s, L3 a pure no-op over L2. Intended (ledger):
  120/60/40t. Measured heal guard
  `health+2 < max` never tops off the last 2 HP → intended clamped heal
  (ledger). Unequip leaves the phase toggle stale in the jar (off-phase first
  heal after re-equip) — engine var TTL/unequip teardown fixes structurally.
- **era:** none.

### Atomic Detonate (`heroic/atomic-detonate`)

- **codex:** `06-enchants-tools-heroic.md § Atomic Detonate`
- **activation:** trigger `MINE`; no chance roll (always full size); no cooldown;
  origin deny as Detonate.
- **decomposition:** as Detonate with a deterministic asymmetric cube:
  1. `BREAK_BLOCK(drops=true, at=@FaceBox{extents=<per-level>, depth=<per-level>})`
     — `FACE_ORIENTED_BOX_SELECTOR` gap (asymmetric half-extents k,i1,l,j1)
  2. `PARTICLE(particle=EXPLOSION_LARGE, count=1)` per block (offset +0.5 y)
  3. `DURABILITY(amount=1, mode=damage, target=item)` once per explosion (same
     gating as Detonate)
  - Per-block: 21-entry deny list (Detonate's minus the duplicate BEDROCK), the
    same pickaxe/spade class lists, comparator/diode-above guard, plus a
    void-drops list of 7 bulk materials (COBBLESTONE, STONE, GRAVEL, DIRT, GRASS,
    SANDSTONE, NETHERRACK — destroyed, dropless, unless sold) →
    `BLOCK_MATERIAL_FILTER` gap (deny + void-drops params).
  - Y floor `y > 0` (engine world floor handles); the jar's `y > 200` ceiling
    clause is unreachable in practice (origin ≤7 + max 7-block span) — not ported.
- **gaps:** `FACE_ORIENTED_BOX_SELECTOR`, `BLOCK_MATERIAL_FILTER` (defined at
  Detonate).
- **interactions:**
  - Same volume-drop composition as Detonate (Telepathy / Auto Smelt+Fuse / Auto
    Sell), same Factions/war-zone protection hooks, same spawner bookkeeping.
  - Explosives Expert (mastery, doc 07) bypasses BOTH tool-class gates (metadata
    flag in the jar → interaction rule `explosives-expert present ⇒ ignore
    tool-class sublists`).
  - Non-heroic partner Detonate: separate face caches in the jar (moot for us);
    application contract applies.
- **strings:** none.
- **numbers:** max 4; table weight 6; item set = 21 tools; tier 7 `§d`; base
  10.0, interval 10.0. Volume per level (before AIR/y filters): L1 depth 4,
  extents (1,2,1,2), 4×4 layer → 64; L2 depth 5, (2,2,2,2), 5×5 → 125;
  L3 depth 6, (2,3,2,3), 6×6 → 216; L4 depth 7,
  (3,3,3,3), 7×7 → 343. Always 100%. Known bugs (ledger): Auto Sell payout drops
  the `× amount` (stacks sell for one unit); origin block duplicated exactly as
  Detonate; null cached face NPE.
- **era:** as Detonate (legacy material aliases, particle rename).

### Bidirectional Teleportation (`heroic/bidirectional-teleportation`)

- **codex:** `06-enchants-tools-heroic.md § Bidirectional Teleportation`
- **activation:** trigger `BOW` (arrow hit on a player); branch on
  `%victim.relation%` (gap var): ALLY/MEMBER → teleport branch; ENEMY/NEUTRAL →
  grapple-or-trap branch. No cooldown.
- **decomposition:**
  - Enemy/neutral, ability A (chance `6.6×L`%):
    1. `PARTICLE(particle=SPELL_WITCH, count=35)` + `PARTICLE(particle=FLAME, count=10)`
       at the victim's eye height (`@EyeHeight`)
    2. `PULL_IMPULSE(who=@Victim, max-range=10×L, cap=6.0+0.5×L)` — gap; condition
       `%distance% <= 10×L` (out-of-range sends the too-far string instead)
    3. `CANCEL()` — a successful grapple deals zero bow damage
    4. `SOUND(ZOMBIE_METAL, 10.0, 1.1)` + `SOUND(WITHER_SHOOT, 10.0, 1.5)` at both parties
    5. `MESSAGE` pair (below)
  - Enemy/neutral, ability B (fallback when A's roll fails):
    1. `FREEZE(duration=20, slow=100, dot=0)` on `@Victim` (walk-speed 0 for 1.0 s;
       arrow damage stands)
    2. `MESSAGE` (trapped string)
  - Ally, ability C (no roll):
    1. condition `%distance% <= 30` and same-world (engine cross-world guard) —
       else too-far string
    2. `CANCEL()` (and the arrow is removed)
    3. `TELEPORT(to=VICTIM)` — keeps the shooter's pitch/yaw
    4. `SOUND(ORB_PICKUP, 0.75, 0.341)` at self; `PARTICLE(SPELL_WITCH, 35)` +
       `PARTICLE(FLAME, 10)` at the ally (+0.5 y)
- **gaps:**
  - `PULL_IMPULSE` — one-shot velocity impulse on the target directed at the
    activator; magnitude `clamp(distance²/50, 1.0, cap)`, Y component scaled
    `y×(-mag/1.75)`; params `max-range`, `cap`; consumers: harpoon/pull effects.
    `VELOCITY` is a fixed vector (`away` only pushes), `GRAPPLE` moves the actor,
    `GRAVITY_WELL` is a periodic area pull — none produce a capped
    distance-scaled single impulse on the victim.
  - `RELATION_VAR` — expose the actor↔victim relation as `%victim.relation%`
    (ALLY/MEMBER/ENEMY/NEUTRAL, duel-aware); consumers: relation-branched combat
    effects. Selector filters (ALLIES/ENEMIES) exist but conditions cannot branch
    an ability on relation.
- **interactions:**
  - GLITCH mask (doc 11) on the victim blocks the entire enemy branch.
  - PvP-region gates: both ends must be PvP-enabled; The End/KOTH PvP-boundary
    veto with its own string (engine region hooks).
  - Vanish (`canSee`) veto.
  - Non-heroic Teleportation replaced per contract; the jar's self-hit handling
    exists only on the non-heroic (regression noted, nothing to port here).
- **strings:**
  - `§c§l(!) §cYour Teleportation target is too far away to pull with Bidirectional Teleportation!` (at max level)
  - `§c§l(!) §cYour Teleportation target is too far away to pull with this Bidirectional Teleportation level!` (below max)
  - `§c§l* BIDIRECTIONAL TELEPORT [towards: §7{damager}§c§l] *` (to the pulled target)
  - `§d§l* BIDIRECTIONAL TELEPORT [pulling: §7{target}§d§l] *` (to the puller)
  - `§c§l* BIDIRECTIONAL TRAPPED [by: §7{damager} ({seconds}s)]§c§l *` — renders `(1s)`
  - `§c§l(!) §cYour ally is too far away to teleport to with this level of Bidirectional Teleportation.`
  - `§c§l(!) §cYou cannot teleport from PvP-enabled to PvP-disabled with Bidirectional Teleportation in The End or KOTH.`
- **numbers:** max 5; base 20.0; interval 10.0; **table weight 0** (heroic-book
  only); item set `{BOW}`. Grapple chance `0.066×L`:
  6.6/13.2/19.8/26.4/33.0%. Grapple max range `10L` blocks (100…2500 dist²).
  Pull cap `6.0+0.5L`: 6.5…8.5. Trap freeze: measured 20t (1.0 s) at every
  level — `(int)(1.0+0.125L)` truncates to 1 —
  intended `(1+0.125L)` s = 22.5/25/27.5/30/32.5t (ledger). Ally range flat 30
  blocks at every level. Enemy branch always does something (grapple or trap).
- **era:** sounds `ZOMBIE_METAL`/`WITHER_SHOOT`/`ORB_PICKUP` are 1.8 names →
  modern `ENTITY_ZOMBIE_ATTACK_IRON_DOOR`/`ENTITY_WITHER_SHOOT`/
  `ENTITY_EXPERIENCE_ORB_PICKUP` (alias both ways for the legacy overlay);
  particle `WITCH_MAGIC`→`SPELL_WITCH`; walk-speed freeze mechanics identical on
  1.8.9.

### Deep Bleed (`heroic/deep-bleed`)

- **codex:** `06-enchants-tools-heroic.md § Deep Bleed`
- **activation:** trigger `ATTACK`; conditions: event not cancelled (engine),
  `%damage% > 0`; chance `8×L + 40`%; stack gate `%victim.bleedstack% < 20`.
- **decomposition:**
  1. `TARGET_SCOPED_VAR(name=bleedstack, op=increment, cap=20, who=@Victim)` — gap
  2. banded rules on `i = max(1, bleedstack/2)` (10 discrete bands, constants per
     band — no arithmetic params needed):
     - victim is a player → `MOVEMENT_SPEED(speed=<band>, ticks=i×20, who=@Victim)`
       — `speed` is an absolute walk-speed fraction (vanilla base 0.2), so the
       per-band constants are 0.1925 (i=1) … 0.1250 (i=10); see numbers
     - victim is not a player → `POTION(effect=SLOW, level=max(1,i/3)+1,
       duration=i×20, who=@Victim)` (Slowness II/III/IV)
  3. `PARTICLE(particle=BLOCK_CRACK, block=REDSTONE_BLOCK, count=max(1,i/2))` +
     `PARTICLE(particle=BLOCK_CRACK, block=BEDROCK, count=max(1,i/2))` at victim +1 y
  4. bands i=5 and i=10 only → `MESSAGE` (below)
- **gaps:** `TARGET_SCOPED_VAR` — SET_VAR-class variable stored ON an arbitrary
  target entity (players AND mobs), readable in conditions as `%victim.<name>%`,
  with a bounded increment mode (`op=increment`, `step`, `cap`) and an external
  clear hook; consumers: stacking debuffs (this), death-payout marks (Master
  Inquisitive). `SET_VAR` is documented per-player/self-scoped with no
  read-modify-write, and no condition scope reads a victim-side custom var.
- **interactions:**
  - Blood Lust ally leech (axes, doc 04): on each proc, allied players with
    Blood Lust `j` within a 7×7×7 box of the victim (excluding the damager) roll
    `0.2+0.05×j` to heal `max(4.0, i×0.1×j)` with `DRIP_LAVA` ×10 and
    `EAT` 0.4/0.6 — authored on Blood Lust's side against this enchant's stack var.
  - Shares the stack key with non-heroic Bleed in the jar (co-occurrence
    impossible via normal application; contract entry covers).
  - Stack clear: an external bleed-clear hook (heal/death systems) resets the
    counter and restores speed — engine: var clear + effect expiry.
- **strings:** `§c** DEEP BLEED [§4-{percent}§c% Speed] **` — fires only when
  `i ∈ {5, 10}`; renders `-18.75` and `-37.5`.
- **numbers:** max 6; table weight 2; item set = 5 axes; base 10.0, interval 8.0.
  Proc chance `0.08L+0.4`: 48/56/64/72/80/88%. Stack cap 20; `i = max(1, stack/2)`.
  Player walk-speed `0.2 − 0.0075×i`: i=1→0.1925 (−3.75%), 2→0.1850 (−7.5%),
  3→0.1775, 4→0.1700, 5→0.1625 (−18.75%), 6→0.1550, 7→0.1475, 8→0.1400,
  9→0.1325, 10→0.1250 (−37.5%). Mob SLOW duration `i×20`t, amplifier
  `max(1, i/3)` (Slowness II at i≤5, III at 6–8, IV at 9–10). Measured: player
  slow persists until externally cleared → shipped bounded at `i×20`t, refreshed
  per proc (mob parity; ledger). Blood Lust numbers above.
- **era:** `BLOCK_CRACK` particle naming varies across the range (legacy world
  effect 2001 in the jar) — resolver alias; walk-speed mechanics identical.

### Demonic Lifesteal (`heroic/demonic-lifesteal`)

- **codex:** `06-enchants-tools-heroic.md § Demonic Lifesteal`
- **activation:** trigger `ATTACK`; chance 15/20/25% (explicit ladder); no
  cooldown.
- **decomposition:**
  1. `MODIFY_HEALTH(amount=8|12|15, mode=give, who=@Self)` — clamped to max health
     by the effect
- **interactions:** NECROMANCER mask (doc 11) on the victim voids the heal (jar
  checks inside the successful roll — the proc is burned either way; same net
  effect single-pass). Counterpart Lifesteal replaced per contract.
- **strings:** none.
- **numbers:** max 3; table weight 2; item set = 5 swords; base 10.0, interval
  8.0. Chance 0.15 / 0.20 / 0.25. Heal-to bonus 8 / 12 / 15 half-hearts
  (4.0 / 6.0 / 7.5 hearts), clamped at max. Measured heal base is
  `(int)health` (fraction silently lost, up to −0.99 HP) → intended exact
  current health + bonus, clamped (ledger). Unreachable ternary tails
  (0.25 / 8) not ported.
- **era:** none.

### Divine Enlighted (`heroic/divine-enlighted`)

- **codex:** `06-enchants-tools-heroic.md § Divine Enlighted`
- **activation:** trigger `ANY_DAMAGE` (gap — jar heals on every damage cause:
  melee, fall, fire, drowning, cactus…); chance `7.5×L`%; no cooldown.
- **decomposition:**
  1. `MODIFY_HEALTH(amount=2, mode=give, who=@Self, wait=1)` — jar delays 1 tick
     so the heal lands after the damage applies; `wait:` covers
- **gaps:** `ANY_DAMAGE_TRIGGER` — a DEFENSE-direction trigger firing on every
  damage-taken event regardless of cause or attacker, exposing `%damagecause%`
  (and null-attacker context); consumers: any-source defensive procs (this,
  Guided Rocket Escape's lethal check, cross-doc death-saves). `DEFENSE` requires
  a target (entity-caused only); `FALL`/`FIRE` cover exactly two causes.
- **interactions:** defensive double-fire: jar member of the double-fire column —
  measured proc rate on melee is the two-pass compound; matrix values are
  SINGLE-PASS intended per D-001. Counterpart Enlighted (heal 1.0, level clamp 3)
  replaced per contract; the heroic drops the clamp (hand-edited lore quirk not
  applicable to engine levels).
- **strings:** none.
- **numbers:** max 3; table weight 2; item set = 20 armor; base 15.0, interval
  3.0. Chance `0.075×L`: 7.5 / 15 / 22.5% per (single-pass) damage event. Heal
  +2.0 HP (1 heart), 1-tick delay, clamped to max.
- **era:** none.

### Ethereal Dodge (`heroic/ethereal-dodge`)

- **codex:** `06-enchants-tools-heroic.md § Ethereal Dodge`
- **activation:** two branches: trigger `DEFENSE` (entity-caused damage) with
  chance `5×L`% and condition clause `%sneaking% == true : +20 %chance%`; trigger
  `FALL` unconditionally.
- **decomposition:**
  - Ability A (`DEFENSE`, chance above):
    1. `CANCEL()`
    2. `MESSAGE(text=§e*DODGE*)`
    3. `PARTICLE(particle=CLOUD, count=10)` (+1 y)
    4. `SOUND(BAT_TAKEOFF, 1.0, 0.75)`
    5. `SET_VAR(name=dodge-window, value=1, ttl=1)`
  - Ability B (`DEFENSE`, ordered after A, condition `%dodge-window% == 1`,
    chance `5+(L-1)×2`%):
    1. `POTION(effect=SPEED, level=max(2,2+L/2)+1, duration=60)` — Speed III at L1
  - Ability C (`FALL`): `CANCEL()`
  - Ability D (`FALL`, condition `%damage% > 2` — exact under the vanilla fall
    formula `damage = distance − 3`, i.e. distance > 5): `MESSAGE(text=§e*FALL DODGED*)`
- **interactions:** double-fire column member — single-pass values per D-001.
  Counterpart Dodge (armor-wide, `0.025×L` + 0.15 sneak) replaced per contract;
  the heroic is boots-only. Engine potion tracking reproduces the
  "stronger-tracked-SPEED wins" refusal.
- **strings:** `§e*DODGE*` · `§e*FALL DODGED*`
- **numbers:** measured max **1** (the jar never assigns `max`; ledger — intended
  5, matching Dodge and the level-scaled formulas). Table weight 2; item set = 5
  boots; base 20.0, interval 5.0. Dodge chance standing `0.05×L` / sneaking
  `0.05×L+0.2`: L1 5%/25%, L2 10%/30%, L3 15%/35%, L4 20%/40%, L5 25%/45%.
  Speed-proc chance `(5+(L-1)×2)`%: 5/7/9/11/13%; amplifier `max(2, 2+L/2)`
  (int): 2/3/3/4/4 → Speed III/IV/IV/V/V, 60t. Fall branch: 100% negation, every
  fall, no roll, no cooldown (measured design, kept); message only above 5
  blocks. Jar's unguarded player cast (crashes on mobs) is moot in-engine.
- **era:** `BAT_TAKEOFF`→`ENTITY_BAT_TAKEOFF`; sword-block-era `%sneaking%`
  unchanged; CLOUD particle fine on 1.8.9.

### Ghostly Ghost (`heroic/ghostly-ghost`)

- **codex:** `06-enchants-tools-heroic.md § Ghostly Ghost`
- **activation:** trigger `PASSIVE` (worn armor) — pure marker.
- **decomposition:**
  1. `SET_VAR(name=ghostly-ghost, value=L, ttl=0)` on equip; cleared on unequip
     (engine WornState teardown)
- **interactions:** consumer UNRESOLVED — no reader of the jar metadata
  (`heroicGhostEnchantment`, nor non-heroic `ghostEnchantment`) exists anywhere in
  the decompiled tree; behavior lives outside the studied plugin. Ships as an
  inert marker until the owner rules on intended behavior; revisit when/if the
  external contract is identified. Non-heroic Ghost replaced per contract; the
  keys are independent flags in the jar (no level merging).
- **strings:** none.
- **numbers:** max 3; **table weight 0** (heroic-book only); item set = 20 armor;
  base 25.0, interval 10.0. Stored value = level (1/2/3); unequip clears
  unconditionally regardless of remaining pieces (jar quirk; engine WornState
  keeps the highest remaining level).
- **era:** none.

### Godly Overload (`heroic/godly-overload`)

- **codex:** `06-enchants-tools-heroic.md § Godly Overload`
- **activation:** trigger `PASSIVE` (worn armor) — permanent while worn.
- **decomposition:**
  1. `POTION(effect=HEALTH_BOOST, level=L+3, duration=permanent-while-worn)` —
     engine passive-potion semantics (armed on equip, lifted on unequip) replace
     the jar's effectively-infinite duration
- **interactions:** equal-or-lower amplifier from another source does not stack
  (engine potion tracking = jar tracker refusal); multiple pieces with the same
  level yield ONE Health Boost. Counterpart Overload (amp `L-1`) replaced per
  contract. Jar's swap-without-unequip lingering-effect hazard is structural in
  the engine's WornState (no port).
- **strings:** none.
- **numbers:** max 3; table weight 2; item set = 20 armor; base 20.0, interval
  5.0. Amplifier `L+2` → Health Boost IV/V/VI; bonus health +16/+20/+24
  half-hearts (+8/+10/+12 hearts) — 22-heart pool at L3.
- **era:** `HEALTH_BOOST` exists on 1.8.9; max-health attribute interplay handled
  by the item-data layer.

### Guided Rocket Escape (`heroic/guided-rocket-escape`)

- **codex:** `06-enchants-tools-heroic.md § Guided Rocket Escape`
- **activation:** trigger `ANY_DAMAGE` (gap, shared with Divine Enlighted — the
  jar saves against any lethal damage); condition
  `%damage% >= %actor.health%` (lethal check — raw final damage) and world gates
  `%actor.world% != world_koth`, not The End, not dungeon parkour (region flag);
  cooldown 300t (15 s); chance 100%.
- **decomposition:**
  1. `CANCEL()`
  2. `VELOCITY(mode=add, y=4+2×L)` — in `world_duels`/`world_duels2`: `y=2+L`
     (separate condition rule)
  3. `REMOVE_POTION(effect=SLOW)` + `REMOVE_POTION(effect=SLOW_DIGGING)`
  4. `POTION(effect=REGENERATION, level=L+1, duration=20×(L+2))` — Regen II/III/IV
  5. `FLY(ticks=20×L)` — fly speed `0.2+0.03×L` needs the `FLY_SPEED_PARAM` gap
  6. `SET_VAR(name=rocket-escape, value=1, ttl=20×(L+2)+5)`; companion ability
     trigger `FALL` condition `%rocket-escape% == 1` → `CANCEL()` (the jar's
     no-fall-damage window)
  7. `MESSAGE` (blank line, body, blank line — see strings)
  8. `SOUND(EXPLODE, 1.0, 0.54)`
  9. `PARTICLE(particle=CLOUD, count=69, spread=2.0)` at launch; repeated once at
     the cleanup tick (`wait=20×(L+2)+5`)
- **gaps:** `ANY_DAMAGE_TRIGGER` (defined at Divine Enlighted);
  `FLY_SPEED_PARAM` — a `speed` param on `FLY` (fraction, vanilla default 0.1);
  consumers: boosted escape flight. `FLY` grants flight with no speed control.
- **interactions:**
  - Cooldown bucket SHARED with non-heroic Rocket Escape; asymmetric read —
    heroic honors 15 s, non-heroic 30 s (a heroic proc suppresses a non-heroic
    proc for 30 s). Interaction rule: shared bucket `rocket-escape`, per-enchant
    threshold.
  - Sabotage (doc 03): within a 1000 ms window after a sabotage tag, the escape
    is vetoed with chance `0.1×sabotageLevel`; the cooldown is burned anyway
    (measured; kept). Veto string below.
  - Double-fire column member — single-pass per D-001.
- **strings:**
  - `§c§l ** §7Guided Rocket Escape:§c§l SABOTAGED **`
  - activation (three messages): empty line, then
    `§a§l(!) §aYour Guided Rocket Escape boots have activated, flight temporarily enabled, recover while they last!`, then empty line
- **numbers:** max 3; base 25.0; interval 5.0; **table weight 0**; item set = 5
  boots. Cooldown 15 000 ms. Launch y 6/8/10 (duels 3/4/5). Regen 60/80/100t at
  amp 1/2/3 (Regen II/III/IV — amp = level, not level−1). Flight 20/40/60t at
  speed 0.23/0.26/0.29; revert restores the vanilla 0.1 (jar clobbers custom fly
  speed — engine restores prior state; entry note, no ledger). Escape-state
  window `20(L+2)+5` = 65/85/105t. Jar leaves `no_fall_damage`/`allow_flight`
  metadata forever — engine `ttl` vars fix structurally.
- **era:** `EXPLODE`→`ENTITY_GENERIC_EXPLODE`; flight-toggle behaviour identical
  on 1.8.9; Folia: velocity+teleport per scheduling rules.

### Heroic Enchant Reflect (`heroic/heroic-enchant-reflect`)

- **codex:** `06-enchants-tools-heroic.md § Heroic Enchant Reflect`
- **activation:** trigger `PASSIVE` marker + engine-level reflection on incoming
  melee/projectile hits (jar implements in the dispatcher, not the enchant).
- **decomposition:**
  1. `PROC_REFLECT(tier-max=7, level-gate=at-least-attacking-level,
     chance=<ladder>)` — gap
- **gaps:** `PROC_REFLECT` — on an incoming hit, chance to execute the attacking
  item's triggered abilities with roles swapped (attacker becomes the victim of
  his own enchant), gated by: attacking enchant tier ≤ `tier-max` AND reflect
  level ≥ attacking enchant level; the reflected enchant is then NOT applied
  normally for that hit; params `tier-max`, `chance` ladder, victim-must-be-player;
  consumers: the reflect family (normal ≤5 / heroic ≤7 / mastery ==8). `REFLECT`
  returns damage %, `ECHO_STRIKE` re-runs the actor's own attack — neither
  re-executes the attacker's procs against him.
- **interactions:** reflect-priority chain is exclusive: mastery (tier==8) else
  heroic (≤7) else normal (≤5) — a wearer with several reflect variants uses only
  the highest-priority branch even if a lower one has a higher level. Reflects
  other heroics and soul enchants (tier 6–7) that the normal reflect cannot.
  Counterpart Enchant Reflect replaced per contract.
- **strings:** none of its own (reflected enchants emit their own).
- **numbers:** max 10; table weight **10**; item set = 20 armor; base 25.0,
  interval 10.0. Chance ladder `0.02 + 0.01×(level/3)` (integer division — four
  distinct steps, measured design, kept): L1–2 2%, L3–5 3%, L6–8 4%, L9–10 5%.
  Level gate: reflect level ≥ attacking enchant's level. Victim must be a player
  or the enchant is skipped entirely (neither direction runs).
- **era:** none.

### Infinite Luck (`heroic/infinite-luck`)

- **codex:** `06-enchants-tools-heroic.md § Infinite Luck`
- **activation:** trigger `PASSIVE` (worn armor) — pure marker; zero local logic.
- **decomposition:**
  1. `SET_VAR(name=infinite-luck, value=L, ttl=0)` while worn
- **interactions:** consumer is the armor-set layer (doc 10 —
  an infinite-luck threshold check: satisfied when stored level ≥ N; each
  worn LEATHER heroic-armor piece adds +12.5 to an accumulator the codex leaves
  UNRESOLVED past that point). Final semantics deferred to matrix 10; this entry
  ships the marker only. Acquisition: tier-7 book draws re-roll Infinite Luck 50%
  of the time (half as likely as any other heroic — pool-weight metadata).
  Counterpart Lucky (self-contained death-save, `(L+1)/400`) replaced per
  contract; unequip clears the var (engine keeps highest remaining piece level,
  fixing the jar's clear-on-any-unequip quirk).
- **strings:** none.
- **numbers:** max 5; table weight 2; item set = 20 armor; base 25.0, interval
  10.0. Stored value = level; threshold semantics `stored ≥ N`. Leather-piece
  bonus +12.5 each (4 pieces = 50.0) — iron/diamond heroic pieces contribute 0.
- **era:** none.

### Lethal Sniper (`heroic/lethal-sniper`)

- **codex:** `06-enchants-tools-heroic.md § Lethal Sniper`
- **activation:** trigger `BOW`; condition `%impactheight% > 1.9` (gap var — arrow
  Y above the victim's feet at impact); chance `62.5 + 7.5×L`%; no cooldown.
- **decomposition:**
  1. `DAMAGE_MOD(side=attack, mode=add, amount=220|240|260|280|300)` — the jar's
     ×3.2/3.4/3.6/3.8/4.0 as additive percent in the damage fold
  2. `MESSAGE(text=<headshot string>, who=@Victim)` — victim only; the shooter
     gets no feedback (measured, kept)
  3. `SOUND(HURT_FLESH, 2.0, 0.3)` at the victim
  4. `PARTICLE(particle=BLOCK_CRACK, block=REDSTONE_BLOCK, count=max(1, L/2))` at
     the victim's eye height (1/1/1/2/2)
- **gaps:** `IMPACT_HEIGHT_VAR` — expose `%impactheight%` on projectile-hit
  activations: the projectile's Y minus the victim's feet Y at impact; consumers:
  headshot-style conditions. No existing var describes the projectile's impact
  geometry (`%actor.belowvictim%` is actor-vs-victim).
- **interactions:**
  - Rage (masks, doc 11): a victim inside the 200 ms rage window is immune to the
    headshot conversion (roll consumed).
  - Arrow Deflect (armor, doc 01): a Lethal Sniper arrow bypasses the victim's
    Arrow Deflect with chance `0.1×L` (10–50%) — authored on Arrow Deflect
    against this enchant (the jar's arrow metadata collapses into engine
    projectile ownership).
  - Counterpart Sniper replaced per contract.
- **strings:** `§c§l*** HEADSHOT [+{multiplier}x DMG] ***` — renders `+3.2x` …
  `+4.0x` (the `+` is the jar's misleading prefix, verbatim).
- **numbers:** max 5; table weight 2; item set `{BOW}`; base 10.0, interval 8.0.
  Chance `0.625+0.075L`: 70 / 77.5 / 85 / 92.5 / 100% (L5 always). Multiplier
  `min(4.0, 0.2L+3.0)`: 3.2 / 3.4 / 3.6 / 3.8 / 4.0 (cap binds exactly at L5).
  Height threshold 1.9 blocks. Jar tags every projectile type (snowballs, pearls)
  harmlessly — engine scopes to the bow's arrow.
- **era:** `HURT_FLESH`→`ENTITY_PLAYER_HURT`; block-crack particle alias; 1.8.9
  arrow geometry identical.

### Master Blacksmith (`heroic/master-blacksmith`)

- **codex:** `06-enchants-tools-heroic.md § Master Blacksmith`
- **activation:** trigger `ATTACK`; chance `12.5×L`%; no cooldown.
- **decomposition:** per level:
  1. `DURABILITY(amount=<2|3>, mode=restore, target=armor)` — jar restores the
     MOST-DAMAGED worn piece; if engine `target=armor` spreads differently, note
     for the effect owner (numbers below are per-piece amounts)
  2. L≥3 only: second `DURABILITY(amount=2, mode=restore, target=armor)` — the
     jar re-scans, so it may repair a different piece
  3. `DAMAGE_MOD(side=attack, mode=add, amount=-(80+5×(L-1)))` — see numbers;
     the wielder's OWN outgoing hit is multiplied by `0.25−0.05L` (measured
     design, kept: the "repair tax" nerfs the attacker's own damage)
- **interactions:** counterpart Blacksmith (flat ×0.5 damage kept, repairs
  1/1/2/2/3) replaced per contract.
- **strings:** none.
- **numbers:** max 5; table weight 2; item set = 5 axes; base 17.0, interval 6.0.
  Proc `0.125×L`: 12.5 / 25 / 37.5 / 50 / 62.5%. Repair: first call 2 (3 at L5),
  second call (L≥3) 2 → totals 2/2/4/4/5. Outgoing-damage multiplier
  `0.25−0.05L`: ×0.20 / ×0.15 / ×0.10 / ×0.05 / ×**0.00** (L5 proc = zero-damage
  hit — measured, kept; as DAMAGE_MOD attack:add: −80 / −85 / −90 / −95 / −100).
  The repair lands regardless of whether any piece needed it.
- **era:** none (durability API uniform via the item-data layer).

### Master Inquisitive (`heroic/master-inquisitive`)

- **codex:** `06-enchants-tools-heroic.md § Master Inquisitive`
- **activation:** two abilities: trigger `ATTACK` with condition
  `%victim.type% != PLAYER` (mobs only), chance `15×L`%; trigger `EXP_GAIN` on
  the marked mob's death XP.
- **decomposition:**
  1. `ATTACK` ability: `TARGET_SCOPED_VAR(name=inquisitive-mark, op=set, value=L,
     who=@Victim)` — gap (last hit's level wins, matching the jar's overwrite)
  2. `EXP_GAIN` ability, condition `%victim.inquisitive-mark% >= 1`:
     `EXP_MULTIPLY(factor=(1.0+0.25×<mark>)×2.0)` — banded per mark level
     (2.5 / 3.0 / 3.5 / 4.0), consuming the mark
- **gaps:** `TARGET_SCOPED_VAR` (defined at Deep Bleed).
- **interactions:** the jar pays out on the mob's death regardless of WHO kills
  it (global death listener); engine scoping pays the mark-owner's XP event —
  felt-equivalent in practice, flag if the owner wants cross-player payout.
  Non-heroic Inquisitive uses a different mark key: a mob marked by two different
  players' items compounds both multipliers (e.g. 1.75 × 3.50 = 6.125×) —
  measured cross-player stacking, recorded as an interaction rule (allowed;
  contract prevents same-item co-occurrence).
- **strings:** none.
- **numbers:** max 4; table weight 7; item set = 5 swords; base 25.0, interval
  5.0. Mark chance `0.075×L×2.0`: 15 / 30 / 45 / 60%. XP multiplier
  `(1.0+0.25×L)×2.0`: 2.50 / 3.00 / 3.50 / 4.00 (int-truncated on award; e.g.
  5 XP → 12/15/17/20). Mark stores the level at hit time; re-hit overwrites.
- **era:** none.

### Mighty Cactus (`heroic/mighty-cactus`)

- **codex:** `06-enchants-tools-heroic.md § Mighty Cactus`
- **activation:** two abilities: trigger `DEFENSE` condition
  `%victim.type% == PLAYER` (the counterpart entity of the defense activation is
  a player), no chance — every PvP hit; trigger `ANY_DAMAGE`/`DEFENSE` condition
  `%damagecause% == THORNS`, chance 50% (L1) / 100% (L2).
- **decomposition:**
  - Ability A (per player hit taken):
    1. `DAMAGE(amount=0.5×L, who=@Attacker)` — routed through the damage
       pipeline (attacker's own defenses may respond; jar identical)
    2. `PARTICLE(particle=BLOCK_CRACK, block=CACTUS, count=1)` at self
  - Ability B (thorns negation): `CANCEL()`
- **interactions:** attacker-gated → the jar's second defensive pass no-ops;
  measured = single-pass already (D-001 context, no value change). Reflect
  ping-pong between two Mighty Cactus wearers is bounded by the engine's damage
  floor (jar note). Counterpart Cactus (equip-map gate, no thorns branch)
  replaced per contract.
- **strings:** none.
- **numbers:** max 2 (lowest-max heroic); table weight 2; item set = 20 armor;
  base 20.0, interval 5.0. Reflect 0.5 / 1.0 damage (0.25 / 0.5 hearts), 100%
  of player hits. THORNS negate 50% (L1) / 100% (L2 — unconditional thorns
  immunity, measured design, kept).
- **era:** THORNS cause exists on 1.8.9; cactus block-crack alias.

### Mighty Cleave (`heroic/mighty-cleave`)

- **codex:** `06-enchants-tools-heroic.md § Mighty Cleave`
- **activation:** trigger `ATTACK`; condition `%damage% > 0`; cooldown 30t
  (1.5 s, per attacker); per-target splash cooldown 20t (per-target cooldown
  bucket); chance 100%.
- **decomposition:**
  1. `DAMAGE(amount=5|5|6|7|7, who=@AOE{r=3.0+0.25×L, filter=ENEMIES,
     exclude=victim})` — splash around the VICTIM, excluding the victim itself
     and the attacker (jar excludes both; AOE excludes self, `exclude=victim`
     param covers the victim), allies and spectators filtered
- **interactions:**
  - Per-victim 20t splash cooldown = engine per-target cooldown bucket (shared
    key with non-heroic Cleave in the jar — same bucket name).
  - mcMMO Skull Splitter lockout/veto is third-party and NOT ported (no mcMMO);
    the jar's cooldown-burn-on-veto quirk is therefore moot.
  - Jar splash is unattributed (no damager ⇒ victim defense enchants don't see
    it); engine `DAMAGE` attributes to the actor — interaction-visible change,
    flagged for the owner (single-pass values unaffected).
  - Counterpart Cleave (radius `0.45L`, damage 1/1/1/2/2/2/3, max 7) replaced
    per contract.
- **strings:** none.
- **numbers:** max 5; table weight 5; item set = 5 axes; base 15.0, interval
  5.0. Search half-extent `3.0+0.25L`: 3.25 / 3.50 / 3.75 / 4.00 / 4.25 (jar
  box; AOE radius equivalent noted). Splash 5 / 5 / 6 / 7 / 7 (2.5–3.5 hearts).
  Cooldowns: self 1500 ms, per-victim 1000 ms.
- **era:** none.

### Paladin Armored (`heroic/paladin-armored`)

- **codex:** `06-enchants-tools-heroic.md § Paladin Armored`
- **activation:** trigger `DEFENSE`; two abilities: bless roll (any player
  attacker) and sword-gated reduction.
- **decomposition:**
  - Ability A (chance `1×L`%, condition `%victim.type% == PLAYER`):
    1. `CURE(category=HARMFUL, count=1)` — `count` is the `CURE_COUNT_PARAM` gap
       (jar removes exactly ONE tracked debuff, the first enumerated)
    2. `MESSAGE(text=§e§l** BLESSED **)`
  - Ability B (no roll; conditions `%victim.helditem% contains _SWORD` and
    `%damage% > <threshold_L>`):
    1. `DAMAGE_MOD(side=defense, mode=add, amount=3×L)` — 3/6/9/12% reduction
    2. `PARTICLE(particle=BLOCK_CRACK, block=DIAMOND_BLOCK, count=1)` at self
- **gaps:** `CURE_COUNT_PARAM` — a `count` param on `CURE` limiting how many
  effects are stripped (default all); consumers: single-cleanse procs. `CURE`
  currently clears the whole category.
- **interactions:** attacker-gated → single-pass measured (D-001 context). The
  bless bypasses the Deep Wounds lockout (jar 1-arg overload — measured, record
  on the Deep Wounds interaction when doc 03 lands). KOTH armor set rate-limits
  the `** BLESSED **` text to once per 160t (armor-set interaction, doc 10).
  Bless fires for ANY player attacker (unarmed/axe too) — only the reduction is
  sword-gated. Counterpart Armored (reduction `0.01875×L`, no bless) replaced
  per contract.
- **strings:** `§e§l** BLESSED **` (from the shared bless routine). Console
  bless log not ported.
- **numbers:** max 4; table weight 4; item set = 20 armor; base 20.0, interval
  5.0. Reduction `0.03×L`: 3 / 6 / 9 / 12% (damage kept 0.97/0.94/0.91/0.88).
  Floor gate: post-reduction damage must exceed 1.0 → thresholds `%damage% >`
  1.03093 / 1.06383 / 1.09890 / 1.13636. Bless chance `0.01×L`: 1 / 2 / 3 / 4%.
- **era:** none.

### Planetary Deathbringer (`heroic/planetary-deathbringer`)

- **codex:** `06-enchants-tools-heroic.md § Planetary Deathbringer`
- **activation:** triggers `ATTACK` + `BOW` (melee and the wearer's projectiles);
  chance `15×L`%; no cooldown.
- **decomposition:**
  1. `DAMAGE_MOD(side=attack, mode=add, amount=100)` — the jar's flat ×2.0
- **interactions:** wearing non-heroic Deathbringer on one piece and Planetary on
  another gives two independent rolls that compound to ×4 when both land
  (measured; the application contract only blocks same-item co-occurrence) —
  interaction rule if the owner wants it capped, else record-and-allow. Jar
  double-rolls even on events later cancelled (`ignoreCancelled=false`) — engine
  fold ordering makes this moot.
- **strings:** none.
- **numbers:** max 3; table weight 9; item set = 20 armor; base 25.0, interval
  10.0. Chance `0.1×L×1.5`: 15 / 30 / 45% (missing-level fallback 15% is
  unreachable in-engine). Multiplier ×2.0 flat at every level.
- **era:** none.

### Polymorphic Metaphysical (`heroic/polymorphic-metaphysical`)

- **codex:** `06-enchants-tools-heroic.md § Polymorphic Metaphysical`
- **activation:** trigger `PASSIVE` (worn boots) — marker + one interaction
  listener.
- **decomposition:**
  1. `SET_VAR(name=metaphysical, value=L, ttl=0)` while worn (highest worn level
     wins — engine WornState resolves; jar's equip-map guard reproduced
     structurally)
- **interactions:**
  - Ice Aspect (doc 03) procs against the wearer are cancelled with chance
    `0.2×L` (20/40/60/80%); on a block the wearer sees the string below —
    authored as an interaction-layer suppression rule against `ice-aspect`
    (chance-per-proc), NOT a gap (proc-veto with chance is interaction-layer
    vocabulary per spec).
  - The stored `metaphysical` level also feeds the proc-chance reductions read
    by Trap (`−0.025×M`, no floor), Titan Trap (`−0.0125×M`, floor 0.01),
    Pummel and Snare — each recorded on the attacking enchant's entry/doc.
  - Jar hazard: non-heroic Metaphysical unconditionally overwrites the shared
    key (heroic IV + plain I worn together → downstream resistance drops to 1
    while the Ice-Aspect block stays 80%); engine intended: highest of both wins
    (structural fix, noted — no numeric deviation single-item).
  - Counterpart Metaphysical (empty defense hook, marker only) replaced per
    contract.
- **strings:** `§d§l** POLYMORPHIC METAPHYSICAL (§7Ice Aspect blocked!§d§l) **`
- **numbers:** max 4; table weight 1; item set = 5 boots; base 17.0, interval
  4.0. Ice Aspect block chance `0.2×L` (jar comparison `<=`, negligible delta):
  20 / 40 / 60 / 80%. What it blocks: Ice Aspect's `0.075×L` Slowness VI proc
  (2/4/6 s).
- **era:** none.

### Reflective Block (`heroic/reflective-block`)

- **codex:** `06-enchants-tools-heroic.md § Reflective Block`
- **activation:** trigger `DEFENSE`; outer roll gates the reduction, inner roll
  gates the reflect; `%blocking%` modulates both.
- **decomposition:**
  - Ability A (chance: blocking `16×L`% / not blocking `70×L`% — measured
    inverted ternary, see ledger; capped 100):
    1. `DAMAGE_MOD(side=defense, mode=add, amount=25×L)` — 25/50/75% reduction
    2. `SET_VAR(name=rblock-armed, value=1, ttl=1)`
  - Ability B (ordered after A; condition `%rblock-armed% == 1 &&
    %victim.type% == PLAYER`; chance `8×L`% with clause
    `%blocking% == true : +5 %chance%`):
    1. `REFLECT(percent=25×L, duration=1)` — returns that share of the
       triggering hit to the attacker (jar computes from pre-reduction final
       damage; REFLECT's hit-share semantics cover)
    2. `MESSAGE(text=<BLOCKED>, who=@Self)` + `MESSAGE(text=<REFLECT>, who=@Attacker)`
    3. `PARTICLE(particle=CRIT, count=25)` at self (+1 y);
       `PARTICLE(particle=SPELL_WITCH, count=25)` at the attacker (+1 y)
    4. `SOUND(ZOMBIE_METAL, 10.0, 2.0)`
- **interactions:** the reduction applies to PvE hits too; only reflect+feedback
  are player-gated (jar shape kept: A has no attacker condition, B does).
  Reflected damage re-enters the pipeline (a mirrored Reflective Block can
  answer once). Double-fire: reduction leg is attacker-blind → jar double-fired
  it; single-pass values per D-001. Counterpart Block (requires blocking,
  `0.1×L`, integer-division reduction 0/50/50%) replaced per contract.
- **strings:**
  - `§a§l* BLOCKED [{damage} DMG] *` (to the defender; one-decimal damage)
  - `§c§l* {defender} REFLECTIVE BLOCK [§7{percent}% DMG§c§l]` (to the attacker;
    renders 25/50/75)
- **numbers:** max 3; base 17.0; interval 4.0; **table weight 0**; item set = 5
  swords. Measured outer roll: blocking `0.16×L` (16/32/48%), NOT blocking
  `0.7×L` (70%/always/always) — inverted; intended blocking `0.7×L` capped
  100 / not-blocking `0.16×L` (ledger). Reduction `0.25×L`: 25/50/75%. Reflect
  roll `0.08×L` +0.05 while blocking: 13/21/29% (blocking), 8/16/24% (not).
  Reflected share = `0.25×L` of the hit (from pre-reduction damage). Most
  successful blocks are silent (feedback only on the inner roll — measured,
  kept).
- **era:** `%blocking%` = 1.8 sword-block vs modern shield — the var abstracts
  it (era seam already in-engine); `ZOMBIE_METAL` sound alias; volume 10.0
  audible very wide (verbatim number kept).

### Reinforced Tank (`heroic/reinforced-tank`)

- **codex:** `06-enchants-tools-heroic.md § Reinforced Tank`
- **activation:** trigger `DEFENSE`; conditions `%victim.type% == PLAYER`,
  `%victim.helditem% contains _AXE`, `%damage% > <threshold_L>`; no roll — 100%
  of qualifying hits; no cooldown.
- **decomposition:**
  1. `DAMAGE_MOD(side=defense, mode=add, amount=2.25×L)`
  2. `PARTICLE(particle=BLOCK_CRACK, block=IRON_BLOCK, count=1)` at self
- **interactions:** attacker-gated → single-pass measured (D-001 context). Hard
  counter to axes only; swords/unarmed bypass entirely (mirror of Paladin
  Armored's sword gate). Counterpart Tank (`0.01875×L`) replaced per contract.
- **strings:** none.
- **numbers:** max 4; table weight 2; item set = 20 armor; base 15.0, interval
  5.0. Reduction `0.0225×L`: 2.25 / 4.5 / 6.75 / 9% (damage kept
  0.9775/0.9550/0.9325/0.9100). Floor thresholds `%damage% >` 1.02302 /
  1.04712 / 1.07239 / 1.09890. `_AXE` substring does NOT match `_PICKAXE`
  (codex-verified) — engine condition uses the same substring.
- **era:** none.

### Shadow Assassin (`heroic/shadow-assassin`)

- **codex:** `06-enchants-tools-heroic.md § Shadow Assassin`
- **activation:** trigger `ATTACK`; same-world guard is engine-implicit; no
  chance, no cooldown — every hit.
- **decomposition:** five ordered range-banded rules (explicit ranges, first
  match):
  1. `%distance% < 0.625` → `DAMAGE_MOD(side=attack, mode=add, amount=7.5×L)`
  2. `0.625 <= %distance% < 1.25` → `DAMAGE_MOD(attack, add, 6×L)`
  3. `1.25 <= %distance% < 1.875` → `DAMAGE_MOD(attack, add, 4.5×L)`
  4. `1.875 <= %distance% < 2.5` → `DAMAGE_MOD(attack, add, 3×L)`
  5. `2.5 <= %distance% < 3.75` → `DAMAGE_MOD(attack, add, 1.5×L)`
  (≥3.75: no bonus; no penalty bands — the heroic removed all three of
  Assassin's long-range penalties)
- **interactions:** applies on the reflected-enchant path too (distance measured
  between the same two entities in reverse) — inherited from PROC_REFLECT
  semantics, no extra rule. Counterpart Assassin (bands ×1, thresholds ÷2.5,
  penalties −0.01L/−0.0225L/−0.0475L beyond 2.0/2.5/3.0) replaced per contract.
- **strings:** none.
- **numbers:** max 5; table weight 5; item set = 5 swords; base 15.0, interval
  6.0. Cumulative-band equivalents (multiplier at L): d<0.625 `1+0.075L`
  (L5 1.375); <1.25 `1+0.06L` (L5 1.300); <1.875 `1+0.045L` (L5 1.225);
  <2.5 `1+0.03L` (L5 1.150); <3.75 `1+0.015L` (L5 1.075); ≥3.75 ×1.000.
  Vanilla melee reach < 3.75 ⇒ every melee hit lands in at least the outer band.
  Foot-to-foot distance (`%distance%` matches).
- **era:** none.

### Titan Trap (`heroic/titan-trap`)

- **codex:** `06-enchants-tools-heroic.md § Titan Trap`
- **activation:** trigger `ATTACK`; conditions `%victim.type% == PLAYER` and
  victim-not-already-frozen (intended form of the jar's inverted guard — see
  ledger); chance `4×L`%; no cooldown.
- **decomposition:**
  1. `FREEZE(duration=35+10×L, slow=99.5, dot=0, who=@Victim)` — walk-speed
     0.001 + jump lock (engine freeze-lock covers the jar's Jump-128 trick);
     restores walk speed on expiry
  2. `TEMP_BLOCK(shape=POINT, material=WATER, ticks=35+10×L, dy=1,
     airOnly=false, who=@Victim)` — the head-height "water" cue, reverted on
     release (engine temp-block ledger replaces the jar's client-only
     block-change packet broadcast to a 64³ box)
  3. `SOUND(WATER, 10.0, 1.1)` at the victim
  4. `PARTICLE(particle=BLOCK_CRACK, block=SNOW_BLOCK, count=1)` at the victim
  5. `MESSAGE(text=<TITAN TRAP>, who=@Victim)`; on expiry
     `MESSAGE(text=<FREE>, who=@Victim, wait=35+10×L)`
- **interactions:**
  - Metaphysical resistance: victim's `metaphysical` level M reduces the proc
    chance by `0.0125×M`, floored at 0.01 (always ≥1% window); on a resist the
    victim sees the METAPHYSICAL string. Interaction rule against the
    metaphysical var.
  - Measured: does NOT fire the proc-veto event and IGNORES Dragon Slayer
    `immune_freeze` (both honored by non-heroic Trap) — recorded as measured
    interaction posture; owner may align via a single interaction rule.
  - Counterpart Trap (flat 35t, no jump lock, no water cue, resistance
    `−0.025×M` unfloored) replaced per contract.
- **strings:**
  - `§8§l** METAPHYSICAL (§8Trap blocked!§l) **` (resist, to the victim)
  - `§c§l** TITAN TRAP [§7{seconds}s§c§l] **` — renders 2.25 / 2.75 / 3.25
  - `§a§l** FREE [§7Titan Trap§a§l] **` (release, to the victim —
    bytecode-verified; the "attacker" reading was a decompiler artifact)
- **numbers:** max 3; table weight 2; item set = 5 swords; base 15.0, interval
  3.0. Proc `0.04×L`: 4 / 8 / 12%. Duration `35+10L`: 45 / 55 / 65t (2.25 /
  2.75 / 3.25 s). Resistance table at L3 (0.12 base): M1 10.75%, M2 9.5%,
  M3 8.25%, M4 7%. Entry-guard bug (ledger): jar reads the ATTACKER's walk
  speed — a slowed attacker can never proc; intended victim-side no-re-trap.
- **era:** Jump-amplifier-128 lock is a 1.8-only overflow trick — FREEZE
  abstracts it (legacy overlay must implement freeze's jump lock era-specifically);
  `STATIONARY_WATER`→`WATER` alias; `Sound.WATER`→`BLOCK_WATER_AMBIENT`.

### Vengeful Diminish (`heroic/vengeful-diminish`)

- **codex:** `06-enchants-tools-heroic.md § Vengeful Diminish`
- **activation:** trigger `DEFENSE`; arming roll chance `5×L`%; no cooldown.
- **decomposition:**
  1. `DAMAGE_MOD(side=defense, mode=add, amount=50)` — the arming hit is halved
  2. `DAMAGE_CAP(factor=0.5, reflect=true, duration=100)` — caps subsequent
     incoming hits at half the (already-halved) arming hit ≈ ¼ of the original,
     reflecting overflow to the attacker; the engine primitive was built for
     this family
  3. `MESSAGE(text=<DIMINISH>, who=@Self)`
- **interactions:** jar shares the armed-cap key with non-heroic Diminish (a cap
  armed by one is honored/reflected by the other) — same-player co-occurrence
  requires two armor pieces; contract blocks same-item only; engine state is
  per-enchant (structural change, recorded). Reflect recursion can re-enter the
  attacker's own gear (engine pipeline bounds it).
- **strings:** `§e§l* DIMINISH [§eMAX DMG: {cap}§l] *` — cap rendered with two
  decimals. Console spam line not ported.
- **numbers:** max 6; table weight 2; item set = 5 chestplates; base 15.0,
  interval 3.0. Arm chance `0.05×L`: 5 / 10 / 15 / 20 / 25 / 30%. Arming hit
  ×0.5; stored cap = post-halving final ÷ 2 (≈ ¼ of original final — the
  double-halving is measured and kept; the message shows the stored cap).
  Measured armed-state semantics (reflects the cap VALUE to any player attacker
  on EVERY hit, clears only when a hit's final damage exceeds the cap — chip
  damage reflects forever) → shipped as DAMAGE_CAP semantics: duration-bounded
  (100t), overflow-above-cap reflect (ledger). No arming `finalDamage > 0`
  gate in the heroic (measured, kept).
- **era:** none.
