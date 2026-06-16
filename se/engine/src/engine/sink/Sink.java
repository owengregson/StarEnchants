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
     * Add a flat damage <em>reduction</em> to the defense side of the fold (heroic flat
     * reduction, §6.1). Subtracted last, so it absorbs exactly this amount regardless of
     * percent context.
     */
    void addFlatReduction(double amount);

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

    /** Grant experience points to a player. */
    void giveExp(Player target, int amount);

    /** Knock {@code target} back, away from {@code from}, with the given strength. */
    void knockback(Entity target, Location from, double strength);

    void potion(LivingEntity target, int potionEffectId, int amplifier, int durationTicks);

    void removePotion(LivingEntity target, int potionEffectId);

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

    void blockChange(Location at, int blockDataId);

    void sound(Location at, int soundId, float volume, float pitch);

    void particle(Location at, int particleId, int count);

    // ── Player feedback ──

    void message(Player target, String message);

    void actionBar(Player target, String message);

    void consoleCommand(String command);

    // ── Event control ──

    /** Cancel the Bukkit event that triggered this activation. */
    void cancelEvent();
}
