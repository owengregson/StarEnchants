package engine.sink;

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

    void lightning(Location at);

    void spawn(Location at, int entityTypeId);

    /** Spawn primed TNT at a location. */
    void spawnTnt(Location at, int count);

    /** Spawn an explosion at a location, optionally breaking blocks. */
    void explode(Location at, double power, boolean breakBlocks);

    /** Launch a fireball from a player, with the given explosion yield. */
    void fireball(Player shooter, double yield);

    /** Spawn a cosmetic firework at a location with the given flight power (FIREWORK). */
    void firework(Location at, int power);

    /** Launch {@code count} projectiles of an entity type from the shooter's eye at {@code speed} (PROJECTILE). */
    void launchProjectile(Player shooter, int entityTypeId, int count, double speed);

    void blockChange(Location at, int blockDataId);

    /** Break the block at {@code at}; {@code drops} controls whether it yields its drops (BREAK_BLOCK). */
    void breakBlock(Location at, boolean drops);

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

    // ── Event control ──

    /** Cancel the Bukkit event that triggered this activation. */
    void cancelEvent();
}
