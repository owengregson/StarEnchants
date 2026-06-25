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
 * Slot-economy cold path (§H): mints the upgrade orb and applies it onto gear, raising the gear's purchased
 * {@link CombatState#added()} count. Clamped to {@link SlotConfig#hardCap()} on TOTAL slots (base + added),
 * so a stack of orbs can't grow an item without bound.
 */
public final class SlotService {

    private final SlotItemCodec codec;
    private final CombatCodec combat;
    private final LoreRenderer lore;
    private final Supplier<SlotConfig> config;
    private final IntSupplier baseSlots; // read live so the cap math tracks a reload of config.yml slots.base (§H)
    private final item.lang.Messages messages;

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

    /** Canonical form (composition root). */
    public SlotService(SlotItemCodec codec, CombatCodec combat, LoreRenderer lore,
                       Supplier<SlotConfig> config, IntSupplier baseSlots, item.lang.Messages messages) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.lore = Objects.requireNonNull(lore, "lore");
        this.config = Objects.requireNonNull(config, "config");
        this.baseSlots = Objects.requireNonNull(baseSlots, "baseSlots");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public boolean isSlotItem(ItemStack stack) {
        return codec.isSlotItem(stack);
    }

    public ItemStack mintOrb() {
        SlotConfig cfg = config.get();
        String amount = Integer.toString(cfg.orbAmount());
        ItemStack stack = ItemFactory.build(
                cfg.orbMaterial(), Material.ENDER_EYE,
                cfg.orbName().replace("{AMOUNT}", amount),
                renderLore(cfg.orbLore(), amount));
        codec.mark(stack, cfg.orbAmount());
        return stack;
    }

    /**
     * Applies {@code slotItem} onto {@code gear} (clamped to the hard cap). The slot item is consumed only
     * when it actually raises the count; gear already at the cap is a no-op that preserves the slot item.
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
        lore.apply(gear, next);
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
