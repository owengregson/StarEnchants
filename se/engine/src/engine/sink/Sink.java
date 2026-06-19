package engine.sink;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The single mutation boundary (docs/architecture.md §3.5, §3.6). An
 * {@code EffectKind.run} never touches an entity, block, or scheduler directly — it
 * emits <em>intents</em> through this interface. The implementation accumulates them
 * into a per-event dispatch plan and flushes them batched, routed by the ability's
 * declared {@link compile.model.Affinity}, on the correct Folia thread.
 *
 * <p>Because the scheduler door is <em>removed</em> rather than discouraged, an
 * effect author cannot write a Folia bug: they never schedule and never mutate. A CI
 * lint enforces that nothing under {@code engine/effect} calls
 * {@code Bukkit.getScheduler()} or mutates an entity outside this interface (§3.5,
 * §8).
 *
 * <p>Version-volatile referents (potions, sounds, particles, materials, entity
 * types) are passed as <em>interned ids</em> resolved at compile time — the runtime
 * never sees a renamed constant (§9). Only the "who/where" (entities and locations,
 * pre-resolved by the selector) are Bukkit handles.
 */
public interface Sink {

    // ── Damage arbiter (§6.1): contribute deltas; never call event.setDamage ──

    /** Add an outgoing-damage percentage to the additive attack bucket (e.g. {@code 0.25} = +25%). */
    void addOutgoingDamage(double percent);

    /** Add a damage-reduction percentage to the additive defense bucket. */
    void addDamageReduction(double percent);

    /**
     * Add a flat damage bonus to the attack side of the fold (heroic flat damage, §6.1).
     * Delivered after the outgoing multiplier, so it is not inflated by the attacker's
     * own percent buffs.
     */
    void addFlatDamage(double amount);

    /**
     * Add a flat damage <em>reduction</em> to the defense side of the fold (a flat-reduction
     * effect, §6.1). Subtracted last, so it absorbs exactly this amount regardless of percent
     * context.
     */
    void addFlatReduction(double amount);

    /**
     * Add the attacker's heroic outgoing-damage percent (§F, ADR-0021): a distinct bounded
     * multiplicative stage applied AFTER the additive fold, not summed into it.
     */
    void addHeroicOutgoing(double percent);

    /** Add the defender's heroic damage-reduction percent (§F): the multiplicative reduction stage. */
    void addHeroicReduction(double percent);

    // ── Entity intents (interned handle ids for version-volatile referents) ──

    void damage(LivingEntity target, double amount);

    void heal(LivingEntity target, double amount);

    /** Instantly kill the target. */
    void kill(LivingEntity target);

    /** Clear the target's fire ticks. */
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

    /** Set a player's walk speed for {@code durationTicks}, then restore the vanilla default (MOVEMENT_SPEED). */
    void movementSpeed(Player target, double speed, int durationTicks);

    /** Make the target invulnerable for {@code durationTicks}, then restore (INVINCIBLE). */
    void invincible(LivingEntity target, int durationTicks);

    /** Add to the target's maximum health (tracked + restored on unequip by the dispatcher). */
    void addMaxHealth(LivingEntity target, double amount);

    /** Damage the durability of the target's worn armor. */
    void damageArmor(LivingEntity target, int amount);

    /** Restore durability to the player's worn armor; {@code amount < 0} fully repairs it. */
    void repairArmor(Player target, int amount);

    void potion(LivingEntity target, int potionEffectId, int amplifier, int durationTicks);

    void removePotion(LivingEntity target, int potionEffectId);

    /** Clear every active potion effect from the target (a full cleanse). */
    void cure(LivingEntity target);

    /** Drop the target's held (main-hand) item into the world, clearing the slot. */
    void disarm(LivingEntity target);

    void ignite(Entity target, int durationTicks);

    void lightningAndDamage(LivingEntity target, double amount);

    /** Add to the target's velocity. */
    void launch(Entity target, double x, double y, double z);

    void teleport(Entity target, Location to);

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

    /** Drop {@code count} of a material as an item entity at {@code at} (DROP_ITEM). */
    void dropItem(Location at, int materialId, int count);

    void sound(Location at, int soundId, float volume, float pitch);

    void particle(Location at, int particleId, int count);

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

    // ── Variable intents (per-player named vars, read back in later conditions as %name%) ──

    /** Set {@code target}'s named variable to {@code value}; {@code ttlTicks <= 0} = no expiry (SET_VAR). */
    void setVar(Player target, String name, String value, int ttlTicks);

    /** Numerically invert {@code target}'s named variable (0↔1), preserving its remaining TTL (INVERT_VAR). */
    void invertVar(Player target, String name);

    // ── Suppression intents (SUPPRESS_ENCHANT — disable an enchant/group/type for a player) ──

    /**
     * Suppress one of {@code target}'s ability scopes for {@code durationTicks} (SUPPRESS_ENCHANT,
     * covering DISABLE_ENCHANT/GROUP/TYPE). {@code scopeKind} is enchant(0)/group(1)/type(2) and
     * {@code scopeId} is the interned cooldown-scope id of the key — so the suppression is keyed by the
     * same scope id the gated abilities lower their scope to, and gate 5 matches it O(1). Player-only.
     */
    void suppress(Player target, int scopeKind, int scopeId, int durationTicks);

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
}
