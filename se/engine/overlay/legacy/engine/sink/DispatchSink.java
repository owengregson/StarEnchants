package engine.sink;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Field;
import java.util.Objects;
import net.minecraft.server.v1_8_R3.ChatComponentText;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.GenericAttributes;
import net.minecraft.server.v1_8_R3.PacketPlayOutChat;
import net.minecraft.server.v1_8_R3.PacketPlayOutTitle;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldParticles;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import platform.caps.Regions;
import platform.resolve.RenameResolvers;
import platform.sched.Scheduling;
import platform.text.Colors;
import schema.spec.HandleCategory;

/**
 * The legacy (1.8.9 / {@code v1_8_R3}) concrete {@link Sink} — the era leaf over {@link DispatchSinkBase} and
 * the same-FQN counterpart to the {@code overlay/modern} impl. The plan/batching, {@link engine.interact.DamageFold}
 * bookkeeping, the stores, and every era-neutral intent body live in the shared base (ADR-0036); this leaf swaps
 * the modern Bukkit API for CraftBukkit 1.8.8 + NMS where 1.8 lacks the floor surface
 * (docs/legacy-1.8.9-codeshare-design.md §3.5).
 *
 * <p>Two interned-id surfaces diverge from modern. First, the ctor takes {@link RenameResolvers} (which can
 * turn an interned id back into its 1.8-era <em>name</em>) instead of {@code RuntimeHandles} (modern-only —
 * it casts to {@code Particle}/{@code Attribute}, neither of which exists on 1.8). Second, an id is resolved
 * to a live value here: name via {@link RenameResolvers#nameOf}, then a 1.8 lookup ({@code Material.getMaterial},
 * {@code PotionEffectType.getByName}, {@code Sound.valueOf}, …). A miss yields {@code null} and that one intent
 * is silently skipped, exactly as modern (the §9 compile-time warn already fired).
 *
 * <p>1.8 gaps handled below: no Bukkit {@code Particle} (NMS {@code PacketPlayOutWorldParticles}); no
 * {@code attribute} package ({@code setMaxHealth} / NMS {@code GenericAttributes}); no
 * {@code meta.Damageable} (durability is on the {@code ItemStack} itself); no off-hand (main hand only); no
 * {@code teleportAsync} (synchronous {@code teleport}); no {@code spigot()}/Adventure (NMS chat/title packets);
 * no {@code Entity.setInvulnerable} (NMS {@code invulnerable} field).
 */
public final class DispatchSink extends DispatchSinkBase {

    private static final Logger LOG = System.getLogger("StarEnchants.Sink");

    /** The 1.8 NMS {@code Entity.invulnerable} flag is private; cache the reflective handle once. */
    private static volatile Field nmsInvulnerableField;

    private final RenameResolvers resolvers;

    /**
     * Sharing the stores (via the per-boot {@link SinkEnv}) is what makes the KNOCKBACK_CONTROL / KEEP_ON_DEATH /
     * TELEBLOCK / IMMUNE flags a hit writes visible to the separate knockback / death / teleport / damage events'
     * listeners.
     */
    public DispatchSink(RenameResolvers resolvers, SinkEnv env) {
        super(env);
        this.resolvers = Objects.requireNonNull(resolvers, "resolvers");
    }

    // ── Interned-id resolution (1.8: name via RenameResolvers, then a 1.8 lookup) ─────────────────

    /** The 1.8 {@link Material} for an interned material id, or {@code null} on a miss. */
    @Override
    protected Material material(int id) {
        String name = resolvers.nameOf(HandleCategory.MATERIAL, id);
        return name == null ? null : Material.getMaterial(name);
    }

    /** The 1.8 {@link Sound} for an interned sound id, or {@code null} on a miss / unknown enum constant. */
    @Override
    protected Sound sound(int id) {
        String name = resolvers.nameOf(HandleCategory.SOUND, id);
        if (name == null) {
            return null;
        }
        try {
            return Sound.valueOf(name); // 1.8 Sound is a plain enum
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** The 1.8 {@link PotionEffectType} for an interned id, or {@code null} on a miss. */
    @Override
    protected PotionEffectType potionEffect(int id) {
        String name = resolvers.nameOf(HandleCategory.POTION_EFFECT, id);
        return name == null ? null : PotionEffectType.getByName(name);
    }

    /** The 1.8 {@link EntityType} for an interned id, by enum then by lowercase name, or {@code null}. */
    @Override
    @SuppressWarnings("deprecation") // fromName(String): the 1.8 legacy entity-name lookup.
    protected EntityType entityType(int id) {
        String name = resolvers.nameOf(HandleCategory.ENTITY_TYPE, id);
        if (name == null) {
            return null;
        }
        try {
            return EntityType.valueOf(name);
        } catch (IllegalArgumentException notEnum) {
            return EntityType.fromName(name.toLowerCase()); // legacy entity name form
        }
    }

    /** The 1.8 {@link Enchantment} for an interned id, or {@code null} on a miss (currently unused by any intent). */
    @SuppressWarnings("unused")
    private Enchantment enchantment(int id) {
        String name = resolvers.nameOf(HandleCategory.ENCHANTMENT, id);
        return name == null ? null : Enchantment.getByName(name);
    }

    @Override
    protected boolean isAir(Material material) {
        return material == Material.AIR; // 1.8 has only the one air variant
    }

    @Override
    protected boolean isItemMaterial(Material material) {
        return material != Material.AIR; // 1.8 has no Material#isItem — approximate as "not air"
    }

    // ── Entity / health leaves (1.8: NMS GenericAttributes / invulnerable field) ──────────────────

    @Override
    protected void applyInvulnerable(LivingEntity target, boolean invulnerable) {
        setNmsInvulnerable(target, invulnerable);
    }

    @Override
    public void addMaxHealth(LivingEntity target, double amount) {
        // 1.8: shift the base max-health value directly; unequip restoration of this delta lands with WornState.
        entityOp(target, () -> {
            net.minecraft.server.v1_8_R3.AttributeInstance maxHealth = maxHealthInstance(target);
            if (maxHealth != null) {
                maxHealth.setValue(Math.max(1.0, maxHealth.getValue() + amount));
            }
        });
    }

    @Override
    public void drainMaxHealth(LivingEntity target, double fraction, double baseline, double flat, int durationTicks) {
        entityOp(target, () -> {
            net.minecraft.server.v1_8_R3.AttributeInstance maxHealth = maxHealthInstance(target);
            if (maxHealth == null) {
                return;
            }
            double overhealth = maxHealth.getValue() - baseline;
            double drain = overhealth * fraction + flat;
            if (drain <= 0) {
                return; // no overhealth to take
            }
            double newValue = Math.max(1.0, maxHealth.getValue() - drain);
            double removed = maxHealth.getValue() - newValue; // exact delta (also when the clamp bit)
            maxHealth.setValue(newValue);
            if (target.getHealth() > newValue) {
                target.setHealth(newValue); // clamp current down to the new cap
            }
            if (durationTicks > 0) {
                Scheduling.onEntityLater(target, durationTicks, () -> {
                    net.minecraft.server.v1_8_R3.AttributeInstance mh = maxHealthInstance(target);
                    if (mh != null) {
                        mh.setValue(mh.getValue() + removed); // add back exactly what was drained
                    }
                });
            }
        });
    }

    /** The NMS max-health attribute instance for {@code entity}, or {@code null}. */
    private static net.minecraft.server.v1_8_R3.AttributeInstance maxHealthInstance(LivingEntity entity) {
        if (!(entity instanceof CraftLivingEntity)) {
            return null;
        }
        return ((CraftLivingEntity) entity).getHandle().getAttributeInstance(GenericAttributes.maxHealth);
    }

    /** The entity's maximum health (attribute on 1.8 lives behind the Damageable accessor). */
    @Override
    @SuppressWarnings("deprecation") // getMaxHealth: the 1.8 accessor.
    protected double maxHealth(LivingEntity entity) {
        net.minecraft.server.v1_8_R3.AttributeInstance maxHealth = maxHealthInstance(entity);
        return maxHealth != null ? maxHealth.getValue() : entity.getMaxHealth();
    }

    @Override
    @SuppressWarnings("deprecation") // setMaxHealth/getMaxHealth: the 1.8 accessors.
    protected void applySpawnHealth(LivingEntity entity, double health) {
        net.minecraft.server.v1_8_R3.AttributeInstance maxHealth = maxHealthInstance(entity);
        if (maxHealth != null) {
            maxHealth.setValue(health);
        } else {
            entity.setMaxHealth(health);
        }
        entity.setHealth(Math.min(health, maxHealth(entity)));
    }

    /** Flip the NMS {@code Entity.invulnerable} flag (1.8 has no public {@code setInvulnerable}). */
    private static void setNmsInvulnerable(LivingEntity entity, boolean invulnerable) {
        if (!(entity instanceof CraftLivingEntity)) {
            return;
        }
        net.minecraft.server.v1_8_R3.Entity handle = ((CraftLivingEntity) entity).getHandle();
        try {
            invulnerableField(handle).setBoolean(handle, invulnerable);
        } catch (ReflectiveOperationException unreachable) {
            LOG.log(Level.WARNING, "could not set 1.8 NMS invulnerable flag", unreachable);
        }
    }

    private static Field invulnerableField(net.minecraft.server.v1_8_R3.Entity handle)
            throws NoSuchFieldException {
        Field cached = nmsInvulnerableField;
        if (cached != null) {
            return cached;
        }
        // Declared on net.minecraft.server.Entity; walk the chain in case of a relocated handle subtype.
        for (Class<?> type = handle.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field found = type.getDeclaredField("invulnerable");
                found.setAccessible(true);
                nmsInvulnerableField = found; // stable for the JVM's lifetime
                return found;
            } catch (NoSuchFieldException keepWalking) {
                // on a superclass
            }
        }
        throw new NoSuchFieldException("invulnerable");
    }

    // ── Teleport leaves ──────────────────────────────────────────────────────────────────────────

    @Override
    protected void teleportTo(Entity target, Location dest) {
        // 1.8: no teleportAsync — synchronous teleport, already on the target's own thread via Scheduling.
        target.teleport(dest);
    }

    /** 1.8 room check: feet + head must be non-solid. No LOS ray (BlockIterator) — {@code from} is ignored. */
    @Override
    protected boolean isSafeDestination(Location dest, Location from) {
        // Cold teleport path — a cross-region / unloaded read is not provably safe → caller uses the fallback.
        return Regions.read("DispatchSink.isSafeDestination", () -> {
            if (dest.getWorld() == null) {
                return false;
            }
            Block feet = dest.getBlock();
            Block head = feet.getRelative(0, 1, 0);
            return !feet.getType().isSolid() && !head.getType().isSolid();
        }, false);
    }

    // ── Inventory / durability leaves (1.8: main hand only + durability on the ItemStack) ─────────

    @Override
    protected ItemStack mainHand(Player target) {
        return target.getInventory().getItemInHand(); // 1.8: main hand only
    }

    @Override
    protected void setMainHand(Player target, ItemStack item) {
        target.getInventory().setItemInHand(item);
    }

    @Override
    protected ItemStack mainHand(EntityEquipment equipment) {
        return equipment.getItemInHand(); // 1.8 main hand
    }

    @Override
    protected void setMainHand(EntityEquipment equipment, ItemStack item) {
        equipment.setItemInHand(item);
    }

    @Override
    protected boolean applyRepair(ItemStack item, int amount) {
        if (item == null || !isDamageable(item)) {
            return false;
        }
        short current = item.getDurability();
        short repaired = amount < 0 ? 0 : (short) Math.max(0, current - amount);
        item.setDurability(repaired);
        return true;
    }

    @Override
    protected boolean applyDamage(ItemStack item, int amount) {
        if (item == null || amount <= 0 || !isDamageable(item)) {
            return false;
        }
        short worn = (short) Math.min(item.getType().getMaxDurability(), item.getDurability() + amount);
        item.setDurability(worn);
        return true;
    }

    @Override
    protected void adjustArmorDurability(LivingEntity entity, int amount, boolean repair) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        ItemStack[] armor = equipment.getArmorContents();
        boolean changed = false;
        for (ItemStack piece : armor) {
            if (piece == null || !isDamageable(piece)) {
                continue;
            }
            short current = piece.getDurability();
            short next;
            if (repair) {
                next = amount < 0 ? 0 : (short) Math.max(0, current - amount);
            } else {
                next = (short) Math.min(piece.getType().getMaxDurability(), current + amount);
            }
            piece.setDurability(next);
            changed = true;
        }
        if (changed) {
            equipment.setArmorContents(armor);
        }
    }

    /** 1.8: durability lives on the {@code ItemStack}; a positive max durability means the item wears. */
    private static boolean isDamageable(ItemStack item) {
        return item.getType().getMaxDurability() > 0;
    }

    // ── World / spawn leaves ───────────────────────────────────────────────────────────────────

    @Override
    protected void setGuardTarget(Entity spawned, LivingEntity target) {
        if (spawned instanceof Creature) {
            // 1.8: the targeting interface is Creature (no Mob). setTarget only stores the reference;
            // the AI runs on the mob's own (spawn) region, so this is not a cross-region read.
            ((Creature) spawned).setTarget(target);
        }
    }

    @Override
    @SuppressWarnings("deprecation") // spawnFallingBlock(Location, Material, byte): the 1.8 falling-block spawn.
    protected FallingBlock spawnFallingBlock(World world, Location loc, Material material) {
        return world.spawnFallingBlock(loc, material, (byte) 0);
    }

    @Override
    protected void doExplosion(World world, Location at, double power, boolean breakBlocks) {
        // 1.8: createExplosion(loc, power, fire) has no block-break flag; the coord overload
        // (x,y,z,power,fire,breakBlocks) does, so route through it to honour breakBlocks.
        world.createExplosion(at.getX(), at.getY(), at.getZ(), (float) power, false, breakBlocks);
    }

    // ── Particle / dust leaves (1.8: NMS PacketPlayOutWorldParticles, no Bukkit Particle) ─────────

    @Override
    public void particle(Location at, int particleId, int count) {
        // 1.8: no Bukkit Particle — spawn via the NMS particle packet sent to players in the same world.
        regionOp(at, () -> {
            EnumParticle resolved = particle(particleId);
            World world = at.getWorld();
            if (resolved == null || world == null) {
                return;
            }
            PacketPlayOutWorldParticles packet = new PacketPlayOutWorldParticles(
                    resolved, true,
                    (float) at.getX(), (float) at.getY(), (float) at.getZ(),
                    0f, 0f, 0f, // no offset spread
                    0f,         // particle data/speed
                    Math.max(1, count));
            for (Player viewer : world.getPlayers()) {
                if (viewer.getLocation().distanceSquared(at) <= 64 * 64) { // vanilla long-distance cutoff
                    sendPacket(viewer, packet);
                }
            }
        });
    }

    @Override
    public void dust(Location at, int particleId, int r, int g, int b, float size, int count) {
        // 1.8 has no DustOptions: the redstone particle's colour rides the packet OFFSET as r/g/b in [0,1]
        // (data 1, count 0 = one coloured mote). A zero red is nudged to 0.001 to dodge the engine's
        // "exactly 0 = default red" special case. `size`/`count` have no 1.8 analogue and are ignored.
        regionOp(at, () -> {
            EnumParticle resolved = particle(particleId);
            World world = at.getWorld();
            if (resolved == null || world == null) {
                return;
            }
            float fr = Math.max(0.001f, clampChannel(r) / 255f);
            float fg = clampChannel(g) / 255f;
            float fb = clampChannel(b) / 255f;
            PacketPlayOutWorldParticles packet = new PacketPlayOutWorldParticles(
                    resolved, true,
                    (float) at.getX(), (float) at.getY(), (float) at.getZ(),
                    fr, fg, fb, // the colour rides the offset
                    1f,         // data/speed = 1 for the redstone colour packet
                    0);         // count 0: a single coloured mote per packet
            for (Player viewer : world.getPlayers()) {
                if (viewer.getLocation().distanceSquared(at) <= 64 * 64) {
                    sendPacket(viewer, packet);
                }
            }
        });
    }

    /** The 1.8 NMS {@link EnumParticle} for an interned id, or {@code null} on a miss / unknown constant. */
    private EnumParticle particle(int id) {
        String name = resolvers.nameOf(HandleCategory.PARTICLE, id);
        if (name == null) {
            return null;
        }
        try {
            return EnumParticle.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    // ── Player-feedback leaves (1.8: NMS chat / title packets, no spigot()/Adventure) ─────────────

    @Override
    public void actionBar(Player target, String message) {
        // 1.8: no spigot()/Adventure — action bar is a chat packet with type byte 2. ChatComponentText
        // renders '§' codes, so translate '&' → '§' first.
        String text = Colors.translate(message);
        entityOp(target, () -> sendPacket(target,
                new PacketPlayOutChat(new ChatComponentText(text), (byte) 2)));
    }

    @Override
    public void title(Player target, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        // 1.8: no 5-arg sendTitle — send the TIMES then the TITLE/SUBTITLE title packets directly.
        // Translate '&' → '§' so colour codes render, not show literally.
        String t = Colors.translate(title);
        String s = Colors.translate(subtitle);
        entityOp(target, () -> {
            sendPacket(target, new PacketPlayOutTitle(fadeIn, stay, fadeOut));
            if (t != null) {
                sendPacket(target, new PacketPlayOutTitle(
                        PacketPlayOutTitle.EnumTitleAction.TITLE, new ChatComponentText(t)));
            }
            if (s != null) {
                sendPacket(target, new PacketPlayOutTitle(
                        PacketPlayOutTitle.EnumTitleAction.SUBTITLE, new ChatComponentText(s)));
            }
        });
    }

    /** Send an NMS packet to {@code player} via its connection (the 1.8 action-bar/title/particle path). */
    private static void sendPacket(Player player, net.minecraft.server.v1_8_R3.Packet<?> packet) {
        if (player instanceof CraftPlayer) {
            EntityPlayer handle = ((CraftPlayer) player).getHandle();
            if (handle.playerConnection != null) {
                handle.playerConnection.sendPacket(packet);
            }
        }
    }
}
