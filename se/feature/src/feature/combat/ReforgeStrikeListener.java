package feature.combat;

import compile.load.ContentHolder;
import engine.sink.EngineDamage;
import engine.stores.BatteryStore;
import engine.stores.EngineStores;
import feature.compat.Sounds;
import feature.compat.Titles;
import item.worn.WornStateStore;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import platform.caps.SpawnInvulnerability;
import platform.lang.Messages;
import platform.sched.Scheduling;
import platform.text.Colors;

/**
 * The MONITOR commit for the three reforge armed-window strikes (ADR-0071), plus the Supernova bank side and
 * the death reset. It reads the {@link ReforgeStrikeRelay} mark the dispatch left for THIS event and — only if
 * the event landed (not cancelled) — commits the consumption the dispatch folded optimistically: the Supernova
 * core spends, the Unhanding window shuffles the victim's hotbar, the Quickening write rewrites the victim's
 * i-frames. A cancelled hit leaves every window armed and every charge intact (the optimistic fold
 * contributions died with the cancelled event).
 *
 * <p>Separately — OUTSIDE the relay, because the store IS the interest registration — a landed hit TAKEN by a
 * Supernova-armed victim banks a fraction of its final damage. The bank rides the SAME pvp/pve/friendly context
 * gate as the discharge (the discharge inherits the dispatch's attacker-branch gate by placement; the bank
 * applies the identical defense-side predicate here), so a charge never banks in a context where it could never
 * discharge. Environment damage (fall/fire) is structurally excluded (not ByEntity); SE's own DoT/reflect ticks
 * are excluded via {@link EngineDamage#active()}; a same-swing re-hit via {@link ReHitGuard#skipped}.
 *
 * <p>Runs on the firing (victim-owning) region thread — every write (i-frames, the victim's own inventory, the
 * store maps) is region-correct there, and the attacker is co-located for a landed melee hit, so
 * attacker-directed feedback is in-region too.
 */
public final class ReforgeStrikeListener implements Listener {

    private final EngineStores stores;
    private final WornStateStore worn;   // refresh the victim after a shuffle — no Bukkit event fires on a same-slot swap
    private final ContentHolder content; // the snapshot the refresh resolves against
    private final Messages messages;
    private final Sounds sounds;
    private final LongSupplier nowTicks;
    private final BooleanSupplier pvpEnabled; // the same MasterConfig-backed suppliers CombatDispatch reads —
    private final BooleanSupplier pveEnabled; // the §2.5 context-gate symmetry (bank mirrors discharge)
    private final TimingOverrideBridge timing; // Mental's (future) window service — present ⇒ no vanilla writes

    public ReforgeStrikeListener(EngineStores stores, WornStateStore worn, ContentHolder content,
                                 Messages messages, Sounds sounds, LongSupplier nowTicks,
                                 BooleanSupplier pvpEnabled, BooleanSupplier pveEnabled,
                                 TimingOverrideBridge timing) {
        this.stores = Objects.requireNonNull(stores, "stores");
        this.worn = Objects.requireNonNull(worn, "worn");
        this.content = Objects.requireNonNull(content, "content");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.pvpEnabled = Objects.requireNonNull(pvpEnabled, "pvpEnabled");
        this.pveEnabled = Objects.requireNonNull(pveEnabled, "pveEnabled");
        this.timing = Objects.requireNonNull(timing, "timing");
    }

    /** NOT ignoreCancelled — it acts on BOTH outcomes: the bank runs on a landed hit, a cancel keeps windows armed. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageResolved(EntityDamageByEntityEvent event) {
        ReforgeStrikeRelay.Pending pending = ReforgeStrikeRelay.consume(event, stores);
        Entity damager = resolveDamager(event);

        // Supernova BANK (hits TAKEN): the store is its own interest registration — no relay. A landed,
        // non-engine, non-re-hit ByEntity hit on an armed Player victim banks a fraction of its final damage.
        if (!event.isCancelled() && !EngineDamage.active() && !ReHitGuard.skipped(event)
                && event.getEntity() instanceof Player victim
                && stores.battery().armed(victim.getUniqueId())
                && !damager.getUniqueId().equals(victim.getUniqueId())
                && contextEnabled(damager instanceof Player)
                && !(damager instanceof Player attacker && CombatDispatch.friendly(attacker, victim))) {
            BatteryStore.Core banked = stores.battery().bank(victim.getUniqueId(), event.getFinalDamage());
            if (banked != null) {
                Titles.sendActionBar(victim, Colors.translate(messages.fragment("reforge.battery.banked-actionbar",
                        "STORED", hearts(banked.banked()), "HITS", banked.hitsLeft())));
            }
        }

        if (pending == null || event.isCancelled()) {
            return; // no reforge windows on this hit, or a cancel that keeps them all armed
        }
        Player attacker = damager instanceof Player p ? p : null;
        LivingEntity victimLiving = event.getEntity() instanceof LivingEntity l ? l : null;

        if (pending.batterySpend()) {
            double unloaded = stores.battery().spend(pending.attacker()); // spent regardless (owner-ruled)
            if (attacker != null) {
                if (unloaded > 0.0) {
                    sounds.play(attacker, event.getEntity().getLocation(), "ENTITY_GENERIC_EXPLODE", 0.6f, 1.6f);
                    messages.sendText(attacker, Colors.translate(
                            messages.fragment("reforge.battery.discharged", "AMOUNT", hearts(unloaded))));
                } else {
                    messages.sendText(attacker,
                            Colors.translate(messages.fragment("reforge.battery.spent-empty")));
                }
            }
        }

        if (pending.disarmStrike() && event.getEntity() instanceof Player victim) {
            int held = victim.getInventory().getHeldItemSlot();
            ItemStack heldItem = victim.getInventory().getItem(held);
            // An empty hand has nothing to unhand — the shipped commit HANDED a random hotbar item TO an
            // empty-handed victim, arming a fist-fighter. The window stays armed (it expires on its own).
            if (heldItem != null && heldItem.getType() != Material.AIR) {
                stores.disarmWindows().consume(pending.attacker());
                int target = shuffleTarget(held, Math.floorMod(ThreadLocalRandom.current().nextInt(), 8));
                ItemStack targetItem = victim.getInventory().getItem(target);
                victim.getInventory().setItem(held, targetItem); // held slot SELECTION stays — shuffled, not locked
                victim.getInventory().setItem(target, heldItem);
                victim.updateInventory(); // deprecated-not-removed across the range; 1.8 clients need the hotbar repaint
                worn.refresh(victim, content.snapshot()); // else the victim's held-weapon abilities / rage read stale
                sounds.play(victim, victim.getLocation(), "ENTITY_ITEM_BREAK", 0.8f, 0.7f);
                messages.sendText(victim,
                        Colors.translate(messages.fragment("reforge.unhanding.shuffled-victim")));
                if (attacker != null) {
                    messages.sendText(attacker,
                            Colors.translate(messages.fragment("reforge.unhanding.struck")));
                }
            }
        }

        if (pending.tempoHit() && victimLiving != null) {
            int max = victimLiving.getMaximumNoDamageTicks();
            // Service-backed steal (api-timing-overrides.md): Mental re-prices its OWN admission gate for
            // this (victim, holder) pair — 0.5 = the authored "twice as fast", refreshed per landed hit
            // for one admission window. Accepted ⇒ nothing vanilla is written (no clobber re-apply, no
            // spawn-invulnerability companion) and nothing global was erased, so the third-party fairness
            // stamp stays un-set.
            if (timing.overrideWindow(pending.victim(), pending.attacker(), 0.5, Math.max(1, max))) {
                return;
            }
            // MENTAL = gate at max/2 — but that models the DEFAULT profile, where max stays vanilla (20).
            // Mental's ct8c-iframes bundle rewrites max to min(attackDelay, 10) per hit with a full-window
            // gate; reading max/2 there makes write ≈ the naturally-decayed counter — a no-op steal while
            // the 1/3 tax still lands (a pure self-nerf). An observed max ≤ 10 IS that profile: treat the
            // whole window as the gate, same as VANILLA.
            int window = pending.tempoWindow().model() == 0 && max > 10 ? max / 2 : max;
            int write = max - window / 2; // admit the holder's next full hit after HALF the window
            LivingEntity victim = victimLiving;
            // NMS's hurt() fresh-hit branch re-sets invulnerableTime = maximumNoDamageTicks AFTER this event
            // fires (the putfield trails the event dispatch — verified in LivingEntity.hurtServer), so an inline
            // write here is immediately clobbered back to the full window. Re-apply the shortened window on the
            // victim's own region the next tick, once that reset has landed, so the mechanic actually takes.
            Scheduling.onEntityLater(victim, 1L, () -> {
                victim.setNoDamageTicks(write);
                if (victim instanceof Player player) {
                    // 1.16.5–1.20.6: the Bukkit setter ALSO arms the player's respawn-invulnerability
                    // timer, whose ServerPlayer gate then voids EVERY hit — the exact inverse of the
                    // steal. Zero the companion; a no-op on every other band.
                    SpawnInvulnerability.disarm(player, write);
                }
            });
            stores.hitTempo().stampStolen(pending.victim(), pending.attacker(), nowTicks.getAsLong() + max / 2);
        }
    }

    /** Battery reset: the dead stay dead (ADR-0051) — a respawn never inherits banked damage. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        stores.battery().clear(event.getEntity().getUniqueId());
    }

    /** The other hotbar slot a shuffle sends the held item to: uniform over the 8 non-held slots. Pure test seam. */
    static int shuffleTarget(int held, int roll) {
        return (held + 1 + roll) % 9;
    }

    /** Whether SE combat applies in this context — pvp on a player damager, else pve (the §2.6 bank symmetry). */
    private boolean contextEnabled(boolean pvp) {
        return pvp ? pvpEnabled.getAsBoolean() : pveEnabled.getAsBoolean();
    }

    /** Resolve a projectile hit to its shooter (the dispatch's rule) — a bow shot is its shooter's hit. */
    private static Entity resolveDamager(EntityDamageByEntityEvent event) {
        Entity raw = event.getDamager();
        if (raw instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return raw;
    }

    /** Health-space value (2.0 = 1 heart) rendered as a one-decimal heart count for the {STORED}/{AMOUNT} tokens. */
    private static String hearts(double healthSpace) {
        return String.format(Locale.ROOT, "%.1f", healthSpace / 2.0);
    }
}
