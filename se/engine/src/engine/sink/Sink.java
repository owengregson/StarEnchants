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

    /** Add the attacker's heroic outgoing percent (§F, ADR-0021): a bounded multiplicative stage applied AFTER the additive fold, not summed into it. */
    void addHeroicOutgoing(double percent);

    /** Add the defender's heroic damage-reduction percent (§F): the multiplicative reduction stage. */
    void addHeroicReduction(double percent);

    // ── Entity intents (interned handle ids for version-volatile referents) ──

    void damage(LivingEntity target, double amount);

    void heal(LivingEntity target, double amount);

    /** Set the target's current health to {@code health} (clamped to [0, max]) — MODIFY_HEALTH's {@code set} mode. */
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

    /** Add to the target's maximum health (tracked + restored on unequip by the dispatcher). */
    void addMaxHealth(LivingEntity target, double amount);

    /**
     * Temporarily lower {@code target}'s max health by {@code fraction} of its "overhealth" (the amount above
     * {@code baseline}, e.g. 20) plus a flat {@code flat}, restoring exactly that delta after
     * {@code durationTicks} (MAX_HEALTH_DRAIN — cupid's Lovestruck). Overlap-safe (each drain restores its own
     * delta). A victim who logs out mid-window keeps the reduction until rejoin + next drain (no ledger).
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
     * {@code durationTicks} — a re-strip every tick until the window elapses (POTION_LOCK — druid's 5s Speed
     * lock on impact, fantasy's Speed lock while webbed). Unlike {@link #removePotion}, a re-application during
     * the window (drinking a potion, a beacon) is removed again next tick. The re-strip task self-cancels at the
     * window's end and stops early if the target leaves the world; a non-positive duration is a one-shot strip.
     */
    void potionLock(LivingEntity target, int potionEffectId, int durationTicks);

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

    void lightningAndDamage(LivingEntity target, double amount);

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
     * Spawn ONE falling block of an interned material at {@code at} that never drops an item or hurts entities
     * and is removed after {@code ttlTicks} — FALLING_BLOCK. When {@code owner} is non-null the block is bound
     * to an IMPACT cast carrying {@code carriedDamage}, so the landing listener fires {@code owner}'s
     * {@code IMPACT}-triggered abilities on whatever it lands on (druid's Terrablender; any "debris" reuses it).
     */
    void fallingBlock(Location at, int materialId, int ttlTicks, UUID owner, double carriedDamage);

    /**
     * Summon {@code count} guardian mobs of an interned type at {@code at}, each set to target
     * {@code target} (the attacker) if it is a mob (GUARD). {@code ttlTicks > 0} auto-removes each after
     * that many ticks; a non-blank {@code name} is shown above each. A targeted superset of
     * {@link #spawnEntity} — the spawn runs on {@code at}'s region and the target reference is captured on
     * the firing thread (only stored on the mob, never read cross-region).
     */
    void guard(LivingEntity target, Location at, int entityTypeId, int count, int ttlTicks, String name);

    /** Spawn an explosion at a location, optionally breaking blocks. */
    void explode(Location at, double power, boolean breakBlocks);

    /** Spawn a cosmetic firework at a location with the given flight power (FIREWORK). */
    void firework(Location at, int power);

    /** Launch {@code count} projectiles of an entity type from the shooter's eye at {@code speed} (PROJECTILE). */
    void launchProjectile(Player shooter, int entityTypeId, int count, double speed);

    void blockChange(Location at, int blockDataId);

    /** Break the block at {@code at}; {@code drops} controls whether it yields its drops (BREAK_BLOCK). */
    void breakBlock(Location at, boolean drops);

    /**
     * Lay a temporary platform of a material in the block layer beneath {@code center}, out to
     * {@code radius} blocks each way, then revert to the captured prior blocks after {@code durationTicks}
     * (WALKER). {@code replaceMode}: 0 = only air, 1 = air or liquid, 2 = anything. Best-effort revert
     * (no temp-block ledger): re-firing over a still-active platform may make a tile permanent.
     */
    void tempPlatform(Location center, int materialId, int radius, int durationTicks, int replaceMode);

    /**
     * Place ONE temporary block at {@code at} of an interned material, reverting to the captured prior block
     * after {@code durationTicks} — but only if the tile is still ours then (overlap-safe, no shared ledger).
     * {@code replaceMode}: 0 = air only, 1 = air/liquid, 2 = anything. The shape geometry lives in
     * {@code TEMP_BLOCK}; this is the per-position primitive. {@code unbreakable} is reserved (best-effort, not
     * yet guarded — a short-lived trap relies on its duration).
     */
    void tempBlock(Location at, int materialId, int durationTicks, int replaceMode, boolean unbreakable);

    /** Drop {@code count} of a material as an item entity at {@code at} (DROP_ITEM). */
    void dropItem(Location at, int materialId, int count);

    void sound(Location at, int soundId, float volume, float pitch);

    void particle(Location at, int particleId, int count);

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

    // ── Economy intents (routed to the global thread; a no-op without an economy provider) ──

    /** Deposit {@code amount} into the player's account (GIVE_MONEY). */
    void giveMoney(Player target, double amount);

    /** Withdraw up to {@code amount} from the player's account (TAKE_MONEY); best-effort if unaffordable. */
    void takeMoney(Player target, double amount);

    /**
     * Transfer {@code fraction} (0..1, clamped) of {@code from}'s CURRENT balance to {@code to}
     * (MODIFY_MONEY steal_percent). The balance-read, withdraw, and deposit happen atomically on the global
     * thread so no other money op interleaves; only the amount actually withdrawn is deposited. A no-op
     * without an economy provider, with a null party, or a non-positive fraction.
     */
    void stealMoneyPercent(Player from, Player to, double fraction);

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
     * victim's location, captured on the firing thread) owned by {@code owner}, active for {@code durationTicks}
     * (MARK_ZONE — devil's hellfire floor). Consulted by the {@code %victim.inzone%} fact so a separate ATTACK
     * bonus deals more to an enemy standing in it. A per-owner registry write, inline like {@link #mark} (no
     * entity hop): only the centre's primitives (world id, x, z) are read.
     */
    void markZone(Location center, UUID owner, double radius, int durationTicks);

    // ── Suppression intents (SUPPRESS_ENCHANT — disable an enchant/group/type for a player) ──

    /**
     * Suppress one of {@code target}'s ability scopes for {@code durationTicks} (SUPPRESS_ENCHANT,
     * covering DISABLE_ENCHANT/GROUP/TYPE). {@code scopeKind} is enchant(0)/group(1)/type(2) and
     * {@code scopeId} is the interned cooldown-scope id of the key — so the suppression is keyed by the
     * same scope id the gated abilities lower their scope to, and gate 5 matches it O(1). Player-only.
     */
    void suppress(Player target, int scopeKind, int scopeId, int durationTicks);

    /**
     * Make {@code target} immune to ALL suppression while {@code on}, or lift it (SUPPRESS_IMMUNE — dragon's
     * Dovahkiin): every {@link #suppress} aimed at them no-ops at the write. A maintained PASSIVE flag — armed
     * on equip, lifted on unequip by the HELD/PASSIVE lifecycle — so it can never leak. Player-only.
     */
    void suppressImmune(Player target, boolean on);

    // ── Event control ──

    /** Cancel the Bukkit event that triggered this activation. */
    void cancelEvent();

    /**
     * Ask the triggering hit to ignore the victim's armor (and enchant-protection) reduction
     * (IGNORE_ARMOR, § combat-flags). An inline read-back like {@link #cancelEvent()}: the combat
     * dispatcher zeroes the event's ARMOR/MAGIC damage modifiers after the fold. Inert on a non-combat
     * trigger (no damage event reads it).
     */
    void ignoreArmor();

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
     * {@code damageType} is 0=sword, 1=axe, 2=projectile, 3=potion(magic/poison/wither), 4=all. Like
     * {@link #controlKnockback}, the future damage is a SEPARATE Bukkit event, so this writes a per-player
     * timed flag a damage listener reads back and cancels matching hits. A non-positive duration is a no-op.
     */
    void immune(Player target, int damageType, int durationTicks);

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
