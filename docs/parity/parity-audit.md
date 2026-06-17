# EE + EA + AE → StarEnchants Feature-Parity Report

**Totals: 31 present · 65 partial · 219 missing — 315 features audited. ~9.8% fully present; ~30.5% present-or-partial.** A large share of the "missing" count is deliberate scope exclusion (web panel, GKits, StatTrak, loot/mob-drop tables, crates, custom crafting, the merchant GUIs) and intentionally-not-reproduced legacy bugs (multiplicative damage stacking, last-of-type crystal collapse, silent unknown-effect no-ops, fail-open conditions). The core engine, item-data layer, damage fold, soul/crystal/set/omni/heroic runtime, and unified `/se` surface are present or partial by design.

## Gaps to close

### Enchant engine — effects DSL, triggers, conditions, pipeline

- **TaskEffect deferred runnable** (EE) — missing: WAIT is realized as compile-time delay tiers + Sink DispatchPlan; the BukkitRunnable-holding mechanism is deliberately rejected (ADR 0011). WAIT behavior itself is present.
- **Per-effect type-prefix routing (TYPE;EFFECT)** (EE) — missing: no semicolon per-effect routing; trigger routing is per-ability (`trigger:`/`triggers:`).
- **Per-effect inline chance (TYPE;CHANCE;EFFECT)** (EE) — missing: single per-ability chance gate only; inline `<chance>` listed TODO in ADR 0020.
- **playEffect dispatch (silent unknown-name no-op)** (EE) — partial: unknown head is a compile-time diagnostic, never a silent runtime no-op (ADR 0004 inverts the wart).
- **stopEnchantment deactivation path** (EE) — missing: `EffectKind` has only `run()`; no stop()/deactivation, no HELD/PASSIVE unequip stop.
- **CustomEffect contract (run/stop lifecycle)** (EE) — partial: `EffectKind` SPI maps the activation half; deactivation half gone by design.
- **Reflection-loaded fixed effect registry (~66 effects)** (EE) — partial: hand-written greppable registry, fewer kinds than EE's ~66.
- **Effect DSL grammar (colon/semicolon splitting)** (EE) — partial: colon-split only, compile-time validated; semicolon routing gone.
- **PLAYER vs TARGET token resolution** (EE) — partial: replaced by `who:` named selectors (@Self/@Victim/@Attacker).
- **EffectType trigger taxonomy (20 values)** (EE) — partial: 19 declared in `BuiltinTriggers`, no INVALID sentinel; many declared-but-unwired.
- **getPvPEffects PvP-trigger set** (EE) — partial: modeled as per-trigger combat `Direction`; drives damage-fold side, not a PvP gate.
- **BOW trigger (arrow EDBE)** (EE) — partial: projectile attributed to shooter but dispatched under ATTACK id, no distinct BOW listener.
- **TRIDENT trigger (1.13.1+ gated)** (EE) — partial: declared; thrown trident runs ATTACK abilities; no distinct TRIDENT id, no 1.13.1 gate.
- **BOW_FIRE trigger (EntityShootBowEvent)** (EE) — partial: declared but no listener; never fires.
- **KILL vs DEATH triggers** (EE) — partial: KILL wired; DEATH declared but unwired.
- **FISHING trigger (PlayerFishEvent)** (EE) — partial: declared, no listener; never fires.
- **EAT trigger (PlayerItemConsumeEvent)** (EE) — partial: declared, no listener; never fires.
- **ITEM_DAMAGE trigger (PlayerItemDamageEvent)** (EE) — partial: declared, no listener; never fires.
- **BREAK trigger (PlayerItemBreakEvent)** (EE) — partial: declared, no listener; never fires.
- **HELD trigger + slot-tracking (start/stop)** (EE) — partial: no HELD fire/stop; `PlayerItemHeldEvent` only refreshes WornState.
- **PASSIVE trigger (armor equip/unequip/join)** (EE) — partial: equip refreshes WornState (passive stats apply); no PASSIVE fire or stop.
- **Item-known vs entity-scan resolution** (EE) — partial: two-way distinction modeled; item-known path effectively unexercised (held triggers unwired).
- **applies/target NOT enforced at fire time** (EE) — missing: an EE bug, deliberately fixed via WornState.byTrigger pre-filtering.
- **Pipeline gate: WorldGuard canRunEnchant** (EE) — partial: generic ProtectionProvider, BUILD flag only; no per-category gating, no blacklisted regions (ADR 0017).
- **Pipeline gate: three cooldown keys** (EE) — partial: all three scopes checked/armed, but TYPE scope unauthorable (ADR 0016); scopes armed automatically not via DISABLE_*.
- **settings.math-random chance mode** (EE) — missing: only percentage roll [0,100); buggy alternate mode not carried.
- **Pipeline gate: cancellable EnchantActivationEvent** (EE) — partial: PreActivate guard exists but wired to ALLOW; public event is a notification, not a veto.
- **EnchantDeactivationEvent** (EE) — missing: no deactivation event, no stop() lifecycle.
- **PvP/anti-grief gating in listeners (Factions/Skyblock/WG)** (EE) — missing: no Factions/Skyblock; only gate-2 WG BUILD flag.
- **WorldGuard blacklisted-regions + startup flag commands** (EE) — missing: BUILD flag only; out of scope (ADR 0017).
- **Condition base class + healthCheck operators** (EE) — partial: replaced by expression engine; all six comparators (incl. `!=`).
- **parseConditions registry + fail-open semantics** (EE) — partial: empty passes; fail mode is the OPPOSITE (fail-closed via NaN), compile-time validation.
- **Condition isBlock** (EE) — missing: no block-type fact for MINE.
- **Condition isProtected** (EE) — missing: no white-scroll/protected fact.
- **Condition isPlayer (entity-type)** (EE) — missing: no entity-type fact.
- **Condition isPlayerHolding** (EE) — missing: no held-material fact.
- **Condition isPlayerRunning** (EE) — missing: no sprinting flag.
- **Condition isTarget (entity-type)** (EE) — missing: victim scope exposes only health.
- **Condition isTargetBlocking** (EE) — missing: flags are activator-only.
- **Condition isTargetCrouching** (EE) — missing: no victim.sneaking.
- **Condition isTargetFlying** (EE) — missing: no victim.flying.
- **Condition isTargetHolding** (EE) — missing: no item fact.
- **Condition isTargetRunning** (EE) — missing: no target sprinting flag.
- **Unregistered conditions (isRaining/swimming)** (EE) — missing: no weather/swimming facts.
- **Cooldown store (currentTimeMillis-based)** (EE) — partial: tick-based, deterministic, Folia-correct instead.
- **MathUtils equation evaluator** (EE) — missing: expression grammar has only boolean/comparison ops; no arithmetic (ADR 0016).
- **Groups / rarity model** (EE) — partial: split into `tier` registry (numeric weight) + `group` (cooldown scope).
- **Targets / applies model with parents** (EE) — partial: named target groups, enforced at runtime; no user-editable targets.yml with parents.
- **requires / blacklist enchant relationships** (EE) — missing: no requires/conflict fields.
- **Enchant-table integration (weighted roll)** (EE) — missing: no enchanting-table/grindstone integration.
- **DAMAGE effect** (EE) — partial: flat amount only, no [min,max] range.
- **DAMAGE_ARC effect** (EE) — missing: no cone/arc shape.
- **DAMAGE_CANCEL effect** (EE) — partial: unified into generic CANCEL.
- **DAMAGE_DISTANCE effect** (EE) — missing: no distance-scaling.
- **DAMAGE_INCREASE effect** (EE) — partial: realized as ADD_DAMAGE:percent (additive fold), no equation/health/distance input.
- **RAGE effect** (EE) — missing: no per-player accumulating stack with reset.
- **REDUCTION effect** (EE) — partial: REDUCE_DAMAGE/FLAT_REDUCE additive; no multiplicative/equation.
- **REDUCE_HEARTS effect** (EE) — missing: no set-health + regen-block-for-duration.
- **IMMUNE effect** (EE) — missing: no typed/durational damage immunity.
- **SHACKLE effect** (EE) — missing.
- **REMOVE_ARMOR effect** (EE) — missing: DISARM exists (main-hand), not armor removal.
- **WRATH effect** (EE) — missing: composite not present (pieces exist separably).
- **HIDE_PLAYER effect** (EE) — missing.
- **TELEPORT effect** (EE) — partial: Sink teleport intent exists; no TELEPORT EffectKind registered.
- **TELEPORT_BEHIND effect** (EE) — missing.
- **TELEPORT_DROPS effect** (EE) — missing.
- **THROW effect** (EE) — partial: THROW/LAUNCH apply velocity; fall-damage-negate nuance unevidenced.
- **TELEBLOCK effect** (EE) — missing.
- **CURE effect** (EE) — *present elsewhere*; *(see Covered)*.
- **HEAL effect** (EE) — partial: flat heal only, no STEAL/ADD mode/range/clamp.
- **HEALTH effect** (EE) — partial: ADDS to max health, no quit-reset tracking.
- **FOOD effect** (EE) — missing: only FEED (add); no REMOVE/range variant.
- **BREAKER effect** (EE) — missing.
- **BREAK_TREE effect** (EE) — missing.
- **TRENCH effect** (EE) — missing: mining shapes TODO (ADR 0020).
- **SMELT effect** (EE) — missing.
- **RAIN effect** (EE) — missing.
- **WEB effect** (EE) — missing: Sink blockChange exists, no WEB kind.
- **FROST effect** (EE) — missing.
- **ROT_DECAY effect** (EE) — missing.
- **STEAL_MONEY effect** (EE) — partial: composable from TAKE_MONEY+GIVE_MONEY; no atomic effect.
- **STEAL_MONEY_PERCENT effect** (EE) — missing: fixed amounts only.
- **STEAL_EXP effect** (EE) — missing: only GIVE_EXP.
- **EXP effect (drop multiplier)** (EE) — missing: GIVE_EXP is flat grant, not a multiplier.
- **REMOVE_SOULS effect** (EE) — missing.
- **DRAIN_SOULS_CONSTANT effect** (EE) — missing: RepeatStore infra unused; dead no-op in EE too.
- **SPAWN effect** (EE) — partial: simple spawn; no health/amount/TTL/owner-targeting params.
- **SPAWN_ARROWS effect** (EE) — missing.
- **EXPLODE effect** (EE) — *present*; *(see Covered)*.
- **AUTO_LOCK effect** (EE) — missing: BOW_FIRE unwired, no homing task.
- **PARTICLE effect** (EE) — partial: core spawn present; BLOCK_BREAK;<material>/bleed prefixes gone.
- **COMMAND effect** (EE) — partial: RUN_COMMAND console-only; no run-as-player branch (ADR 0020).
- **FISH effect** (EE) — missing.
- **DROPS effect** (EE) — missing.
- **DROP_HEAD effect** (EE) — missing: headhunter content unsupported by a built-in.
- **DISABLE_ENCHANTMENT effect** (EE) — partial: suppression gate/stores exist, but no DISABLE effect kind / suppress() intent populates them.
- **DISABLE_ENCHANTMENT_GROUP effect** (EE) — partial: group suppression infra exists, no effect kind.
- **DISABLE_ENCHANTMENT_TYPE effect** (EE) — partial: TYPE scope infra exists, no effect kind, unauthorable.
- **AE Fractor condition expression engine** (AE) — partial: real expression engine present; lacks `contains` (pipe-OR) and `matchesregex` (ADR 0020 TODO).
- **AE condition flow-control results (LEFT:RESULT)** (AE) — partial: Flow enum + chanceDelta exist but compiler never emits FORCE/ALLOW/Δ; not wired end-to-end.
- **AE variable vocabulary (50+)** (AE) — partial: only 7 built-in facts; PAPI passthrough partially closes the gap.
- **AE parameterized dynamic variables** (AE) — missing: fixed scope.name lookup; no SetVariable/InvertVariable.
- **AE PlaceholderAPI passthrough in conditions/effects** (AE) — partial: conditions yes; general effect-arg passthrough not evidenced.
- **AE target selectors set** (AE) — partial: 5 selectors only; many missing (AllPlayers, NearestPlayer, EntityInSight, Block, mining shapes).
- **AE inline effect-string macro tags** (AE) — missing: `<random>/<chance>/<condition>` TODO (ADR 0020).
- **AE builder+pipeline execution (ActionExecution)** (AE) — partial: fixed gate pipeline present; no skipChances/skipConditions/asRepeating builder flags.
- **AE RepeatingTrigger (per-item repeat delay)** (AE) — partial: repeatTicks + RepeatStore exist but no REPEATING trigger and nothing arms it.
- **AE-only triggers (combat/movement/lifecycle)** (AE) — missing: none of ~40 extra trigger classes exist.
- **AE granular fishing-lifecycle triggers** (AE) — missing.
- **AE external-integration triggers** (AE) — missing: no CustomMob/AdvancedSeasons.
- **AE pluggable effect/trigger/target SPI** (AE) — partial: internal builder registration; no public runtime registration SPI.
- **AE flow-control effects** (AE) — partial: only CANCEL; no CancelUse/DisableActivation/SetVariable/InvertVariable.
- **AE damage-typed/combat-control effects** (AE) — partial: additive subset present; no DoubleDamage/HalfDamage/typed/knockback-control/ResetCombo.
- **AE totem/guard/revive effects** (AE) — missing.
- **AE status/utility effects** (AE) — partial: only DISARM/KNOCKBACK/LAUNCH/THROW; many missing.
- **AE inline per-effect chance vs per-level chance** (AE) — missing: single per-ability gate; inline TODO.

### Menus / GUIs

- **Hand-rolled Menu framework (Menu/Button/UUID registry)** (EE+EA) — missing: deliberately not ported; thin direct event wiring.
- **Button = ItemStack + ClickAction pair** (EE+EA) — missing: clicks resolved by slot arithmetic.
- **ClickAction / CloseAction functional interfaces** (EE+EA) — missing: no close handling; menu stages no items.
- **Single global click router (routes by raw slot)** (EE+EA) — partial: one router, but routes only to EnchantMenu by holder type; cancels all clicks.
- **Bottom-inventory click handling + default cancel** (EE) — missing: bottom clicks unconditionally cancelled; no bottomClickAction.
- **Close-action item return on close** (EE) — missing: no InventoryCloseEvent handler; not needed.
- **fillMenu filler-pane fill** (EE+EA) — missing: empty slots left null.
- **PagedMenu (linked-list pages)** (EE+EA) — partial: pagination exists but stateless rebuild, no linked Menu pages.
- **MenuPartition (configurable content region)** (EE) — partial: hard-coded constants, not configurable.
- **Template (per-page decoration/nav/back)** (EE+EA) — missing: inline re-stamp only; no Template, no back button.
- **NextButton / PreviousButton nav buttons** (EE+EA) — partial: functional ARROW nav exists, not reusable Button subclasses.
- **UpdatingMenu + ticking scheduler** (EE+EA) — missing: no ticking menu.
- **open() lifecycle hooks (make/onFirstOpen)** (EE) — missing: only a Folia thread hop, no open sound.
- **refresh()/update() in-place rebuild** (EE) — missing: pages re-open fresh.
- **Title color + 31/32-char truncation** (EE) — partial: title exists, no color/truncation guard.
- **Enchanter menu — command + permission** (EE) — missing: no Enchanter shop.
- **Enchanter — config-driven slot layout** (EE) — missing: no menu config schema.
- **Enchanter — buy unopened group book** (EE) — missing.
- **Enchanter — run console command on purchase** (EE) — missing.
- **Enchanter — dual currency cost (EXP/money), per-slot** (EE) — missing: economy SPI not wired to menus.
- **Enchanter — inventory-full guard** (EE) — missing.
- **AE Enchanter — config-driven shop (open-sound/keep-open/multi-currency)** (AE) — missing.
- **Alchemist menu — command + permission** (EE) — missing.
- **Alchemist — two input slots + preview + confirm** (EE) — missing.
- **Alchemist — combine two same books → +1 level** (EE) — partial: only ADR-0019 dust-onto-book combining, no book-merge menu.
- **Alchemist — combine two magic dusts → next-rarity** (EE) — missing.
- **Alchemist — live output/confirm recompute** (EE) — missing.
- **Alchemist — random EXP cost** (EE) — missing.
- **Alchemist — consume both inputs on confirm, restage** (EE) — missing.
- **AE Alchemist — configurable combine rules/chance/pricing** (AE) — missing.
- **Tinkerer menu — command + permission** (EE) — missing.
- **Tinkerer — paired input/output salvage layout** (EE) — missing.
- **Tinkerer — salvage enchanted item → XP bottle** (EE) — missing.
- **Tinkerer — salvage book → secret dust** (EE) — missing.
- **Tinkerer — confirm consumes/grants; close returns** (EE) — missing.
- **Tinkerer — EXP-bottle redeem on interact** (EE) — missing.
- **AE Tinkerer — config-driven slots/vouchers/pricing** (AE) — missing.
- **Enchants browse menu (group→enchant browser)** (EE) — partial: flat paginated direct-apply menu, no 3-level browser/config.
- **Enchants — main menu group buttons** (EE) — missing.
- **Enchants — paged group menu (border/nav/back/dummies)** (EE) — missing.
- **Enchants — OP-only detail menu granting free books** (EE) — missing.
- **Effects list GUI (/ee effects)** (EE) — missing.
- **EA Effects list GUI (paged BOOK list)** (EA) — missing.
- **EA Armor Sets list GUI** (EA) — missing.
- **EA Armor Set view GUI** (EA) — missing.
- **EA Modifiers (crystals) list GUI** (EA) — missing.
- **AE Armor Sets preview GUI** (AE) — missing.
- **AE GKits GUI + preview + editor** (AE) — missing.
- **AE Admin inventory GUI (all enchants at 100%)** (AE) — missing.
- **AE Market / EnchantPreview GUIs** (AE) — missing: out of scope (no web server).
- **Godly Transmog menu — reorder GUI** (EE) — missing.
- **Menu-listener item-apply via SWAP_WITH_CURSOR** (EE+EA) — partial: cursor-onto-item present but via LEFT/RIGHT in own inventory, not SWAP_WITH_CURSOR/menu.
- **EA Crystal/modifier apply interaction** (EA) — partial: present via carrier gesture, different guard list.
- **EA Crystal-Extractor apply interaction** (EA) — missing.
- **EA Armor/Weapon/Heroic upgrade apply interactions** (EA) — partial: heroic runtime present; no upgrade carrier/listener.
- **EnchantPanelController — web-panel bridge** (EE) — missing: out of scope.
- **ItemStackBuilder — menu item builder + placeholder/NBT** (EE+EA) — missing.
- **EXPUtils — vanilla-curve XP charge/grant** (EE) — missing.
- **Cross-version X-series resolution in menus** (EE+EA) — partial: own resolve layer (ADR 0008); not the X-series library.
- **Per-menu Vault currency support** (EE) — missing: economy SPI not wired to a menu.

### Commands

- **EE root command alias group (/ee, /eliteenchants…)** (EE) — missing: dropped for single `/se` (ADR 0013).
- **EE /ee help (default page)** (EE) — partial: `/se` usage page lists merged subcommands.
- **Splodgebox watermark easter egg** (EE) — missing: inappropriate to carry.
- **EE reload does NOT reload enchantmentTable/menus (partial reload)** (EE) — missing: an EE bug; SE does full transactional swap.
- **EE /ee removeenchant** (EE) — missing: no inverse remove command/API.
- **EE /ee list** (EE) — missing: catalog browsable only via GUI/tab-complete.
- **EE /ee givebook <player> <enchant> <level> [success]** (EE) — partial: `/se book` to sender only, no target/success args.
- **EE /ee giverandombook <group>** (EE) — missing.
- **EE /ee giveunopenedbook <group>** (EE) — missing.
- **EE /ee giveitem <player> <item>** (EE) — missing: no general giveitem dispatcher.
- **giveitem whitescroll** (EE) — partial: mechanic implemented, no give command.
- **giveitem slot-gem** (EE) — missing.
- **giveitem holy-whitescroll** (EE) — missing.
- **giveitem blackscroll** (EE) — missing.
- **giveitem transmog / godly-transmog / item-nametag / randomizer** (EE) — missing.
- **giveitem soul-gem <souls>** (EE) — partial: `/se gem` stamps a 0-soul gem on sender's item; no count/target args.
- **giveitem upgrade-orb** (EE) — missing.
- **giveitem default/unknown help fallback** (EE) — missing.
- **EE inventory-full guard on giveitem/givedust** (EE) — partial: `/se book` drops overflow at feet instead.
- **EE /ee givedust <player> <type>** (EE) — partial: success-dust mechanic exists, no givedust command.
- **givedust secret / omni-secret / magic / omni-magic** (EE) — missing: only flat success-bonus dust.
- **EE /ee effects (effects GUI)** (EE) — missing: EffectSpec metadata unexposed.
- **EE /ee upload / upload <group> / download <token>** (EE) — missing: out of scope (no web server).
- **EE /bless standalone command + conditional registration/cooldown** (EE) — missing.
- **EE /splitsouls** (EE) — missing.
- **EE /alchemist, /enchanter, /tinkerer commands** (EE) — missing.
- **EE ACF static tab-completions (@enchants/@groups/@items/@dusts/@players)** (EE) — partial: live Bukkit completer for enchant/crystal/migrate/flags; no @groups/@items/@dusts/@players.
- **EE per-command ACF permission gating (no plugin.yml perms)** (EE) — missing: SE does the opposite (one `starenchants.admin` node).
- **EA root command alias group (/ea, /elitearmor…)** (EA) — missing: dropped (ADR 0013).
- **EA /ea help** (EA) — partial: `/se` usage page.
- **EA reload does NOT re-wire effects (partial reload)** (EA) — missing: EA bug; SE re-resolves online players.
- **EA /ea give|givearmor <player> <set> <type>** (EA) — missing.
- **EA /ea upgrade|giveupgrade** (EA) — missing.
- **EA /ea modifier <player> <set>** (EA) — partial: `/se crystal` self-applies; no give-to-player.
- **EA /ea modifierlist** (EA) — missing.
- **EA /ea extractor** (EA) — missing.
- **EA /ea omni|omnigive** (EA) — partial: omni runtime present; no omni item/give command.
- **EA /ea heroic|heroicgive** (EA) — partial: heroic runtime present; no heroic item/give command.
- **EA /ea crate|givecrate** (EA) — missing.
- **EA /ea ingredient** (EA) — missing.
- **EA /ea list|admin (armor list GUI)** (EA) — missing.
- **EA /ea effects (effect reference GUI)** (EA) — missing.
- **EA /ea debug (toggle + confirm GUI)** (EA) — missing.
- **EA ACF dynamic completions @sets/@modifiers/@crates** (EA) — missing.
- **EA inventory-full guard on give subcommands** (EA) — missing.
- **EE+EA share ACF framework + per-command permission** (EE+EA) — missing: plain Bukkit + single node by design.
- **EE+EA dual effects-reference surface (GUI vs reference)** (EE+EA) — missing: ADR-0013 reference set unbuilt.
- **AE main command /advancedenchantments (/ae, /customenchants)** (AE) — missing: unified `/se`.
- **AE /ae lastchanged** (AE) — partial: `/se reload` reports generation/count/diagnostics; no add/remove diff.
- **AE /ae unenchant** (AE) — missing.
- **AE /ae givebook** (AE) — partial: `/se book` to sender, no target/rate args.
- **AE /ae givercbook / giverandombook / giveitem / give / givegkit / greset / tinkereritem** (AE) — missing.
- **AE /ae magicdust** (AE) — partial: dust mechanic exists, no give command.
- **AE /ae setsouls** (AE) — partial: SoulLedger.setSouls internal; no command.
- **AE /ae info|about** (AE) — missing.
- **AE /ae list** (AE) — missing.
- **AE /ae open <enchanter|tinkerer|alchemist>** (AE) — missing.
- **AE /ae admin** (AE) — partial: `/se menu` applies enchant (no rate concept).
- **AE /ae view / market / premade / pasteenchants / pastetypes / plinfo / zip / debug / editor / effectlist / claim / listnbt / dev** (AE) — missing (several out of scope: market/paste = no web server).
- **AE per-permission tab completion** (AE) — missing: single node, nothing to filter.
- **AE standalone /aegive, /advancedsets** (AE) — missing.
- **AE configurable /enchanter, /tinkerer, /alchemist, /withdrawsouls, GKits, /enchants, /enchant info** (AE) — missing.
- **AE configurable /apply command** (AE) — partial: internal WornState re-resolution; no user-facing command.
- **AE permissions declared in plugin.yml with op defaults** (AE) — partial: one node `starenchants.admin` default-op; no granular ae.* tree or bypass perms.

### Crystals / modifiers / synergies / omni

- **Armor crystal (Modifier) item type** (EA) — partial: redesigned as a first-class authored effect SOURCE; no NETHER_STAR Modifier item.
- **Per-crystal apply success chance ({CHANCE})** (EA) — missing: apply-success is per-carrier, not per-crystal.
- **Crystal NBT identity (Modifier/Modifier-Chance keys)** (EA) — missing: identity is stable keys in CombatState.crystals list.
- **Random vs fixed-chance crystal generation** (EA) — missing: no crystal-item minting.
- **Crystal application via SWAP_WITH_CURSOR** (EA) — partial: LEFT/RIGHT carrier gesture, not SWAP_WITH_CURSOR.
- **Apply guard: same crystal not already on item** (EA) — missing: deliberately stacks (fixes last-of-type).
- **apply-multiple config: one-crystal-per-item cap** (EA) — partial: hard cap 16, no per-item toggle.
- **Apply guard: armor-types target matching** (EA) — partial: applies-to item groups instead of throwing ArmorTarget enum.
- **Apply guard: set-gating via require-sets** (EA) — missing.
- **Apply guard: generic crystals reject set pieces** (EA) — missing.
- **Settings.both: bypass set-gating** (EA) — missing.
- **Crystal consumed on success and failure** (EA) — partial: carrier consumed before roll; per-carrier not per-crystal.
- **Apply/fail chat messages with {CHANCE}** (EA) — partial: hard-coded service strings, no {CHANCE}.
- **apply() writes per-effect-type NBT (<type>-modifier)** (EA) — missing: deliberately eliminated.
- **Last-of-a-type-wins NBT collapse** (EA) — missing: the central EA bug, explicitly fixed.
- **apply() set-membership compound + flag** (EA) — missing.
- **Added-Lore appended on apply** (EA) — missing: lore re-rendered from state.
- **Convert: crystal turns item into a set piece** (EA) — missing.
- **Custom model data on crystal item** (EA) — missing.
- **Crystals read only from worn armor pieces** (EA) — partial: SE reads any worn/held item (unified source model).
- **Set of modifier-aware effects** (EE+EA) — missing: uniform source erasure, no allowlist.
- **DISABLE_ENCHANT/GROUP crystals are dead** (EE+EA) — missing: bug fixed (crystal-DISABLE now works).
- **Per-source chance gate on additive effects** (EA) — missing: chance is per-ability.
- **Modifier removal (remove()) used by extractor** (EA) — missing: apply-only, no removal.
- **Modifier loading from per-file YAMLs** (EA) — partial: per-file load present; only 6 crystals, path-derived ids, no 132-file dance.
- **/ea modifier <player> <id> — give crystal** (EA) — missing: `/se crystal` self-applies only.
- **/ea modifierlist — crystal browse GUI** (EA) — missing.
- **Pairwise synergy crystal naming scheme** (EA) — missing: rejected (ADR 0006); replacement unimplemented.
- **Synergy crystal = union of two sets** (EA) — missing: planned combines:[a,b] unimplemented.
- **Identity-pair (self-synergy) crystals** (EA) — missing.
- **Synergy 'Multi Crystal' presentation** (EA) — missing.
- **Synergy effect DSL token set** (EA) — missing.
- **Crystal Extractor item + /ea extractor command** (EA) — missing.
- **Extraction via SWAP_WITH_CURSOR / guards / random removal / returns crystal / consumed / no-revert** (EA) — missing: no extraction at all.
- **OMNI Crystal item + /ea omni command** (EA) — missing: omni concept exists as a flag, no item/command/write path.
- **OMNI application via SWAP_WITH_CURSOR + guards + roll + consumption + success marker/lore** (EA) — missing: no omni apply path.
- **OMNI per-set completion variant (single increment)** (EA) — partial: unified resolver computes all sets in one pass.
- **OMNI piece counts as an armor piece** (EA) — partial: counts by slot; no isArmorPiece NBT predicate, flag unwritable.
- **OMNI weapon grants no weapon set bonus** (EA) — missing: no omni-weapon concept.
- **Heroic item tier (stronger-than-diamond variant)** (EA) — partial: flat-stat triple runtime built and tested; no in-game producer.

### Heroic, crafting, crates

- **Heroic armor leather base / weapon gold base / configurable lore placeholders / default color** (EA) — missing: heroic is stat-only, no item construction.
- **Heroic damage reduction (incoming)** (EA) — partial: FLAT reduction in additive fold (ADR 0012), not percentage.
- **Heroic bonus outgoing weapon damage** (EA) — partial: FLAT outgoing add, not percentage; live-tested.
- **Heroic durability protection (cancel item damage by chance)** (EA) — missing: stat stored but inert, no consumer.
- **Heroic effects stack multiplicatively** (EA) — missing: inverted to additive by design (ADR 0012).
- **convert() preserves meta when making heroic** (EA) — missing: no producer.
- **Generic heroic upgrade item + {CHANCE} + random/fixed chance** (EA) — missing.
- **Per-set Upgrade item + {%percent%} + color override** (EA) — missing.
- **Apply per-set/generic/weapon upgrade via SWAP_WITH_CURSOR + roll + all guards/messages** (EA) — missing: no upgrade subsystem.
- **/ea heroic / /ea upgrade commands** (EA) — missing.
- **Spectrum set heroic-by-default** (EA) — missing: no HEROIC_* material routing.
- **Custom crafting recipes (crafting.yml, ShapedRecipe) + all recipe sub-features** (EA) — missing: in scope per ADR 0001 but unimplemented — a real gap.
- **Block using items as vanilla ingredients / shared NamespacedKey / regenerate-on-error** (EA) — missing: no crafting subsystem.
- **Crates/lootboxes (crates.yml) + reward types/pool/open/animation/countdown/grant/broadcast/sounds/guard/give/reload** (EA) — missing: in scope per ADR 0001 but unimplemented — a real gap.
- **Folia-hostile crate scheduling (parity note)** (EA) — missing: moot (no crates).
- **GKits (timed god-kits)** (AE) — missing: out of scope (ADR 0007).
- **Soul-gem crafting-grid anti-dupe** (AE) — missing: no crafting-grid guard on soul gems.

### Integrations

- **WorldGuard region gating — central canRunEnchant umbrella** (EE) — partial: single actor-location BUILD test, no EffectType→flag routing.
- **WorldGuard pvp/interact/block-break flag queries** (EE) — partial: BUILD flag only; fail-open default inverted from EE.
- **WorldGuard blacklisted-regions list** (EE) — missing.
- **WorldGuard auto-flag execute-commands on enable** (EE) — missing: native BUILD semantics, no global-region flag.
- **WorldGuard version detection v6/v7 (bundled wrapper)** (EE+EA) — missing: compiles against real WG API (ADR 0017).
- **WorldGuard per-effect re-checks on AoE block-break** (EE) — missing: single gate-2 question.
- **WorldGuard support (EA) — canPvP gate on offensive sets** (EA) — partial: unified actor-location gate, not victim canPvP.
- **Factions multi-provider bridge / safezone / canUse / friendly suppression / EA gating** (EE+EA) — missing: dropped (ADR 0001); would be a future ProtectionProvider add-on.
- **Towny support via FactionsBridge** (EE+EA) — missing.
- **Skyblock provider detection / friendly-fire / build-permission** (EE) — missing.
- **Vault economy auto-detection** (EE) — partial: first-party EconomyProvider via ServicesManager; no concrete Vault provider bundled; double amounts.
- **Enchanter menu money/XP purchase** (EE) — missing.
- **STEAL_MONEY / STEAL_MONEY_PERCENT effect (Vault PvP transfer)** (EE) — missing.
- **ItemsAdder item source for armor/weapons** (EA) — missing.
- **EliteBosses BOSS_DAMAGE effect** (EA) — missing.
- **ArmorEquip InventoryClick library** (EE+EA) — partial: native PlayerArmorChangeEvent instead of shaded Arnah lib.
- **PlaceholderAPI parity gap** (EE+EA) — partial: condition passthrough seam present, bridge unwired (test-only).
- **bStats metrics** (EE+EA) — missing.
- **FactionsBridge bundled multi-provider library** (EE+EA) — missing: dropped (ADR 0001).
- **Layered protection-gate flow** (EE) — partial: single unified gate, only WG provides a provider.
- **AE PlaceholderAPI expansion (advancedenchantments_*)** (AE) — missing: expose-placeholders side unimplemented.
- **AE PlaceholderAPI passthrough hook (relational)** (AE) — partial: passthrough seam present, not production-wired, no relational placeholders.
- **AE pluggable protection-handler SPI (canBreak/canAttack/isProtected)** (AE) — partial: narrow allows(actor,where), not the triad.
- **AE WorldGuard hook (BUILD/BLOCK_BREAK/region/mob-spawning)** (AE) — partial: BUILD only.
- **AE Factions / Towny / TownyChat / Lands / GriefPrevention / GriefDefender / ProtectionStones / Residence / SuperiorSkyblock2 hooks** (AE) — missing: future ProtectionProvider add-ons.
- **AE Vault permission / LuckPerms hooks** (AE) — missing: economy-only SPI, no permission integration.
- **AE ItemsAdder / Oraxen / SlimeFun / MythicMobs / mcMMO / AuraSkills / AdvancedSkills / AdvancedChests hooks** (AE) — missing.
- **AE anticheat exemption hooks (AAC/Vulcan/Spartan/…)** (AE) — missing.
- **AE vanish / hologram / ViaVersion / Geyser / DiscordSRV / Dynmap / BeaconsPlus3 / ProtocolLib+TAB hooks** (AE) — missing.
- **AE self/AdvancedEnchantments API hook** (AE) — partial: first-party se-api module exists, not a ServicesManager hook.
- **AE hook-load ordering + success summary log** (AE) — missing: one-shot ServicesManager read at enable.
- **AE pluggable hook/trigger/target/effect SPI** (AE) — partial: engine registration SPI present; integration-specific surface narrower.
- **AE pluggable enchanter-payment SPI** (AE) — missing: single first-party economy provider only.

### AdvancedEnchantments-only feature systems

- **GKits (timed god-kit system) + all sub-features (drop chance, command-items, item building, cooldown tracking/bypass/reset/formatting, main GUI, placeholders, skull icons, hidden kits, fillers, open commands/sounds, preview GUI, in-game editor, public API)** (AE) — missing: explicitly out of scope (ADR 0007).
- **Random world-chest / villager loot population + generation chance/cap/toggle/weighted-table/blacklist/separate-configs** (AE) — missing: out of scope (ADR 0007).
- **Per-mob custom drop tables + item vocabulary / spawner exclusion / damage-cause gating** (AE) — missing: out of scope (ADR 0007).
- **Custom weapons (named items) + default enchants random-range / bound abilities / requireSet / NBT identity** (AE) — missing: not named in ADR 0007 but under its "engine-level only" decision.
- **Web enchantment marketplace (browse/download/upload/share/cache-refresh)** (AE) — missing: out of scope (ADR 0001, no web server).
- **Premade setup import (config packs) + async download/backup** (AE) — missing: out of scope; bundled default content instead.
- **Pluggable effect / trigger / target-selector SPI (AEAPI.register\*)** (AE) — partial: internal builder registration present; public runtime register\* not yet in se-api.
- **Pluggable enchanter-payment SPI** (AE) — missing.
- **Repeating trigger + instant-apply / TTL resume queue / lifecycle management** (AE) — missing: RepeatStore present but dead/unwired.
- **Command trigger** (AE) — missing.
- **Totem / remove-health-totem / remove-health-damage-totem / revive / guard / steal-guard / invincible / keep-on-death effects** (AE) — missing.
- **Double-damage / half-damage effects** (AE) — missing: additive arbiter by design (ADR 0012).
- **Increase-damage / decrease-damage / negate-damage effects** (AE) — partial: AddDamage/ReduceDamage/FlatReduce via additive fold; different mechanism.
- **Remove-health vs remove-health-damage (raw vs typed)** (AE) — partial: HEALTH/DAMAGE exist; no armor-ignoring raw variant / 0.01 prime trick.
- **Ignore-armor-damage / ignore-armor-protection / disable-knockback / stop-knockback / pull-closer / pull-away / screen-freeze / freeze / shuffle-hotbar / reset-combo / bleed / blood / teleport-drops / remove-random-armor / auto-reel / set-catch-time / disable-activation / cancel-use effects** (AE) — missing.
- **Snowblind effect** (AE) — partial: equivalent behavior via CANCEL (no "snowblind" identity).
- **Steal-health effect** (AE) — partial: composable DAMAGE+HEAL (shipped lifesteal); no atomic primitive.
- **Steal-exp effect** (AE) — missing.
- **Steal-money effect** (AE) — partial: composable TAKE_MONEY+GIVE_MONEY; no atomic transfer.
- **Drop-held-item effect** (AE) — partial: folded into DISARM.

### Item & economy system

- **Enchant book item (EnchantBook)** (EE) — partial: unified carrier ItemDef of kind book; no per-item EnchantmentSuccess/destroyRate NBT triple / placeholder system.
- **Book material follows group / {DESCRIPTION} expansion / dummy book / random-from-group** (EE) — missing: tiers not groups; deterministic state-rendered lore; no random-pool mint.
- **Book apply: required-enchants / already-at-level / blacklisted-enchants gates** (EE) — missing: re-apply is plain overwrite; no prerequisite/conflict checks.
- **Book apply: slot gate (non-upgrades)** (EE) — partial: free-slot gate exists; BASE_SLOTS flat 6, added=0 always.
- **Permission-based rank success increase** (EE) — missing.
- **Book apply: PreEnchantItemEvent chance override** (EE) — missing.
- **Book apply: SUCCESS/DESTROY/FAIL branches** (EE) — partial: three outcomes exist; single roll, no per-outcome events/particle/sound config.
- **remove-slot-when-fail** (EE) — missing.
- **Book success/fail/destroy particles and sounds** (EE) — missing: chat only.
- **Unopened (mystery) book + reveal firework** (EE) — missing.
- **White Scroll item / removal helper** (EE) — partial: protect-scroll carrier (boolean PDC guard) instead of per-scroll UUID item; no applied-lore line, no admin strip command.
- **Holy White Scroll item / gate / death-save / respawn return / combined removal** (EE) — missing: no death-survival protection.
- **Black Scroll item / extract enchant / use-random-chance** (EE) — missing.
- **Transmog Scroll / re-sort / applied-name suffix; Godly Transmog item / menu / interact lock** (EE) — missing.
- **Randomizer Scroll item / reroll book success** (EE) — missing.
- **Soul Gem item** (EE) — partial: PDC gem (UUID+count) on any held item; no configured EMERALD item, soul-tier coloring, or rendered display.
- **Soul mode toggle** (EE) — partial: via `/se soulmode` command, tracked by UUID; no right-click-air, no sound config.
- **Soul mode auto-deactivation** (EE) — partial: QUIT only; no slot-click/drop/death deactivation.
- **Soul mode particle aura (SoulGemTask)** (EE) — missing.
- **REMOVE_SOULS / DRAIN_SOULS_CONSTANT effects** (EE) — missing.
- **Soul Gem combining (stacking) / /splitsouls** (EE) — missing: per-gem identity by design.
- **Upgrade Orb item + apply/type-gate/chance/idempotent/bonus-slots/interact-lock** (EE) — missing: SlotLedger.withAddedSlots arithmetic exists but unused.
- **Slot Gem item (+1 slot) + capacity gate** (EE) — missing: "gem" kind is a content book.
- **Item Nametag item / rename via chat / blacklisted-words** (EE) — missing.
- **Secret Dust / Omni-Secret Dust / Mystery Dust items** (EE) — missing: only flat success-bonus dust (ADR 0019).
- **Magic Dust item** (EE) — partial: success-bonus dust is the analog; not group-bound, no sound/particle.
- **Omni-Magic Dust item** (EE) — partial: the single dust is already group-agnostic; no distinct marker/feedback.
- **Dust sound and particle feedback** (EE) — missing: chat only.
- **customEnchantSlots capacity counter** (EE+EA) — partial: SlotLedger arithmetic + flat BASE_SLOTS=6; not persisted as a per-item PDC int with purchasable slots.
- **Slot consumption on new enchant** (EE+EA) — partial: new consumes, re-apply doesn't; used derived from count, no keepSlots path.
- **Slot add/minus/set/randomise primitives** (EE) — partial: max/remaining/canApply/withApplied/withAddedSlots only; no minus/set/randomise.
- **Slots lore rendering / Randomise-slots mode / SlotsIgnore opt-out** (EE) — missing.
- **Custom-enchant JSON storage (customEnchantList)** (EE+EA) — partial: replaced by versioned PDC blob; legacy-item read-compat (architecture §4.3) not implemented.
- **Lore/name FORMAT system (DEFAULT vs HYPIXEL)** (EE) — missing: single fixed render format.
- **GiveItem / GiveDust commands** (EE) — missing: most item types unimplemented.
- **Item identity NBT-API layer** (EE) — partial: first-party PDC codecs (se:* keys), not shaded NBT-API.
- **ItemStackBuilder placeholder substitution** (EE) — partial: legacy `&` translation + state-rendered lore; no placeholder maps/CMD/glow applied.
- **Global anti-grief ItemListener (right-click / block-place suppression)** (EE) — missing.
- **SWAP_WITH_CURSOR drag-and-drop apply convention** (EE) — partial: LEFT/RIGHT gesture in own inventory, not SWAP_WITH_CURSOR.
- **Vault economy hook (VaultController)** (EE) — partial: first-party EconomyProvider SPI; no bundled Vault dep; double amounts.
- **STEAL_MONEY / STEAL_MONEY_PERCENT effect (Vault transfer)** (EE) — missing.
- **Enchanter menu money/EXP purchase (Vault)** (EE) — missing.
- **Tinkerer XP-bottle / secret-dust salvage** (EE) — missing.
- **Alchemist book/dust combine economy** (EE) — missing: ADR 0019 rejects rarity-tinkering.
- **AE StatTrak tracker items** (AE) — missing: out of scope (architecture §7).
- **AE token economy (ObtainToken)** (AE) — missing: money-only SPI.
- **AE slot-increaser / rename-tag / scroll item line** (AE) — missing: migrator covers enchants/sets only.

### Armor sets

- **Per-set config file model (one YAML = one set)** (EA) — partial: file-per-set shape survives; parsed model fully redesigned (a single levelless ability).
- **13 bundled armor sets** (EA) — partial: only 6 redesigned sets ship; original 13 named files absent.
- **Per-set resilient parse (one bad set survives)** (EA) — partial: opposite — transactional reject-all on a malformed set (ADR 0014).
- **Armor set root metadata fields / Apply-Remove messages / sounds** (EA) — missing: set is a triggered ability, no Armor metadata object.
- **Per-piece item definition sections + standard builder keys + CUSTOM_SKULL / HEROIC_ / LEATHER_ / ItemsAdder materials + vanilla/custom-enchant stamping + weapon hide-enchants + arbitrary NBT + Upgrade section** (EA) — missing: SE never builds set items; membership is a stamped PDC setKey.
- **NBT identity tags armor-value / weapon-value** (EA) — partial: setKey replaces armor-value; no separate weapon-value channel.
- **ArmorEquip detection backbone** (EA) — partial: native PlayerArmorChangeEvent, not the synthetic-event lib.
- **Equip-source coverage** (EA) — partial: delegated to PlayerArmorChangeEvent; no hand-rolled per-source matrix.
- **ArmorType suffix matching** (EA) — missing: reads armor slots directly, matches by setKey.
- **Set apply / remove / join lifecycle** (EA) — partial: declarative WornState refresh; no delay/message/sound/event/diff.
- **Per-player applied-set state map** (EA) — partial: BitSet in immutable WornState, not List<String> map.
- **Block-place prevention for skull pieces** (EA) — missing.
- **Reset-health-on-quit toggle** (EA) — missing.
- **Set/weapon effect DSL — colon-delimited tokens** (EA) — partial: terse form survives as the general unified DSL; legacy token set not reproduced.
- **Three-source effect resolution (set/weapon/modifier)** (EA) — partial: unified to FIVE source kinds via erasure; no 3-source resolver/hasModifier lookup.
- **Weapon-effects fallback to worn-armor Effects** (EA) — missing.
- **Accumulated chance gate on stacked effects** (EA) — partial: per-ability chance, not summed-then-single-roll.
- **Multiplicative registration-order damage stacking** (EA) — missing: inverted to additive fold (ADR 0012).
- **Reflection-based per-effect event hook (registerEvent)** (EA) — missing: fixed Paper-native listener set.
- **Effect registry + reflective instantiation + effects GUI feed** (EA) — partial: explicit registry + doc metadata; no effects-list GUI, no reflection.
- **Conditional effect registration by hooked plugin** (EA) — missing: fixed list; EE merged not hooked.
- **BOW_DAMAGE / BOW_REDUCTION effects** (EA) — partial: composable from BOW/DEFENSE trigger + DAMAGE/REDUCE; no dedicated token, no arrow-only DEFENSE split.
- **BOSS_DAMAGE effect** (EA) — missing.
- **BOMBER / BLESS / BUTCHER / COMMAND(equip) / DODGE / SMITE effects** (EA) — partial: composable from primitives; no faithful named effect (and no equip/remove lifecycle for COMMAND).
- **DROPS / DURABILITY / FISH / GALAXY / HUNGER_LOSS / SNOWIFY / SUFFOCATE / WARP / WEB / HALLOWEEN_PUMPKIN effects** (EA) — missing.
- **EXP effect (multiply XP drops)** (EA) — partial: GIVE_EXP flat grant, not a multiplier.
- **FALL_DAMAGE effect** (EA) — partial: composable as FALL trigger + CANCEL; no always-on negation token.
- **PARTICLE effect (shapes on apply/combat/constant)** (EA) — partial: PARTICLE present + repeat knob; no shape library / apply phase.
- **Set/weapon damage handlers read held item** (EA) — partial: held item contributes via WornState; no separate weapon-effects channel.
- **Heroic upgraded-gear variant (NBT markers)** (EA) — partial: heroic is a first-class flat-stat SOURCE; legacy presence-only marker scheme replaced.
- **Heroic flat config stats (reduction/damage/durability)** (EA) — partial: damage/reduction additive in fold; durability stored but inert.
- **Heroic reduction applies to all damage causes** (EA) — partial: fold is cause-agnostic; all-cause coverage unverified.
- **Heroic diamond→gold/leather conversion** (EA) — missing.
- **Per-set heroic upgrade application / generic upgrade / outcome messaging** (EA) — missing.
- **Omni gem application (apply omni to plain gear)** (EA) — partial: omni runtime + gem carrier exist; no shipped omni gem that stamps the flag.
- **Set give command with type codes** (EA) — missing.
- **Set reload command (sets only)** (EA) — partial: unified `/se reload`, not sets-only.
- **Set tab-completion and list (@sets)** (EA) — missing.
- **Public EliteArmorAPI facade** (EA) — partial: unified se-api exists; not the EA-specific facade with item-building methods.
- **Delayed heavy set init (10-tick)** (EA) — missing: replaced by enable-time snapshot (ADR 0014).

## Covered

Present features by category (counts + notable examples):

- **Enchant engine — effects DSL, triggers, conditions, pipeline (~20 present):** ATTACK/DEFENSE dual-fire on EDBE; MINE, INTERACT/INTERACT_LEFT/INTERACT_RIGHT, FALL, FIRE triggers wired; multi-trigger abilities (triggerMask); WAIT chaining (cumulative, fixes EE overwrite bug); pipeline gates — type+level guard, chance roll, condition check, souls cost, cooldown arming; conditions isPlayerBlocking/Crouching/Flying/Health, isTargetHealth; per-level options (chance/souls/cooldown/condition/effects); effects DAMAGE_ARMOR, KNOCKBACK, KILL, POTION, CURE, FEED, FILL_OXYGEN, EXTINGUISH, IGNITE(FLAME), TNT, FIREBALL, LIGHTNING/STRIKE, EXPLODE, FLY, ADD_DURABILITY, ADD_DURABILITY_ITEM, REPAIR, CANCEL, MESSAGE(+ACTIONBAR/TITLE), SOUND, RUN_COMMAND; AE `@Head{k=v}` selector grammar.
- **Crystals / modifiers / synergies / omni (5 present):** crystal effects stack additively with set+weapon+heroic into one fold; OMNI set-completion wildcard math ("the single most important correctness rule"); apply guards (single target only, unknown-modifier id rejected).
- **Armor sets (5 present):** configurable piece-count threshold (`pieces`); full-set detection over 4 armor slots by setKey; omni wildcard contributes to every set's count; isArmorPiece predicate (setKey OR omni); ATTACK vs DEFENSE damage-side semantics; DAMAGE / REDUCTION set effects (additive); DISABLE_ENCHANT / DISABLE_GROUP (interned-id suppression, now crystal-borne too); FLY / HEAL / POTION set effects.
- **Item & economy system (3 present):** book apply gates — amount>1, target-includable; destroy-on-fail toggle; White Scroll apply (protect against one destroy, consumed on save); soul consumption as enchant cost (gate 10).
- **Commands (6 present):** unified `/se reload [--dry-run]` (transactional off-thread swap) covering EE/EA/AE responsibilities; `/se enchant <key> [level]` (EE/AE addenchant + AE enchant); `/se menu` enchant browser GUI (paginated 6-row).
- **Integrations (1 present):** EE↔EA enchant stamping — achieved by elimination (one unified engine, enchants on armor are first-class, no cross-plugin hook).
- **AdvancedEnchantments-only feature systems (1 present):** DISARM effect.

## Recommended next increments

Highest-value gaps, prioritized (✱ = explicitly deferred in an ADR, so a conscious decision is required before building):

1. **Wire the declared-but-unwired triggers (DEATH, BOW_FIRE, FISHING, EAT, ITEM_DAMAGE, BREAK, HELD, PASSIVE) to Bukkit listeners.** The vocabulary, routing metadata, and WornState plumbing already exist; this unblocks a large class of content and the item-known overload with minimal new architecture.
2. **Activate the suppression effects (DISABLE_ENCHANTMENT/GROUP/TYPE).** The gate, stores, and interned suppressKeys already exist — only the effect kinds and a `suppress()` Sink intent are missing. High leverage for combat balance.
3. **Persist the slot capacity to PDC and ship the Slot/Upgrade item line.** `SlotLedger.withAddedSlots` arithmetic exists but `added=0` is hardcoded; wiring per-item `customEnchantSlots` + an upgrade-orb/slot-gem unlocks book-apply parity and several give commands.
4. **Implement the legacy item-NBT migration read-compat (architecture §4.3).** The migrator only reads YAML today; reading legacy `customEnchantList`/`customEnchantSlots` from existing items is the practical adoption blocker for real servers.
5. **Register the TELEPORT effect kind** (Sink intent already exists) and the small composable status effects (FREEZE/move-lock, BLEED DoT, PULL_CLOSER/PULL_AWAY) — cheap wins that close common AE/EA content needs.
6. **Add the `/se` reference/admin surface** (`effects`/`conditions`/`triggers`/`selectors`/`info`/`list`) — ADR-0013 already specifies it and `EffectSpec.doc()` metadata is in-engine; only the command wiring is missing. ✱(ADR 0013 intent unbuilt)
7. **Wire RepeatStore to a real REPEATING trigger** ✱ — the store and `repeatTicks` field exist but nothing arms the recurring task; needed for AE RepeatingTrigger and any DRAIN/aura behavior.
8. **Production-wire the PlaceholderAPI bridge** — the condition passthrough seam exists but `papiResolver` is test-only; install a real bridge and (optionally) register an expansion to close the AE PAPI parity gap.
9. **Add `contains`/`matchesregex` condition operators and inline `<chance>`/`<random>` effect tags** ✱ (ADR 0020 TODOs) — needed for faithful AE config migration.

Deliberately deferred / out of scope (do not build without revisiting the ADR): GKits, StatTrak, loot/mob-drop tables, the web marketplace/panel, the merchant GUIs (Enchanter/Alchemist/Tinkerer), and the ~40 third-party integration hooks (Factions/Towny/Lands/mcMMO/anticheat/vanish/etc., intended as future ProtectionProvider/se-api add-ons). **Custom crafting recipes and crates are in scope per ADR 0001 but unimplemented — these are real, undeferred gaps, not exclusions.**

## Audit completeness — additional gaps the audit itself missed

This confirms a distinct missing feature: the EE **grindstone listener strips custom enchants from the result item** (so a grindstone removes EE custom enchants too, version-gated ≥1.14.1). The audit has "Enchant-table integration (weighted roll)" but no grindstone-strip line. This is a real subsystem/listener absent from the audit.

I now have enough findings. Let me compile the audit gaps.

## Audit gaps — legacy features/subsystems present in the analysis but NOT enumerated in the audit list

The audit is broad (effects, triggers, conditions, items, scrolls, dust, slots, menus, commands, armor sets, modifiers/crystals, heroic, crafting, crates, integrations/hooks, GKits, AE deltas). But these concrete items from CATALOG.md / ae-reference.md / the per-subsystem docs are **not represented by any audit line**:

1. **AE anvil-control subsystem (`anvil.yml`, `handlers/anvil/`)** — a whole subsystem. AE optionally "takes control of anvils" so custom (and vanilla) enchants combine in vanilla anvils, with `use-repair-cost`, `allow-illegal-vanilla/custom-enchant-combinations` toggles, per-vanilla-enchant max-level caps, and a configurable listener priority. The audit only has the Alchemist GUI "combine two same books" and `config.yml applying.anvil-application` is never checked. The vanilla-anvil takeover/combining engine is entirely absent.

2. **EE GrindstoneListeners — custom-enchant strip on grindstone** (`enchants/listeners/GrindstoneListeners.java`, version-gated ≥ 1.14.1). On taking the grindstone RESULT slot it calls `removeEnchantments(item)`, stripping EE custom enchants. The audit has the enchant-table roll but no grindstone behavior line at all (it's a distinct listener with its own version gate and Folia/inventory-thread implications).

3. **EnchantTableListener (the live event handler) and AE enchant-table pricing/`enchantmentBooks`** — the audit's "Enchant-table integration (weighted roll)" covers EE's `EnchantTableController` weighting, but misses: (a) the `EnchantTableListener` event-handling/registration itself as a feature, and (b) AE's richer table model in `enchantmentTable.yml` — `prices` (custom XP pricing per tier), `enchantmentBooks` (give custom book instead of applying directly), and per-tier `enchantCount` ranges. The AE table is a separate, more capable variant not enumerated.

4. **Enchant-application limitation / "unmodifiable item" gate** (`config.yml enchantLimitation`: lore-string OR NBT-tag that blocks custom enchants from being applied; AE `combining.break-item/upgrade/use-chances` toggles). The audit has many per-book apply gates (amount, target, slot, blacklist, already-at-level) but no line for an external "this item is locked from modification" lore/NBT gate, nor for the AE combining-on-item toggles (break-item, upgrade-if-higher, use-chances).

5. **Lore/name FORMAT system breadth** — the audit has one line "Lore/name FORMAT system (DEFAULT vs HYPIXEL)". But the analysis documents several distinct sub-features under it that each carry real rewrite risk and are arguably separate audit items: HYPIXEL 3-per-line compaction, `settings.lore-line` insertion index, `RomanNumeral` level rendering (1..3999 guard), the `{GROUP-COLOR}{ENCHANTMENT} {LEVEL}` placeholder style, and the per-message `Message`/lang enum (`lang.yml`) with its placeholder set. The lang/Message localization layer (and EE's shipped ACF i18n `.properties` for ~18 locales) is not enumerated anywhere.

6. **NBT-API → PDC migration contract as an explicit deliverable** — the audit lists "Item identity NBT-API layer" and "Custom-enchant JSON storage (customEnchantList)" but does NOT enumerate the **legacy-key read-compat / migration contract** (the exact key set: `customEnchantList`, `customEnchantSlots`, `SlotsIgnore`, `AddedSlots`, `Transmog`, `GodlyTransmog`, `armor-value`, `weapon-value`, `omni-armor`, `modifiers`, `<type>-modifier`, `heroic-armor/weapon`, scroll/dust/orb/soul markers) that StarEnchants must read to import existing player items. This is a cross-cutting subsystem (config-and-migration), distinct from "storing enchants."

7. **bStats metrics + inherited NBT-API metrics/update-check/license behavior** — the audit's "bStats metrics — EE/EA own + inherited from shaded libs" exists, but the **inherited NBT-API bStats (id 1058) + update-check thread + package-relocation warnings**, and the EE web-panel/license HTTP behavior, are operational side-effects the rewrite must explicitly suppress; they're documented (ee-itemdata §3.3/§6, CATALOG §9) but not broken out as their own audit concern beyond the single metrics line.

8. **AE soul withdraw/split economy granularity** — the audit has EE `/splitsouls`, soul combine, `AE /ae setsouls`, and `AE configurable /withdrawsouls command`, but not the **soul-amount tier-color rendering** (`{SOUL-COLOR}` by amount) or the SoulGem combine/split sound feedback (`BLOCK_ANVIL_USE`) as parity behaviors — minor, borderline (the core `/splitsouls` and combine are covered).

Lower-confidence / likely-already-implied (NOT counting as gaps): StatTrak trackers, keep-on-death, soulbound, web marketplace, mob drops, loot population, anticheat hooks — all present in the audit. GKits is present. The `Splodgebox watermark` and `/ee upload|download` web stubs are present.

**Highest-confidence true gaps: #1 (AE anvil-control), #2 (grindstone custom-enchant strip), #4 (unmodifiable-item / combining-on-item toggles), and #6 (the legacy-NBT migration key contract).** #3 and #5 are partially-covered surfaces where significant sub-features were collapsed into one line. The audit's effect/condition/trigger/item/command/set coverage is otherwise complete.
