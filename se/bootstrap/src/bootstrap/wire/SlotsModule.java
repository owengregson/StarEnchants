package bootstrap.wire;

import feature.slot.SlotListener;
import feature.slot.SlotService;
import item.codec.ItemKeys;
import item.codec.SlotItemCodec;

/**
 * Slot economy (§H, ADR-0047): the enchantment-slot expander orb. BOOT-gated on {@code features.slots} — the
 * one apply listener registers only when the feature is on (a toggle change needs a restart).
 */
final class SlotsModule {

    private final BootCore core;
    final SlotService slots;

    SlotsModule(BootCore core) {
        this.core = core;
        // base MUST match the ItemEnchanter default so the cap is computed off the same base.
        SlotItemCodec slotItemCodec = new SlotItemCodec(ItemKeys.of().slotItem(), ItemKeys.of().slotSuccess(),
                core.store());
        this.slots = new SlotService(slotItemCodec, core.codec(), core.lore(),
                () -> core.items().config().slotsOrDefault(),
                (java.util.function.IntSupplier) () -> core.master().config().slots().base(),
                core.messages(), core.itemGroups(), core.rolls());
    }

    FeatureModule module() {
        return FeatureModule.named("slots")
                .toggle(Toggle.boot("features.slots",
                        () -> core.master().config().features().slots(),
                        "slots feature disabled (config.yml features.slots) — slot-expander apply not registered"))
                .events(new SlotListener(slots, core.messages(), core.sounds()))
                .pluginItem(slots::isSlotItem)
                .lang("slot", "command.give.slot")
                .build();
    }
}
