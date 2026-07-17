package engine.sink;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The single mutation boundary (§3.5, §3.6). An {@code EffectKind.run} never touches an entity, block, or
 * scheduler directly — it emits <em>intents</em> here; the implementation batches them and flushes routed by
 * the ability's declared {@link compile.model.Affinity} on the correct Folia thread. Removing the scheduler
 * door (rather than discouraging it) makes Folia-correctness structural, not disciplinary (§8 CI lint).
 *
 * <p>Version-volatile referents (potions, sounds, particles, materials, entity types) are passed as
 * <em>interned ids</em> resolved at compile time, so the runtime never sees a renamed constant (§9). Only the
 * "who/where" (entities and locations, pre-resolved by the selector) are Bukkit handles.
 */
public interface Sink {

    // ── Damage arbiter (§6.1): contribute deltas; never call event.setDamage ──

    /** Add an outgoing-damage percentage to the additive attack bucket (e.g. {@code 0.25} = +25%). */
    void addOutgoingDamage(double percent);

    /** Add a damage-reduction percentage to the additive defense bucket. */
    void addDamageReduction(double percent);

    /** Add a flat damage bonus to the attack side (§6.1), applied after the outgoing multiplier so percent buffs don't inflate it. */
    void addFlatDamage(double amount);

    /** Add a flat damage reduction to the defense side (§6.1), subtracted last so it absorbs exactly this amount regardless of percent context. */
    void addFlatReduction(double amount);

    /** Add the victim's heroic percent reduction to its heroic-tagged bucket — behaves as {@link #addDamageReduction} unless the hit set {@link #ignoreHeroic()} (ADR-0053). */
    void addHeroicReduction(double percent);

    /** Add the victim's heroic flat reduction to its heroic-tagged bucket — behaves as {@link #addFlatReduction} unless the hit set {@link #ignoreHeroic()} (ADR-0053). */
    void addHeroicFlatReduction(double amount);

    // ── Entity intents (interned handle ids for version-volatile referents) ──

    /**
     * Deal {@code amount} damage to {@code target}, attributed to {@code attacker} when one is in scope
     * (ADR-0054). Routing: a zero-WAIT intent aimed at the declared {@linkplain SinkReadback#eventEntity
     * event entity} joins the damage fold — a same-hit rider: one hurt, one immunity window, so it can
     * never window-reject the attacker's next melee. Everything else defers to the target's own thread
     * as a real application; with a non-null attacker it fires an attributed
     * {@code EntityDamageByEntityEvent} (downstream plugins see who dealt it, and vanilla applies its
     * usual source-aware handling, knockback included), with none it stays the bare ownerless hurt. SE's
     * own combat pipeline never procs off either form (the {@link EngineDamage} frame).
     */
    void damage(LivingEntity target, double amount, LivingEntity attacker);

    /** {@link #damage(LivingEntity, double, LivingEntity)} with no attacker — the add-on SPI form. */
    default void damage(LivingEntity target, double amount) {
        damage(target, amount, null);
    }

    /**
     * Deal {@code percentOfMax}% of the TARGET's own maximum health as damage (DAMAGE {@code percent-of-max} —
     * ADR-0049 Natures Wrath), with {@link #damage(LivingEntity, double, LivingEntity)}'s same-hit-rider
     * routing and attribution. On the deferred path the max-health read + the damage both run on the
     * target's own region thread, so a cross-region victim is never read off-thread; on the fold path the
     * firing thread owns the event entity by definition (ADR-0051). A non-positive percent is a no-op.
     */
    void damagePercentOfMax(LivingEntity target, double percentOfMax, LivingEntity attacker);

    /** {@link #damagePercentOfMax(LivingEntity, double, LivingEntity)} with no attacker. */
    default void damagePercentOfMax(LivingEntity target, double percentOfMax) {
        damagePercentOfMax(target, percentOfMax, null);
    }

    /**
     * Raise the target's current health by {@code amount} (clamped to max). A write that lands after its
     * target died is dropped — the dead stay dead; only a zero-WAIT write to the firing event's own entity
     * precedes the kill decision (ADR-0051).
     */
    void heal(LivingEntity target, double amount);

    /**
     * Set the target's current health to {@code health} (clamped to [0, max]) — MODIFY_HEALTH's {@code set}
     * mode. Dropped on a target that died before it landed (ADR-0051), like {@link #heal}.
     */
    void setHealth(LivingEntity target, double health);

    void kill(LivingEntity target);

    void extinguish(LivingEntity target);

    /** Restore the target's air/oxygen to full. */
    void fillAir(LivingEntity target);

    /** Restore food points to a player (clamped to the vanilla maximum). */
    void feed(Player target, int foodPoints);

    /** Repair the player's held item; {@code amount < 0} fully repairs it. */
    void repairHand(Player target, int amount);

    /** Wear down the durability of the player's held item by {@code amount} (clamped to its maximum). */
    void damageHand(Player target, int amount);

    /** Grant experience points to a player. */
    void giveExp(Player target, int amount);

    /** Withdraw experience points from a player (clamped at zero). */
    void takeExp(Player target, int amount);

    /**
     * Move at most what {@code from} actually holds of {@code amount} experience points to {@code to}
     * (MODIFY_EXP transfer). The available-XP read and the debit happen atomically on {@code from}'s own
     * region thread, so nothing is credited that was not withdrawn — a victim with less XP than {@code amount}
     * mints no XP. A null party or a non-positive amount is a no-op.
     */
    void transferExp(Player from, Player to, int amount);

    /** Drain food points from a player (clamped at zero); the give counterpart is {@link #feed}. */
    void takeFood(Player target, int foodPoints);

    /** Knock {@code target} back, away from {@code from}, with the given strength. */
    void knockback(Entity target, Location from, double strength);

    /** Grant a player temporary flight for {@code durationTicks} ({@code < 0} = until cleared). */
    void setFlight(Player target, int durationTicks);

    /**
     * Allow or revoke a player's flight ability (FLY_MODE — supreme's out-of-combat fly), survival/adventure
     * only. {@code allow} grants the ABILITY to fly (not forced airborne, unlike {@link #setFlight}); not
     * {@code allow} stops + disallows it. Re-evaluated each REPEATING period against the combat tag.
     */
    void flyMode(Player target, boolean allow);

    /** Set a player's walk speed for {@code durationTicks}, then restore the vanilla default (MOVEMENT_SPEED). */
    void movementSpeed(Player target, double speed, int durationTicks);

    /** Make the target invulnerable for {@code durationTicks}, then restore (INVINCIBLE). */
    void invincible(LivingEntity target, int durationTicks);

    /** Add to the target's maximum health — a PERMANENT base shift (event-trigger HEALTH); worn HEALTH bonuses
     *  ride {@link #applyWornMaxHealth} instead. */
    void addMaxHealth(LivingEntity target, double amount);

    /**
     * Reconcile the ONE plugin-owned worn max-health modifier to {@code total} ({@code <= 0} removes it) —
     * the {@code HEALTH}-on-PASSIVE/HELD channel (ADR-0053 follow-up). SET-not-add, keyed by a fixed modifier
     * identity, so a crash/relog can never stack or strand a bonus: the next refresh reconciles whatever the
     * playerdata carried. Never touches the attribute BASE; never collides with the Overload potion pool or the
     * drain's own modifier (all distinct identities by design). Clamps current health down when the total shrinks.
     */
    void applyWornMaxHealth(Player target, double total);

    /**
     * Reconcile the ONE plugin-owned worn water-movement modifier to {@code total} ({@code <= 0} removes
     * it) — the {@code WATER_SPEED}-on-PASSIVE/HELD channel (ADR-0060), the exact
     * {@link #applyWornMaxHealth} contract on the {@code water_movement_efficiency} attribute. Clamped to
     * the attribute's [0,1] domain. A no-op where the attribute does not exist (pre-1.21 modern, 1.8.9).
     */
    void applyWornWaterSpeed(Player target, double total);

    /**
     * Temporarily lower {@code target}'s max health by {@code fraction} of its "overhealth" (its EFFECTIVE max
     * above {@code baseline}, e.g. 20) plus a flat {@code flat}, restoring it after {@code durationTicks}
     * (MAX_HEALTH_DRAIN — cupid's Lovestruck, grim's overhealth window). Overhealth is measured on the effective
     * max, so it takes hearts from ANY source — a HEALTH_BOOST potion (overload / nature crystal) or a named
     * "+hearts" attribute modifier (the worn HEALTH channel of {@link #applyWornMaxHealth}, santa-hat-style), not
     * just base shifts — via a temporary NEGATIVE max-health modifier (a distinct identity from the worn one, so
     * the two never collide and the reconciler never fights it; never a base write). Overlap-safe (a unique
     * modifier per drain, each removed on its own timer). Restored on quit too (F07): a logout mid-window drops
     * the modifier before the playerdata save, so it can neither be made permanent by logging out nor leak.
     */
    void drainMaxHealth(LivingEntity target, double fraction, double baseline, double flat, int durationTicks);

    /** Damage the durability of the target's worn armor. */
    void damageArmor(LivingEntity target, int amount);

    /** Restore durability to the player's worn armor; {@code amount < 0} fully repairs it. */
    void repairArmor(Player target, int amount);

    void potion(LivingEntity target, int potionEffectId, int amplifier, int durationTicks);

    void removePotion(LivingEntity target, int potionEffectId);

    /**
     * Strip an interned potion effect from {@code target} now and <em>continuously deny</em> it for
     * {@code durationTicks} (POTION_LOCK — druid's 5s Speed lock, fantasy's Speed-while-webbed, grim's overhealth
     * window). Unlike {@link #removePotion}, a re-application during the window (a passive driver re-asserting the
     * buff each tick, drinking a potion, a beacon) is DENIED: on modern the type is registered in
     * {@link LockedPotions} and {@code PotionLockListener} cancels the {@code EntityPotionEffectEvent} outright, so
     * the effect can never even flash in (the fix for "the buff comes back glitching then goes away" — a bare
     * re-strip always lost a tick to the driver). A per-tick re-strip backstops the guard and is the sole enforcer
     * on 1.8.9 (no such event). The tasks self-cancel at the window's end and stop early if the target leaves the
     * world; a non-positive duration is a one-shot strip with no lock window.
     */
    void potionLock(LivingEntity target, int potionEffectId, int durationTicks);

    /**
     * Freeze {@code target} for {@code durationTicks} (FREEZE — Ice Aspect, ADR-0065): freeze ticks pinned
     * at max (the vanilla powder-snow visual; Paper's freeze-tick lock where available, a per-tick re-pin on
     * the 1.17.1 floor and for mobs; a recorded no-op on 1.8.9), an attacker-attributed DoT of
     * {@code dotPerTick} every {@code dotPeriodTicks} (raw pre-armor half-hearts on the ADR-0054 deferred
     * path — never the fold, never attack-scaled), and a {@code slowPercent}% movement slow through the
     * plugin-owned MOVEMENT_SPEED modifier channel ({@code neutralizeFrostSlow} additionally cancels
     * vanilla's own -50%-at-full-freeze frost modifier so the authored percent is the real slow). A re-proc
     * REFRESHES the live window (deadline + attribution) — never a second DoT chain. Liveness-gated
     * (the dead stay dead, ADR-0051), quit-safe, disable-swept. Vanilla's own freeze self-damage is
     * cancelled for the window by the modern guard listener, so this DoT is the only damage source.
     */
    void freeze(LivingEntity target, int durationTicks, double dotPerTick, int dotPeriodTicks,
                double slowPercent, boolean neutralizeFrostSlow, LivingEntity attacker);

    /** Clear every active potion effect from the target. */
    void cure(LivingEntity target);

    /**
     * Clear only the active potion effects in one {@link PotionCategories} bucket from the target —
     * {@code 0}=all, {@code 1}=harmful, {@code 2}=beneficial, {@code 3}=neutral ({@code CURE { category }}).
     * Classification is by canonical name, so it is version-stable; category {@code 0} equals {@link #cure}.
     */
    void cureByCategory(LivingEntity target, int category);

    /** Drop the target's held (main-hand) item into the world, clearing the slot. */
    void disarm(LivingEntity target);

    /** Drop one random worn armour piece from the target into the world, clearing its slot (REMOVE_ARMOR). */
    void removeArmor(LivingEntity target);

    /**
     * Temporarily replace {@code target}'s armour piece at {@code slotIndex} ({@code getArmorContents} index:
     * 0=boots .. 3=helmet) with an interned {@code materialId}, restoring the original after
     * {@code durationTicks} (EQUIP_SWAP — spooky's pumpkin helmet). Death-/quit-safe via the {@link TempEquip}
     * ledger + its listener (the real piece drops / is kept, never the placeholder). Player-only.
     */
    void swapEquipment(Player target, int slotIndex, int materialId, int durationTicks);

    void ignite(Entity target, int durationTicks);

    /**
     * Strike the target with lightning and deal {@code amount} extra damage ({@code <= 0} = cosmetic bolt
     * only), the damage attributed to {@code attacker} when one is in scope (ADR-0054). Always a genuinely
     * separate application — a bolt is its own proc, never a same-hit rider, so it never joins the fold.
     */
    void lightningAndDamage(LivingEntity target, double amount, LivingEntity attacker);

    /** {@link #lightningAndDamage(LivingEntity, double, LivingEntity)} with no attacker — the add-on SPI form. */
    default void lightningAndDamage(LivingEntity target, double amount) {
        lightningAndDamage(target, amount, null);
    }

    /** Add to the target's velocity. */
    void launch(Entity target, double x, double y, double z);

    void teleport(Entity target, Location to);

    /**
     * Teleport {@code target} to {@code preferred} when it is a safe landing — room for a body and an
     * unobstructed line of sight from {@code sightFrom} — otherwise to {@code fallback} (e.g. on top of the
     * reference), or nowhere when {@code fallback} is null (TELEPORT_BEHIND). The block-safety reads run on
     * the target's own region thread; the version edge (modern raytrace / {@code isPassable} vs the 1.8
     * solidity check) lives in the overlays. A cross-region / unloaded read fails closed → the fallback.
     */
    void teleportSafe(Entity target, Location preferred, Location fallback, Location sightFrom);

    // ── World / block intents ──

    /**
     * Spawn {@code count} entities of an interned type at {@code at} (SPAWN_ENTITY), owned by {@code ownerId}
     * when non-null and the spawn is {@link org.bukkit.entity.Tameable} (a tamed, owned summon). {@code ttlTicks > 0}
     * auto-removes each after that many ticks; {@code health > 0} sets each living spawn's max + current health.
     * Replaces the old {@code spawn}/{@code spawnTnt} intents (a primed-TNT type auto-primes).
     */
    void spawnEntity(Location at, int entityTypeId, int count, int ttlTicks, double health, java.util.UUID ownerId);

    /** {@link #spawnEntity(Location, int, int, int, double, java.util.UUID)} with no owner. */
    default void spawnEntity(Location at, int entityTypeId, int count, int ttlTicks, double health) {
        spawnEntity(at, entityTypeId, count, ttlTicks, health, null);
    }

    /**
     * {@link #spawnEntity(Location, int, int, int, double, java.util.UUID)} plus per-summon behaviour
     * {@code flags} (ADR-0052): charge/no-AI/saddle happen inside the spawn op (spawns are fire-and-forget —
     * this is the only place the entity is reachable); {@code mountActivator} seats {@code activator};
     * tracked flags (no-target, hit-gated detonation) register in {@link PetSummons} for the summon-guard
     * listener. {@link SummonFlags#none()} flags route to the plain {@link #spawnEntity} path unchanged.
     */
    void spawnSummon(Location at, int entityTypeId, int count, int ttlTicks, double health, UUID ownerId,
                     Player activator, SummonFlags flags);

    /**
     * Summon {@code count} entities of an interned type evenly spaced on a {@code radius}-block ring
     * around {@code origin} raised {@code rise} blocks (the summoner's chest), each facing directly
     * outward, keeping VANILLA AI, auto-removed after {@code ttlTicks} (SPAWN_SWARM, ADR-0060).
     * {@code speedFraction < 1} slows each summon to that fraction of its vanilla steady-state AI speed
     * via a per-tick velocity damp on its own entity scheduler. Tracked in {@link SwarmSpawns} for the
     * disable sweep.
     */
    void spawnSwarm(Location origin, int entityTypeId, int count, double radius, double rise,
                    int ttlTicks, double speedFraction);

    /**
     * Spawn ONE falling block of an interned material at {@code at} that never drops an item or hurts entities
     * and is removed after {@code ttlTicks} — FALLING_BLOCK. When {@code owner} is non-null the block is bound
     * to an IMPACT cast carrying {@code carriedDamage}, so the landing listener fires {@code owner}'s
     * {@code IMPACT}-triggered abilities on {@code target} — the entity the grid was aimed at — when it lands
     * under it (druid's Terrablender; any "debris" reuses it).
     */
    void fallingBlock(Location at, int materialId, int ttlTicks, UUID owner, UUID target, double carriedDamage);

    /**
     * Summon {@code count} guardian mobs of an interned type at {@code at}, each set to target
     * {@code target} (the attacker) if it is a mob (GUARD). {@code ttlTicks > 0} auto-removes each after
     * that many ticks; a non-blank {@code name} is shown above each. A targeted superset of
     * {@link #spawnEntity} — the spawn runs on {@code at}'s region and the target reference is captured on
     * the firing thread (only stored on the mob, never read cross-region). When {@code owner} is non-null each
     * guard is bound to it in {@code GuardianCasts}, so a hit on the guard fires the owner's {@code GUARDIAN_HURT}
     * abilities (ADR-0049 Blood Link).
     */
    void guard(LivingEntity target, Location at, int entityTypeId, int count, int ttlTicks, String name, UUID owner);

    /** Spawn an explosion at a location, optionally breaking blocks. */
    void explode(Location at, double power, boolean breakBlocks);

    /** Spawn a cosmetic firework at a location with the given flight power (FIREWORK). */
    void firework(Location at, int power);

    /**
     * Launch {@code count} projectiles of an entity type from the shooter's eye at {@code speed} (PROJECTILE).
     * For an {@link org.bukkit.entity.Explosive} projectile, {@code explosiveYield >= 0} sets its blast yield and
     * {@code incendiary} sets whether the blast lights fires (ADR-0049 Hellfire); a negative yield keeps the
     * vanilla default and both are inert on a non-explosive projectile.
     */
    void launchProjectile(Player shooter, int entityTypeId, int count, double speed, double explosiveYield,
                          boolean incendiary);

    void blockChange(Location at, int blockDataId);

    /** Break the block at {@code at}; {@code drops} controls whether it yields its drops (BREAK_BLOCK). */
    void breakBlock(Location at, boolean drops);

    /**
     * Lay a temporary platform of a material in the block layer beneath {@code center}, out to
     * {@code radius} blocks each way, then revert after {@code durationTicks} (WALKER). {@code replaceMode}:
     * 0 = only air, 1 = air or liquid, 2 = anything. Each tile routes through the shared {@code TempBlockLedger}
     * (one revert per tile), so overlapping platforms/trails compound and every tile returns to its true
     * original — never stranded permanent.
     */
    void tempPlatform(Location center, int materialId, int radius, int durationTicks, int replaceMode);

    /**
     * Place ONE temporary block at {@code at} of an interned material, reverting after {@code durationTicks}
     * ({@code replaceMode}: 0 = air only, 1 = air/liquid, 2 = anything). Overlapping placements at one tile
     * stack as layers in the sink's shared {@code TempBlockLedger}, so the final revert restores the true
     * original rather than an intermediate temp block. The shape geometry lives in {@code TEMP_BLOCK}; this is
     * the per-position primitive. {@code unbreakable} is accepted-but-moot: the {@code TempBlockGuardListener}
     * guard now enforces unbreakability (and immovability) for EVERY tracked tile via the shared ledger, so a
     * temp block can no longer be mined for a free drop or displaced off its key regardless of this flag (F25/F26).
     */
    void tempBlock(Location at, int materialId, int durationTicks, int replaceMode, boolean unbreakable);

    /**
     * Fill a temporary {@code width × height × depth} box of an interned material, horizontally centred on
     * {@code center} with its base at the centre's block Y, reverting after {@code durationTicks}
     * ({@code TEMP_BLOCK} shape {@code BOX} — the Spider webs, ADR-0052). Same ledger/replace semantics as
     * {@link #tempBlock}; each tile re-keys to its own region (a box may straddle a Folia boundary).
     */
    void tempBox(Location center, int materialId, int width, int height, int depth, int durationTicks,
                 int replaceMode);

    /**
     * Build the temporary pet CAGE (ADR-0052): a {@code width × height × depth} AIR interior wrapped by an
     * iron-bars-style ring with a floor and roof plate, base-centred at {@code base}, reverting after
     * {@code durationTicks}; then teleport {@code first} and {@code second} to opposite interior cells,
     * facing each other. The FULL structure volume is safety-checked (every cell replaceable as air) on the
     * base's region before any tile is placed — an unsafe volume places nothing and teleports no one.
     */
    void cage(Location base, LivingEntity first, LivingEntity second, int floorMaterialId, int wallMaterialId,
              int roofMaterialId, int width, int height, int depth, int durationTicks);

    /**
     * Stamp the next cell of a walker's footprint SNAKE — the radius-0 {@code FOOTPRINT} trail (devil's
     * netherrack path). Unlike {@link #tempBlock}, which stamps one point, this joins the walker's previous
     * cell to {@code currentCell} with a 4-connected staircase so a sprint leaves no gaps and a diagonal reads
     * as clean L-steps, never corner-touching stamps. The path memory keyed {@code (trailKeyDefId, walker)}
     * rides the sink's shared {@code TrailWalker} so it survives across the REPEATING trigger's separate
     * activations; a teleport / death / relog / world change restarts at the current cell. Each staircase cell
     * ground-snaps to solid floor (climbing a one-block stair, pausing over air) and reverts after
     * {@code durationTicks} through the shared {@code TempBlockLedger}, each on its OWN region (a line may
     * straddle a Folia region boundary).
     */
    void tempBlockTrail(int trailKeyDefId, UUID walker, Location currentCell, int materialId, int durationTicks);

    /** Drop {@code count} of a material as an item entity at {@code at} (DROP_ITEM). */
    void dropItem(Location at, int materialId, int count);

    void sound(Location at, int soundId, float volume, float pitch);

    void particle(Location at, int particleId, int count);

    /**
     * Spawn {@code count} particles at {@code at} carrying an interned block material as BLOCK_CRACK/BLOCK_DUST
     * data (ADR-0049 Bleed's redstone crack). {@code blockMaterialId < 0} means no block data (a plain burst); a
     * material the resolved particle does not accept falls back to a plain burst in the overlay leaf.
     * {@code offsetX/Y/Z} are the per-axis spread (0 = a point burst — byte-identical to the old zero-offset call).
     */
    void particle(Location at, int particleId, int count, int blockMaterialId,
                  double offsetX, double offsetY, double offsetZ);

    /**
     * Spawn {@code count} particles at {@code target}'s MID-BODY (its feet + half its height), read AT DISPATCH time
     * on the target's region thread (deferred-safe, like {@link #potion}), with optional block data
     * ({@code blockMaterialId < 0} = none) and the per-axis {@code offsetX/Y/Z} spread. The PARTICLE {@code who}-slot
     * path (ADR-0049) — a per-target burst that lands where each target actually is.
     */
    void particle(LivingEntity target, int particleId, int count, int blockMaterialId,
                  double offsetX, double offsetY, double offsetZ);

    /**
     * Draw {@code count} coloured-dust motes at a single point — the per-point primitive for the shaped-particle
     * effects (PARTICLE_RING / PARTICLE_LINE / TETHER). {@code r}/{@code g}/{@code b} are 0-255; {@code size}
     * scales the mote. The version edge (modern {@code Particle.DustOptions} vs the 1.8 redstone offset-RGB
     * trick) lives in the overlay impls; a non-dust resolved particle falls back to a plain burst.
     */
    void dust(Location at, int particleId, int r, int g, int b, float size, int count);

    // ── Player inventory intents ──

    /** Give {@code count} of a material to the player, dropping any overflow at their feet (GIVE_ITEM). */
    void giveItem(Player target, int materialId, int count);

    /** Remove up to {@code count} of a material from the player's inventory (REMOVE_ITEM). */
    void removeItem(Player target, int materialId, int count);

    // ── Player feedback ──

    void message(Player target, String message);

    void actionBar(Player target, String message);

    /** Show a title + subtitle to a player with the given fade-in / stay / fade-out timings (ticks). */
    void title(Player target, String title, String subtitle, int fadeIn, int stay, int fadeOut);

    void consoleCommand(String command);

    /**
     * Run {@code command} as {@code actor} ({@link Player#performCommand}) on the actor's own thread — the
     * player-side counterpart to {@link #consoleCommand} (RUN_COMMAND {@code as: player}). Unlike the console
     * path it is an actor intent, so it routes to the actor's entity thread; a null actor is a no-op.
     */
    void playerCommand(Player actor, String command);

    // ── Economy intents (routed to the global thread; a no-op without an economy provider) ──

    /** Deposit {@code amount} into the player's account (GIVE_MONEY). */
    void giveMoney(Player target, double amount);

    /** Withdraw up to {@code amount} from the player's account (TAKE_MONEY); best-effort if unaffordable. */
    void takeMoney(Player target, double amount);

    /**
     * Move at most what {@code from} actually holds of {@code amount} to {@code to} (MODIFY_MONEY transfer).
     * The balance-read, withdraw, and deposit happen atomically on the global thread, so nothing is credited
     * that was not charged — a victim who cannot pay mints nothing. A no-op without an economy provider, with
     * a null party, or a non-positive amount.
     */
    void transferMoney(Player from, Player to, double amount);

    /**
     * Transfer {@code fraction} (0..1, clamped) of {@code from}'s CURRENT balance to {@code to}
     * (MODIFY_MONEY steal_percent). The balance-read, withdraw, and deposit happen atomically on the global
     * thread so no other money op interleaves; only the amount actually withdrawn is deposited. A no-op
     * without an economy provider, with a null party, or a non-positive fraction.
     */
    void stealMoneyPercent(Player from, Player to, double fraction);

    /**
     * Deposit {@code fraction} (0..1, clamped) of the target's CURRENT balance back to the SAME target
     * (MODIFY_MONEY {@code interest_percent} — the Fish pet's income, ADR-0052). Mints new money (never a
     * transfer); one deposit is ceilinged by the live env {@code moneyInterestCap} ({@code <= 0} = uncapped).
     * The balance-read and deposit happen atomically on the global thread. A no-op without an economy
     * provider or a non-positive fraction.
     */
    void interestMoneyPercent(Player target, double fraction);

    /**
     * Remove one White/Holy-White protection marker from a random protected piece of the target's worn
     * armour (+ held item when {@code includeHand}) — {@code STRIP_SCROLL} (ADR-0052 Anubis). Runs on the
     * TARGET's own region thread; the strip goes through the env's {@link GearProtection} seam (marker
     * release + lore recompose) and the mutated stack is written back to the equipment slot. A target with
     * no protected piece is a no-op.
     */
    void stripScroll(LivingEntity target, boolean holy, boolean includeHand);

    // ── Soul intents (actor-only; debited from the activator's active gem, no-op without a soul system) ──

    /**
     * Debit {@code amount} souls from {@code holder}'s active gem {@code gemId} (REMOVE_SOULS). Souls bind
     * to the activator, so there is no target — the holder is the constant end. Routed to the holder's own
     * thread (the durable PDC write-through is region-bound to where the gem sits); a no-op without a soul
     * system, a non-positive amount, or a gem that is not the seeded active one.
     */
    void removeSouls(Player holder, UUID gemId, int amount);

    /**
     * Debit {@code amount} souls from {@code target}'s OWN active gem (REMOVE_SOULS with a victim target —
     * drain the enemy's souls). Resolves the target's gem from the soul-mode store inside the debit
     * collaborator; a no-op if the target is not in soul mode or there is no soul system. Routed to the
     * target's own thread (the gem's PDC write-through is region-bound there).
     */
    void removeSoulsFrom(Player target, int amount);

    // ── Variable intents (per-player named vars, read back in later conditions as %name%) ──

    /** Set {@code target}'s named variable to {@code value}; {@code ttlTicks <= 0} = no expiry (SET_VAR). */
    void setVar(Player target, String name, String value, int ttlTicks);

    /** Numerically invert {@code target}'s named variable (0↔1), preserving its remaining TTL (INVERT_VAR). */
    void invertVar(Player target, String name);

    /**
     * Mark {@code victim} so {@code marker} deals an extra {@code percent}% outgoing damage to them for
     * {@code durationTicks} (MARK — reaper's Mark of the Reaper); consulted by the damage fold on the marker's
     * later hits. A per-(victim, marker) flag write, inline like {@link #setVar} (no entity hop).
     */
    void mark(LivingEntity victim, UUID marker, double percent, int durationTicks);

    /**
     * Register a wearer-owned area zone — a cylinder of {@code radius} blocks centred on {@code center} (the
     * victim's location, captured on the firing thread) owned by {@code owner}, laid under {@code victim}
     * (nullable), active for {@code durationTicks} (MARK_ZONE — devil's hellfire floor). Consulted by the
     * {@code %victim.inzone%} fact so a separate ATTACK bonus deals more to an enemy standing in it, and by the
     * hellfire magma-immunity — which is scoped to {@code victim} only. A per-owner registry write, inline like
     * {@link #mark} (no entity hop): only the centre's primitives (world id, x, z) are read.
     */
    void markZone(Location center, UUID owner, UUID victim, double radius, int durationTicks);

    // ── Suppression intents (SUPPRESS_ENCHANT — disable an enchant/group/type for a player) ──

    /**
     * Suppress one of {@code target}'s ability scopes for {@code durationTicks} (SUPPRESS_ENCHANT,
     * covering DISABLE_ENCHANT/GROUP/TYPE). {@code scopeKind} is {@code compile.model.ScopeKinds}
     * enchant(0)/group(1)/type(2) and
     * {@code scopeId} is the interned cooldown-scope id of the key — so the suppression is keyed by the
     * same scope id the gated abilities lower their scope to, and gate 5 matches it O(1). Player-only.
     */
    void suppress(Player target, int scopeKind, int scopeId, int durationTicks);

    /**
     * As {@link #suppress(Player, int, int, int)} but attributed to the emitting ability (ADR-0045: {@code /se
     * why} names the suppressor). The default drops the attribution for sinks that do not record it.
     */
    default void suppress(Player target, int scopeKind, int scopeId, int durationTicks, int byDefId) {
        suppress(target, scopeKind, scopeId, durationTicks);
    }

    /**
     * As {@link #suppress(Player, int, int, int, int)} but selecting the mode (ADR-0049 SUPPRESS {@code mode}):
     * {@code nextHit == false} is the timed DISABLE_* window ({@code durationTicks}); {@code nextHit == true} arms
     * a one-shot (Neutralize) that suppresses the target's scope for their next {@code charges} incoming hits, then
     * clears — consumed by the combat dispatcher after each hit, never by time. The default routes to the timed form.
     */
    default void suppress(Player target, int scopeKind, int scopeId, int durationTicks, int byDefId,
                          boolean nextHit, int charges) {
        suppress(target, scopeKind, scopeId, durationTicks, byDefId);
    }

    /**
     * Set {@code target}'s suppression-immunity CHANCE in {@code [0,100]} (SUPPRESS_IMMUNE — dragon's Dovahkiin;
     * {@code 0} lifts it): each {@link #suppress} aimed at them rolls it, so {@code 100} no-ops every suppression
     * and a lower value ignores that fraction (ADR-0034). A maintained PASSIVE flag — armed on equip, lifted on
     * unequip by the HELD/PASSIVE lifecycle — so it can never leak. Player-only.
     */
    void suppressImmune(Player target, int chance);

    // ── ADR-0049 combat marks (per-player windows read by the combat dispatcher at the fold-commit site) ──

    /**
     * Mark {@code afflicted} so {@code percent}% of THEIR outgoing damage reflects back onto them for
     * {@code durationTicks} (REFLECT — Hex). A per-player window write, inline like {@link #mark} (the UUID is
     * captured on the firing thread, no entity hop); consulted when {@code afflicted} is the attacker on a later hit.
     */
    void reflectMark(Player afflicted, double percent, int durationTicks);

    /**
     * Debuff {@code target}'s outgoing damage by {@code percent}% for {@code durationTicks} (WEAKEN — Destruction),
     * NON-STACKING (stronger percent / later expiry). A per-player window write, inline like {@link #mark};
     * consulted on {@code target}'s later attack side.
     */
    void weaken(Player target, double percent, int durationTicks);

    /**
     * Arm a one-shot damage cap for {@code target} (DAMAGE_CAP — Diminish): the target's next incoming hit within
     * {@code durationTicks} is capped at {@code factor} × their LAST-taken damage, and if {@code reflectOverflow}
     * the excess is dealt back to the attacker. The cap value is computed AT ARM time from the last-taken history
     * (no history arms nothing). A per-player window write, inline like {@link #mark}.
     */
    void armDamageCap(Player target, double factor, boolean reflectOverflow, int durationTicks);

    /**
     * Request one extra pass of the attacker-side activation walk over the same hit (ECHO_STRIKE — Double Strike):
     * the dispatcher re-runs the walk exactly once and folds both passes into the ONE event commit. An inline
     * read-back like {@link #cancelEvent()}; a second request during the echo pass is ignored (no third pass).
     */
    void requestEchoStrike();

    // ── Event control ──

    /** Cancel the Bukkit event that triggered this activation. */
    void cancelEvent();

    /** Scale the XP of the triggering PlayerExpChangeEvent (EXP_GAIN). Accumulates multiplicatively. */
    void multiplyExp(double factor);

    /**
     * Ask the triggering hit to ignore the victim's armor (and enchant-protection) reduction
     * (IGNORE_ARMOR, § combat-flags). An inline read-back like {@link #cancelEvent()}: the combat
     * dispatcher zeroes the event's ARMOR/MAGIC damage modifiers after the fold. Inert on a non-combat
     * trigger (no damage event reads it).
     */
    void ignoreArmor();

    /**
     * Ask the triggering hit to drop the victim's HEROIC reduction contributions from the damage fold
     * (IGNORE_HEROIC — Midas, ADR-0053). An inline read-back like {@link #ignoreArmor()}, honoured at the
     * fold commit; attacker-side heroic weapon damage is untouched. Inert on a non-combat trigger.
     */
    void ignoreHeroic();

    /**
     * Scale {@code victim}'s incoming knockback for {@code ttlTicks} (KNOCKBACK_CONTROL, § combat-flags):
     * {@code multiplier <= 0} cancels it, {@code 0.5} halves it, {@code 2} doubles it. Unlike
     * {@link #ignoreArmor()}, the knockback is a SEPARATE Bukkit event fired the same tick as the hit, so
     * this cannot be an inline read-back — it writes a short-TTL per-victim flag a knockback listener reads.
     * Inert on a server with no knockback event. The clamp at zero and TTL live in the store.
     */
    void controlKnockback(LivingEntity victim, double multiplier, int ttlTicks);

    /**
     * Arm "keep items + levels on death" for {@code target} for {@code ttlTicks} (KEEP_ON_DEATH,
     * § combat-flags). Like {@link #controlKnockback}, the death is a SEPARATE Bukkit event, so this writes
     * a short-TTL per-player flag a death listener reads. Author on REPEATING (TTL &ge; the period) for an
     * always-on keep while worn, since the engine has no unequip teardown. A non-positive TTL is a no-op.
     */
    void keepOnDeath(Player target, int ttlTicks);

    /**
     * Block {@code target} from teleporting (ender-pearl / chorus-fruit) for {@code durationTicks} (TELEBLOCK,
     * § combat-flags). Like {@link #controlKnockback}, the launch/teleport is a SEPARATE Bukkit event, so this
     * writes a per-player timed flag a teleport listener reads back. A non-positive duration is a no-op.
     */
    void teleblock(Player target, int durationTicks);

    /**
     * Make {@code target} immune to a damage cause for {@code durationTicks} (IMMUNE, § combat-flags):
     * {@code damageType} is 0=sword, 1=axe, 2=projectile, 3=potion(magic/poison/wither), 4=all,
     * 5=fishhook (rod-bobber knockback, ADR-0053). Like
     * {@link #controlKnockback}, the future damage is a SEPARATE Bukkit event, so this writes a per-player
     * timed flag a damage listener reads back and cancels matching hits. A non-positive duration is a no-op.
     */
    void immune(Player target, int damageType, int durationTicks);

    /**
     * Ward {@code target} with typed flag {@code wardType} for {@code durationTicks}, carrying {@code amount}
     * (WARD, ADR-0053): {@code wardType} is 0=mob-target, 1=invsee, 2=near, 3=splash-heal. Like
     * {@link #teleblock}, the guarded action is a SEPARATE Bukkit event, so this writes a per-player timed
     * flag a feature guard reads back. A non-positive duration is a no-op.
     */
    void ward(Player target, int wardType, int durationTicks, double amount);

    /**
     * Ask the triggering block-break (MINE) to auto-smelt the broken block (SMELT): an inline read-back like
     * {@link #ignoreArmor()}, applied by the MINE dispatcher (which drops the smelted result and suppresses
     * the raw drop). Inert outside a block-break.
     */
    void smelt();

    /**
     * Ask the triggering block-break (MINE) to send the block's drops straight to the breaker's inventory
     * (TELEPORT_DROPS): an inline read-back applied by the MINE dispatcher. Inert outside a block-break.
     */
    void teleportDrops();

    /**
     * Ask the triggering bow-shot (BOW_FIRE) to make the fired projectile home onto the nearest line-of-sight
     * target (AUTO_LOCK): an inline read-back applied by the bow dispatcher, which starts a per-projectile
     * steering task. Inert outside a bow shot.
     */
    void seek();
}
