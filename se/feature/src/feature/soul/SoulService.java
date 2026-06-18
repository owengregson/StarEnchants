package feature.soul;

import compile.load.SoulGemConfig;
import engine.interact.SoulLedger;
import engine.stores.SoulModeStore;
import item.codec.SoulCodec;
import item.codec.SoulData;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import platform.sched.Scheduling;

/**
 * The soul gameplay loop (docs/architecture.md §6.3, docs/v3-directives.md §D) — the binding between the
 * durable on-item {@link SoulData} and the in-memory {@link SoulLedger} authority, made Folia-correct.
 *
 * <p><strong>The threading discipline.</strong> The ledger's in-memory authority is the live truth
 * during a session; the gem's PDC is the durable backing. The authority is seeded ONCE when a player
 * toggles soul mode on (run on the player's own thread, where reading their held gem is region-safe).
 * From then on every read uses the authority — never a live cross-region item read. A spend debits the
 * authority synchronously under the ledger's stripe lock (any thread), and the durable PDC write is
 * DEFERRED to the gem-holder's own entity thread (combat fires on the victim's region, but the gem is
 * the attacker's — writing it inline would be a cross-region mutation on Folia).
 *
 * <p><strong>Deposit on any kill.</strong> Souls now accrue on EVERY kill regardless of soul-mode state
 * (a new mechanic — no original had per-kill deposit): {@link #onKill} defers to the killer's own thread,
 * locates the gem they carry (main hand first, then the rest of the inventory), and credits it — through
 * the ledger when that gem is the seeded active one, else straight to its PDC. Spending stays gated by
 * soul mode (the pipeline's gate 10 / {@code REMOVE_SOULS}). {@link #combine} sums two gems into a fresh
 * one and {@link #split} carves a count off the held gem into a new one — both reconciling the ledger and
 * any active mode whose gem identity they retire.
 */
public final class SoulService {

    private final SoulLedger ledger;
    private final SoulModeStore modes;
    private final SoulCodec codec;
    private final Supplier<SoulGemConfig> config;
    private final java.util.function.BooleanSupplier depositOnAnyKill; // §D config.yml souls.deposit-on-any-kill
    private final item.lang.Messages messages; // §L lang.yml — the soul-mode messages

    /** Soul service with deposit-on-any-kill always on + default messages (the common test/fixture form). */
    public SoulService(SoulLedger ledger, SoulModeStore modes, SoulCodec codec, Supplier<SoulGemConfig> config) {
        this(ledger, modes, codec, config, () -> true);
    }

    /** As above, with a deposit toggle but default messages. */
    public SoulService(SoulLedger ledger, SoulModeStore modes, SoulCodec codec, Supplier<SoulGemConfig> config,
                       java.util.function.BooleanSupplier depositOnAnyKill) {
        this(ledger, modes, codec, config, depositOnAnyKill, item.lang.Messages.defaults());
    }

    /**
     * Canonical form (the composition root): {@code depositOnAnyKill} (config.yml {@code souls.deposit-on-any-kill},
     * §D) is read live, so a {@code /se reload} can switch the deposit-on-kill mechanic off without restarting;
     * {@code messages} sources the soul-mode chat from {@code lang.yml} (§L).
     */
    public SoulService(SoulLedger ledger, SoulModeStore modes, SoulCodec codec, Supplier<SoulGemConfig> config,
                       java.util.function.BooleanSupplier depositOnAnyKill, item.lang.Messages messages) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.modes = Objects.requireNonNull(modes, "modes");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.config = Objects.requireNonNull(config, "config");
        this.depositOnAnyKill = Objects.requireNonNull(depositOnAnyKill, "depositOnAnyKill");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** The result of {@link #toggle}, for the command to relay. */
    public enum Toggle { NO_GEM, ENABLED, DISABLED }

    /** The outcome of {@link #split}, for the command to relay. */
    public record SplitResult(Status status, int moved, int remaining) {
        public enum Status { OK, NO_GEM, BAD_AMOUNT, TOO_MANY }

        public boolean ok() {
            return status == Status.OK;
        }
    }

    /**
     * Toggle soul mode for {@code player} based on the gem in their main hand. MUST run on the
     * player's own thread (reads the held item). Enabling seeds the ledger authority with the gem's
     * stored count so no later read crosses a region.
     */
    public Toggle toggle(Player player) {
        SoulGemConfig cfg = config.get();
        SoulData gem = codec.read(player.getInventory().getItemInMainHand());
        if (gem == null) {
            return Toggle.NO_GEM;
        }
        UUID id = player.getUniqueId();
        if (modes.active(id).filter(active -> active.equals(gem.gemId())).isPresent()) {
            // Flush the live authority to PDC BEFORE forgetting it (we are on the holder's thread): else a
            // spend whose deferred write is still in flight would find the authority gone and no-op, leaving
            // the durable count un-debited — the souls would refund (a dupe). Same flush-then-forget as clear().
            persist(player, gem.gemId());
            modes.deactivate(id);
            ledger.forget(gem.gemId());
            message(player, messages.format("soul.deactivate"));
            playSound(player, cfg.soundDeactivate());
            return Toggle.DISABLED;
        }
        modes.activate(id, gem.gemId());
        seed(gem); // prime the authority from the durable count on this (player) thread
        message(player, messages.format("soul.activate"));
        playSound(player, cfg.soundActivate());
        return Toggle.ENABLED;
    }

    /** Send a configured soul-gem message (legacy {@code &} colour codes translated), unless blank. */
    private static void message(Player player, String raw) {
        if (raw != null && !raw.isBlank()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', raw));
        }
    }

    /**
     * Play a gem sound for {@code player} at their location, iff sounds are enabled and the token is set.
     * Uses the cross-version {@code playSound(Location, String, …)} overload — a namespaced sound key
     * works on every version in the range, so no {@code Sound} resolver is needed (the constant/interface
     * break at 1.21.3 never enters the picture). Must run on the player's own thread.
     */
    private void playSound(Player player, String token) {
        SoulGemConfig cfg = config.get();
        if (cfg.sounds() && token != null && !token.isBlank()) {
            player.playSound(player.getLocation(), token, 1.0f, 1.0f);
        }
    }

    /**
     * Mint a fresh soul gem ITEM from the configured likeness (a new identity, 0 souls) — a DISTINCT
     * configured item (material / name / lore), NOT a stamp onto held gear (docs/v3-directives.md §D).
     * Pure construction (no entity read), so it is Folia-safe to call from any thread; the caller GIVES
     * the stack on the player's own thread.
     */
    public ItemStack mintGem() {
        return mintGemStack(SoulData.fresh(UUID.randomUUID()));
    }

    /** Build a gem ITEM carrying exactly {@code data} (identity + count) from the configured likeness. */
    private ItemStack mintGemStack(SoulData data) {
        SoulGemConfig cfg = config.get();
        ItemStack gem = ItemFactory.build(
                ItemFactory.material(cfg.material(), Material.EMERALD),
                cfg.name(),
                renderGemLore(cfg, data.souls()));
        codec.write(gem, data);
        return gem;
    }

    /** Whether {@code stack} is a soul gem (carries {@link SoulData}). */
    public boolean isGem(ItemStack stack) {
        return codec.read(stack) != null;
    }

    /**
     * Render the gem's lore from config + the current soul count — {@code {AMOUNT}} → the count,
     * {@code {SOUL-COLOR}} → the configured count-tiered {@code &}-colour ({@link SoulGemConfig#colorFor}).
     * Lore is rendered from state, never parsed back (item-data-model §4.2). Pure (no Bukkit) —
     * {@link ItemFactory} colours the returned lines.
     */
    static List<String> renderGemLore(SoulGemConfig cfg, int souls) {
        String amount = Integer.toString(souls);
        String soulColor = cfg.colorFor(souls);
        List<String> out = new ArrayList<>(cfg.lore().size());
        for (String line : cfg.lore()) {
            out.add(line.replace("{AMOUNT}", amount).replace("{SOUL-COLOR}", soulColor));
        }
        return out;
    }

    /**
     * Deposit souls into {@code killer}'s carried gem for a kill of {@code victimType} (per-mob amount,
     * else the flat per-kill amount) — on ANY kill, regardless of soul mode (docs/v3-directives.md §D).
     * Defers to the killer's own thread (a death fires on the victim's region) where reading their
     * inventory is region-safe; a no-op if the configured amount is ≤ 0 or they carry no gem.
     */
    public void onKill(Player killer, org.bukkit.entity.EntityType victimType) {
        if (!depositOnAnyKill.getAsBoolean()) {
            return; // §D deposit-on-kill disabled in config.yml (souls.deposit-on-any-kill: false)
        }
        int amount = config.get().soulsFor(victimType == null ? null : victimType.name());
        if (amount <= 0) {
            return;
        }
        Scheduling.onEntity(killer, () -> creditKill(killer, amount));
    }

    /** Credit {@code amount} to the killer's carried gem. MUST run on the killer's own thread. */
    private void creditKill(Player killer, int amount) {
        PlayerInventory inv = killer.getInventory();
        int slot = locateGemSlot(inv);
        if (slot < 0) {
            return; // the killer carries no gem
        }
        SoulData data = codec.read(inv.getItem(slot));
        if (data == null) {
            return;
        }
        UUID gemId = data.gemId();
        if (ledger.peek(gemId).isPresent()) {
            // The seeded active gem — credit through the ledger so the live authority stays correct;
            // the write-through persists the new count to the gem wherever it sits (we are on the killer's thread).
            ledger.deposit(gemId, balanceFor(killer, gemId), amount);
        } else {
            writeGem(inv, slot, data.withSouls(data.souls() + amount));
        }
    }

    /**
     * Combine two gems into a fresh gem carrying the SUM of their souls (docs/v3-directives.md §D) —
     * the authoritative count for a seeded (active) source, else its durable count. Both source
     * identities are retired (forgotten from the ledger; soul mode silently turns off if it pointed at
     * one), so the new gem's identity is the only survivor. Returns the new gem, or {@code null} if
     * either stack is not a gem. MUST run on {@code player}'s own thread (mutates state + plays a sound).
     */
    public ItemStack combine(Player player, ItemStack a, ItemStack b) {
        SoulData da = codec.read(a);
        SoulData db = codec.read(b);
        if (da == null || db == null) {
            return null;
        }
        long sum = (long) authoritativeSouls(da.gemId(), da.souls()) + authoritativeSouls(db.gemId(), db.souls());
        int total = (int) Math.min(Integer.MAX_VALUE, sum);
        retire(player, da.gemId());
        retire(player, db.gemId());
        ItemStack gem = mintGemStack(new SoulData(UUID.randomUUID(), total));
        playSound(player, config.get().soundCombine());
        return gem;
    }

    /**
     * Split {@code amount} souls off the gem in {@code player}'s main hand into a NEW gem (never
     * auto-split — only via {@code /se split}). The held gem keeps the remainder; the new gem (a fresh
     * identity) is given to the player (overflow dropped). Routes the debit through the ledger when the
     * held gem is the seeded active one, so the live authority stays correct. MUST run on {@code player}'s
     * own thread.
     */
    public SplitResult split(Player player, int amount) {
        if (amount <= 0) {
            return new SplitResult(SplitResult.Status.BAD_AMOUNT, amount, 0);
        }
        PlayerInventory inv = player.getInventory();
        int held = inv.getHeldItemSlot();
        SoulData data = codec.read(inv.getItem(held));
        if (data == null) {
            return new SplitResult(SplitResult.Status.NO_GEM, amount, 0);
        }
        UUID gemId = data.gemId();
        OptionalInt seeded = ledger.peek(gemId);
        int have = seeded.isPresent() ? seeded.getAsInt() : data.souls();
        if (amount >= have) {
            return new SplitResult(SplitResult.Status.TOO_MANY, amount, have); // never split everything away
        }
        int remain;
        if (seeded.isPresent()) {
            // The `have` above was a lock-free peek; a concurrent spend on another region thread could have
            // dropped the authority below `amount` since. tryConsume re-checks atomically under the stripe
            // lock — only mint the carved gem if the debit ACTUALLY happened, else no souls are created.
            if (!ledger.tryConsume(gemId, balanceFor(player, gemId), amount)) {
                return new SplitResult(SplitResult.Status.TOO_MANY, amount, ledger.peek(gemId).orElse(0));
            }
            remain = ledger.peek(gemId).orElse(have - amount);
        } else {
            remain = have - amount;
            writeGem(inv, held, data.withSouls(remain));
        }
        ItemStack fresh = mintGemStack(new SoulData(UUID.randomUUID(), amount));
        inv.addItem(fresh).values()
                .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        return new SplitResult(SplitResult.Status.OK, amount, remain);
    }

    /** The soul binding for {@code actor}'s active gem, or empty if soul mode is off for them. */
    public Optional<SoulBinding> bindingFor(Player actor) {
        return modes.active(actor.getUniqueId())
                .map(gemId -> new SoulBinding(gemId, deferredBalance(actor, gemId)));
    }

    /**
     * Debit {@code amount} souls from {@code holder}'s gem {@code gemId} — the {@code REMOVE_SOULS} effect's
     * collaborator (engine-side {@link engine.sink.SoulDebit}, docs/v3-directives.md §D). Charges the
     * in-memory authority atomically ({@link SoulLedger#tryConsume}) and write-throughs the new count to the
     * gem's PDC <em>wherever it sits</em> via {@link #balanceFor} (NOT a main-hand assumption — the gem is
     * usually in the bag during combat). MUST run on {@code holder}'s own thread (the {@code DispatchSink}
     * routes it there). A non-positive amount, or a gem that is not the seeded active one, is a no-op — souls
     * are only ever spent against the seeded active gem, so a stale PDC-only gem is never silently drained.
     */
    public void debit(Player holder, UUID gemId, int amount) {
        if (amount <= 0 || ledger.peek(gemId).isEmpty()) {
            return; // free, or the gem is not seeded/active → nothing to spend
        }
        ledger.tryConsume(gemId, balanceFor(holder, gemId), amount);
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

    /** The authoritative count for {@code gemId} (the live ledger value when seeded, else {@code durable}). */
    private int authoritativeSouls(UUID gemId, int durable) {
        return ledger.peek(gemId).orElse(durable);
    }

    /** Retire a gem identity being destroyed: drop any soul mode pointing at it, forget its authority. */
    private void retire(Player player, UUID gemId) {
        if (modes.active(player.getUniqueId()).filter(gemId::equals).isPresent()) {
            modes.deactivate(player.getUniqueId()); // its identity is gone; spending can't continue against it
        }
        ledger.forget(gemId);
    }

    /** The main-hand gem slot if it holds one, else the first inventory slot carrying a gem, else {@code -1}. */
    private int locateGemSlot(PlayerInventory inv) {
        int held = inv.getHeldItemSlot();
        if (codec.read(inv.getItem(held)) != null) {
            return held;
        }
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (codec.read(contents[i]) != null) {
                return i;
            }
        }
        return -1;
    }

    /** Write {@code data} onto the gem at {@code slot} and re-render its lore. Caller is on the holder's thread. */
    private void writeGem(PlayerInventory inv, int slot, SoulData data) {
        ItemStack stack = inv.getItem(slot);
        if (codec.read(stack) == null) {
            return; // the gem moved out from under us — skip rather than stamp the wrong item
        }
        codec.write(stack, data);
        reRenderGemLore(stack, data.souls());
        inv.setItem(slot, stack);
    }

    /**
     * A ledger {@link SoulLedger.Balance} that write-throughs the gem identified by {@code gemId}
     * WHEREVER it sits in {@code holder}'s inventory — located by identity, not a fixed slot — so a spend
     * or deposit persists even when the active gem is not in the main hand (the common case in combat: you
     * hold the weapon, the gem is in the bag). {@code setSouls} must run on the holder's own thread.
     */
    private SoulLedger.Balance balanceFor(Player holder, UUID gemId) {
        return new SoulLedger.Balance() {
            @Override
            public int souls() {
                return 0; // peek is present whenever this is used; never the seed source
            }

            @Override
            public void setSouls(int next) {
                writeAuthorityTo(holder, gemId, next);
            }
        };
    }

    /** Write {@code next} onto the gem identified by {@code gemId} wherever it sits, re-rendering its lore. */
    private void writeAuthorityTo(Player holder, UUID gemId, int next) {
        PlayerInventory inv = holder.getInventory();
        int slot = locateGemSlotById(inv, gemId);
        if (slot < 0) {
            return; // the gem is no longer in the inventory (dropped/destroyed) — nothing to write
        }
        ItemStack stack = inv.getItem(slot);
        SoulData cur = codec.read(stack);
        if (cur != null && cur.gemId().equals(gemId)) {
            codec.write(stack, cur.withSouls(next));
            reRenderGemLore(stack, next);
            inv.setItem(slot, stack);
        }
    }

    /** The slot of the gem whose identity is {@code gemId}, or {@code -1} if it is not in the inventory. */
    private int locateGemSlotById(PlayerInventory inv, UUID gemId) {
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            SoulData data = codec.read(contents[i]);
            if (data != null && data.gemId().equals(gemId)) {
                return i;
            }
        }
        return -1;
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
     * player's thread — so concurrent/out-of-order deferred writes all converge to the same truth — and
     * sends the configured soul-use message with the remaining count.
     */
    private SoulLedger.Balance deferredBalance(Player player, UUID gemId) {
        return new SoulLedger.Balance() {
            @Override
            public int souls() {
                return 0; // dead path: authority is seeded at toggle-on; never re-read while active
            }

            @Override
            public void setSouls(int next) {
                Scheduling.onEntity(player, () -> {
                    persist(player, gemId);
                    message(player, messages.format("soul.soul-use", "AMOUNT", next));
                });
            }
        };
    }

    /**
     * Flush the live authority for {@code gemId} to the gem WHEREVER it sits in {@code player}'s inventory
     * (located by identity — NOT assumed to be in the main hand), iff it is still carried. Reads the
     * authority via {@link SoulLedger#peek} (NO seeding), so a forgotten gem is a no-op rather than a
     * 0-seed; writing the current authority (not a stale snapshot) keeps every deferred write idempotent
     * and convergent. Must run on {@code player}'s own thread.
     */
    private void persist(Player player, UUID gemId) {
        OptionalInt authority = ledger.peek(gemId);
        if (authority.isPresent()) {
            writeAuthorityTo(player, gemId, authority.getAsInt());
        }
    }

    /**
     * Re-render the held gem's lore (name unchanged) from config + the new count. Must run on the
     * holder's own thread. A null meta (impossible for a real gem) is a no-op.
     */
    @SuppressWarnings("deprecation") // setLore(List): the floor-stable item-meta path
    private void reRenderGemLore(ItemStack gem, int souls) {
        ItemMeta meta = gem.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> lore = renderGemLore(config.get(), souls);
        meta.setLore(lore.isEmpty() ? null : lore.stream().map(ItemFactory::color).toList());
        gem.setItemMeta(meta);
    }
}
