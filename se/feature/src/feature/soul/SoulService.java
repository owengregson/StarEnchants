package feature.soul;

import compile.load.SoulGemConfig;
import engine.interact.SoulLedger;
import engine.stores.SoulModeStore;
import item.codec.SoulCodec;
import item.codec.SoulData;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.sched.Scheduling;

/**
 * The soul gameplay loop (docs/architecture.md §6.3) — the binding between the durable on-item
 * {@link SoulData} and the in-memory {@link SoulLedger} authority, made Folia-correct.
 *
 * <p><strong>The threading discipline.</strong> The ledger's in-memory authority is the live truth
 * during a session; the gem's PDC is the durable backing. The authority is seeded ONCE when a player
 * toggles soul mode on (run on the player's own thread, where reading their held gem is region-safe).
 * From then on every read uses the authority — never a live cross-region item read. A spend debits the
 * authority synchronously under the ledger's stripe lock (any thread), and the durable PDC write is
 * DEFERRED to the gem-holder's own entity thread (combat fires on the victim's region, but the gem is
 * the attacker's — writing it inline would be a cross-region mutation on Folia).
 */
public final class SoulService {

    private final SoulLedger ledger;
    private final SoulModeStore modes;
    private final SoulCodec codec;
    private final Supplier<SoulGemConfig> config;

    public SoulService(SoulLedger ledger, SoulModeStore modes, SoulCodec codec, Supplier<SoulGemConfig> config) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.modes = Objects.requireNonNull(modes, "modes");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.config = Objects.requireNonNull(config, "config");
    }

    /** The result of {@link #toggle}, for the command to relay. */
    public enum Toggle { NO_GEM, ENABLED, DISABLED }

    /**
     * Toggle soul mode for {@code player} based on the gem in their main hand. MUST run on the
     * player's own thread (reads the held item). Enabling seeds the ledger authority with the gem's
     * stored count so no later read crosses a region.
     */
    public Toggle toggle(Player player) {
        SoulData gem = codec.read(player.getInventory().getItemInMainHand());
        if (gem == null) {
            return Toggle.NO_GEM;
        }
        UUID id = player.getUniqueId();
        if (modes.active(id).filter(active -> active.equals(gem.gemId())).isPresent()) {
            modes.deactivate(id);
            ledger.forget(gem.gemId());
            message(player, config.get().messageDeactivate());
            return Toggle.DISABLED;
        }
        modes.activate(id, gem.gemId());
        seed(gem); // prime the authority from the durable count on this (player) thread
        message(player, config.get().messageActivate());
        return Toggle.ENABLED;
    }

    /** Send a configured soul-gem message (legacy {@code &} colour codes translated), unless blank. */
    private static void message(Player player, String raw) {
        if (raw != null && !raw.isBlank()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', raw));
        }
    }

    /**
     * Stamp a fresh soul gem (a new identity, 0 souls) onto {@code player}'s held item. MUST run on
     * the player's own thread. Returns {@code false} if no item is held.
     */
    public boolean stampGem(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            return false;
        }
        codec.write(held, SoulData.fresh(UUID.randomUUID()));
        player.getInventory().setItemInMainHand(held);
        return true;
    }

    /** Deposit the configured {@code souls-per-kill} into {@code killer}'s active gem (no-op if soul mode is off). */
    public void onKill(Player killer) {
        int amount = config.get().soulsPerKill();
        if (amount <= 0) {
            return;
        }
        modes.active(killer.getUniqueId()).ifPresent(gemId -> {
            // Only deposit onto a SEEDED authority: an active gem is always seeded (toggle-on seeds it
            // on the player's thread), so a peek-miss here would mean a broken invariant — skip rather
            // than let deposit seed the authority from the 0-returning deferred balance and wipe it.
            if (ledger.peek(gemId).isPresent()) {
                ledger.deposit(gemId, deferredBalance(killer, gemId), amount);
            }
        });
    }

    /** The soul binding for {@code actor}'s active gem, or empty if soul mode is off for them. */
    public Optional<SoulBinding> bindingFor(Player actor) {
        return modes.active(actor.getUniqueId())
                .map(gemId -> new SoulBinding(gemId, deferredBalance(actor, gemId)));
    }

    /**
     * Forget a player's soul mode + authority on quit. Runs on the player's own thread (the quit
     * event), so it FLUSHES the live authority to the gem's PDC before forgetting it — otherwise a
     * deferred write still in flight (or that an unloaded Folia entity will never run) would lose a
     * just-spent balance, refunding the souls. Flush-then-forget makes the durable copy authoritative.
     */
    public void clear(Player player) {
        UUID id = player.getUniqueId();
        modes.active(id).ifPresent(gemId -> {
            persist(player, gemId); // synchronous flush of the live authority (we are on the player's thread)
            ledger.forget(gemId);
        });
        modes.clear(id);
    }

    /** Seed the ledger authority from a gem's durable count (caller is on the gem-holder's thread). */
    private void seed(SoulData gem) {
        ledger.balance(gem.gemId(), new SoulLedger.Balance() {
            @Override
            public int souls() {
                return gem.souls();
            }

            @Override
            public void setSouls(int souls) {
                // seed-only: the authority is being primed, nothing to write back here
            }
        });
    }

    /**
     * A balance whose writes DEFER to {@code player}'s own thread. Its {@code souls()} is never the
     * seed source (the authority is pre-seeded at toggle-on), so it returns 0 defensively. On a debit
     * it schedules a flush of the LIVE authority (not the captured snapshot) to the gem on the
     * player's thread — so concurrent/out-of-order deferred writes all converge to the same truth.
     */
    private SoulLedger.Balance deferredBalance(Player player, UUID gemId) {
        return new SoulLedger.Balance() {
            @Override
            public int souls() {
                return 0; // dead path: authority is seeded at toggle-on; never re-read while active
            }

            @Override
            public void setSouls(int next) {
                Scheduling.onEntity(player, () -> persist(player, gemId));
            }
        };
    }

    /**
     * Flush the live authority for {@code gemId} to {@code player}'s main-hand gem, iff that gem is
     * still held. Reads the authority via {@link SoulLedger#peek} (NO seeding), so a forgotten gem is
     * a no-op rather than a 0-seed; writing the current authority (not a stale snapshot) keeps every
     * deferred write idempotent and convergent. Must run on {@code player}'s own thread.
     */
    private void persist(Player player, UUID gemId) {
        OptionalInt authority = ledger.peek(gemId);
        if (authority.isEmpty()) {
            return; // forgotten (e.g. after quit) — nothing authoritative to write
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        SoulData gem = codec.read(held);
        if (gem != null && gem.gemId().equals(gemId)) {
            codec.write(held, gem.withSouls(authority.getAsInt()));
            player.getInventory().setItemInMainHand(held);
        }
    }
}
