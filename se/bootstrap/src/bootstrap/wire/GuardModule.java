package bootstrap.wire;

/**
 * The vanilla-mechanic guard slot (ADR-0047): declares the {@link Wire.PluginItemGuard} position, which the
 * fold materializes into a {@code VanillaGuardListener} over the OR of every module's plugin-item contribution
 * — a module cannot see the whole registry, the fold can. Custom items do ONLY their intended action.
 */
final class GuardModule {

    private final org.bukkit.event.Listener stationGuard;
    private final org.bukkit.event.Listener headEquipGuard;
    private final org.bukkit.event.Listener dispenseArmorGuard;

    GuardModule(BootCore core) {
        // Vanilla-station guard (G04/G05/G06): the era-seam smithing/grindstone/anvil guard over set gear, built
        // once and registered right after the vanilla-mechanic guard so both live in the one guard module.
        this.stationGuard = core.bindings().stationGuard(core.stationGuardRules());
        // Helmet-slot deny for mask/pet heads (1.8.4): a mask activates applied ONTO a helmet, a pet from the
        // HOTBAR — neither belongs in the helmet slot. One null/AIR-safe head-item predicate (the composition-root
        // wrapping the VanillaGuardListener uses) feeds both the inventory-route guard and the modern dispenser seam.
        java.util.function.Predicate<org.bukkit.inventory.ItemStack> isHeadItem =
                stack -> stack != null && stack.getType() != org.bukkit.Material.AIR
                        && (core.maskCodec().isMask(stack) || core.petCodec().isPet(stack));
        this.headEquipGuard = new feature.guard.HeadEquipGuard(isHeadItem);
        this.dispenseArmorGuard = core.bindings().dispenseArmorGuard(isHeadItem);
    }

    FeatureModule module() {
        return FeatureModule.named("guard")
                .pluginItemGuard()
                .events(headEquipGuard)
                .events(dispenseArmorGuard)
                .events(stationGuard)
                .build();
    }
}
