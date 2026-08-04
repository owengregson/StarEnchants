package engine.sink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * A concrete {@link DispatchSinkBase} for the ADR-0071 movement-intent tests ({@code BlinkForwardTest},
 * {@code GrappleIntentTest}): the plan/gate/store machinery is the real base, while every era leaf is a
 * scriptable no-op. {@code isSafeDestination} is driven by an injected predicate (default: everything
 * standable); {@code teleportTo}, {@code dustDirect} and the interned {@code potionEffect} lookup are
 * recorded so a test can assert the landing cell, the mote fan-out and the reeled slow. Every other leaf is
 * an inert stub — none of these tests exercise health/durability/spawn paths.
 */
final class RecordingSink extends DispatchSinkBase {

    /** Cell → standable? Default: always safe. A test scripts a wall by returning false for its cells. */
    Predicate<Location> safe = loc -> true;
    /** The type {@link #potionEffect(int)} returns for any unmapped id (a reeled-enemy Slowness in GrappleIntentTest). */
    PotionEffectType slowType;
    /** Per-id potion types, so a loadout test can tell one entry of a summon's buffs from another. */
    final Map<Integer, PotionEffectType> potions = new HashMap<>();
    /** The type {@link #entityType(int)} returns for any id (the summon paths need a non-null one). */
    EntityType spawnType;
    /** The sound {@link #sound(int)} returns for any id; {@code null} (the default) makes every cue a no-op. */
    Sound cueSound;

    final List<Location> teleports = new ArrayList<>();
    final List<Location> dust = new ArrayList<>();
    /** Plan-free bursts (PERIODIC_DAMAGE's per-pulse cue), so a test can count them per pulse. */
    final List<Burst> bursts = new ArrayList<>();
    int safeChecks;

    record Burst(LivingEntity target, int particleId, int count) {
    }

    RecordingSink(SinkEnv env) {
        super(env);
    }

    // ── the leaves the movement intents actually reach ──
    @Override
    protected boolean isSafeDestination(Location dest, Location from) {
        safeChecks++;
        return safe.test(dest);
    }

    @Override
    protected void teleportTo(Entity target, Location dest) {
        teleports.add(dest);
    }

    @Override
    protected void dustDirect(Location at, int particleId, int r, int g, int b, float size, int count) {
        dust.add(at);
    }

    @Override
    protected void particleDirect(LivingEntity target, int particleId, int count, double spread) {
        bursts.add(new Burst(target, particleId, count));
    }

    @Override
    protected void particleDirect(Location at, int particleId, int count, double spread) {
        bursts.add(new Burst(null, particleId, count));
    }

    @Override
    protected PotionEffectType potionEffect(int id) {
        return potions.getOrDefault(id, slowType);
    }

    // ── inert leaves (never exercised by these tests) ──
    @Override
    protected Material material(int id) {
        return null;
    }

    @Override
    protected Sound sound(int id) {
        return cueSound;
    }

    @Override
    protected EntityType entityType(int id) {
        return spawnType;
    }

    @Override
    protected boolean isAir(Material material) {
        return true;
    }

    @Override
    protected boolean isItemMaterial(Material material) {
        return false;
    }

    /** Effective max health {@link #maxHealth} reports; a HEALTH_BOOST test drives it off the live amplifier. */
    java.util.function.ToDoubleFunction<LivingEntity> effectiveMaxHealth = entity -> 20.0;

    @Override
    protected double maxHealth(LivingEntity entity) {
        return effectiveMaxHealth.applyAsDouble(entity);
    }

    @Override
    protected boolean hasMaxHealthAttribute(LivingEntity entity) {
        return false;
    }

    @Override
    protected double maxHealthBase(LivingEntity entity) {
        return 20.0;
    }

    @Override
    protected void setMaxHealthBase(LivingEntity entity, double value) {
    }

    @Override
    protected void setWornMaxHealthModifier(Player player, double total) {
    }

    @Override
    protected void setWornWaterSpeedModifier(Player player, double total) {
    }

    @Override
    protected void addMaxHealthModifier(LivingEntity entity, UUID id, String name, double delta) {
    }

    @Override
    protected void removeMaxHealthModifier(LivingEntity entity, UUID id) {
    }

    @Override
    protected boolean freezeVisualStart(LivingEntity target) {
        return false;
    }

    @Override
    protected void freezePin(LivingEntity target) {
    }

    @Override
    protected void freezeVisualEnd(LivingEntity target) {
    }

    @Override
    protected void applyFrozenSlow(LivingEntity target, double slowPercent, boolean neutralizeFrostSlow) {
    }

    @Override
    protected void removeFrozenSlow(LivingEntity target) {
    }

    @Override
    protected void applySpawnHealth(LivingEntity entity, double health) {
    }

    @Override
    protected void applyInvulnerable(LivingEntity target, boolean invulnerable) {
    }

    @Override
    protected ItemStack mainHand(Player target) {
        return null;
    }

    @Override
    protected void setMainHand(Player target, ItemStack item) {
    }

    @Override
    protected ItemStack mainHand(EntityEquipment equipment) {
        return null;
    }

    @Override
    protected void setMainHand(EntityEquipment equipment, ItemStack item) {
    }

    @Override
    protected int itemDamage(ItemStack item) {
        return -1;
    }

    @Override
    protected void setItemDamage(ItemStack item, int damage) {
    }

    @Override
    protected void setGuardTarget(Entity spawned, LivingEntity target) {
    }

    @Override
    protected void applyNoAi(LivingEntity entity) {
    }

    @Override
    protected void applyBarShape(World world, int x, int y, int z,
                                 boolean north, boolean south, boolean east, boolean west) {
    }

    @Override
    protected void mountEntity(Entity vehicle, Entity passenger) {
    }

    @Override
    protected void applySaddle(LivingEntity entity) {
    }

    @Override
    protected void applySpawnSpeed(LivingEntity entity, double multiplier) {
    }

    @Override
    protected FallingBlock spawnFallingBlock(World world, Location loc, Material material) {
        return null;
    }

    @Override
    protected void doExplosion(World world, Location at, double power, boolean breakBlocks) {
    }

    // ── visual + player-feedback Sink methods the base leaves to the overlays ──
    @Override
    public void dust(Location at, int particleId, int r, int g, int b, float size, int count) {
        regionOp(at, () -> dustDirect(at, particleId, r, g, b, size, count)); // mirrors the overlay wrapper
    }

    @Override
    public void particle(Location at, int particleId, int count) {
    }

    @Override
    public void particle(Location at, int particleId, int count, int blockMaterialId,
                         double offsetX, double offsetY, double offsetZ) {
    }

    @Override
    public void particle(LivingEntity target, int particleId, int count, int blockMaterialId,
                         double offsetX, double offsetY, double offsetZ) {
    }

    @Override
    public void actionBar(Player target, String message) {
    }

    @Override
    public void title(Player target, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
    }
}
