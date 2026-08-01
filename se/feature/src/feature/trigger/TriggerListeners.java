package feature.trigger;

import engine.run.ActivationContext;
import feature.combat.CosmicProjectilePower;
import feature.compat.Hands;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.entity.LivingEntity;

/**
 * Maps non-combat Bukkit events to {@link TriggerDispatch} (§3.3): break → MINE, kill → KILL, interact →
 * INTERACT(+direction), fall/fire → FALL/FIRE. Each handler runs on its firing region thread; the dispatch
 * routes mutations through the Sink, so no handler touches a cross-region entity directly.
 */
public final class TriggerListeners implements Listener {

    private final TriggerDispatch dispatch;
    private final java.util.function.BooleanSupplier heroicAllScope; // §F: reduction-scope == ALL (live)
    private final Hands hands;
    private final PlacedBlockTracker placed; // nullable: no placed-block guard (tester rigs)
    private final java.util.function.BooleanSupplier miningGuard; // §F33: placed-block guard enabled (live)

    /** Default form: heroic reduction is ENTITY-scoped (environmental damage gets no heroic reduction), no guard. */
    public TriggerListeners(TriggerDispatch dispatch, Hands hands) {
        this(dispatch, () -> false, hands, null, () -> false);
    }

    public TriggerListeners(TriggerDispatch dispatch, java.util.function.BooleanSupplier heroicAllScope, Hands hands,
            PlacedBlockTracker placed, java.util.function.BooleanSupplier miningGuard) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.heroicAllScope = Objects.requireNonNull(heroicAllScope, "heroicAllScope");
        this.hands = Objects.requireNonNull(hands, "hands");
        this.placed = placed;
        this.miningGuard = Objects.requireNonNull(miningGuard, "miningGuard");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        dispatch.fire(player, dispatch.blockDamage,
                new ActivationContext(player, null, null, event.getBlock().getLocation(), 0.0, event.getBlock()),
                event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        Player player = event.getPlayer();
        // §F33: a block the breaker placed pays no MINE reward. Runs BEFORE the tracker's MONITOR unmark, so the
        // mark is still visible here; the whole dispatch is skipped (QoL MINE effects included, config-gated).
        if (placed != null && miningGuard.getAsBoolean() && placed.isPlaced(event.getBlock())) {
            return;
        }
        // the broken block backs the %block.type%/%isblock% facts (region-owned on this thread)
        dispatch.fireMine(player,
                new ActivationContext(player, null, null, event.getBlock().getLocation(), 0.0, event.getBlock()),
                event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        // KILL fires for the killer's worn gear
        if (killer != null) {
            dispatch.fire(killer, dispatch.kill,
                    new ActivationContext(killer, dead, null, dead.getLocation()), null);
        }
        // DEATH fires for the dying player's own worn gear
        if (dead instanceof Player dying) {
            dispatch.fireAllyDeath(dying);
            dispatch.fire(dying, dispatch.death,
                    new ActivationContext(dying, killer, killer, dying.getLocation()), null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBowFire(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player shooter) {
            if (event.getProjectile() != null) {
                CosmicProjectilePower.record(event.getProjectile().getUniqueId(), event.getForce());
            }
            if (event.getForce() < 0.75F) {
                return;
            }
            dispatch.fireBow(shooter, self(shooter), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        // Fire only on a successful catch (a fish or a reeled-in entity), not every bite/cast state.
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH
                || event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            dispatch.fire(event.getPlayer(), dispatch.fishing, self(event.getPlayer()), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        dispatch.fire(event.getPlayer(), dispatch.eat, self(event.getPlayer()), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        // Scale the amount in place — EXP_MULTIPLY accumulates a factor the dispatch applies here; never grant new XP.
        dispatch.fireExp(event.getPlayer(), self(event.getPlayer()), event);
    }

    // ITEM_DAMAGE fires from DurabilityTriggerListener (overlay) — PlayerItemDamageEvent is 1.9+ (§4).

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemBreak(PlayerItemBreakEvent event) {
        // PlayerItemBreakEvent is not cancellable — the item is already gone.
        dispatch.fire(event.getPlayer(), dispatch.breakItem, self(event.getPlayer()), null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!hands.isMainHand(event)) {
            return; // one fire per interaction — the off-hand pass is a duplicate of the same click
        }
        Player player = event.getPlayer();
        org.bukkit.block.Block clicked = event.getClickedBlock();
        org.bukkit.Location at = clicked == null ? player.getLocation() : clicked.getLocation();
        ActivationContext context = new ActivationContext(player, null, null, at, 0.0, clicked);
        dispatch.fire(player, dispatch.interact, context, event);
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            dispatch.fire(player, dispatch.interactLeft, context, event);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            dispatch.fire(player, dispatch.interactRight, context, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnvironmentalDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return; // entity-on-entity combat is CombatListener's job (ATTACK/DEFENSE)
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // §F reduction-scope: heroic softens environmental damage only when scope == ALL (default ENTITY = PvP only)
        // General DEFENSE runs once for every non-entity cause. FALL/FIRE are additional specialized channels,
        // folded into the same event so heroic and damage modifiers are never committed twice.
        boolean heroic = heroicAllScope.getAsBoolean();
        int specific = switch (event.getCause()) {
            case FALL -> dispatch.fall;
            case FIRE, FIRE_TICK, LAVA -> dispatch.fire;
            default -> -1;
        };
        ActivationContext context = new ActivationContext(player, null, null, player.getLocation(),
                event.getDamage(), event.getFinalDamage(), null, 0, event.getCause().name(), false, 0, 0);
        dispatch.fireEnvironmentalDamage(player, specific, context, event, heroic);
    }

    private static ActivationContext self(Player player) {
        return new ActivationContext(player, null, null, player.getLocation());
    }
}
