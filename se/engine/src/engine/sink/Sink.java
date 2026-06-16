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

    /** Add a flat amount to the damage fold (heroic flat stats, §6.1). */
    void addFlatDamage(double amount);

    // ── Entity intents (interned handle ids for version-volatile referents) ──

    void damage(LivingEntity target, double amount);

    void heal(LivingEntity target, double amount);

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
