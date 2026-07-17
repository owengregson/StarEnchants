package bootstrap.wire;

import feature.menu.Mintable;
import feature.pet.PetFoodListener;
import feature.pet.PetHomeStore;
import feature.pet.PetHotbarListener;
import feature.pet.PetLevelCue;
import feature.pet.PetLevelListener;
import feature.pet.PetMessenger;
import feature.pet.PetService;
import feature.pet.PetSummonListener;
import feature.pet.PetSweep;
import feature.pet.PetUseListener;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;
import platform.sched.Scheduling;

/**
 * Pets (ADR-0052): a leveling head-item content family on the shared engine. The {@code features.pets}
 * toggle is LIVE (listeners register regardless and short-circuit on the flag — a pet is a content item,
 * the use-items/crystals rule). Wires the right-click claim + death teardown, kill/XP leveling, the
 * ADR-0041 Pet Food gesture, hotbar-change refreshes, the summon-flag guard, the per-minute sweep (passive
 * exp + hotbar-drift reconcile), and the pet / pet-food mints. The armed-window store and the sweep's
 * fingerprints are per-player state, cleared by the quit sweep; the summon registry clears on disable.
 */
final class PetsModule {

    private static final long SWEEP_TICKS = 1200L; // one minute — the passive-exp cadence

    private final BootCore core;
    private final EquipModule equip;
    final PetService pets;
    final PetLevelListener leveler;
    final PetSweep sweep;
    final PetHomeStore homes = new PetHomeStore(); // ADR-0061: the Mole dig-home windows
    final feature.trigger.WaterSpeedDriver waterSpeed;
    final List<Mintable> mints;

    PetsModule(BootCore core, EquipModule equip) {
        this.core = core;
        this.equip = equip;
        PetMessenger messenger = new PetMessenger(core.messages(), () -> core.master().config().pets());
        PetLevelCue cue = new PetLevelCue(() -> core.master().config().pets(), core.particleFx(), core.sounds());
        // The cold-path run reuses the shared TriggerDispatch (its runner/sink wiring), so no second engine spine.
        this.pets = new PetService(core.content(), core.petCodec(), core.triggerDispatch(),
                core.bindings().texturedHeads(), core.bindings().headEquip(), core.vanillaEnchants(),
                messenger, core.petArmedStore(),
                () -> core.master().config().pets(),
                () -> core.items().config().petOrDefault(),
                () -> core.items().config().petFoodOrDefault(),
                equip.refresher(), core.tick()::get, core.bindings().actorProbe()::isAir,
                cue, core.rolls(), homes, core.stores().teleblock());
        this.leveler = new PetLevelListener(pets, core.petCodec(), () -> core.master().config().pets(),
                equip.refresher(), enabled());
        this.sweep = new PetSweep(core.petCodec(), leveler, equip.refresher(), enabled());
        // The WATER_SPEED worn channel (ADR-0060): the MaxHealthDriver twin, refreshed by the end-of-refresh
        // hook below (a suppression window applied mid-refresh lands on the next full refresh — accepted).
        this.waterSpeed = new feature.trigger.WaterSpeedDriver(core.triggerDispatch(), core.content(),
                core.worn(), core.stores().suppression(), core.tick()::get,
                core.triggers().idOf("HELD").orElse(-1), core.triggers().idOf("PASSIVE").orElse(-1));
        this.mints = List.of(Mints.pet(pets, core.content()), Mints.petFood(pets));
    }

    private BooleanSupplier enabled() {
        return () -> core.master().config().features().pets();
    }

    FeatureModule module() {
        BooleanSupplier enabled = enabled();
        return FeatureModule.named("pets")
                .toggle(Toggle.live("features.pets", enabled))
                .events(new PetUseListener(pets, core.petCodec(), core.hands(), enabled))
                .events(leveler)
                .events(new PetFoodListener(pets, core.petCodec(),
                        () -> core.items().config().petFoodOrDefault(), core.messages(), core.particleFx(),
                        core.sounds(), equip.refresher(), enabled))
                .events(new PetHotbarListener(core.petCodec(), equip.refresher(), enabled))
                .events(new PetSummonListener(enabled))
                .install("water-speed refresh hook", () -> equip.onRefresh(waterSpeed::refresh))
                .menu(75, new feature.menu.PetsBrowserMenu(core.content(), pets,
                        () -> core.master().config().pets(), core.caps(), core.messages(),
                        core.menusHolder()::config, core.vanillaEnchants()))
                .mints(mints)
                .store(core.petArmedStore())
                .store(homes) // ADR-0061: a dig-home window never survives a quit
                .store(sweep)
                .pluginItem(stack -> core.petCodec().isPet(stack) || core.petCodec().isPetFood(stack))
                // "feedback" is the UNIVERSAL action-feedback root (ADR-0061) — pets is its first consumer
                // and owns it for introspection until a more central module exists.
                .lang("pet", "command.pet", "command.give.pet", "command.give.petfood",
                        "command.error.no-such-pet", "feedback")
                // The per-minute sweep: passive held-time exp + the hotbar-drift reconcile (no pickup event).
                .boot(() -> Scheduling.repeatingGlobal(SWEEP_TICKS, SWEEP_TICKS, () -> {
                    for (Player player : core.plugin().getServer().getOnlinePlayers()) {
                        Scheduling.onEntity(player, () -> sweep.run(player));
                    }
                }))
                .stop("pet summon registry", engine.sink.PetSummons::clearAll)
                .stop("bat swarms", engine.sink.SwarmSpawns::removeAll)
                .stop("pet armed windows", core.petArmedStore()::clearAll)
                .stop("pet home windows", homes::clearAll)
                .build();
    }
}
