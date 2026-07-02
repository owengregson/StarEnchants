package api.spi;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The curated mutation surface an {@link AddonEffect} emits through — a deliberately small subset of the
 * engine's internal {@code Sink} (docs/architecture.md §3.5, §3.6). An add-on effect never touches an
 * entity, block, or scheduler directly; it emits <em>intents</em> here, and the engine batches them and
 * flushes each on the correct Folia thread per the ability's declared {@link AddonAffinity}. That is what
 * makes an add-on Folia-correct by construction.
 *
 * <p>The full {@code Sink} has ~80 intents; this exposes the stable, broadly-useful ~16 and hides the
 * niche/experimental ones so the public contract stays small. Each method maps 1:1 to an engine intent
 * (named in its Javadoc) with identical semantics.
 *
 * <p><strong>Version-volatile ids.</strong> Potions, sounds, particles, materials, block data, and entity
 * types are passed as <em>interned ids</em>, not Bukkit enums — resolve them at compile time by declaring a
 * {@code HANDLE}-typed param ({@code D.sound()}, {@code D.material()}, …) and reading it via
 * {@link AddonEffectCtx#integer(String)}, so the runtime never sees a constant renamed across the version
 * range (§9). Only the "who/where" (entities and locations) are live Bukkit handles.
 */
public interface AddonSink {

    /** Add an outgoing-damage percentage to the additive attack bucket ({@code 0.25} = +25%) — engine {@code addOutgoingDamage} (DAMAGE_MOD). */
    void addOutgoingDamage(double percent);

    /** Add a damage-reduction percentage to the additive defense bucket — engine {@code addDamageReduction} (DAMAGE_MOD). */
    void addDamageReduction(double percent);

    /** Deal {@code amount} direct damage to {@code target} — engine {@code damage} (DAMAGE). */
    void damage(LivingEntity target, double amount);

    /** Apply an interned potion effect to {@code target} — engine {@code potion} (POTION). */
    void potion(LivingEntity target, int potionEffectId, int amplifier, int durationTicks);

    /** Strike {@code target} with a damaging bolt of lightning — engine {@code lightningAndDamage} (LIGHTNING). */
    void lightningAndDamage(LivingEntity target, double amount);

    /** Add to {@code target}'s velocity — engine {@code launch} (VELOCITY). */
    void launch(Entity target, double x, double y, double z);

    /** Teleport {@code target} to {@code to} — engine {@code teleport} (TELEPORT). */
    void teleport(Entity target, Location to);

    /** Play an interned sound at {@code at} — engine {@code sound} (SOUND). */
    void sound(Location at, int soundId, float volume, float pitch);

    /** Spawn {@code count} of an interned particle at {@code at} — engine {@code particle} (PARTICLE). */
    void particle(Location at, int particleId, int count);

    /**
     * Spawn {@code count} entities of an interned type at {@code at} — engine {@code spawnEntity}
     * (SPAWN_ENTITY). {@code ttlTicks > 0} auto-removes each after that many ticks; {@code health > 0} sets
     * each living spawn's max + current health.
     */
    void spawnEntity(Location at, int entityTypeId, int count, int ttlTicks, double health);

    /** Set the block at {@code at} to an interned block-data value — engine {@code blockChange} (SET_BLOCK). */
    void blockChange(Location at, int blockDataId);

    /** Give {@code count} of an interned material to {@code target}, dropping overflow at their feet — engine {@code giveItem} (GIVE_ITEM). */
    void giveItem(Player target, int materialId, int count);

    /** Grant {@code amount} experience points to {@code target} — engine {@code giveExp} (MODIFY_EXP give). */
    void giveExp(Player target, int amount);

    /** Send {@code target} a chat message — engine {@code message} (MESSAGE chat). */
    void message(Player target, String message);

    /** Show {@code target} an action-bar message — engine {@code actionBar} (MESSAGE actionbar). */
    void actionBar(Player target, String message);

    /** Cancel the Bukkit event that triggered this activation — engine {@code cancelEvent} (CANCEL). */
    void cancelEvent();
}
