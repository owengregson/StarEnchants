# Cosmic Enchants → StarEnchants Feature-Parity Report

**Totals: 31 present · 65 partial · 219 missing — 315 features audited. ~9.8% fully present; ~30.5% present-or-partial.** A large share of the "missing" count is deliberate scope exclusion (web panel, GKits, StatTrak, loot/mob-drop tables, crates, custom crafting, the merchant GUIs) and intentionally-not-reproduced legacy bugs (multiplicative damage stacking, last-of-type crystal collapse, silent unknown-effect no-ops, fail-open conditions). The core engine, item-data layer, damage fold, soul/crystal/set/omni/heroic runtime, and unified `/se` surface are present or partial by design.

## Gaps to close

### Enchant engine — effects DSL, triggers, conditions, pipeline

- **TaskEffect deferred runnable** (Cosmic Enchants-style) — missing: WAIT is realized as compile-time delay tiers + Sink DispatchPlan; the BukkitRunnable-holding mechanism is deliberately rejected (ADR 0011). WAIT behavior itself is present.
- **Per-effect type-prefix routing (TYPE;EFFECT)** (Cosmic Enchants-style) — missing: no semicolon per-effect routing; trigger routing is per-ability (`trigger:`/`triggers:`).
- **Per-effect inline chance (TYPE;CHANCE;EFFECT)** (Cosmic Enchants-style) — missing: single per-ability chance gate only; inline `<chance>` listed TODO in ADR 0020.
- **playEffect dispatch (silent unknown-name no-op)** (Cosmic Enchants-style) — partial: unknown head is a compile-time diagnostic, never a silent runtime no-op (ADR 0004 inverts the wart).
- **stopEnchantment deactivation path** (Cosmic Enchants-style) — missing: `EffectKind` has only `run()`; no stop()/deactivation, no HELD/PASSIVE unequip stop.
- **CustomEffect contract (run/stop lifecycle)** (Cosmic Enchants-style) — partial: `EffectKind` SPI maps the activation half; deactivation half gone by design.
- **Reflection-loaded fixed effect registry (~66 effects)** (Cosmic Enchants-style) — partial: hand-written greppable registry, fewer kinds than a Cosmic Enchants-style original's ~66.
- **Effect DSL grammar (colon/semicolon splitting)** (Cosmic Enchants-style) — partial: colon-split only, compile-time validated; semicolon routing gone.
- **PLAYER vs TARGET token resolution** (Cosmic Enchants-style) — partial: replaced by `who:` named selectors (@Self/@Victim/@Attacker).
- **EffectType trigger taxonomy (20 values)** (Cosmic Enchants-style) — partial: 19 declared in `BuiltinTriggers`, no INVALID sentinel; many declared-but-unwired.
- **getPvPEffects PvP-trigger set** (Cosmic Enchants-style) — partial: modeled as per-trigger combat `Direction`; drives damage-fold side, not a PvP gate.
- **BOW trigger (arrow EDBE)** (Cosmic Enchants-style) — partial: projectile attributed to shooter but dispatched under ATTACK id, no distinct BOW listener.
- **TRIDENT trigger (1.13.1+ gated)** (Cosmic Enchants-style) — partial: declared; thrown trident runs ATTACK abilities; no distinct TRIDENT id, no 1.13.1 gate.
- **BOW_FIRE trigger (EntityShootBowEvent)** (Cosmic Enchants-style) — partial: declared but no listener; never fires.
- **KILL vs DEATH triggers** (Cosmic Enchants-style) — partial: KILL wired; DEATH declared but unwired.
- **FISHING trigger (PlayerFishEvent)** (Cosmic Enchants-style) — partial: declared, no listener; never fires.
- **EAT trigger (PlayerItemConsumeEvent)** (Cosmic Enchants-style) — partial: declared, no listener; never fires.
- **ITEM_DAMAGE trigger (PlayerItemDamageEvent)** (Cosmic Enchants-style) — partial: declared, no listener; never fires.
- **BREAK trigger (PlayerItemBreakEvent)** (Cosmic Enchants-style) — partial: declared, no listener; never fires.
- **HELD trigger + slot-tracking (start/stop)** (Cosmic Enchants-style) — partial: no HELD fire/stop; `PlayerItemHeldEvent` only refreshes WornState.
- **PASSIVE trigger (armor equip/unequip/join)** (Cosmic Enchants-style) — partial: equip refreshes WornState (passive stats apply); no PASSIVE fire or stop.
- **Item-known vs entity-scan resolution** (Cosmic Enchants-style) — partial: two-way distinction modeled; item-known path effectively unexercised (held triggers unwired).
- **applies/target NOT enforced at fire time** (Cosmic Enchants-style) — missing: a Cosmic Enchants-style bug, deliberately fixed via WornState.byTrigger pre-filtering.
- **Pipeline gate: WorldGuard canRunEnchant** (Cosmic Enchants-style) — partial: generic ProtectionProvider, BUILD flag only; no per-category gating, no blacklisted regions (ADR 0017).
- **Pipeline gate: three cooldown keys** (Cosmic Enchants-style) — partial: all three scopes checked/armed, but TYPE scope unauthorable (ADR 0016); scopes armed automatically not via DISABLE_*.
- **settings.math-random chance mode** (Cosmic Enchants-style) — missing: only percentage roll [0,100); buggy alternate mode not carried.
- **Pipeline gate: cancellable EnchantActivationEvent** (Cosmic Enchants-style) — partial: PreActivate guard exists but wired to ALLOW; public event is a notification, not a veto.
- **EnchantDeactivationEvent** (Cosmic Enchants-style) — missing: no deactivation event, no stop() lifecycle.
- **PvP/anti-grief gating in listeners (Factions/Skyblock/WG)** (Cosmic Enchants-style) — missing: no Factions/Skyblock; only gate-2 WG BUILD flag.
- **WorldGuard blacklisted-regions + startup flag commands** (Cosmic Enchants-style) — missing: BUILD flag only; out of scope (ADR 0017).
- **Condition base class + healthCheck operators** (Cosmic Enchants-style) — partial: replaced by expression engine; all six comparators (incl. `!=`).
- **parseConditions registry + fail-open semantics** (Cosmic Enchants-style) — partial: empty passes; fail mode is the OPPOSITE (fail-closed via NaN), compile-time validation.
- **Condition isBlock** (Cosmic Enchants-style) — missing: no block-type fact for MINE.
- **Condition isProtected** (Cosmic Enchants-style) — missing: no white-scroll/protected fact.
- **Condition isPlayer (entity-type)** (Cosmic Enchants-style) — missing: no entity-type fact.
- **Condition isPlayerHolding** (Cosmic Enchants-style) — missing: no held-material fact.
- **Condition isPlayerRunning** (Cosmic Enchants-style) — missing: no sprinting flag.
- **Condition isTarget (entity-type)** (Cosmic Enchants-style) — missing: victim scope exposes only health.
- **Condition isTargetBlocking** (Cosmic Enchants-style) — missing: flags are activator-only.
- **Condition isTargetCrouching** (Cosmic Enchants-style) — missing: no victim.sneaking.
- **Condition isTargetFlying** (Cosmic Enchants-style) — missing: no victim.flying.
- **Condition isTargetHolding** (Cosmic Enchants-style) — missing: no item fact.
- **Condition isTargetRunning** (Cosmic Enchants-style) — missing: no target sprinting flag.
- **Unregistered conditions (isRaining/swimming)** (Cosmic Enchants-style) — missing: no weather/swimming facts.
- **Cooldown store (currentTimeMillis-based)** (Cosmic Enchants-style) — partial: tick-based, deterministic, Folia-correct instead.
- **MathUtils equation evaluator** (Cosmic Enchants-style) — missing: expression grammar has only boolean/comparison ops; no arithmetic (ADR 0016).
- **Groups / rarity model** (Cosmic Enchants-style) — partial: split into `tier` registry (numeric weight) + `group` (cooldown scope).
- **Targets / applies model with parents** (Cosmic Enchants-style) — partial: named target groups, enforced at runtime; no user-editable targets.yml with parents.
- **requires / blacklist enchant relationships** (Cosmic Enchants-style) — missing: no requires/conflict fields.
- **Enchant-table integration (weighted roll)** (Cosmic Enchants-style) — missing: no enchanting-table/grindstone integration.
- **DAMAGE effect** (Cosmic Enchants-style) — partial: flat amount only, no [min,max] range.
- **DAMAGE_ARC effect** (Cosmic Enchants-style) — missing: no cone/arc shape.
- **DAMAGE_CANCEL effect** (Cosmic Enchants-style) — partial: unified into generic CANCEL.
- **DAMAGE_DISTANCE effect** (Cosmic Enchants-style) — missing: no distance-scaling.
- **DAMAGE_INCREASE effect** (Cosmic Enchants-style) — partial: realized as ADD_DAMAGE:percent (additive fold), no equation/health/distance input.
- **RAGE effect** (Cosmic Enchants-style) — missing: no per-player accumulating stack with reset.
- **REDUCTION effect** (Cosmic Enchants-style) — partial: REDUCE_DAMAGE/FLAT_REDUCE additive; no multiplicative/equation.
- **REDUCE_HEARTS effect** (Cosmic Enchants-style) — missing: no set-health + regen-block-for-duration.
- **IMMUNE effect** (Cosmic Enchants-style) — missing: no typed/durational damage immunity.
- **SHACKLE effect** (Cosmic Enchants-style) — missing.
- **REMOVE_ARMOR effect** (Cosmic Enchants-style) — missing: DISARM exists (main-hand), not armor removal.
- **WRATH effect** (Cosmic Enchants-style) — missing: composite not present (pieces exist separably).
- **HIDE_PLAYER effect** (Cosmic Enchants-style) — missing.
- **TELEPORT effect** (Cosmic Enchants-style) — partial: Sink teleport intent exists; no TELEPORT EffectKind registered.
- **TELEPORT_BEHIND effect** (Cosmic Enchants-style) — missing.
- **TELEPORT_DROPS effect** (Cosmic Enchants-style) — missing.
- **THROW effect** (Cosmic Enchants-style) — partial: THROW/LAUNCH apply velocity; fall-damage-negate nuance unevidenced.
- **TELEBLOCK effect** (Cosmic Enchants-style) — missing.
- **CURE effect** (Cosmic Enchants-style) — *present elsewhere*; *(see Covered)*.
- **HEAL effect** (Cosmic Enchants-style) — partial: flat heal only, no STEAL/ADD mode/range/clamp.
- **HEALTH effect** (Cosmic Enchants-style) — partial: ADDS to max health, no quit-reset tracking.
- **FOOD effect** (Cosmic Enchants-style) — missing: only FEED (add); no REMOVE/range variant.
- **BREAKER effect** (Cosmic Enchants-style) — missing.
- **BREAK_TREE effect** (Cosmic Enchants-style) — missing.
- **TRENCH effect** (Cosmic Enchants-style) — missing: mining shapes TODO (ADR 0020).
- **SMELT effect** (Cosmic Enchants-style) — missing.
- **RAIN effect** (Cosmic Enchants-style) — missing.
- **WEB effect** (Cosmic Enchants-style) — missing: Sink blockChange exists, no WEB kind.
- **FROST effect** (Cosmic Enchants-style) — missing.
- **ROT_DECAY effect** (Cosmic Enchants-style) — missing.
- **STEAL_MONEY effect** (Cosmic Enchants-style) — partial: composable from TAKE_MONEY+GIVE_MONEY; no atomic effect.
- **STEAL_MONEY_PERCENT effect** (Cosmic Enchants-style) — missing: fixed amounts only.
- **STEAL_EXP effect** (Cosmic Enchants-style) — missing: only GIVE_EXP.
- **EXP effect (drop multiplier)** (Cosmic Enchants-style) — missing: GIVE_EXP is flat grant, not a multiplier.
- **REMOVE_SOULS effect** (Cosmic Enchants-style) — missing.
- **DRAIN_SOULS_CONSTANT effect** (Cosmic Enchants-style) — missing: RepeatStore infra unused; dead no-op in Cosmic Enchants-style too.
- **SPAWN effect** (Cosmic Enchants-style) — partial: simple spawn; no health/amount/TTL/owner-targeting params.
- **SPAWN_ARROWS effect** (Cosmic Enchants-style) — missing.
- **EXPLODE effect** (Cosmic Enchants-style) — *present*; *(see Covered)*.
- **AUTO_LOCK effect** (Cosmic Enchants-style) — missing: BOW_FIRE unwired, no homing task.
- **PARTICLE effect** (Cosmic Enchants-style) — partial: core spawn present; BLOCK_BREAK;<material>/bleed prefixes gone.
- **COMMAND effect** (Cosmic Enchants-style) — partial: RUN_COMMAND console-only; no run-as-player branch (ADR 0020).
- **FISH effect** (Cosmic Enchants-style) — missing.
- **DROPS effect** (Cosmic Enchants-style) — missing.
- **DROP_HEAD effect** (Cosmic Enchants-style) — missing: headhunter content unsupported by a built-in.
- **DISABLE_ENCHANTMENT effect** (Cosmic Enchants-style) — partial: suppression gate/stores exist, but no DISABLE effect kind / suppress() intent populates them.
- **DISABLE_ENCHANTMENT_GROUP effect** (Cosmic Enchants-style) — partial: group suppression infra exists, no effect kind.
- **DISABLE_ENCHANTMENT_TYPE effect** (Cosmic Enchants-style) — partial: TYPE scope infra exists, no effect kind, unauthorable.
- **Cosmic Enchants-style Fractor condition expression engine** (Cosmic Enchants-style) — partial: real expression engine present; lacks `contains` (pipe-OR) and `matchesregex` (ADR 0020 TODO).
- **Cosmic Enchants-style condition flow-control results (LEFT:RESULT)** (Cosmic Enchants-style) — partial: Flow enum + chanceDelta exist but compiler never emits FORCE/ALLOW/Δ; not wired end-to-end.
- **Cosmic Enchants-style variable vocabulary (50+)** (Cosmic Enchants-style) — partial: only 7 built-in facts; PAPI passthrough partially closes the gap.
- **Cosmic Enchants-style parameterized dynamic variables** (Cosmic Enchants-style) — missing: fixed scope.name lookup; no SetVariable/InvertVariable.
- **Cosmic Enchants-style PlaceholderAPI passthrough in conditions/effects** (Cosmic Enchants-style) — partial: conditions yes; general effect-arg passthrough not evidenced.
- **Cosmic Enchants-style target selectors set** (Cosmic Enchants-style) — partial: 5 selectors only; many missing (AllPlayers, NearestPlayer, EntityInSight, Block, mining shapes).
- **Cosmic Enchants-style inline effect-string macro tags** (Cosmic Enchants-style) — missing: `<random>/<chance>/<condition>` TODO (ADR 0020).
- **Cosmic Enchants-style builder+pipeline execution (ActionExecution)** (Cosmic Enchants-style) — partial: fixed gate pipeline present; no skipChances/skipConditions/asRepeating builder flags.
- **Cosmic Enchants-style RepeatingTrigger (per-item repeat delay)** (Cosmic Enchants-style) — partial: repeatTicks + RepeatStore exist but no REPEATING trigger and nothing arms it.
- **Cosmic Enchants-style-only triggers (combat/movement/lifecycle)** (Cosmic Enchants-style) — missing: none of ~40 extra trigger classes exist.
- **Cosmic Enchants-style granular fishing-lifecycle triggers** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style external-integration triggers** (Cosmic Enchants-style) — missing: no CustomMob/AdvancedSeasons.
- **Cosmic Enchants-style pluggable effect/trigger/target SPI** (Cosmic Enchants-style) — partial: internal builder registration; no public runtime registration SPI.
- **Cosmic Enchants-style flow-control effects** (Cosmic Enchants-style) — partial: only CANCEL; no CancelUse/DisableActivation/SetVariable/InvertVariable.
- **Cosmic Enchants-style damage-typed/combat-control effects** (Cosmic Enchants-style) — partial: additive subset present; no DoubleDamage/HalfDamage/typed/knockback-control/ResetCombo.
- **Cosmic Enchants-style totem/guard/revive effects** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style status/utility effects** (Cosmic Enchants-style) — partial: only DISARM/KNOCKBACK/LAUNCH/THROW; many missing.
- **Cosmic Enchants-style inline per-effect chance vs per-level chance** (Cosmic Enchants-style) — missing: single per-ability gate; inline TODO.

### Menus / GUIs

- **Hand-rolled Menu framework (Menu/Button/UUID registry)** (Cosmic Enchants-style) — missing: deliberately not ported; thin direct event wiring.
- **Button = ItemStack + ClickAction pair** (Cosmic Enchants-style) — missing: clicks resolved by slot arithmetic.
- **ClickAction / CloseAction functional interfaces** (Cosmic Enchants-style) — missing: no close handling; menu stages no items.
- **Single global click router (routes by raw slot)** (Cosmic Enchants-style) — partial: one router, but routes only to EnchantMenu by holder type; cancels all clicks.
- **Bottom-inventory click handling + default cancel** (Cosmic Enchants-style) — missing: bottom clicks unconditionally cancelled; no bottomClickAction.
- **Close-action item return on close** (Cosmic Enchants-style) — missing: no InventoryCloseEvent handler; not needed.
- **fillMenu filler-pane fill** (Cosmic Enchants-style) — missing: empty slots left null.
- **PagedMenu (linked-list pages)** (Cosmic Enchants-style) — partial: pagination exists but stateless rebuild, no linked Menu pages.
- **MenuPartition (configurable content region)** (Cosmic Enchants-style) — partial: hard-coded constants, not configurable.
- **Template (per-page decoration/nav/back)** (Cosmic Enchants-style) — missing: inline re-stamp only; no Template, no back button.
- **NextButton / PreviousButton nav buttons** (Cosmic Enchants-style) — partial: functional ARROW nav exists, not reusable Button subclasses.
- **UpdatingMenu + ticking scheduler** (Cosmic Enchants-style) — missing: no ticking menu.
- **open() lifecycle hooks (make/onFirstOpen)** (Cosmic Enchants-style) — missing: only a Folia thread hop, no open sound.
- **refresh()/update() in-place rebuild** (Cosmic Enchants-style) — missing: pages re-open fresh.
- **Title color + 31/32-char truncation** (Cosmic Enchants-style) — partial: title exists, no color/truncation guard.
- **Enchanter menu — command + permission** (Cosmic Enchants-style) — missing: no Enchanter shop.
- **Enchanter — config-driven slot layout** (Cosmic Enchants-style) — missing: no menu config schema.
- **Enchanter — buy unopened group book** (Cosmic Enchants-style) — missing.
- **Enchanter — run console command on purchase** (Cosmic Enchants-style) — missing.
- **Enchanter — dual currency cost (EXP/money), per-slot** (Cosmic Enchants-style) — missing: economy SPI not wired to menus.
- **Enchanter — inventory-full guard** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style Enchanter — config-driven shop (open-sound/keep-open/multi-currency)** (Cosmic Enchants-style) — missing.
- **Alchemist menu — command + permission** (Cosmic Enchants-style) — missing.
- **Alchemist — two input slots + preview + confirm** (Cosmic Enchants-style) — missing.
- **Alchemist — combine two same books → +1 level** (Cosmic Enchants-style) — partial: only ADR-0019 dust-onto-book combining, no book-merge menu.
- **Alchemist — combine two magic dusts → next-rarity** (Cosmic Enchants-style) — missing.
- **Alchemist — live output/confirm recompute** (Cosmic Enchants-style) — missing.
- **Alchemist — random EXP cost** (Cosmic Enchants-style) — missing.
- **Alchemist — consume both inputs on confirm, restage** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style Alchemist — configurable combine rules/chance/pricing** (Cosmic Enchants-style) — missing.
- **Tinkerer menu — command + permission** (Cosmic Enchants-style) — missing.
- **Tinkerer — paired input/output salvage layout** (Cosmic Enchants-style) — missing.
- **Tinkerer — salvage enchanted item → XP bottle** (Cosmic Enchants-style) — missing.
- **Tinkerer — salvage book → secret dust** (Cosmic Enchants-style) — missing.
- **Tinkerer — confirm consumes/grants; close returns** (Cosmic Enchants-style) — missing.
- **Tinkerer — EXP-bottle redeem on interact** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style Tinkerer — config-driven slots/vouchers/pricing** (Cosmic Enchants-style) — missing.
- **Enchants browse menu (group→enchant browser)** (Cosmic Enchants-style) — partial: flat paginated direct-apply menu, no 3-level browser/config.
- **Enchants — main menu group buttons** (Cosmic Enchants-style) — missing.
- **Enchants — paged group menu (border/nav/back/dummies)** (Cosmic Enchants-style) — missing.
- **Enchants — OP-only detail menu granting free books** (Cosmic Enchants-style) — missing.
- **Effects list GUI (effects)** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style Effects list GUI (paged BOOK list)** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style Armor Sets list GUI** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style Armor Set view GUI** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style Modifiers (crystals) list GUI** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style Armor Sets preview GUI** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style GKits GUI + preview + editor** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style Admin inventory GUI (all enchants at 100%)** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style Market / EnchantPreview GUIs** (Cosmic Enchants-style) — missing: out of scope (no web server).
- **Godly Transmog menu — reorder GUI** (Cosmic Enchants-style) — missing.
- **Menu-listener item-apply via SWAP_WITH_CURSOR** (Cosmic Enchants-style) — partial: cursor-onto-item present but via LEFT/RIGHT in own inventory, not SWAP_WITH_CURSOR/menu.
- **Cosmic Enchants-style Crystal/modifier apply interaction** (Cosmic Enchants-style) — partial: present via carrier gesture, different guard list.
- **Cosmic Enchants-style Crystal-Extractor apply interaction** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style Armor/Weapon/Heroic upgrade apply interactions** (Cosmic Enchants-style) — partial: heroic runtime present; no upgrade carrier/listener.
- **EnchantPanelController — web-panel bridge** (Cosmic Enchants-style) — missing: out of scope.
- **ItemStackBuilder — menu item builder + placeholder/NBT** (Cosmic Enchants-style) — missing.
- **EXPUtils — vanilla-curve XP charge/grant** (Cosmic Enchants-style) — missing.
- **Cross-version X-series resolution in menus** (Cosmic Enchants-style) — partial: own resolve layer (ADR 0008); not the X-series library.
- **Per-menu Vault currency support** (Cosmic Enchants-style) — missing: economy SPI not wired to a menu.

### Commands

- **Cosmic Enchants-style root command alias group (legacy root + aliases)** (Cosmic Enchants-style) — missing: dropped for single `/se` (ADR 0013).
- **Cosmic Enchants-style help (default page)** (Cosmic Enchants-style) — partial: `/se` usage page lists merged subcommands.
- **Splodgebox watermark easter egg** (Cosmic Enchants-style) — missing: inappropriate to carry.
- **Cosmic Enchants-style reload does NOT reload enchantmentTable/menus (partial reload)** (Cosmic Enchants-style) — missing: a Cosmic Enchants-style bug; SE does full transactional swap.
- **Cosmic Enchants-style removeenchant** (Cosmic Enchants-style) — missing: no inverse remove command/API.
- **Cosmic Enchants-style list** (Cosmic Enchants-style) — missing: catalog browsable only via GUI/tab-complete.
- **Cosmic Enchants-style givebook <player> <enchant> <level> [success]** (Cosmic Enchants-style) — partial: `/se book` to sender only, no target/success args.
- **Cosmic Enchants-style giverandombook <group>** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style giveunopenedbook <group>** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style giveitem <player> <item>** (Cosmic Enchants-style) — missing: no general giveitem dispatcher.
- **giveitem whitescroll** (Cosmic Enchants-style) — partial: mechanic implemented, no give command.
- **giveitem slot-gem** (Cosmic Enchants-style) — missing.
- **giveitem holy-whitescroll** (Cosmic Enchants-style) — missing.
- **giveitem blackscroll** (Cosmic Enchants-style) — missing.
- **giveitem transmog / godly-transmog / item-nametag / randomizer** (Cosmic Enchants-style) — missing.
- **giveitem soul-gem <souls>** (Cosmic Enchants-style) — partial: `/se gem` stamps a 0-soul gem on sender's item; no count/target args.
- **giveitem upgrade-orb** (Cosmic Enchants-style) — missing.
- **giveitem default/unknown help fallback** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style inventory-full guard on giveitem/givedust** (Cosmic Enchants-style) — partial: `/se book` drops overflow at feet instead.
- **Cosmic Enchants-style givedust <player> <type>** (Cosmic Enchants-style) — partial: success-dust mechanic exists, no givedust command.
- **givedust secret / omni-secret / magic / omni-magic** (Cosmic Enchants-style) — missing: only flat success-bonus dust.
- **Cosmic Enchants-style effects (effects GUI)** (Cosmic Enchants-style) — missing: EffectSpec metadata unexposed.
- **Cosmic Enchants-style upload / upload <group> / download <token>** (Cosmic Enchants-style) — missing: out of scope (no web server).
- **Cosmic Enchants-style /bless standalone command + conditional registration/cooldown** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style /splitsouls** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style /alchemist, /enchanter, /tinkerer commands** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style ACF static tab-completions (@enchants/@groups/@items/@dusts/@players)** (Cosmic Enchants-style) — partial: live Bukkit completer for enchant/crystal/migrate/flags; no @groups/@items/@dusts/@players.
- **Cosmic Enchants-style per-command ACF permission gating (no plugin.yml perms)** (Cosmic Enchants-style) — missing: SE does the opposite (one `starenchants.admin` node).
- **Cosmic Enchants-style root command alias group (legacy root + aliases)** (Cosmic Enchants-style) — missing: dropped (ADR 0013).
- **Cosmic Enchants-style help** (Cosmic Enchants-style) — partial: `/se` usage page.
- **Cosmic Enchants-style reload does NOT re-wire effects (partial reload)** (Cosmic Enchants-style) — missing: a Cosmic Enchants-style bug; SE re-resolves online players.
- **Cosmic Enchants-style give|givearmor <player> <set> <type>** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style upgrade|giveupgrade** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style modifier <player> <set>** (Cosmic Enchants-style) — partial: `/se crystal` self-applies; no give-to-player.
- **Cosmic Enchants-style modifierlist** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style extractor** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style omni|omnigive** (Cosmic Enchants-style) — partial: omni runtime present; no omni item/give command.
- **Cosmic Enchants-style heroic|heroicgive** (Cosmic Enchants-style) — partial: heroic runtime present; no heroic item/give command.
- **Cosmic Enchants-style crate|givecrate** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style ingredient** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style list|admin (armor list GUI)** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style effects (effect reference GUI)** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style debug (toggle + confirm GUI)** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style ACF dynamic completions @sets/@modifiers/@crates** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style inventory-full guard on give subcommands** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style share ACF framework + per-command permission** (Cosmic Enchants-style) — missing: plain Bukkit + single node by design.
- **Cosmic Enchants-style dual effects-reference surface (GUI vs reference)** (Cosmic Enchants-style) — missing: ADR-0013 reference set unbuilt.
- **Cosmic Enchants-style main command (legacy root + aliases)** (Cosmic Enchants-style) — missing: unified `/se`.
- **Cosmic Enchants-style lastchanged** (Cosmic Enchants-style) — partial: `/se reload` reports generation/count/diagnostics; no add/remove diff.
- **Cosmic Enchants-style unenchant** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style givebook** (Cosmic Enchants-style) — partial: `/se book` to sender, no target/rate args.
- **Cosmic Enchants-style givercbook / giverandombook / giveitem / give / givegkit / greset / tinkereritem** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style magicdust** (Cosmic Enchants-style) — partial: dust mechanic exists, no give command.
- **Cosmic Enchants-style setsouls** (Cosmic Enchants-style) — partial: SoulLedger.setSouls internal; no command.
- **Cosmic Enchants-style info|about** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style list** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style open <enchanter|tinkerer|alchemist>** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style admin** (Cosmic Enchants-style) — partial: `/se menu` applies enchant (no rate concept).
- **Cosmic Enchants-style view / market / premade / pasteenchants / pastetypes / plinfo / zip / debug / editor / effectlist / claim / listnbt / dev** (Cosmic Enchants-style) — missing (several out of scope: market/paste = no web server).
- **Cosmic Enchants-style per-permission tab completion** (Cosmic Enchants-style) — missing: single node, nothing to filter.
- **Cosmic Enchants-style standalone give/sets aliases** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style configurable /enchanter, /tinkerer, /alchemist, /withdrawsouls, GKits, /enchants, /enchant info** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style configurable /apply command** (Cosmic Enchants-style) — partial: internal WornState re-resolution; no user-facing command.
- **Cosmic Enchants-style permissions declared in plugin.yml with op defaults** (Cosmic Enchants-style) — partial: one node `starenchants.admin` default-op; no granular per-command permission tree or bypass perms.

### Crystals / modifiers / synergies / omni

- **Armor crystal (Modifier) item type** (Cosmic Enchants-style) — partial: redesigned as a first-class authored effect SOURCE; no NETHER_STAR Modifier item.
- **Per-crystal apply success chance ({CHANCE})** (Cosmic Enchants-style) — missing: apply-success is per-carrier, not per-crystal.
- **Crystal NBT identity (Modifier/Modifier-Chance keys)** (Cosmic Enchants-style) — missing: identity is stable keys in CombatState.crystals list.
- **Random vs fixed-chance crystal generation** (Cosmic Enchants-style) — missing: no crystal-item minting.
- **Crystal application via SWAP_WITH_CURSOR** (Cosmic Enchants-style) — partial: LEFT/RIGHT carrier gesture, not SWAP_WITH_CURSOR.
- **Apply guard: same crystal not already on item** (Cosmic Enchants-style) — missing: deliberately stacks (fixes last-of-type).
- **apply-multiple config: one-crystal-per-item cap** (Cosmic Enchants-style) — partial: hard cap 16, no per-item toggle.
- **Apply guard: armor-types target matching** (Cosmic Enchants-style) — partial: applies-to item groups instead of throwing ArmorTarget enum.
- **Apply guard: set-gating via require-sets** (Cosmic Enchants-style) — missing.
- **Apply guard: generic crystals reject set pieces** (Cosmic Enchants-style) — missing.
- **Settings.both: bypass set-gating** (Cosmic Enchants-style) — missing.
- **Crystal consumed on success and failure** (Cosmic Enchants-style) — partial: carrier consumed before roll; per-carrier not per-crystal.
- **Apply/fail chat messages with {CHANCE}** (Cosmic Enchants-style) — partial: hard-coded service strings, no {CHANCE}.
- **apply() writes per-effect-type NBT (<type>-modifier)** (Cosmic Enchants-style) — missing: deliberately eliminated.
- **Last-of-a-type-wins NBT collapse** (Cosmic Enchants-style) — missing: the central a Cosmic Enchants-style bug, explicitly fixed.
- **apply() set-membership compound + flag** (Cosmic Enchants-style) — missing.
- **Added-Lore appended on apply** (Cosmic Enchants-style) — missing: lore re-rendered from state.
- **Convert: crystal turns item into a set piece** (Cosmic Enchants-style) — missing.
- **Custom model data on crystal item** (Cosmic Enchants-style) — missing.
- **Crystals read only from worn armor pieces** (Cosmic Enchants-style) — partial: SE reads any worn/held item (unified source model).
- **Set of modifier-aware effects** (Cosmic Enchants-style) — missing: uniform source erasure, no allowlist.
- **DISABLE_ENCHANT/GROUP crystals are dead** (Cosmic Enchants-style) — missing: bug fixed (crystal-DISABLE now works).
- **Per-source chance gate on additive effects** (Cosmic Enchants-style) — missing: chance is per-ability.
- **Modifier removal (remove()) used by extractor** (Cosmic Enchants-style) — missing: apply-only, no removal.
- **Modifier loading from per-file YAMLs** (Cosmic Enchants-style) — partial: per-file load present; only 6 crystals, path-derived ids, no 132-file dance.
- **modifier <player> <id> — give crystal** (Cosmic Enchants-style) — missing: `/se crystal` self-applies only.
- **modifierlist — crystal browse GUI** (Cosmic Enchants-style) — missing.
- **Pairwise synergy crystal naming scheme** (Cosmic Enchants-style) — missing: rejected (ADR 0006); replacement unimplemented.
- **Synergy crystal = union of two sets** (Cosmic Enchants-style) — missing: planned combines:[a,b] unimplemented.
- **Identity-pair (self-synergy) crystals** (Cosmic Enchants-style) — missing.
- **Synergy 'Multi Crystal' presentation** (Cosmic Enchants-style) — missing.
- **Synergy effect DSL token set** (Cosmic Enchants-style) — missing.
- **Crystal Extractor item + extractor command** (Cosmic Enchants-style) — missing.
- **Extraction via SWAP_WITH_CURSOR / guards / random removal / returns crystal / consumed / no-revert** (Cosmic Enchants-style) — missing: no extraction at all.
- **OMNI Crystal item + omni command** (Cosmic Enchants-style) — missing: omni concept exists as a flag, no item/command/write path.
- **OMNI application via SWAP_WITH_CURSOR + guards + roll + consumption + success marker/lore** (Cosmic Enchants-style) — missing: no omni apply path.
- **OMNI per-set completion variant (single increment)** (Cosmic Enchants-style) — partial: unified resolver computes all sets in one pass.
- **OMNI piece counts as an armor piece** (Cosmic Enchants-style) — partial: counts by slot; no isArmorPiece NBT predicate, flag unwritable.
- **OMNI weapon grants no weapon set bonus** (Cosmic Enchants-style) — missing: no omni-weapon concept.
- **Heroic item tier (stronger-than-diamond variant)** (Cosmic Enchants-style) — partial: flat-stat triple runtime built and tested; no in-game producer.

### Heroic, crafting, crates

- **Heroic armor leather base / weapon gold base / configurable lore placeholders / default color** (Cosmic Enchants-style) — missing: heroic is stat-only, no item construction.
- **Heroic damage reduction (incoming)** (Cosmic Enchants-style) — partial: FLAT reduction in additive fold (ADR 0012), not percentage.
- **Heroic bonus outgoing weapon damage** (Cosmic Enchants-style) — partial: FLAT outgoing add, not percentage; live-tested.
- **Heroic durability protection (cancel item damage by chance)** (Cosmic Enchants-style) — missing: stat stored but inert, no consumer.
- **Heroic effects stack multiplicatively** (Cosmic Enchants-style) — missing: inverted to additive by design (ADR 0012).
- **convert() preserves meta when making heroic** (Cosmic Enchants-style) — missing: no producer.
- **Generic heroic upgrade item + {CHANCE} + random/fixed chance** (Cosmic Enchants-style) — missing.
- **Per-set Upgrade item + {%percent%} + color override** (Cosmic Enchants-style) — missing.
- **Apply per-set/generic/weapon upgrade via SWAP_WITH_CURSOR + roll + all guards/messages** (Cosmic Enchants-style) — missing: no upgrade subsystem.
- **heroic / upgrade commands** (Cosmic Enchants-style) — missing.
- **Spectrum set heroic-by-default** (Cosmic Enchants-style) — missing: no HEROIC_* material routing.
- **Custom crafting recipes (crafting.yml, ShapedRecipe) + all recipe sub-features** (Cosmic Enchants-style) — missing: in scope per ADR 0001 but unimplemented — a real gap.
- **Block using items as vanilla ingredients / shared NamespacedKey / regenerate-on-error** (Cosmic Enchants-style) — missing: no crafting subsystem.
- **Crates/lootboxes (crates.yml) + reward types/pool/open/animation/countdown/grant/broadcast/sounds/guard/give/reload** (Cosmic Enchants-style) — missing: in scope per ADR 0001 but unimplemented — a real gap.
- **Folia-hostile crate scheduling (parity note)** (Cosmic Enchants-style) — missing: moot (no crates).
- **GKits (timed god-kits)** (Cosmic Enchants-style) — missing: out of scope (ADR 0007).
- **Soul-gem crafting-grid anti-dupe** (Cosmic Enchants-style) — missing: no crafting-grid guard on soul gems.

### Integrations

- **WorldGuard region gating — central canRunEnchant umbrella** (Cosmic Enchants-style) — partial: single actor-location BUILD test, no EffectType→flag routing.
- **WorldGuard pvp/interact/block-break flag queries** (Cosmic Enchants-style) — partial: BUILD flag only; fail-open default inverted from Cosmic Enchants-style.
- **WorldGuard blacklisted-regions list** (Cosmic Enchants-style) — missing.
- **WorldGuard auto-flag execute-commands on enable** (Cosmic Enchants-style) — missing: native BUILD semantics, no global-region flag.
- **WorldGuard version detection v6/v7 (bundled wrapper)** (Cosmic Enchants-style) — missing: compiles against real WG API (ADR 0017).
- **WorldGuard per-effect re-checks on AoE block-break** (Cosmic Enchants-style) — missing: single gate-2 question.
- **WorldGuard support (Cosmic Enchants-style) — canPvP gate on offensive sets** (Cosmic Enchants-style) — partial: unified actor-location gate, not victim canPvP.
- **Factions multi-provider bridge / safezone / canUse / friendly suppression / Cosmic Enchants-style gating** (Cosmic Enchants-style) — missing: dropped (ADR 0001); would be a future ProtectionProvider add-on.
- **Towny support via FactionsBridge** (Cosmic Enchants-style) — missing.
- **Skyblock provider detection / friendly-fire / build-permission** (Cosmic Enchants-style) — missing.
- **Vault economy auto-detection** (Cosmic Enchants-style) — partial: first-party EconomyProvider via ServicesManager; no concrete Vault provider bundled; double amounts.
- **Enchanter menu money/XP purchase** (Cosmic Enchants-style) — missing.
- **STEAL_MONEY / STEAL_MONEY_PERCENT effect (Vault PvP transfer)** (Cosmic Enchants-style) — missing.
- **ItemsAdder item source for armor/weapons** (Cosmic Enchants-style) — missing.
- **EliteBosses BOSS_DAMAGE effect** (Cosmic Enchants-style) — missing.
- **ArmorEquip InventoryClick library** (Cosmic Enchants-style) — partial: native PlayerArmorChangeEvent instead of shaded Arnah lib.
- **PlaceholderAPI parity gap** (Cosmic Enchants-style) — partial: condition passthrough seam present, bridge unwired (test-only).
- **bStats metrics** (Cosmic Enchants-style) — missing.
- **FactionsBridge bundled multi-provider library** (Cosmic Enchants-style) — missing: dropped (ADR 0001).
- **Layered protection-gate flow** (Cosmic Enchants-style) — partial: single unified gate, only WG provides a provider.
- **Cosmic Enchants-style PlaceholderAPI expansion (a `<plugin>_*` namespace)** (Cosmic Enchants-style) — missing: expose-placeholders side unimplemented.
- **Cosmic Enchants-style PlaceholderAPI passthrough hook (relational)** (Cosmic Enchants-style) — partial: passthrough seam present, not production-wired, no relational placeholders.
- **Cosmic Enchants-style pluggable protection-handler SPI (canBreak/canAttack/isProtected)** (Cosmic Enchants-style) — partial: narrow allows(actor,where), not the triad.
- **Cosmic Enchants-style WorldGuard hook (BUILD/BLOCK_BREAK/region/mob-spawning)** (Cosmic Enchants-style) — partial: BUILD only.
- **Cosmic Enchants-style Factions / Towny / TownyChat / Lands / GriefPrevention / GriefDefender / ProtectionStones / Residence / SuperiorSkyblock2 hooks** (Cosmic Enchants-style) — missing: future ProtectionProvider add-ons.
- **Cosmic Enchants-style Vault permission / LuckPerms hooks** (Cosmic Enchants-style) — missing: economy-only SPI, no permission integration.
- **Cosmic Enchants-style ItemsAdder / Oraxen / SlimeFun / MythicMobs / mcMMO / AuraSkills / AdvancedSkills / AdvancedChests hooks** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style anticheat exemption hooks (AAC/Vulcan/Spartan/…)** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style vanish / hologram / ViaVersion / Geyser / DiscordSRV / Dynmap / BeaconsPlus3 / ProtocolLib+TAB hooks** (Cosmic Enchants-style) — missing.
- **Cosmic Enchants-style self/Cosmic Enchants-style API hook** (Cosmic Enchants-style) — partial: first-party se-api module exists, not a ServicesManager hook.
- **Cosmic Enchants-style hook-load ordering + success summary log** (Cosmic Enchants-style) — missing: one-shot ServicesManager read at enable.
- **Cosmic Enchants-style pluggable hook/trigger/target/effect SPI** (Cosmic Enchants-style) — partial: engine registration SPI present; integration-specific surface narrower.
- **Cosmic Enchants-style pluggable enchanter-payment SPI** (Cosmic Enchants-style) — missing: single first-party economy provider only.

### Cosmic Enchants-style-only feature systems

- **GKits (timed god-kit system) + all sub-features (drop chance, command-items, item building, cooldown tracking/bypass/reset/formatting, main GUI, placeholders, skull icons, hidden kits, fillers, open commands/sounds, preview GUI, in-game editor, public API)** (Cosmic Enchants-style) — missing: explicitly out of scope (ADR 0007).
- **Random world-chest / villager loot population + generation chance/cap/toggle/weighted-table/blacklist/separate-configs** (Cosmic Enchants-style) — missing: out of scope (ADR 0007).
- **Per-mob custom drop tables + item vocabulary / spawner exclusion / damage-cause gating** (Cosmic Enchants-style) — missing: out of scope (ADR 0007).
- **Custom weapons (named items) + default enchants random-range / bound abilities / requireSet / NBT identity** (Cosmic Enchants-style) — missing: not named in ADR 0007 but under its "engine-level only" decision.
- **Web enchantment marketplace (browse/download/upload/share/cache-refresh)** (Cosmic Enchants-style) — missing: out of scope (ADR 0001, no web server).
- **Premade setup import (config packs) + async download/backup** (Cosmic Enchants-style) — missing: out of scope; bundled default content instead.
- **Pluggable effect / trigger / target-selector SPI (a Cosmic Enchants-style register\* API)** (Cosmic Enchants-style) — partial: internal builder registration present; public runtime register\* not yet in se-api.
- **Pluggable enchanter-payment SPI** (Cosmic Enchants-style) — missing.
- **Repeating trigger + instant-apply / TTL resume queue / lifecycle management** (Cosmic Enchants-style) — missing: RepeatStore present but dead/unwired.
- **Command trigger** (Cosmic Enchants-style) — missing.
- **Totem / remove-health-totem / remove-health-damage-totem / revive / guard / steal-guard / invincible / keep-on-death effects** (Cosmic Enchants-style) — missing.
- **Double-damage / half-damage effects** (Cosmic Enchants-style) — missing: additive arbiter by design (ADR 0012).
- **Increase-damage / decrease-damage / negate-damage effects** (Cosmic Enchants-style) — partial: AddDamage/ReduceDamage/FlatReduce via additive fold; different mechanism.
- **Remove-health vs remove-health-damage (raw vs typed)** (Cosmic Enchants-style) — partial: HEALTH/DAMAGE exist; no armor-ignoring raw variant / 0.01 prime trick.
- **Ignore-armor-damage / ignore-armor-protection / disable-knockback / stop-knockback / pull-closer / pull-away / screen-freeze / freeze / shuffle-hotbar / reset-combo / bleed / blood / teleport-drops / remove-random-armor / auto-reel / set-catch-time / disable-activation / cancel-use effects** (Cosmic Enchants-style) — missing.
- **Snowblind effect** (Cosmic Enchants-style) — partial: equivalent behavior via CANCEL (no "snowblind" identity).
- **Steal-health effect** (Cosmic Enchants-style) — partial: composable DAMAGE+HEAL (shipped lifesteal); no atomic primitive.
- **Steal-exp effect** (Cosmic Enchants-style) — missing.
- **Steal-money effect** (Cosmic Enchants-style) — partial: composable TAKE_MONEY+GIVE_MONEY; no atomic transfer.
- **Drop-held-item effect** (Cosmic Enchants-style) — partial: folded into DISARM.

### Item & economy system

- **Enchant book item (EnchantBook)** (Cosmic Enchants-style) — partial: unified carrier ItemDef of kind book; no per-item EnchantmentSuccess/destroyRate NBT triple / placeholder system.
- **Book material follows group / {DESCRIPTION} expansion / dummy book / random-from-group** (Cosmic Enchants-style) — missing: tiers not groups; deterministic state-rendered lore; no random-pool mint.
- **Book apply: required-enchants / already-at-level / blacklisted-enchants gates** (Cosmic Enchants-style) — missing: re-apply is plain overwrite; no prerequisite/conflict checks.
- **Book apply: slot gate (non-upgrades)** (Cosmic Enchants-style) — partial: free-slot gate exists; BASE_SLOTS flat 6, added=0 always.
- **Permission-based rank success increase** (Cosmic Enchants-style) — missing.
- **Book apply: PreEnchantItemEvent chance override** (Cosmic Enchants-style) — missing.
- **Book apply: SUCCESS/DESTROY/FAIL branches** (Cosmic Enchants-style) — partial: three outcomes exist; single roll, no per-outcome events/particle/sound config.
- **remove-slot-when-fail** (Cosmic Enchants-style) — missing.
- **Book success/fail/destroy particles and sounds** (Cosmic Enchants-style) — missing: chat only.
- **Unopened (mystery) book + reveal firework** (Cosmic Enchants-style) — missing.
- **White Scroll item / removal helper** (Cosmic Enchants-style) — partial: protect-scroll carrier (boolean PDC guard) instead of per-scroll UUID item; no applied-lore line, no admin strip command.
- **Holy White Scroll item / gate / death-save / respawn return / combined removal** (Cosmic Enchants-style) — missing: no death-survival protection.
- **Black Scroll item / extract enchant / use-random-chance** (Cosmic Enchants-style) — missing.
- **Transmog Scroll / re-sort / applied-name suffix; Godly Transmog item / menu / interact lock** (Cosmic Enchants-style) — missing.
- **Randomizer Scroll item / reroll book success** (Cosmic Enchants-style) — missing.
- **Soul Gem item** (Cosmic Enchants-style) — partial: PDC gem (UUID+count) on any held item; no configured EMERALD item, soul-tier coloring, or rendered display.
- **Soul mode toggle** (Cosmic Enchants-style) — partial: via `/se soulmode` command, tracked by UUID; no right-click-air, no sound config.
- **Soul mode auto-deactivation** (Cosmic Enchants-style) — partial: QUIT only; no slot-click/drop/death deactivation.
- **Soul mode particle aura (SoulGemTask)** (Cosmic Enchants-style) — missing.
- **REMOVE_SOULS / DRAIN_SOULS_CONSTANT effects** (Cosmic Enchants-style) — missing.
- **Soul Gem combining (stacking) / /splitsouls** (Cosmic Enchants-style) — missing: per-gem identity by design.
- **Upgrade Orb item + apply/type-gate/chance/idempotent/bonus-slots/interact-lock** (Cosmic Enchants-style) — missing: SlotLedger.withAddedSlots arithmetic exists but unused.
- **Slot Gem item (+1 slot) + capacity gate** (Cosmic Enchants-style) — missing: "gem" kind is a content book.
- **Item Nametag item / rename via chat / blacklisted-words** (Cosmic Enchants-style) — missing.
- **Secret Dust / Omni-Secret Dust / Mystery Dust items** (Cosmic Enchants-style) — missing: only flat success-bonus dust (ADR 0019).
- **Magic Dust item** (Cosmic Enchants-style) — partial: success-bonus dust is the analog; not group-bound, no sound/particle.
- **Omni-Magic Dust item** (Cosmic Enchants-style) — partial: the single dust is already group-agnostic; no distinct marker/feedback.
- **Dust sound and particle feedback** (Cosmic Enchants-style) — missing: chat only.
- **customEnchantSlots capacity counter** (Cosmic Enchants-style) — partial: SlotLedger arithmetic + flat BASE_SLOTS=6; not persisted as a per-item PDC int with purchasable slots.
- **Slot consumption on new enchant** (Cosmic Enchants-style) — partial: new consumes, re-apply doesn't; used derived from count, no keepSlots path.
- **Slot add/minus/set/randomise primitives** (Cosmic Enchants-style) — partial: max/remaining/canApply/withApplied/withAddedSlots only; no minus/set/randomise.
- **Slots lore rendering / Randomise-slots mode / SlotsIgnore opt-out** (Cosmic Enchants-style) — missing.
- **Custom-enchant JSON storage (customEnchantList)** (Cosmic Enchants-style) — partial: replaced by versioned PDC blob; legacy-item read-compat (architecture §4.3) not implemented.
- **Lore/name FORMAT system (DEFAULT vs HYPIXEL)** (Cosmic Enchants-style) — missing: single fixed render format.
- **GiveItem / GiveDust commands** (Cosmic Enchants-style) — missing: most item types unimplemented.
- **Item identity NBT-API layer** (Cosmic Enchants-style) — partial: first-party PDC codecs (se:* keys), not shaded NBT-API.
- **ItemStackBuilder placeholder substitution** (Cosmic Enchants-style) — partial: legacy `&` translation + state-rendered lore; no placeholder maps/CMD/glow applied.
- **Global anti-grief ItemListener (right-click / block-place suppression)** (Cosmic Enchants-style) — missing.
- **SWAP_WITH_CURSOR drag-and-drop apply convention** (Cosmic Enchants-style) — partial: LEFT/RIGHT gesture in own inventory, not SWAP_WITH_CURSOR.
- **Vault economy hook (VaultController)** (Cosmic Enchants-style) — partial: first-party EconomyProvider SPI; no bundled Vault dep; double amounts.
- **STEAL_MONEY / STEAL_MONEY_PERCENT effect (Vault transfer)** (Cosmic Enchants-style) — missing.
- **Enchanter menu money/EXP purchase (Vault)** (Cosmic Enchants-style) — missing.
- **Tinkerer XP-bottle / secret-dust salvage** (Cosmic Enchants-style) — missing.
- **Alchemist book/dust combine economy** (Cosmic Enchants-style) — missing: ADR 0019 rejects rarity-tinkering.
- **Cosmic Enchants-style StatTrak tracker items** (Cosmic Enchants-style) — missing: out of scope (architecture §7).
- **Cosmic Enchants-style token economy (ObtainToken)** (Cosmic Enchants-style) — missing: money-only SPI.
- **Cosmic Enchants-style slot-increaser / rename-tag / scroll item line** (Cosmic Enchants-style) — missing: migrator covers enchants/sets only.

### Armor sets

- **Per-set config file model (one YAML = one set)** (Cosmic Enchants-style) — partial: file-per-set shape survives; parsed model fully redesigned (a single levelless ability).
- **13 bundled armor sets** (Cosmic Enchants-style) — partial: only 6 redesigned sets ship; original 13 named files absent.
- **Per-set resilient parse (one bad set survives)** (Cosmic Enchants-style) — partial: opposite — transactional reject-all on a malformed set (ADR 0014).
- **Armor set root metadata fields / Apply-Remove messages / sounds** (Cosmic Enchants-style) — missing: set is a triggered ability, no Armor metadata object.
- **Per-piece item definition sections + standard builder keys + CUSTOM_SKULL / HEROIC_ / LEATHER_ / ItemsAdder materials + vanilla/custom-enchant stamping + weapon hide-enchants + arbitrary NBT + Upgrade section** (Cosmic Enchants-style) — missing: SE never builds set items; membership is a stamped PDC setKey.
- **NBT identity tags armor-value / weapon-value** (Cosmic Enchants-style) — partial: setKey replaces armor-value; no separate weapon-value channel.
- **ArmorEquip detection backbone** (Cosmic Enchants-style) — partial: native PlayerArmorChangeEvent, not the synthetic-event lib.
- **Equip-source coverage** (Cosmic Enchants-style) — partial: delegated to PlayerArmorChangeEvent; no hand-rolled per-source matrix.
- **ArmorType suffix matching** (Cosmic Enchants-style) — missing: reads armor slots directly, matches by setKey.
- **Set apply / remove / join lifecycle** (Cosmic Enchants-style) — partial: declarative WornState refresh; no delay/message/sound/event/diff.
- **Per-player applied-set state map** (Cosmic Enchants-style) — partial: BitSet in immutable WornState, not List<String> map.
- **Block-place prevention for skull pieces** (Cosmic Enchants-style) — missing.
- **Reset-health-on-quit toggle** (Cosmic Enchants-style) — missing.
- **Set/weapon effect DSL — colon-delimited tokens** (Cosmic Enchants-style) — partial: terse form survives as the general unified DSL; legacy token set not reproduced.
- **Three-source effect resolution (set/weapon/modifier)** (Cosmic Enchants-style) — partial: unified to FIVE source kinds via erasure; no 3-source resolver/hasModifier lookup.
- **Weapon-effects fallback to worn-armor Effects** (Cosmic Enchants-style) — missing.
- **Accumulated chance gate on stacked effects** (Cosmic Enchants-style) — partial: per-ability chance, not summed-then-single-roll.
- **Multiplicative registration-order damage stacking** (Cosmic Enchants-style) — missing: inverted to additive fold (ADR 0012).
- **Reflection-based per-effect event hook (registerEvent)** (Cosmic Enchants-style) — missing: fixed Paper-native listener set.
- **Effect registry + reflective instantiation + effects GUI feed** (Cosmic Enchants-style) — partial: explicit registry + doc metadata; no effects-list GUI, no reflection.
- **Conditional effect registration by hooked plugin** (Cosmic Enchants-style) — missing: fixed list; Cosmic Enchants-style merged not hooked.
- **BOW_DAMAGE / BOW_REDUCTION effects** (Cosmic Enchants-style) — partial: composable from BOW/DEFENSE trigger + DAMAGE/REDUCE; no dedicated token, no arrow-only DEFENSE split.
- **BOSS_DAMAGE effect** (Cosmic Enchants-style) — missing.
- **BOMBER / BLESS / BUTCHER / COMMAND(equip) / DODGE / SMITE effects** (Cosmic Enchants-style) — partial: composable from primitives; no faithful named effect (and no equip/remove lifecycle for COMMAND).
- **DROPS / DURABILITY / FISH / GALAXY / HUNGER_LOSS / SNOWIFY / SUFFOCATE / WARP / WEB / HALLOWEEN_PUMPKIN effects** (Cosmic Enchants-style) — missing.
- **EXP effect (multiply XP drops)** (Cosmic Enchants-style) — partial: GIVE_EXP flat grant, not a multiplier.
- **FALL_DAMAGE effect** (Cosmic Enchants-style) — partial: composable as FALL trigger + CANCEL; no always-on negation token.
- **PARTICLE effect (shapes on apply/combat/constant)** (Cosmic Enchants-style) — partial: PARTICLE present + repeat knob; no shape library / apply phase.
- **Set/weapon damage handlers read held item** (Cosmic Enchants-style) — partial: held item contributes via WornState; no separate weapon-effects channel.
- **Heroic upgraded-gear variant (NBT markers)** (Cosmic Enchants-style) — partial: heroic is a first-class flat-stat SOURCE; legacy presence-only marker scheme replaced.
- **Heroic flat config stats (reduction/damage/durability)** (Cosmic Enchants-style) — partial: damage/reduction additive in fold; durability stored but inert.
- **Heroic reduction applies to all damage causes** (Cosmic Enchants-style) — partial: fold is cause-agnostic; all-cause coverage unverified.
- **Heroic diamond→gold/leather conversion** (Cosmic Enchants-style) — missing.
- **Per-set heroic upgrade application / generic upgrade / outcome messaging** (Cosmic Enchants-style) — missing.
- **Omni gem application (apply omni to plain gear)** (Cosmic Enchants-style) — partial: omni runtime + gem carrier exist; no shipped omni gem that stamps the flag.
- **Set give command with type codes** (Cosmic Enchants-style) — missing.
- **Set reload command (sets only)** (Cosmic Enchants-style) — partial: unified `/se reload`, not sets-only.
- **Set tab-completion and list (@sets)** (Cosmic Enchants-style) — missing.
- **Public a Cosmic Enchants-style armor API facade** (Cosmic Enchants-style) — partial: unified se-api exists; not the Cosmic Enchants-style-specific facade with item-building methods.
- **Delayed heavy set init (10-tick)** (Cosmic Enchants-style) — missing: replaced by enable-time snapshot (ADR 0014).

## Covered

Present features by category (counts + notable examples):

- **Enchant engine — effects DSL, triggers, conditions, pipeline (~20 present):** ATTACK/DEFENSE dual-fire on EDBE; MINE, INTERACT/INTERACT_LEFT/INTERACT_RIGHT, FALL, FIRE triggers wired; multi-trigger abilities (triggerMask); WAIT chaining (cumulative, fixes Cosmic Enchants-style overwrite bug); pipeline gates — type+level guard, chance roll, condition check, souls cost, cooldown arming; conditions isPlayerBlocking/Crouching/Flying/Health, isTargetHealth; per-level options (chance/souls/cooldown/condition/effects); effects DAMAGE_ARMOR, KNOCKBACK, KILL, POTION, CURE, FEED, FILL_OXYGEN, EXTINGUISH, IGNITE(FLAME), TNT, FIREBALL, LIGHTNING/STRIKE, EXPLODE, FLY, ADD_DURABILITY, ADD_DURABILITY_ITEM, REPAIR, CANCEL, MESSAGE(+ACTIONBAR/TITLE), SOUND, RUN_COMMAND; Cosmic Enchants-style `@Head{k=v}` selector grammar.
- **Crystals / modifiers / synergies / omni (5 present):** crystal effects stack additively with set+weapon+heroic into one fold; OMNI set-completion wildcard math ("the single most important correctness rule"); apply guards (single target only, unknown-modifier id rejected).
- **Armor sets (5 present):** configurable piece-count threshold (`pieces`); full-set detection over 4 armor slots by setKey; omni wildcard contributes to every set's count; isArmorPiece predicate (setKey OR omni); ATTACK vs DEFENSE damage-side semantics; DAMAGE / REDUCTION set effects (additive); DISABLE_ENCHANT / DISABLE_GROUP (interned-id suppression, now crystal-borne too); FLY / HEAL / POTION set effects.
- **Item & economy system (3 present):** book apply gates — amount>1, target-includable; destroy-on-fail toggle; White Scroll apply (protect against one destroy, consumed on save); soul consumption as enchant cost (gate 10).
- **Commands (6 present):** unified `/se reload [--dry-run]` (transactional off-thread swap) covering Cosmic Enchants-style responsibilities; `/se enchant <key> [level]` (the Cosmic Enchants-style addenchant + enchant commands); `/se menu` enchant browser GUI (paginated 6-row).
- **Integrations (1 present):** Cosmic Enchants-style enchant stamping — achieved by elimination (one unified engine, enchants on armor are first-class, no cross-plugin hook).
- **Cosmic Enchants-style-only feature systems (1 present):** DISARM effect.

## Recommended next increments

Highest-value gaps, prioritized (✱ = explicitly deferred in an ADR, so a conscious decision is required before building):

1. **Wire the declared-but-unwired triggers (DEATH, BOW_FIRE, FISHING, EAT, ITEM_DAMAGE, BREAK, HELD, PASSIVE) to Bukkit listeners.** The vocabulary, routing metadata, and WornState plumbing already exist; this unblocks a large class of content and the item-known overload with minimal new architecture.
2. **Activate the suppression effects (DISABLE_ENCHANTMENT/GROUP/TYPE).** The gate, stores, and interned suppressKeys already exist — only the effect kinds and a `suppress()` Sink intent are missing. High leverage for combat balance.
3. **Persist the slot capacity to PDC and ship the Slot/Upgrade item line.** `SlotLedger.withAddedSlots` arithmetic exists but `added=0` is hardcoded; wiring per-item `customEnchantSlots` + an upgrade-orb/slot-gem unlocks book-apply parity and several give commands.
4. **Implement the legacy item-NBT migration read-compat (architecture §4.3).** The migrator only reads YAML today; reading legacy `customEnchantList`/`customEnchantSlots` from existing items is the practical adoption blocker for real servers.
5. **Register the TELEPORT effect kind** (Sink intent already exists) and the small composable status effects (FREEZE/move-lock, BLEED DoT, PULL_CLOSER/PULL_AWAY) — cheap wins that close common Cosmic Enchants-style content needs.
6. **Add the `/se` reference/admin surface** (`effects`/`conditions`/`triggers`/`selectors`/`info`/`list`) — ADR-0013 already specifies it and `EffectSpec.doc()` metadata is in-engine; only the command wiring is missing. ✱(ADR 0013 intent unbuilt)
7. **Wire RepeatStore to a real REPEATING trigger** ✱ — the store and `repeatTicks` field exist but nothing arms the recurring task; needed for Cosmic Enchants-style RepeatingTrigger and any DRAIN/aura behavior.
8. **Production-wire the PlaceholderAPI bridge** — the condition passthrough seam exists but `papiResolver` is test-only; install a real bridge and (optionally) register an expansion to close the Cosmic Enchants-style PAPI parity gap.
9. **Add `contains`/`matchesregex` condition operators and inline `<chance>`/`<random>` effect tags** ✱ (ADR 0020 TODOs) — needed for faithful Cosmic Enchants-style config migration.

Deliberately deferred / out of scope (do not build without revisiting the ADR): GKits, StatTrak, loot/mob-drop tables, the web marketplace/panel, the merchant GUIs (Enchanter/Alchemist/Tinkerer), and the ~40 third-party integration hooks (Factions/Towny/Lands/mcMMO/anticheat/vanish/etc., intended as future ProtectionProvider/se-api add-ons). **Custom crafting recipes and crates are in scope per ADR 0001 but unimplemented — these are real, undeferred gaps, not exclusions.**

## Audit completeness — additional gaps the audit itself missed

This confirms a distinct missing feature: the Cosmic Enchants-style **grindstone listener strips custom enchants from the result item** (so a grindstone removes Cosmic Enchants-style custom enchants too, version-gated ≥1.14.1). The audit has "Enchant-table integration (weighted roll)" but no grindstone-strip line. This is a real subsystem/listener absent from the audit.

I now have enough findings. Let me compile the audit gaps.

## Audit gaps — legacy features/subsystems present in the analysis but NOT enumerated in the audit list

The audit is broad (effects, triggers, conditions, items, scrolls, dust, slots, menus, commands, armor sets, modifiers/crystals, heroic, crafting, crates, integrations/hooks, GKits, Cosmic Enchants-style deltas). But these concrete items from the local analysis docs (the catalog + per-subsystem reference notes) are **not represented by any audit line**:

1. **Cosmic Enchants-style anvil-control subsystem (`anvil.yml`, `handlers/anvil/`)** — a whole subsystem. Cosmic Enchants-style optionally "takes control of anvils" so custom (and vanilla) enchants combine in vanilla anvils, with `use-repair-cost`, `allow-illegal-vanilla/custom-enchant-combinations` toggles, per-vanilla-enchant max-level caps, and a configurable listener priority. The audit only has the Alchemist GUI "combine two same books" and `config.yml applying.anvil-application` is never checked. The vanilla-anvil takeover/combining engine is entirely absent.

2. **Cosmic Enchants-style GrindstoneListeners — custom-enchant strip on grindstone** (`enchants/listeners/GrindstoneListeners.java`, version-gated ≥ 1.14.1). On taking the grindstone RESULT slot it calls `removeEnchantments(item)`, stripping Cosmic Enchants-style custom enchants. The audit has the enchant-table roll but no grindstone behavior line at all (it's a distinct listener with its own version gate and Folia/inventory-thread implications).

3. **EnchantTableListener (the live event handler) and Cosmic Enchants-style enchant-table pricing/`enchantmentBooks`** — the audit's "Enchant-table integration (weighted roll)" covers a Cosmic Enchants-style original's `EnchantTableController` weighting, but misses: (a) the `EnchantTableListener` event-handling/registration itself as a feature, and (b) a Cosmic Enchants-style original's richer table model in `enchantmentTable.yml` — `prices` (custom XP pricing per tier), `enchantmentBooks` (give custom book instead of applying directly), and per-tier `enchantCount` ranges. The Cosmic Enchants-style table is a separate, more capable variant not enumerated.

4. **Enchant-application limitation / "unmodifiable item" gate** (`config.yml enchantLimitation`: lore-string OR NBT-tag that blocks custom enchants from being applied; Cosmic Enchants-style `combining.break-item/upgrade/use-chances` toggles). The audit has many per-book apply gates (amount, target, slot, blacklist, already-at-level) but no line for an external "this item is locked from modification" lore/NBT gate, nor for the Cosmic Enchants-style combining-on-item toggles (break-item, upgrade-if-higher, use-chances).

5. **Lore/name FORMAT system breadth** — the audit has one line "Lore/name FORMAT system (DEFAULT vs HYPIXEL)". But the analysis documents several distinct sub-features under it that each carry real rewrite risk and are arguably separate audit items: HYPIXEL 3-per-line compaction, `settings.lore-line` insertion index, `RomanNumeral` level rendering (1..3999 guard), the `{GROUP-COLOR}{ENCHANTMENT} {LEVEL}` placeholder style, and the per-message `Message`/lang enum (`lang.yml`) with its placeholder set. The lang/Message localization layer (and a Cosmic Enchants-style original's shipped ACF i18n `.properties` for ~18 locales) is not enumerated anywhere.

6. **NBT-API → PDC migration contract as an explicit deliverable** — the audit lists "Item identity NBT-API layer" and "Custom-enchant JSON storage (customEnchantList)" but does NOT enumerate the **legacy-key read-compat / migration contract** (the exact key set: `customEnchantList`, `customEnchantSlots`, `SlotsIgnore`, `AddedSlots`, `Transmog`, `GodlyTransmog`, `armor-value`, `weapon-value`, `omni-armor`, `modifiers`, `<type>-modifier`, `heroic-armor/weapon`, scroll/dust/orb/soul markers) that StarEnchants must read to import existing player items. This is a cross-cutting subsystem (config-and-migration), distinct from "storing enchants."

7. **bStats metrics + inherited NBT-API metrics/update-check/license behavior** — the audit's "bStats metrics — Cosmic Enchants-style own + inherited from shaded libs" exists, but the **inherited NBT-API bStats (id 1058) + update-check thread + package-relocation warnings**, and the Cosmic Enchants-style web-panel/license HTTP behavior, are operational side-effects the rewrite must explicitly suppress; they're documented in the local analysis notes (the item-data + catalog references) but not broken out as their own audit concern beyond the single metrics line.

8. **Cosmic Enchants-style soul withdraw/split economy granularity** — the audit has Cosmic Enchants-style `/splitsouls`, soul combine, `Cosmic Enchants-style setsouls`, and `Cosmic Enchants-style configurable /withdrawsouls command`, but not the **soul-amount tier-color rendering** (`{SOUL-COLOR}` by amount) or the SoulGem combine/split sound feedback (`BLOCK_ANVIL_USE`) as parity behaviors — minor, borderline (the core `/splitsouls` and combine are covered).

Lower-confidence / likely-already-implied (NOT counting as gaps): StatTrak trackers, keep-on-death, soulbound, web marketplace, mob drops, loot population, anticheat hooks — all present in the audit. GKits is present. The `Splodgebox watermark` and `upload|download` web stubs are present.

**Highest-confidence true gaps: #1 (Cosmic Enchants-style anvil-control), #2 (grindstone custom-enchant strip), #4 (unmodifiable-item / combining-on-item toggles), and #6 (the legacy-NBT migration key contract).** #3 and #5 are partially-covered surfaces where significant sub-features were collapsed into one line. The audit's effect/condition/trigger/item/command/set coverage is otherwise complete.
