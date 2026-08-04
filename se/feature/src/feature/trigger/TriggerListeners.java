package feature.trigger;

import engine.run.ActivationContext;
import engine.sink.EngineDamage;
import feature.compat.Hands;
import java.util.Objects;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
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
            dispatch.fire(dying, dispatch.death,
                    new ActivationContext(dying, killer, killer, dying.getLocation()), null);
            fireProximity(dying);
        }
    }

    /**
     * PROXIMITY_EVENT: ONE walk out from the body, firing every OTHER player in range on their own gear. Folia
     * holds by construction — {@code getNearbyEntities} runs on the dying player's region and returns only
     * co-region entities, so no observer is ever touched cross-region. Range and relation are the ability's
     * own {@code %distance%} / {@code %victim.relation%} conditions, evaluated per observer inside the walk,
     * so asking for a relation filter never costs a second scan (the {@code %nearbyallies%} rule).
     */
    private void fireProximity(Player dying) {
        if (dispatch.proximityEvent < 0) {
            return;
        }
        double r = TriggerDispatch.PROXIMITY_RADIUS;
        for (Entity nearby : dying.getNearbyEntities(r, r, r)) {
            if (nearby instanceof Player observer) {
                dispatch.fireProximity(observer, dying);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBowFire(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player shooter) {
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (!hands.isMainHand(event)) {
            return; // one fire per interaction — the off-hand pass is a duplicate of the same click
        }
        Player player = event.getPlayer();
        ActivationContext context = clicked(player, event);
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
            return; // entity-on-entity combat is CombatListener's job (ATTACK/DEFENSE/HURT)
        }
        if (EngineDamage.active()) {
            // SE-issued unattributed damage (a DoT tick, a bare sink.damage) arrives here as its own
            // EntityDamageEvent. HURT fires on every cause, so without this frame check an ability that deals
            // damage from HURT would proc itself forever — the ADR-0054 re-entrancy contract CombatDispatch
            // enforces on the entity path.
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // §F reduction-scope: heroic softens environmental damage only when scope == ALL (default ENTITY = PvP only)
        boolean heroic = heroicAllScope.getAsBoolean();
        ActivationContext context = damaged(player, event);
        switch (event.getCause()) {
            case FALL -> dispatch.fireDamage(player, dispatch.fall, dispatch.hurt, context, event, heroic);
            case FIRE, FIRE_TICK, LAVA ->
                    dispatch.fireDamage(player, dispatch.fire, dispatch.hurt, context, event, heroic);
            // Every other cause: HURT alone, sharing one fold with the heroic-only reduction it subsumes here.
            default -> dispatch.fireDamage(player, dispatch.hurt, -1, context, event, heroic);
        }
    }

    /**
     * The payload for a damage-taken activation: the pending hit's cause ({@code %damagecause%}), its pre-fold
     * amount ({@code %damage%}) and the vanilla-final figure {@code %posthit.health%} prices against — read
     * here, before any SE fold, exactly as the DEFENSE side reads it (the wave 1b.3 ruling).
     */
    private static ActivationContext damaged(Player player, EntityDamageEvent event) {
        return new ActivationContext(player, null, null, player.getLocation(), event.getDamage(), null, 0,
                event.getCause().name(), false, 0, 0, event.getFinalDamage(), 0.0, "");
    }

    /**
     * The payload for a click. A click ON a block carries it, so {@code %block.type%}/{@code %isblock%} read the
     * face the player actually hit and {@code @Here} anchors there rather than at their own feet — the difference
     * between an ability that breaks the clicked block and one that breaks the ground under the clicker. A click at
     * open air has no block, and stays the bare self-context it has always been.
     */
    private static ActivationContext clicked(Player player, PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        return block == null
                ? self(player)
                : new ActivationContext(player, null, null, block.getLocation(), 0.0, block);
    }

    private static ActivationContext self(Player player) {
        return new ActivationContext(player, null, null, player.getLocation());
    }
}
