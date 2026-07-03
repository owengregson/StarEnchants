package bootstrap.wire;

import feature.menu.MenuRegistry;
import java.util.List;

/**
 * The ordered registry of every {@link FeatureModule} (ADR-0047): explicit, compile-checked construction — no
 * service locator, no classpath scan. Modules are built in dependency order (each ctor takes {@code core} plus
 * the earlier modules it needs); {@link #registry} is the fold order, which reproduces the shipped listener /
 * menu / disable sequences. Every {@code *Module} here must appear in the {@code registry} list below
 * (ModuleTreeGateTest G2-b).
 */
public final class Modules {

    final CombatModule combat;
    final EquipModule equip;
    final SoulsModule souls;
    final TriggersModule triggers;
    final ControlsModule controls;
    final StoresModule stores;
    final GuardModule guard;
    final CarriersModule carriers;
    final CrystalsModule crystals;
    final HeroicModule heroic;
    final SlotsModule slots;
    final BooksModule books;
    final ScrollsModule scrolls;
    final TraksModule traks;
    final EnchantsModule enchants;
    final SetsModule sets;
    final MenusModule menus;
    final ReloadModule reload;
    final CommandsModule commands;

    /** THE ordered registry — the fold order (differs from construction order only for reload↔menus). */
    final List<FeatureModule> registry;

    public Modules(BootCore core) {
        this.combat = new CombatModule(core);
        this.equip = new EquipModule(core);
        this.souls = new SoulsModule(core);
        this.triggers = new TriggersModule(core);
        this.controls = new ControlsModule(core);
        this.stores = new StoresModule(core);
        this.guard = new GuardModule(core);
        this.carriers = new CarriersModule(core);
        this.crystals = new CrystalsModule(core);
        this.heroic = new HeroicModule(core);
        this.slots = new SlotsModule(core);
        this.books = new BooksModule(core, carriers);      // layers on the carrier economy
        this.scrolls = new ScrollsModule(core, carriers);  // layers on the carrier economy
        this.traks = new TraksModule(core);
        this.enchants = new EnchantsModule(core);
        this.sets = new SetsModule(core);
        // reload is CONSTRUCTED before menus (the operator console needs the reloader), but the REGISTRY keeps
        // menus at 17 and reload at 18 — registry order governs the fold; construction order is this ctor.
        this.reload = new ReloadModule(core, equip);
        this.menus = new MenusModule(core, reload, carriers, crystals, heroic, slots, books, scrolls, traks);
        this.commands = new CommandsModule(core, reload, menus, carriers, crystals, heroic, slots, books, scrolls,
                traks);

        this.registry = List.of(combat.module(), equip.module(), souls.module(), triggers.module(),
                controls.module(), stores.module(), guard.module(), carriers.module(), crystals.module(),
                heroic.module(), slots.module(), books.module(), scrolls.module(), traks.module(),
                enchants.module(), sets.module(), menus.module(), reload.module(), commands.module());
    }

    /** The ordered fold registry (the composition root folds this). */
    public List<FeatureModule> registry() {
        return registry;
    }

    /** The shared menu registry the fold registers every module's rank-ordered menu into. */
    public MenuRegistry menuRegistry() {
        return menus.registry;
    }
}
