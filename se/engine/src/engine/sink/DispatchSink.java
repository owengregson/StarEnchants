package engine.sink;

import engine.interact.DamageFold;
import java.util.List;
import java.util.Objects;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.util.UUID;
import platform.economy.EconomyService;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import schema.spec.HandleCategory;

/**
 * The concrete {@link Sink} — the single mutation boundary and the only engine code that knows
 * about threads (docs/architecture.md §3.5–3.6). An {@code EffectKind} emits intents here; this
 * class captures them and routes each to the thread that owns its target, so an effect author can
 * neither schedule nor touch an entity and therefore cannot write a Folia bug.
 *
 * <p>Two kinds of intent:
 * <ul>
 *   <li><strong>Inline feedback</strong> — the damage-fold contributions and {@code cancelEvent}
 *       feed back into the very Bukkit event being processed, so they are accumulated synchronously
 *       on the firing thread and read back by the trigger listener (which folds {@link #fold()}
 *       onto the event once and honours {@link #cancelled()}); they never schedule.</li>
 *   <li><strong>World mutations</strong> — everything else. Each is captured into the
 *       {@link DispatchPlan} and routed to the thread that owns its target (the entity's region, the
 *       location's region, or the global thread), flushed batched after the gate walk. A mutation is
 *       NEVER run inline on the firing thread: the target is frequently a <em>different</em> entity or
 *       region than the firing one — a defender retaliating against its attacker, an AoE bystander —
 *       and inlining such a mutation would be a cross-region wrong-thread access on Folia. So the
 *       owning thread (via {@code Scheduling}, which is inline-on-the-main-thread on Paper) is the
 *       only safe routing; the declared affinity is advisory, not a licence to skip the hop.</li>
 * </ul>
 *
 * <p>Version-volatile referents arrive as interned handle ids and are resolved to live Bukkit
 * objects through {@link RuntimeHandles} at apply time — on the correct thread, cached after the
 * first lookup, never on the inline combat path's critical section (§9). An id that does not
 * resolve on this version yields a {@code null} object and that one intent is silently skipped:
 * the "warn" half of §9's warn-and-skip already fired at compile time, where the resolver reports
 * an unknown token against the same {@code RegistrySupport} lookup — so an interned id failing to
 * resolve at runtime is a can't-happen on a stable server, not a config error worth re-logging.
 *
 * <p>Lifecycle: one instance per event. {@link #fold()} and {@link #cancelled()} accumulate across
 * all of the event's abilities; {@link #flush()} is called once, last. Not thread-safe by design —
 * it is filled and flushed on the single firing thread (§6); the batches it schedules run later on
 * their own threads over immutable captured primitives.
 */
public final class DispatchSink implements Sink {

    private final RuntimeHandles handles;
    private final EconomyService economy;
    private final DispatchPlan plan = new DispatchPlan();
    private final DamageFold fold = new DamageFold();

    private boolean cancelled;
    private boolean flushed;

    /** A sink with no economy — money intents are no-ops (the default for tests and economy-free paths). */
    public DispatchSink(RuntimeHandles handles) {
        this(handles, EconomyService.NONE);
    }

    public DispatchSink(RuntimeHandles handles, EconomyService economy) {
        this.handles = Objects.requireNonNull(handles, "handles");
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    // ── Read-backs (called by the firing system, never by an effect) ─────────────────────────────

    /** The damage arbiter for this event; the trigger listener folds it onto the event once (§6.1). */
    public DamageFold fold() {
        return fold;
    }

    /** Whether an effect asked for the triggering event to be cancelled (§3.6 event control). */
    public boolean cancelled() {
        return cancelled;
    }

    /** Schedule every deferred intent on its owning thread; call once after the gate walk. Idempotent. */
    public void flush() {
        if (flushed) {
            return;
        }
        flushed = true;
        plan.flush();
    }

    private void entityOp(Entity target, Runnable op) {
        if (target != null) {
            plan.onEntity(target, op); // always the entity's own thread — never inline (may be cross-region)
        }
    }

    private void regionOp(Location at, Runnable op) {
        if (at != null) {
            plan.onRegion(at, op); // always the location's region thread — never inline
        }
    }

    private void globalOp(Runnable op) {
        // Global work (e.g. console commands) always routes to the global region thread, never
        // inline on a firing region thread — even under a CONTEXT_LOCAL ability — so Folia's
        // global-region invariants hold. flush() always runs after the gate walk, so it is not lost.
        plan.onGlobal(op);
    }

    // ── Damage arbiter: contribute deltas, never setDamage (§6.1) ────────────────────────────────

    @Override
    public void addOutgoingDamage(double percent) {
        fold.addOutgoing(percent);
    }

    @Override
    public void addDamageReduction(double percent) {
        fold.addReduction(percent);
    }

    @Override
    public void addFlatDamage(double amount) {
        fold.addFlatDamage(amount);
    }

    @Override
    public void addFlatReduction(double amount) {
        fold.addFlatReduction(amount);
    }

    // ── Entity intents ───────────────────────────────────────────────────────────────────────────

    @Override
    public void damage(LivingEntity target, double amount) {
        entityOp(target, () -> target.damage(amount));
    }

    @Override
    public void heal(LivingEntity target, double amount) {
        entityOp(target, () -> target.setHealth(Math.min(target.getHealth() + amount, maxHealth(target))));
    }

    @Override
    public void kill(LivingEntity target) {
        entityOp(target, () -> target.setHealth(0.0));
    }

    @Override
    public void extinguish(LivingEntity target) {
        entityOp(target, () -> target.setFireTicks(0));
    }

    @Override
    public void fillAir(LivingEntity target) {
        entityOp(target, () -> target.setRemainingAir(target.getMaximumAir()));
    }

    @Override
    public void feed(Player target, int foodPoints) {
        entityOp(target, () -> target.setFoodLevel(Math.min(20, target.getFoodLevel() + foodPoints)));
    }

    @Override
    public void repairHand(Player target, int amount) {
        entityOp(target, () -> {
            ItemStack item = target.getInventory().getItemInMainHand();
            if (applyRepair(item, amount)) {
                target.getInventory().setItemInMainHand(item);
            }
        });
    }

    @Override
    public void giveExp(Player target, int amount) {
        entityOp(target, () -> target.giveExp(amount));
    }

    @Override
    public void knockback(Entity target, Location from, double strength) {
        // `from` is the activator's location, captured by the effect on the firing thread — an
        // immutable snapshot, never a live cross-region entity read. We read `target.getLocation()`
        // here, which is correct because this body runs on the target's own thread (entityOp).
        entityOp(target, () -> {
            Vector delta = target.getLocation().toVector().subtract(from.toVector());
            Vector direction = delta.lengthSquared() > 1.0e-6 ? delta.normalize() : new Vector(0, 1, 0);
            target.setVelocity(target.getVelocity().add(direction.multiply(strength)));
        });
    }

    @Override
    public void setFlight(Player target, int durationTicks) {
        entityOp(target, () -> {
            target.setAllowFlight(true);
            target.setFlying(true);
            if (durationTicks >= 0) {
                Scheduling.onEntityLater(target, durationTicks, () -> clearTemporaryFlight(target));
            }
        });
    }

    @Override
    public void addMaxHealth(LivingEntity target, double amount) {
        entityOp(target, () -> {
            // The unequip restoration of this delta is wired when the WornState resolver lands
            // (§5.5); for now the base value is shifted directly so the effect is observable.
            AttributeInstance maxHealth = maxHealthAttribute(target);
            if (maxHealth != null) {
                maxHealth.setBaseValue(Math.max(1.0, maxHealth.getBaseValue() + amount));
            }
        });
    }

    @Override
    public void damageArmor(LivingEntity target, int amount) {
        entityOp(target, () -> adjustArmorDurability(target, amount, false));
    }

    @Override
    public void repairArmor(Player target, int amount) {
        entityOp(target, () -> adjustArmorDurability(target, amount, true));
    }

    @Override
    public void potion(LivingEntity target, int potionEffectId, int amplifier, int durationTicks) {
        entityOp(target, () -> {
            PotionEffectType type = handles.potionEffect(potionEffectId);
            if (type != null) {
                target.addPotionEffect(new PotionEffect(type, durationTicks, amplifier));
            }
        });
    }

    @Override
    public void removePotion(LivingEntity target, int potionEffectId) {
        entityOp(target, () -> {
            PotionEffectType type = handles.potionEffect(potionEffectId);
            if (type != null) {
                target.removePotionEffect(type);
            }
        });
    }

    @Override
    public void cure(LivingEntity target) {
        // Snapshot the active types first: removePotionEffect mutates the live collection, so
        // iterating it directly while removing would be a concurrent-modification hazard.
        entityOp(target, () -> {
            for (PotionEffect active : List.copyOf(target.getActivePotionEffects())) {
                target.removePotionEffect(active.getType());
            }
        });
    }

    @Override
    public void disarm(LivingEntity target) {
        // Runs on the target's own thread (entityOp), so reading its equipment + dropping at its
        // location is region-correct — never a cross-region read.
        entityOp(target, () -> {
            EntityEquipment equipment = target.getEquipment();
            if (equipment == null) {
                return;
            }
            ItemStack held = equipment.getItemInMainHand();
            if (held == null || held.getType().isAir()) {
                return;
            }
            equipment.setItemInMainHand(null);
            World world = target.getWorld();
            if (world != null) {
                world.dropItemNaturally(target.getLocation(), held);
            }
        });
    }

    @Override
    public void ignite(Entity target, int durationTicks) {
        entityOp(target, () -> target.setFireTicks(Math.max(0, durationTicks)));
    }

    @Override
    public void lightningAndDamage(LivingEntity target, double amount) {
        entityOp(target, () -> {
            World world = target.getWorld();
            if (world != null) {
                world.strikeLightning(target.getLocation());
            }
            target.damage(amount);
        });
    }

    @Override
    public void launch(Entity target, double x, double y, double z) {
        entityOp(target, () -> target.setVelocity(target.getVelocity().add(new Vector(x, y, z))));
    }

    @Override
    public void teleport(Entity target, Location to) {
        // Teleports are async on Folia; teleportAsync is correct on Paper too and present on the floor API.
        entityOp(target, () -> target.teleportAsync(to));
    }

    // ── World / block intents ────────────────────────────────────────────────────────────────────

    @Override
    public void lightning(Location at) {
        regionOp(at, () -> {
            World world = at.getWorld();
            if (world != null) {
                world.strikeLightning(at);
            }
        });
    }

    @Override
    public void spawn(Location at, int entityTypeId) {
        regionOp(at, () -> {
            EntityType type = handles.entityType(entityTypeId);
            World world = at.getWorld();
            if (type != null && world != null) {
                world.spawnEntity(at, type);
            }
        });
    }

    @Override
    public void spawnTnt(Location at, int count) {
        regionOp(at, () -> {
            World world = at.getWorld();
            if (world != null) {
                for (int i = 0; i < count; i++) {
                    world.spawn(at, TNTPrimed.class);
                }
            }
        });
    }

    @Override
    public void explode(Location at, double power, boolean breakBlocks) {
        regionOp(at, () -> {
            World world = at.getWorld();
            if (world != null) {
                world.createExplosion(at, (float) power, false, breakBlocks);
            }
        });
    }

    @Override
    public void fireball(Player shooter, double yield) {
        entityOp(shooter, () -> {
            Fireball fireball = shooter.launchProjectile(Fireball.class);
            fireball.setYield((float) yield);
        });
    }

    @Override
    public void blockChange(Location at, int blockDataId) {
        // The floor behaviour treats the handle as a Material; full BlockData (with states) is a
        // compat-modern follow-up (the doc's "BlockData sends"). Materials cover the common case.
        regionOp(at, () -> {
            Material material = handles.material(blockDataId);
            if (material != null && material.isBlock()) {
                at.getBlock().setType(material);
            }
        });
    }

    @Override
    public void sound(Location at, int soundId, float volume, float pitch) {
        regionOp(at, () -> {
            Sound resolved = handles.sound(soundId);
            World world = at.getWorld();
            if (resolved != null && world != null) {
                world.playSound(at, resolved, volume, pitch);
            }
        });
    }

    @Override
    public void particle(Location at, int particleId, int count) {
        regionOp(at, () -> {
            Particle resolved = handles.particle(particleId);
            World world = at.getWorld();
            if (resolved != null && world != null) {
                world.spawnParticle(resolved, at, count);
            }
        });
    }

    // ── Player feedback ──────────────────────────────────────────────────────────────────────────

    @Override
    public void message(Player target, String message) {
        entityOp(target, () -> target.sendMessage(message));
    }

    @Override
    public void actionBar(Player target, String message) {
        // The Spigot chat API is the one action-bar path stable across the whole 1.17.1 → 26.1.x range.
        entityOp(target, () -> target.spigot().sendMessage(
                ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message)));
    }

    @Override
    @SuppressWarnings("deprecation") // sendTitle(String, String, int, int, int): deprecated-not-removed across the whole range.
    public void title(Player target, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        // The 5-arg String sendTitle is the one title path stable across the whole 1.17.1 → 26.1.x range
        // (the Adventure Title API is not present on the spigot-mapped floor's bundled api).
        entityOp(target, () -> target.sendTitle(title, subtitle, fadeIn, stay, fadeOut));
    }

    @Override
    public void consoleCommand(String command) {
        globalOp(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    // ── Economy intents ──────────────────────────────────────────────────────────────────────────

    @Override
    public void giveMoney(Player target, double amount) {
        if (target == null) {
            return;
        }
        // Capture the UUID on the firing thread (immutable, thread-safe); the economy call runs on the
        // global thread — never touching the live player object off its region.
        UUID id = target.getUniqueId();
        globalOp(() -> economy.deposit(id, amount));
    }

    @Override
    public void takeMoney(Player target, double amount) {
        if (target == null) {
            return;
        }
        UUID id = target.getUniqueId();
        globalOp(() -> economy.withdraw(id, amount));
    }

    // ── Event control ──────────────────────────────────────────────────────────────────────────

    @Override
    public void cancelEvent() {
        cancelled = true;
    }

    // ── Cross-version helpers ────────────────────────────────────────────────────────────────────

    /** The max-health attribute instance for {@code entity}, resolved version-adaptively, or {@code null}. */
    private AttributeInstance maxHealthAttribute(LivingEntity entity) {
        Object attribute = handles.resolveByName(HandleCategory.ATTRIBUTE, "GENERIC_MAX_HEALTH");
        return attribute instanceof Attribute resolved ? entity.getAttribute(resolved) : null;
    }

    /** The entity's maximum health via the attribute, falling back to the floor accessor. */
    @SuppressWarnings("deprecation") // getMaxHealth: deprecated-not-removed across the whole range.
    private double maxHealth(LivingEntity entity) {
        AttributeInstance maxHealth = maxHealthAttribute(entity);
        return maxHealth != null ? maxHealth.getValue() : entity.getMaxHealth();
    }

    /** Repair one item by {@code amount} (negative = full); returns whether the meta changed. */
    private static boolean applyRepair(ItemStack item, int amount) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int repaired = amount < 0 ? 0 : Math.max(0, damageable.getDamage() - amount);
            damageable.setDamage(repaired);
            item.setItemMeta(meta);
            return true;
        }
        return false;
    }

    /** Adjust every worn armour piece's durability — repair when {@code repair}, else damage it. */
    private static void adjustArmorDurability(LivingEntity entity, int amount, boolean repair) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        ItemStack[] armor = equipment.getArmorContents();
        boolean changed = false;
        for (ItemStack piece : armor) {
            if (piece == null) {
                continue;
            }
            ItemMeta meta = piece.getItemMeta();
            if (meta instanceof Damageable damageable) {
                int current = damageable.getDamage();
                int next;
                if (repair) {
                    next = amount < 0 ? 0 : Math.max(0, current - amount);
                } else {
                    next = Math.min(piece.getType().getMaxDurability(), current + amount);
                }
                damageable.setDamage(next);
                piece.setItemMeta(meta);
                changed = true;
            }
        }
        if (changed) {
            equipment.setArmorContents(armor);
        }
    }

    /** Strip temporarily-granted flight, but never from a player who can fly by game mode. */
    private static void clearTemporaryFlight(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }
}
