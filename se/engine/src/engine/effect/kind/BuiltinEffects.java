package engine.effect.kind;

import engine.effect.EffectRegistry;

/**
 * The explicit, greppable list of every built-in effect kind (§7, §13.2) — no annotation scan, no generated
 * table, so adding an effect is one {@code .register(new ...)} line a reviewer reads top to bottom.
 */
public final class BuiltinEffects {

    private BuiltinEffects() {
    }

    public static EffectRegistry registry() {
        return EffectRegistry.builder()
                // Damage arbiter contributions + direct damage (§6.1).
                .register(new DamageEffect())
                .register(new DamageModEffect()) // §C canonical; replaces ADD_DAMAGE/REDUCE_DAMAGE/FLAT_DAMAGE/FLAT_REDUCE
                .register(new DamageScaleEffect()) // count-scaled fold contribution (KOTH Victorious: +N% per nearby player)
                // Entity intents.
                .register(new HealthModEffect()) // §C canonical MODIFY_HEALTH (give/take/transfer); replaces HEAL
                .register(new IgniteEffect())
                .register(new LightningEffect())
                .register(new TeleportEffect())
                .register(new TeleportBehindEffect()) // safe blink behind a reference (stellar Dimensional Shift)
                // Player feedback + event control.
                .register(new MessageEffect()) // §C canonical; channel chat/actionbar/title — replaces ACTIONBAR/TITLE
                .register(new RunCommandEffect())
                .register(new CancelEffect())
                // Handle-using kinds (resolved tokens, §9).
                .register(new PotionEffect())
                .register(new RemovePotionEffect())
                .register(new CureEffect())
                .register(new SoundEffect())
                .register(new ParticleEffect())
                .register(new ParticleRingEffect()) // shaped coloured dust: ring (KOTH Victorious crown aura)
                .register(new ParticleLineEffect()) // shaped coloured dust: line/tether to each target
                // Entity-state intents.
                .register(new KillEffect())
                .register(new ExtinguishEffect())
                .register(new FillOxygenEffect())
                .register(new DurabilityEffect()) // §C canonical; replaces ADD_DURABILITY/ADD_DURABILITY_ITEM/REPAIR/DAMAGE_ARMOR
                .register(new ExpEffect()) // §C canonical MODIFY_EXP (give/take/transfer); replaces GIVE_EXP
                .register(new FoodEffect()) // §C canonical MODIFY_FOOD (give/take); replaces FEED
                .register(new MoneyEffect()) // §C canonical MODIFY_MONEY (give/take/transfer); replaces GIVE_MONEY/TAKE_MONEY
                .register(new RemoveSoulsEffect()) // §D actor-only soul debit; charges the activator's active gem
                .register(new DisarmEffect())
                .register(new RemoveArmorEffect()) // Cosmic Enchants-style REMOVE_ARMOR: drop a random worn armour piece
                .register(new EquipSwapEffect()) // temporary armour swap (spooky Scarecrow pumpkin helmet)
                // World / spawn intents.
                .register(new ExplodeEffect())
                .register(new SpawnEntityEffect()) // §C canonical; replaces SPAWN/TNT (FIREBALL folded into PROJECTILE)
                .register(new SpawnSwarmEffect()) // ADR-0060 ring summon at chest height, vanilla AI + TTL (Bat pet)
                .register(new FallingBlockEffect()) // cosmetic falling-block grid (druid Terrablender grass rain)
                .register(new GuardEffect()) // § combat-flags GUARD: summon mob(s) targeting the attacker
                // Movement + vitals.
                .register(new VelocityEffect()) // §C canonical; replaces THROW/LAUNCH/KNOCKBACK
                .register(new WaterSpeedEffect()) // ADR-0060 worn water_movement_efficiency channel (Kraken pet)
                .register(new FreezeEffect()) // ADR-0065 frozen window: pinned freeze visual + DoT + slow (Ice Aspect)
                .register(new FlyEffect())
                .register(new FlyModeEffect()) // out-of-combat flight (supreme Gifted Child)
                .register(new HealthEffect())
                .register(new MaxHealthDrainEffect()) // timed overhealth drain (cupid Lovestruck)
                // §C block + item primitives.
                .register(new SetBlockEffect())
                .register(new BreakBlockEffect())
                .register(new WalkerEffect()) // §C new primitive: temporary revert-after-ticks platform
                .register(new TempBlockEffect()) // temp block shapes (yeti ice, fantasy cobweb, devil netherrack)
                .register(new DropItemEffect())
                .register(new GiveItemEffect())
                .register(new RemoveItemEffect())
                // §C spawn / visual primitives.
                .register(new FireworkEffectKind())
                .register(new ProjectileEffect())
                // §C temporary player-state primitives.
                .register(new MovementSpeedEffect())
                .register(new InvincibleEffect())
                // §A writable variables + § combat-flags.
                .register(new SetVarEffect()) // §A SET_VAR: per-player named var, read back as %name%
                .register(new MarkEffect()) // per-(victim, marker) damage mark (reaper Mark of the Reaper)
                .register(new InvertVarEffect()) // §A INVERT_VAR: numeric flip of a per-player named var
                .register(new IgnoreArmorEffect()) // § combat-flags IGNORE_ARMOR: hit bypasses armor/protection
                .register(new IgnoreHeroicEffect()) // ADR-0053 IGNORE_HEROIC: hit drops the victim's heroic reduction
                .register(new KnockbackControlEffect()) // § combat-flags KNOCKBACK_CONTROL: cancel/scale incoming knockback
                .register(new KeepOnDeathEffect()) // § combat-flags KEEP_ON_DEATH: keep items+levels on a death
                .register(new TeleblockEffect()) // § combat-flags TELEBLOCK: block a target from teleporting
                .register(new WardEffect()) // ADR-0053 WARD: typed timed guard flag (mob-target/invsee/near/splash-heal)
                .register(new ImmuneEffect()) // § combat-flags IMMUNE: timed immunity to a damage cause
                .register(new SuppressEffect()) // §C SUPPRESS: disable a target's enchant/group/type (DISABLE_*)
                .register(new SuppressImmuneEffect()) // maintained PASSIVE immunity to all suppression (dragon Dovahkiin)
                // Inline read-backs applied by the MINE / BOW_FIRE dispatchers (Cosmic Enchants-style SMELT/TELEPORT_DROPS/AUTO_LOCK).
                .register(new SmeltEffect())
                .register(new TeleportDropsEffect())
                .register(new SeekEffect())
                // Signature pack set-ability primitives. Order here is only for reading — a kind's head is its
                // identity (lookup is by head), so registration order carries no meaning; grouped for the reader.
                .register(new PotionLockEffect()) // strip + continuously deny a potion (druid/fantasy Speed lock)
                .register(new MarkZoneEffect()) // actor-owned area zone read by %victim.inzone% (devil hellfire)
                // EXP_GAIN read-back: scale the triggering PlayerExpChangeEvent's XP in the listener, never grant new XP.
                .register(new ExpMultiplyEffect())
                // ADR-0049 combat primitives: per-player marks the combat dispatcher reads at the fold-commit site,
                // plus the attacker-side echo re-proc. Order is only for reading (lookup is by head).
                .register(new ReflectEffect())    // Hex: reflect a portion of the target's outgoing damage back onto them
                .register(new WeakenEffect())     // Destruction: non-stacking outgoing-damage debuff on the target
                .register(new DamageCapEffect())  // Diminish: cap the wearer's next hit at a fraction of the last taken
                .register(new EchoStrikeEffect()) // Double Strike: re-run the attacker walk once over the same hit
                // ADR-0052 pet primitives (appended last; a kind's head is its identity).
                .register(new CageEffect())        // Cage pet: temp sealed cage + both parties teleported inside
                .register(new StripScrollEffect()) // Anubis pet: strip a White/Holy-White marker off victim gear
                .register(new DigHomeEffect())     // ADR-0061 Mole pet: service-owned dig/recall-home marker
                .register(new LightningModEffect()) // ADR-0063 worn lightning channel (Bolt crystal): no-op run
                // ADR-0071 reforge surfaces (appended in the pinned §2 head order; a kind's head is its
                // identity — order is only for reading).
                .register(new GravityWellEffect())   // Singularity: service-owned collapsing-star marker
                .register(new GrappleEffect())       // Leviathan's Reach: service-owned reel/zip marker
                .register(new BlinkEffect())         // Blink: farthest-open-cell forward teleport intent
                .register(new SwapPositionEffect())  // Castling: service-owned channel marker
                .register(new HitTempoEffect())      // Quickening Fang: halve the holder's effective hit window
                .register(new JavelinEffect())       // Javelin: service-owned particle-projectile marker
                .register(new BatteryEffect())       // Supernova Core: bank incoming, unload on the next hit
                .register(new DisarmShuffleEffect()) // The Unhanding: armed-window hotbar shuffle + self-malus
                .register(new ConvertSummonEffect()) // Summoner's Bell: flip enemy summons permanently
                .register(new TrapBreakEffect())     // Turnkey: early-restore confining structures intact
                // Cosmic-port wave 1d.2. APPENDED, never inserted: registration order fixes the dense kind
                // ids the compiler stamps (ADR-0039), so an insertion silently re-dispatches shipped content.
                .register(new PeriodicDamageEffect())    // actor-attributed burn, optionally converting a vanilla DoT
                .register(new DotAmplifyMarkEffect())    // multiply the target's incoming wither/poison ticks
                .register(new OutgoingDebuffEffect())    // WEAKEN + a cause filter + per-hit feedback
                .register(new DespawnEffect())           // silent mob removal: no drops, no XP, no death event
                .register(new ViewerHideEffect())        // per-viewer packet hide (armour included)
                .register(new ProjectileDressingEffect()) // rider on the BOW_FIRE projectile (inline read-back)
                .register(new HeadTrophyEffect())        // arm a templated player-head drop on the victim's next death
                .register(new SummonRebindEffect())      // replace an owned summon with a fresh upgraded one
                // Cosmic-port wave 2b. APPENDED, never inserted (ADR-0039 dense kind ids).
                .register(new SoulModeDisableEffect())   // force a player out of active soul mode
                .register(new SoulTransferEffect())      // steal souls from a victim's gems, credit a fraction
                .register(new ProcReboundEffect())       // Enchant Reflect: re-run an incoming proc with roles swapped
                // Cosmic-port wave 2d. APPENDED, never inserted (ADR-0039 dense kind ids).
                .register(new FacingSetEffect())         // Horrify: turn a body toward/away from a reference
                .register(new FallShieldEffect())        // one-shot fall cancel on an arbitrary player
                .register(new VulnerabilityEffect())     // Mark of the Beast: +% incoming from EVERY source
                .register(new SuppressIncomingEffect())  // masks: gate 5 read from the DEFENDER's end
                .register(new StatusClearEffect())       // lift a named engine window (teleblock/potion-lock/disarm)
                // Cosmic-port wave 2d.2. APPENDED, never inserted (ADR-0039 dense kind ids).
                .register(new StackingDotEffect())       // Rot and Decay: a ladder that burns only on the wearer's ground
                .register(new DelayedStrikeFieldEffect()) // Revenge of Yijki: telegraphed ground spots, struck as one
                .build();
    }
}
