package feature.soul;

import compile.load.SoulGemConfig;
import engine.interact.SoulLedger;
import engine.sink.SoulDebit;
import engine.stores.SoulModeStore;
import feature.compat.Hands;
import feature.compat.Sounds;
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
 * The soul gameplay loop (§6.3, §D) — the binding between the durable on-item {@link SoulData} and the
 * in-memory {@link SoulLedger} authority, made Folia-correct.
 *
 * <p><strong>Threading discipline.</strong> The ledger is the live truth during a session; the gem's PDC
 * is the durable backing. The authority is seeded ONCE at toggle-on (player's own thread, where reading
 * their held gem is region-safe); thereafter reads use the authority, never a live cross-region item read.
 * A spend debits synchronously under the ledger's stripe lock (any thread), but the PDC write is DEFERRED
 * to the gem-holder's entity thread — combat fires on the victim's region while the gem is the attacker's,
 * so an inline write would be a cross-region mutation on Folia.
 *
 * <p><strong>Deposit on any kill.</strong> Souls accrue on EVERY kill regardless of soul-mode state
 * ({@link #onKill}); spending stays gated by soul mode (gate 10 / {@code REMOVE_SOULS}). {@link #combine}
 * and {@link #split} reconcile the ledger and any active mode whose gem identity they retire.
 */
public final class SoulService implements SoulDebit {

    private final SoulLedger ledger;
    private final SoulModeStore modes;
    private final SoulCodec codec;
    private final Supplier<SoulGemConfig> config;
    private final java.util.function.BooleanSupplier depositOnAnyKill; // read live so a reload can flip it (§D)
    private final item.lang.Messages messages;
    private final feature.fx.ParticleFx particles; // on-activate/deactivate spawns; the aura is SoulParticleDriver

    /** Soul service with deposit-on-any-kill always on + default messages (the common test/fixture form). */
    public SoulService(SoulLedger ledger, SoulModeStore modes, SoulCodec codec, Supplier<SoulGemConfig> config) {
        this(ledger, modes, codec, config, () -> true);
    }

    /** As above, with a deposit toggle but default messages. */
    public SoulService(SoulLedger ledger, SoulModeStore modes, SoulCodec codec, Supplier<SoulGemConfig> config,
                       java.util.function.BooleanSupplier depositOnAnyKill) {
        this(ledger, modes, codec, config, depositOnAnyKill, item.lang.Messages.defaults());
    }

    /** As above, with messages but no particle fx (the no-fx form). */
    public SoulService(SoulLedger ledger, SoulModeStore modes, SoulCodec codec, Supplier<SoulGemConfig> config,
                       java.util.function.BooleanSupplier depositOnAnyKill, item.lang.Messages messages) {
        this(ledger, modes, codec, config, depositOnAnyKill, messages, feature.fx.ParticleFx.NONE);
    }

    /** Canonical form (composition root). */
    public SoulService(SoulLedger ledger, SoulModeStore modes, SoulCodec codec, Supplier<SoulGemConfig> config,
                       java.util.function.BooleanSupplier depositOnAnyKill, item.lang.Messages messages,
                       feature.fx.ParticleFx particles) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.modes = Objects.requireNonNull(modes, "modes");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.config = Objects.requireNonNull(config, "config");
        this.depositOnAnyKill = Objects.requireNonNull(depositOnAnyKill, "depositOnAnyKill");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.particles = Objects.requireNonNull(particles, "particles");
    }

    public enum Toggle { NO_GEM, ENABLED, DISABLED }

    public record SplitResult(Status status, int moved, int remaining) {
        public enum Status { OK, NO_GEM, BAD_AMOUNT, TOO_MANY }

        public boolean ok() {
            return status == Status.OK;
        }
    }

    /**
     * Toggle soul mode from the main-hand gem. MUST run on the player's own thread (reads the held item).
     * Enabling seeds the ledger authority from the gem's count so no later read crosses a region.
     */
    public Toggle toggle(Player player) {
        SoulGemConfig cfg = config.get();
        SoulData gem = codec.read(Hands.mainHand(player));
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
            particles.spawn(player, cfg.particlesDeactivate(), 1);
            return Toggle.DISABLED;
        }
        modes.activate(id, gem.gemId());
        seed(gem);
        message(player, messages.format("soul.activate"));
        playSound(player, cfg.soundActivate());
        particles.spawn(player, cfg.particlesActivate(), 1);
        return Toggle.ENABLED;
    }

    private static void message(Player player, String raw) {
        if (raw != null && !raw.isBlank()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', raw));
        }
    }

    /**
     * The {@code playSound(Location, String, …)} string overload takes a namespaced key, dodging the
     * {@code Sound} constant/interface break at 1.21.3 — no resolver needed. Runs on the player's own thread.
     */
    private void playSound(Player player, String token) {
        SoulGemConfig cfg = config.get();
        if (cfg.sounds() && token != null && !token.isBlank()) {
            Sounds.play(player, player.getLocation(), token, 1.0f, 1.0f);
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

    /** Credit {@code amount} to the killer's carried gem. MUST run on the killer's own thread. */
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
        UUID gemId = data.gemId();
        if (ledger.peek(gemId).isPresent()) {
            // seeded active gem: credit through the ledger so the live authority stays correct (write-through persists)
            ledger.deposit(gemId, balanceFor(killer, gemId), amount);
        } else {
            writeGem(inv, slot, data.withSouls(data.souls() + amount));
        }
    }

    /**
     * Combine two gems into a fresh gem holding the SUM of their souls (§D). Both source identities are
     * retired, so the new gem is the only survivor. {@code null} if either stack is not a gem. MUST run on
     * {@code player}'s own thread.
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
     * Split {@code amount} souls off the main-hand gem into a new gem (never auto-split — only via
     * {@code /se split}); overflow dropped. Routes the debit through the ledger when the held gem is the
     * seeded active one. MUST run on {@code player}'s own thread.
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
            // `have` was a lock-free peek; a concurrent spend may have dropped the authority below `amount`.
            // tryConsume re-checks atomically under the stripe lock, so souls are minted only if it actually debited.
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

    public Optional<SoulBinding> bindingFor(Player actor) {
        return modes.active(actor.getUniqueId())
                .map(gemId -> new SoulBinding(gemId, deferredBalance(actor, gemId)));
    }

    /**
     * Debit souls from {@code holder}'s gem — the {@code REMOVE_SOULS} effect's collaborator
     * ({@link engine.sink.SoulDebit}, §D). Charges atomically ({@link SoulLedger#tryConsume}) and
     * write-throughs to the gem's PDC <em>wherever it sits</em> (usually the bag during combat, not the
     * main hand). MUST run on {@code holder}'s own thread. Only ever hits the seeded active gem, so a stale
     * PDC-only gem is never silently drained.
     */
    @Override
    public void debit(Player holder, UUID gemId, int amount) {
        if (amount <= 0 || ledger.peek(gemId).isEmpty()) {
            return;
        }
        ledger.tryConsume(gemId, balanceFor(holder, gemId), amount);
    }

    /**
     * Debit the target's OWN active gem (the {@code REMOVE_SOULS:…:@Victim} drain-the-enemy case). Runs on the
     * target's thread (the sink routes it there); no-op if the target is not in soul mode.
     */
    @Override
    public void debitTarget(Player target, int amount) {
        modes.active(target.getUniqueId()).ifPresent(gemId -> debit(target, gemId, amount));
    }

    /**
     * Forget a player's soul mode + authority on quit. Flushes the live authority to PDC BEFORE forgetting it
     * — else a deferred write still in flight (or one an unloaded Folia entity never runs) would lose a
     * just-spent balance and refund the souls.
     */
    public void clear(Player player) {
        UUID id = player.getUniqueId();
        modes.active(id).ifPresent(gemId -> {
            persist(player, gemId);
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
            modes.deactivate(player.getUniqueId());
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
        reRenderGem(stack, data.souls());
        inv.setItem(slot, stack);
    }

    /**
     * A {@link SoulLedger.Balance} that write-throughs the gem WHEREVER it sits — located by identity, not a
     * fixed slot — so a spend/deposit persists when the gem is in the bag, not the main hand (the combat
     * case). {@code setSouls} runs on the holder's own thread.
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
            reRenderGem(stack, next);
            inv.setItem(slot, stack);
        }
    }

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
     * A balance whose writes DEFER to {@code player}'s thread, flushing the LIVE authority (not the captured
     * snapshot) so concurrent/out-of-order deferred writes all converge to the same truth.
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
     * Flush the live authority to the gem (by identity, wherever it sits), iff still carried. Peeks with NO
     * seeding, so a forgotten gem is a no-op rather than a 0-seed; writing the current authority (not a stale
     * snapshot) keeps every deferred write idempotent. Must run on {@code player}'s own thread.
     */
    private void persist(Player player, UUID gemId) {
        OptionalInt authority = ledger.peek(gemId);
        if (authority.isPresent()) {
            writeAuthorityTo(player, gemId, authority.getAsInt());
        }
    }

    /** Re-render the gem's name + lore from config + the new count. Must run on the holder's own thread. */
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
        List<String> lore = renderGemLore(cfg, souls);
        meta.setLore(lore.isEmpty() ? null : lore.stream().map(ItemFactory::color).toList());
        gem.setItemMeta(meta);
    }
}
