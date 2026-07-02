package feature.soul;

import compile.load.SoulGemConfig;
import compile.load.SoundCue;
import engine.interact.SoulPool;
import engine.interact.SoulSpender;
import engine.sink.SoulDebit;
import engine.stores.SoulModeStore;
import feature.compat.Hands;
import feature.compat.Sounds;
import item.codec.SoulCodec;
import item.codec.SoulData;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import platform.sched.Scheduling;

/**
 * The soul gameplay loop (§6.3, §D). Soul mode is a per-PLAYER ON/OFF that drains the player's gems
 * cross-gem, LEAST-souls gem first — the durable counts live on each gem's {@link SoulData} PDC, and the
 * in-memory {@link SoulPool} is the spend authority over their SUM so gate-10 affordability never reads a
 * cross-region inventory.
 *
 * <p><strong>Threading.</strong> {@link #trySpend} (gate 10, any thread) charges the pool atomically and
 * raises {@code pending}; the physical drain — {@link #drainLeastFirst} — runs on the gem-holder's region
 * thread (deferred via {@link Scheduling#onEntity}), where reading/mutating the inventory is Folia-safe.
 * {@link #flushPending} settles the owed drain; every holder-thread soul operation (maintain tick, deposit,
 * combine, split, toggle-off, quit) flushes first so the durable gem counts it reads are accurate, then
 * {@link SoulPool#resync resyncs} the pool from physical so external pickups/drops are reflected without
 * manufacturing or destroying souls.
 *
 * <p><strong>Zero-gem invariant.</strong> A zero-soul gem may exist ONLY as the player's lone gem
 * ({@link #cleanup}); soul mode auto-disables only when total souls across all gems reaches zero.
 */
public final class SoulService implements SoulDebit, SoulSpender {

    private final SoulPool pool;
    private final SoulModeStore modes;
    private final SoulCodec codec;
    private final Supplier<SoulGemConfig> config;
    private final java.util.function.BooleanSupplier depositOnAnyKill; // read live so a reload can flip it (§D)
    private final item.lang.Messages messages;
    private final feature.fx.ParticleFx particles; // on-activate/deactivate spawns; the aura is SoulParticleDriver
    // §I a line a separate system owns (white/holy PROTECTED, trak counts) that the gem re-render must PRESERVE
    // rather than wipe; default "preserve nothing" keeps the test/fixture forms server-free.
    private final java.util.function.Predicate<String> preservedLoreLine;
    // §D per-player TOTAL souls across all carried gems, refreshed on the holder thread each maintain() tick;
    // read by the PAPI feed (in-memory, thread-safe — never a cross-region inventory read).
    private final ConcurrentHashMap<UUID, Integer> cachedTotal = new ConcurrentHashMap<>();

    /** Soul service with deposit-on-any-kill always on + default messages (the common test/fixture form). */
    public SoulService(SoulPool pool, SoulModeStore modes, SoulCodec codec, Supplier<SoulGemConfig> config) {
        this(pool, modes, codec, config, () -> true);
    }

    /** As above, with a deposit toggle but default messages. */
    public SoulService(SoulPool pool, SoulModeStore modes, SoulCodec codec, Supplier<SoulGemConfig> config,
                       java.util.function.BooleanSupplier depositOnAnyKill) {
        this(pool, modes, codec, config, depositOnAnyKill, item.lang.Messages.defaults());
    }

    /** As above, with messages but no particle fx (the no-fx form). */
    public SoulService(SoulPool pool, SoulModeStore modes, SoulCodec codec, Supplier<SoulGemConfig> config,
                       java.util.function.BooleanSupplier depositOnAnyKill, item.lang.Messages messages) {
        this(pool, modes, codec, config, depositOnAnyKill, messages, feature.fx.ParticleFx.NONE);
    }

    /** Particles-but-no-lore-preservation form (the test/fixture canonical). */
    public SoulService(SoulPool pool, SoulModeStore modes, SoulCodec codec, Supplier<SoulGemConfig> config,
                       java.util.function.BooleanSupplier depositOnAnyKill, item.lang.Messages messages,
                       feature.fx.ParticleFx particles) {
        this(pool, modes, codec, config, depositOnAnyKill, messages, particles, line -> false);
    }

    /** Canonical form (composition root): {@code preservedLoreLine} marks scroll/trak lines to keep on re-render. */
    public SoulService(SoulPool pool, SoulModeStore modes, SoulCodec codec, Supplier<SoulGemConfig> config,
                       java.util.function.BooleanSupplier depositOnAnyKill, item.lang.Messages messages,
                       feature.fx.ParticleFx particles, java.util.function.Predicate<String> preservedLoreLine) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.modes = Objects.requireNonNull(modes, "modes");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.config = Objects.requireNonNull(config, "config");
        this.depositOnAnyKill = Objects.requireNonNull(depositOnAnyKill, "depositOnAnyKill");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.particles = Objects.requireNonNull(particles, "particles");
        this.preservedLoreLine = Objects.requireNonNull(preservedLoreLine, "preservedLoreLine");
    }

    /** {@code NO_GEM}: not holding a gem. {@code NO_SOULS}: held a zero gem — toggle already played the
     *  {@code soul.empty} feedback (F), so the caller adds nothing. {@code ENABLED}/{@code DISABLED}. */
    public enum Toggle { NO_GEM, NO_SOULS, ENABLED, DISABLED }

    public record SplitResult(Status status, int moved, int remaining) {
        public enum Status { OK, NO_GEM, BAD_AMOUNT, TOO_MANY }

        public boolean ok() {
            return status == Status.OK;
        }
    }

    /**
     * Toggle soul mode (§D). MUST run on the player's own thread (reads + mutates their inventory). Enabling
     * seeds the pool from the player's TOTAL souls; disabling flushes any pending spend then drops the pool.
     */
    public Toggle toggle(Player player) {
        SoulGemConfig cfg = config.get();
        UUID id = player.getUniqueId();
        if (modes.active(id).isPresent()) {
            flushPending(player); // settle owed drains to PDC before dropping the pool, else a spend refunds
            modes.deactivate(id);
            pool.disable(id);
            messageLines(player, messages.lines("soul.deactivate"));
            playSounds(player, cfg.sounds().toggleOff());
            particles.spawn(player, cfg.particles().disable());
            return Toggle.DISABLED;
        }
        SoulData held = codec.read(Hands.mainHand(player));
        if (held == null) {
            return Toggle.NO_GEM; // not a gem in hand — the command reports soul.empty
        }
        // F: right-clicking a ZERO-soul gem never enables — fail out instantly with the soul.empty feedback.
        if (held.souls() <= 0) {
            messageLines(player, messages.lines("soul.empty"));
            playSounds(player, cfg.sounds().toggleOff());
            particles.spawn(player, cfg.particles().disable());
            return Toggle.NO_SOULS;
        }
        modes.activate(id, id); // the stored marker is just "on" (the player's own id); the pool holds the souls
        pool.enable(id, totalSouls(player));
        messageLines(player, messages.lines("soul.activate"));
        playSounds(player, cfg.sounds().toggleOn());
        particles.spawn(player, cfg.particles().enable());
        return Toggle.ENABLED;
    }

    /** Send a single message (already colour-translated by {@code Messages.format}); a blank line is skipped. */
    private static void message(Player player, String raw) {
        if (raw != null && !raw.isBlank()) {
            player.sendMessage(raw);
        }
    }

    /** Send a multi-line message (already colour-translated by {@code Messages.lines}); a blank line stays blank. */
    private static void messageLines(Player player, List<String> lines) {
        for (String line : lines) {
            player.sendMessage(line);
        }
    }

    /**
     * Play each configured cue (our {@code { sound, volume, pitch }} bracket form) at the player. The
     * {@code playSound(Location, String, …)} string overload takes a namespaced/registry sound key, dodging the
     * {@code Sound} constant/interface break at 1.21.3 — no resolver needed. Runs on the player's own thread.
     */
    private void playSounds(Player player, List<SoundCue> cues) {
        for (SoundCue cue : cues) {
            Sounds.play(player, player.getLocation(), cue.name(), cue.volume(), cue.pitch());
        }
    }

    /**
     * Mint a fresh soul gem (new identity, 0 souls) — a DISTINCT configured item, NOT a stamp onto held gear
     * (§D). Pure construction, so Folia-safe from any thread.
     */
    public ItemStack mintGem() {
        return mintGemStack(SoulData.fresh(UUID.randomUUID()));
    }

    /** Mint a fresh soul gem already carrying {@code souls} (§J {@code /se give gem <player> [amount]}). */
    public ItemStack mintGem(int souls) {
        return mintGemStack(new SoulData(UUID.randomUUID(), Math.max(0, souls)));
    }

    private ItemStack mintGemStack(SoulData data) {
        SoulGemConfig cfg = config.get();
        ItemStack gem = ItemFactory.buildItem(
                cfg.material(), Material.EMERALD,
                renderGemName(cfg, data.souls()),
                renderGemLore(cfg, data.souls()));
        codec.write(gem, data);
        return gem;
    }

    public boolean isGem(ItemStack stack) {
        return codec.read(stack) != null;
    }

    /** Render gem lore from config + count; rendered from state, never parsed back (§4.2). Pure (no Bukkit). */
    static List<String> renderGemLore(SoulGemConfig cfg, int souls) {
        String amount = Integer.toString(souls);
        String soulColor = cfg.colorFor(souls);
        List<String> out = new ArrayList<>(cfg.lore().size());
        for (String line : cfg.lore()) {
            out.add(subSoul(line, amount, soulColor));
        }
        return out;
    }

    /**
     * Render the gem's display NAME from config + count — the name carries the live soul count (EE likeness),
     * so it is re-rendered alongside the lore on every deposit/spend/split/combine. Pure (no Bukkit).
     */
    static String renderGemName(SoulGemConfig cfg, int souls) {
        return subSoul(cfg.name(), Integer.toString(souls), cfg.colorFor(souls));
    }

    /** Substitute the soul placeholders {@code {AMOUNT}} (the count) and {@code {SOUL-COLOR}} (its tier colour). */
    private static String subSoul(String s, String amount, String soulColor) {
        return s.replace("{AMOUNT}", amount).replace("{SOUL-COLOR}", soulColor);
    }

    /**
     * Deposit souls into the killer's carried gem (per-mob amount, else flat per-kill) on ANY kill, regardless
     * of soul mode (§D). Defers to the killer's thread (death fires on the victim's region) where reading their
     * inventory is region-safe.
     */
    public void onKill(Player killer, org.bukkit.entity.EntityType victimType) {
        if (!depositOnAnyKill.getAsBoolean()) {
            return; // §D toggle off
        }
        int amount = config.get().soulsFor(victimType == null ? null : victimType.name());
        if (amount <= 0) {
            return;
        }
        Scheduling.onEntity(killer, () -> creditKill(killer, amount));
    }

    /** Credit {@code amount} to the killer's carried gem (durable PDC), then keep the pool in sync. Killer thread. */
    private void creditKill(Player killer, int amount) {
        PlayerInventory inv = killer.getInventory();
        int slot = locateGemSlot(inv);
        if (slot < 0) {
            return;
        }
        SoulData data = codec.read(inv.getItem(slot));
        if (data == null) {
            return;
        }
        if (modes.active(killer.getUniqueId()).isPresent()) {
            flushPending(killer); // settle owed drains first, then credit + resync so the pool reflects the deposit
            data = codec.read(inv.getItem(slot)); // re-read: a flush may have changed this gem's count
            if (data == null) {
                return;
            }
        }
        writeGem(inv, slot, data.withSouls(data.souls() + amount));
        if (modes.active(killer.getUniqueId()).isPresent()) {
            pool.resync(killer.getUniqueId(), totalSouls(killer));
        }
    }

    /**
     * Combine two gems into a fresh gem holding the SUM of their souls (§D). {@code null} if either stack is not
     * a gem. MUST run on {@code player}'s own thread. Souls are conserved, so the pool needs no adjustment beyond
     * the {@link #flushPending} that settles the source counts before they are read.
     */
    public ItemStack combine(Player player, ItemStack a, ItemStack b) {
        if (modes.active(player.getUniqueId()).isPresent()) {
            flushPending(player); // settle any owed drain so the gems' durable counts are accurate before summing
        }
        SoulData da = codec.read(a);
        SoulData db = codec.read(b);
        if (da == null || db == null) {
            return null;
        }
        long sum = (long) da.souls() + db.souls();
        int total = (int) Math.min(Integer.MAX_VALUE, sum);
        ItemStack gem = mintGemStack(new SoulData(UUID.randomUUID(), total));
        playSounds(player, config.get().sounds().combine());
        return gem;
    }

    /**
     * Split {@code amount} souls off the main-hand gem into a new gem (never auto-split — only via
     * {@code /se split}); overflow dropped. Souls are conserved. MUST run on {@code player}'s own thread.
     */
    public SplitResult split(Player player, int amount) {
        if (amount <= 0) {
            return new SplitResult(SplitResult.Status.BAD_AMOUNT, amount, 0);
        }
        if (modes.active(player.getUniqueId()).isPresent()) {
            flushPending(player); // settle owed drains so the held gem's durable count is accurate before carving
        }
        PlayerInventory inv = player.getInventory();
        int held = inv.getHeldItemSlot();
        SoulData data = codec.read(inv.getItem(held));
        if (data == null) {
            return new SplitResult(SplitResult.Status.NO_GEM, amount, 0);
        }
        int have = data.souls();
        if (amount >= have) {
            return new SplitResult(SplitResult.Status.TOO_MANY, amount, have); // never split everything away
        }
        int remain = have - amount;
        writeGem(inv, held, data.withSouls(remain));
        ItemStack fresh = mintGemStack(new SoulData(UUID.randomUUID(), amount));
        inv.addItem(fresh).values()
                .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        playSounds(player, config.get().sounds().split());
        return new SplitResult(SplitResult.Status.OK, amount, remain);
    }

    /** Soul context for one activation — present iff the actor is in soul mode (the marker rides to gate 10/§D). */
    public Optional<SoulBinding> bindingFor(Player actor) {
        return modes.active(actor.getUniqueId()).map(marker -> new SoulBinding(actor.getUniqueId()));
    }

    /**
     * Gate-10 spend (§D / {@link SoulSpender}). Charges {@code player}'s cross-gem pool atomically (any thread);
     * on success the physical drain + the {@code soul.soul-use} feedback are deferred to the holder's region
     * thread. {@code false} when not in soul mode or the pool can't afford the full cost.
     */
    @Override
    public boolean trySpend(UUID player, int cost) {
        if (cost <= 0) {
            return true;
        }
        if (!pool.trySpend(player, cost)) {
            return false;
        }
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            Scheduling.onEntity(p, () -> {
                flushPending(p);
                soulUseFeedback(p);
            });
        }
        return true;
    }

    /**
     * {@code REMOVE_SOULS} drain (§D, {@link SoulDebit}). Drains {@code amount} from {@code holder}'s gems
     * least-first; no-op when the holder is not in soul mode. MUST run on {@code holder}'s own thread.
     */
    @Override
    public void debit(Player holder, UUID gemId, int amount) {
        if (amount <= 0 || modes.active(holder.getUniqueId()).isEmpty()) {
            return;
        }
        flushPending(holder); // settle gate-10 spends first so this drain composes on the accurate physical total
        drainLeastFirst(holder, amount);
        pool.resync(holder.getUniqueId(), totalSouls(holder));
    }

    /**
     * Drain the target's OWN gems (the {@code REMOVE_SOULS:…:@Victim} drain-the-enemy case). Runs on the target's
     * thread (the sink routes it there); no-op if the target is not in soul mode.
     */
    @Override
    public void debitTarget(Player target, int amount) {
        if (modes.active(target.getUniqueId()).isPresent()) {
            debit(target, target.getUniqueId(), amount);
        }
    }

    /** Forget a player's soul mode + pool on quit, flushing any owed drain to PDC first (else a spend refunds). */
    public void clear(Player player) {
        UUID id = player.getUniqueId();
        if (modes.active(id).isPresent()) {
            flushPending(player);
        }
        modes.clear(id);
        pool.disable(id);
    }

    /**
     * Per-tick maintenance on the player's OWN region thread. Settles any owed gate-10 drain, enforces the
     * zero-gem cleanup invariant (for every player), refreshes the cached total, and — in soul mode — keeps the
     * pool in sync or AUTO-DISABLES (with the disable feedback) when no souls remain anywhere.
     */
    public void maintain(Player player) {
        UUID id = player.getUniqueId();
        boolean active = modes.active(id).isPresent();
        if (active) {
            flushPending(player);
        }
        cleanup(player);
        int total = totalSouls(player);
        cachedTotal.put(id, total);
        if (!active) {
            return;
        }
        if (total <= 0) {
            modes.deactivate(id);
            pool.disable(id);
            SoulGemConfig cfg = config.get();
            messageLines(player, messages.lines("soul.empty"));
            playSounds(player, cfg.sounds().toggleOff());
            particles.spawn(player, cfg.particles().disable());
        } else {
            pool.resync(id, total);
        }
    }

    /** The player's last-known TOTAL souls across all gems — the PAPI feed (in-memory, any thread). */
    public int soulTotal(UUID player) {
        return cachedTotal.getOrDefault(player, 0);
    }

    /** Drop the cached soul total on quit — the eviction the EngineStoreListener wires as the one quit authority (§5.4). */
    public void evictCache(UUID player) {
        cachedTotal.remove(player);
    }

    /**
     * Drain UP TO {@code amount} souls from {@code player}'s gems, LEAST-souls gem first, spilling to the next as
     * each empties; then enforce the cleanup invariant (a gem drained to zero is removed unless it is the lone
     * gem). Holder thread. Returns the amount actually drained.
     */
    private int drainLeastFirst(Player player, int amount) {
        PlayerInventory inv = player.getInventory();
        List<GemView> gems = new ArrayList<>(gemViews(player));
        gems.sort(Comparator.comparingInt(GemView::souls).thenComparingInt(GemView::slot));
        int remaining = amount;
        for (GemView gem : gems) {
            if (remaining <= 0) {
                break;
            }
            if (gem.souls() <= 0) {
                continue;
            }
            int take = Math.min(remaining, gem.souls());
            writeGem(inv, gem.slot(), new SoulData(gem.gemId(), gem.souls() - take));
            remaining -= take;
        }
        cleanup(player);
        return amount - remaining;
    }

    /** Drain to physical any souls spent at gate 10 but not yet settled (idempotent). Holder thread. */
    private void flushPending(Player player) {
        int pending = pool.takePending(player.getUniqueId());
        if (pending > 0) {
            drainLeastFirst(player, pending);
        }
    }

    /** The per-spend feedback: the {@code soul.soul-use} line (the new TOTAL) + use sound + use particle. */
    private void soulUseFeedback(Player player) {
        SoulGemConfig cfg = config.get();
        message(player, messages.format("soul.soul-use", "AMOUNT", pool.total(player.getUniqueId())));
        playSounds(player, cfg.sounds().use());
        particles.spawn(player, cfg.particles().use());
    }

    /**
     * Enforce the zero-gem inventory invariant (§D): a zero-soul gem may exist ONLY as the player's lone gem.
     * Remove every zero gem when any NONZERO gem is present; else keep exactly one zero gem (the lone gem is never
     * destroyed) and drop the rest. The which-slots decision is the pure {@link #redundantZeroSlots}.
     */
    private void cleanup(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int slot : redundantZeroSlots(gemViews(player))) {
            inv.setItem(slot, null); // a redundant zero gem (qty 1) — remove it
        }
    }

    /** Snapshot of the player's carried soul gems (slot + identity + durable souls). Holder thread. */
    private List<GemView> gemViews(Player player) {
        List<GemView> out = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            SoulData data = codec.read(contents[i]);
            if (data != null) {
                out.add(new GemView(i, data.gemId(), data.souls()));
            }
        }
        return out;
    }

    /** Total souls across all carried gems. Holder thread (reads the inventory). */
    private int totalSouls(Player player) {
        long sum = 0;
        for (GemView gem : gemViews(player)) {
            sum += gem.souls();
        }
        return (int) Math.min(Integer.MAX_VALUE, sum);
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
        reRenderGem(stack, data.souls());
        inv.setItem(slot, stack);
    }

    /** Re-render the gem's name + lore from config + the new count, PRESERVING scroll/trak lines. Holder thread. */
    @SuppressWarnings("deprecation") // setDisplayName/setLore(List): the floor-stable item-meta path
    private void reRenderGem(ItemStack gem, int souls) {
        ItemMeta meta = gem.getItemMeta();
        if (meta == null) {
            return;
        }
        SoulGemConfig cfg = config.get();
        String name = renderGemName(cfg, souls);
        if (name != null && !name.isBlank()) {
            meta.setDisplayName(ItemFactory.color(name));
        }
        // Preserve applied-scroll PROTECTED lines + trak lines: a separate system owns them, so the gem-body
        // re-render must KEEP them rather than wipe them (§I robust composition — the white/holy scroll line fix).
        List<String> preserved = new ArrayList<>();
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                if (preservedLoreLine.test(line)) {
                    preserved.add(line);
                }
            }
        }
        // Wrap exactly as the mint path does (ItemFactory.buildItem), else the gem lore visibly unwraps the first
        // time the count changes (mint wraps; this re-render must too).
        List<String> body = ItemFactory.wrapLore(renderGemLore(cfg, souls));
        List<String> lore = new ArrayList<>();
        if (body != null) {
            for (String line : body) {
                lore.add(ItemFactory.color(line));
            }
        }
        lore.addAll(preserved); // already colour-translated when the scroll/trak stamped them
        meta.setLore(lore.isEmpty() ? null : lore);
        gem.setItemMeta(meta);
    }

    /** A carried soul gem (inventory slot, gem identity, current souls) — the unit the pure selectors work over. */
    record GemView(int slot, UUID gemId, int souls) {
    }

    /**
     * The inventory slots whose zero-soul gems are REDUNDANT and must be removed (§D invariant). Pure: when any
     * gem still has souls every zero gem is redundant; otherwise all zero gems EXCEPT the lowest-slot one (the
     * lone gem, never destroyed) are redundant. A no-zero / single-lone-zero inventory yields nothing.
     */
    static List<Integer> redundantZeroSlots(List<GemView> gems) {
        boolean anyNonzero = gems.stream().anyMatch(g -> g.souls() > 0);
        List<Integer> zeros = gems.stream().filter(g -> g.souls() == 0)
                .map(GemView::slot).sorted().collect(java.util.stream.Collectors.toList());
        if (zeros.isEmpty()) {
            return List.of();
        }
        return anyNonzero ? zeros : zeros.subList(1, zeros.size()); // keep the lone zero gem when no other exists
    }

    /** The least-souls NONZERO gem (the drain target), ties broken by lowest slot. Pure. */
    static Optional<GemView> leastNonzero(List<GemView> gems) {
        return gems.stream().filter(g -> g.souls() > 0)
                .min(Comparator.comparingInt(GemView::souls).thenComparingInt(GemView::slot));
    }
}
