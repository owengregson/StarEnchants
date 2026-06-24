package feature.slot;

import compile.load.SlotConfig;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.SlotItemCodec;
import item.mint.ItemFactory;
import item.render.LoreRenderer;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * The slot-economy cold path (docs/v3-directives.md §H) — MINTS the upgrade orb (a configurable {@code +N})
 * and APPLIES it onto a piece of gear, raising the gear's purchased {@link CombatState#added()} slot count,
 * clamped to one universal {@link SlotConfig#hardCap() cap} on the TOTAL slots (base + added) any item may
 * reach, so a stack of orbs cannot grow an item without bound.
 *
 * <p>The granted slots persist in the gear's combat blob; the slot item itself is identity-only
 * ({@link SlotItemCodec}), off the combat hot path. Deterministic (no roll). Folia-correct: a gesture fires
 * on the clicking player's own region thread, so mutating their cursor/inventory is in-thread.
 */
public final class SlotService {

    private final SlotItemCodec codec;
    private final CombatCodec combat;
    private final LoreRenderer lore;
    private final Supplier<SlotConfig> config;
    private final IntSupplier baseSlots; // §H config.yml slots.base — read live so the cap math tracks a reload
    private final item.lang.Messages messages; // §L lang.yml — apply/at-cap + guards

    /** Fixed base-slots + default messages form (tests/fixtures). */
    public SlotService(SlotItemCodec codec, CombatCodec combat, LoreRenderer lore,
                       Supplier<SlotConfig> config, int baseSlots) {
        this(codec, combat, lore, config, () -> Math.max(0, baseSlots));
    }

    /** As above with a live base-slots supplier but default messages. */
    public SlotService(SlotItemCodec codec, CombatCodec combat, LoreRenderer lore,
                       Supplier<SlotConfig> config, IntSupplier baseSlots) {
        this(codec, combat, lore, config, baseSlots, item.lang.Messages.defaults());
    }

    /**
     * Canonical form (the composition root): {@code baseSlots} is read live, so the universal cap math
     * ({@code hardCap - base}, the cap itself staying in {@code items/slots.yml} per §H) re-tunes on a
     * {@code /se reload} that changes {@code config.yml}'s {@code slots.base}; {@code messages} sources the
     * apply/at-cap chat from {@code lang.yml} (§L).
     */
    public SlotService(SlotItemCodec codec, CombatCodec combat, LoreRenderer lore,
                       Supplier<SlotConfig> config, IntSupplier baseSlots, item.lang.Messages messages) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.lore = Objects.requireNonNull(lore, "lore");
        this.config = Objects.requireNonNull(config, "config");
        this.baseSlots = Objects.requireNonNull(baseSlots, "baseSlots");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Whether {@code stack} is a slot expander (orb) item. */
    public boolean isSlotItem(ItemStack stack) {
        return codec.isSlotItem(stack);
    }

    /** Mint an upgrade orb (grants the configured {@code +N}). */
    public ItemStack mintOrb() {
        SlotConfig cfg = config.get();
        String amount = Integer.toString(cfg.orbAmount());
        ItemStack stack = ItemFactory.build(
                ItemFactory.material(cfg.orbMaterial(), Material.ENDER_EYE),
                cfg.orbName().replace("{AMOUNT}", amount),
                renderLore(cfg.orbLore(), amount));
        codec.mark(stack, cfg.orbAmount());
        return stack;
    }

    /**
     * Apply the slot item {@code slotItem} onto {@code gear}, raising its purchased slot count by the item's
     * granted {@code +N} (clamped to the universal hard cap). The slot item is consumed only when it actually
     * raises the count; an item already at the cap is a no-op (the slot item is preserved).
     */
    public SlotResult applyTo(ItemStack slotItem, ItemStack gear) {
        if (gear == null || gear.getType() == Material.AIR) {
            return SlotResult.unchanged(messages.format("slot.not-gear"));
        }
        if (gear.getAmount() > 1) {
            return SlotResult.unchanged(messages.format("common.single-item"));
        }
        int grant = codec.amountOf(slotItem);
        if (grant <= 0) {
            return SlotResult.unchanged(messages.format("slot.not-slot-item"));
        }
        SlotConfig cfg = config.get();
        int base = baseSlots.getAsInt();
        int maxAdded = Math.max(0, cfg.hardCap() - base); // the cap is on TOTAL slots (base + added)
        CombatState current = combat.read(gear);
        if (current.added() >= maxAdded) {
            return SlotResult.unchanged(messages.format("slot.at-cap"));
        }
        int newAdded = Math.min(current.added() + grant, maxAdded);
        CombatState next = current.withAdded(newAdded);
        combat.write(gear, next);
        lore.apply(gear, next); // keep the gear's lore in sync (unchanged enchant/crystal lines re-rendered)
        consume(slotItem);
        int total = base + newAdded;
        return SlotResult.committed(gear, messages.format("slot.apply", "SLOTS", total));
    }

    private static java.util.List<String> renderLore(java.util.List<String> lore, String amount) {
        java.util.List<String> out = new java.util.ArrayList<>(lore.size());
        for (String line : lore) {
            out.add(line.replace("{AMOUNT}", amount));
        }
        return out;
    }

    private static void consume(ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
    }
}
