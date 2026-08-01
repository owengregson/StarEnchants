package feature.scroll;

import compile.load.ScrollsConfig;
import feature.apply.Rolls;
import engine.effect.kind.ActiveMasks;
import feature.apply.GestureOutcome;
import feature.compat.Mats;
import item.codec.AppliedSlot;
import item.codec.ScrollCodec;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.item.ItemGroups;
import platform.text.Tokens;

/**
 * Holy white scroll (§I): APPLIED to a piece of gear (drag onto it) — on a successful apply roll it stamps a
 * one-shot keep-on-death marker (adding it to that item's applied-utility set, {@link AppliedSlot}, alongside
 * any traks/white scroll); on the owner's death the marked item is kept and the marker consumed. This is
 * per-ITEM, distinct from the
 * always-on {@code KEEP_ON_DEATH} enchant flag (whole inventory, {@link feature.combat.KeepOnDeathListener}).
 *
 * <p>The apply rolls a success in the configured {@code [min, max]} range; a failed roll spends the scroll
 * without protecting and never destroys the gear (only enchant books destroy). The roll is injected for tests.
 */
public final class HolyScrollService {

    public static final String HOLY = "HOLY";

    private final ScrollCodec scrolls;
    private final AppliedSlot slot;
    private final Supplier<ScrollsConfig> config;
    private final Random random;
    private final platform.lang.Messages messages;
    private final Consumer<ItemStack> reRender; // refresh gear lore so the HOLY PROTECTED line tracks the marker
    private final ItemGroups groups; // §I applies-to gate — the holy scroll only protects the configured item kinds

    /** {@code reRender} recomposes gear lore after a guard toggle (ADR-0040). */
    public HolyScrollService(ScrollCodec scrolls, AppliedSlot slot, Supplier<ScrollsConfig> config, Random random,
                             platform.lang.Messages messages, Consumer<ItemStack> reRender, ItemGroups groups) {
        this.scrolls = Objects.requireNonNull(scrolls, "scrolls");
        this.slot = Objects.requireNonNull(slot, "slot");
        this.config = Objects.requireNonNull(config, "config");
        this.random = Objects.requireNonNull(random, "random");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.reRender = Objects.requireNonNull(reRender, "reRender");
        this.groups = Objects.requireNonNull(groups, "groups");
    }

    public boolean isHolyScroll(ItemStack stack) {
        return HOLY.equals(scrolls.kind(stack));
    }

    public ItemStack mint() {
        ScrollsConfig.Holy cfg = config.get().holy();
        String kinds = ItemGroups.kindsLabel(cfg.appliesTo());
        List<String> lore = Tokens.subLines(cfg.lore(), "KINDS", kinds);
        // The default material (TOTEM_OF_UNDYING) is absent on 1.8 — resolve by name with a floor-stable fallback.
        ItemStack stack = ItemFactory.buildItem(
                cfg.material(), Mats.or("TOTEM_OF_UNDYING", Material.PAPER), cfg.name(), lore);
        scrolls.mark(stack, HOLY);
        return stack;
    }

    /**
     * Apply the holy scroll {@code cursor} onto {@code gear}: roll the configured success; on success add the
     * keep marker to the gear's applied-utility set and consume the scroll; on a failed roll consume the scroll
     * without protecting. Refused (nothing consumed) if the target is invalid or already holy-protected.
     */
    public GestureOutcome applyTo(ItemStack cursor, ItemStack gear) {
        if (gear == null || gear.getType() == Material.AIR) {
            return GestureOutcome.noop(messages.format("scroll.holy.apply-target"));
        }
        if (gear.getAmount() > 1) {
            return GestureOutcome.noop(messages.format("common.single-item"));
        }
        if (slot.holds(gear, AppliedSlot.HOLY)) {
            return GestureOutcome.noop(messages.format("scroll.holy.already"));
        }
        ScrollsConfig.Holy cfg = config.get().holy();
        if (!groups.matches(gear.getType(), cfg.appliesTo())) {
            return GestureOutcome.noop(messages.format("common.wrong-applies", "KINDS", ItemGroups.kindsLabel(cfg.appliesTo())));
        }
        int success = Rolls.between(random, cfg.minSuccess(), cfg.maxSuccess());
        consume(cursor); // spent whether the roll succeeds or fails
        if (!Rolls.passes(random, success)) {
            return GestureOutcome.committed(gear, messages.format("scroll.holy.fail"));
        }
        slot.occupy(gear, AppliedSlot.HOLY);
        reRender.accept(gear); // stamp the HOLY PROTECTED line from the new keep marker
        return GestureOutcome.committed(gear, messages.format("scroll.holy.applied"));
    }

    /**
     * Remove every holy-protected item from {@code drops}, clearing each one's keep marker (consumed on death),
     * and return them so the caller can stash them for re-grant on respawn. Mutates {@code drops}.
     */
    public List<ItemStack> keepFromDrops(List<ItemStack> drops) {
        return keepFromDrops(null, drops);
    }

    /**
     * Player-aware death path. Monopoly Mask's advertised 33% Holy White Scroll negation preserves the
     * one-shot marker on each saved item whose independent roll lands; the item is still removed from drops.
     */
    public List<ItemStack> keepFromDrops(Player owner, List<ItemStack> drops) {
        boolean monopoly = owner != null && ActiveMasks.has(owner, "masks/monopoly-mask");
        List<ItemStack> kept = new ArrayList<>();
        Iterator<ItemStack> it = drops.iterator();
        while (it.hasNext()) {
            ItemStack drop = it.next();
            if (drop != null && slot.holds(drop, AppliedSlot.HOLY)) {
                if (!monopoly || random.nextDouble() > 0.33) {
                    slot.release(drop, AppliedSlot.HOLY); // ordinary one-shot consumption
                    reRender.accept(drop);
                }
                kept.add(drop);
                it.remove();
            }
        }
        return kept;
    }

    /** The death message for keeping {@code count} holy-protected item(s). */
    public String keptMessage(int count) {
        return messages.format("scroll.holy.kept", "AMOUNT", count);
    }

    private static void consume(ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
    }
}
