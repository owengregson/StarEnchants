package bootstrap.wire;

import feature.menu.MenuRegistry;
import feature.menu.Mintable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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
    final UseItemsModule useItems;
    final PetsModule pets;
    final MasksModule masks;
    final ReforgesModule reforges;
    final ScrollsModule scrolls;
    final TraksModule traks;
    final BlessModule bless;
    final EnchantsModule enchants;
    final SetsModule sets;
    final MenusModule menus;
    final ReloadModule reload;
    final CommandsModule commands;

    /** THE ordered registry — the fold order (differs from construction order only for reload↔menus). */
    final List<FeatureModule> registry;

    public Modules(BootCore core, Supplier<ModuleFold.Report> foldReport) {
        this.combat = new CombatModule(core);
        this.equip = new EquipModule(core);
        this.souls = new SoulsModule(core);
        this.triggers = new TriggersModule(core);
        this.controls = new ControlsModule(core);
        this.stores = new StoresModule(core);
        this.guard = new GuardModule(core);
        this.carriers = new CarriersModule(core);
        // ADR-0070/0074: BOTH constructed before crystals, because each hands the ONE Item Extractor a hook —
        // reforges pop off gear, a composite mask splits (ADR-0074). The REGISTRY still keeps masks directly
        // after pets and reforges directly after masks: the reload↔menus precedent (construction order ≠ fold
        // order), so what a module needs at build time never dictates when it folds.
        this.reforges = new ReforgesModule(core);
        this.masks = new MasksModule(core, equip);         // ADR-0053 helmet masks (subscribes the equip-refresh seam)
        this.crystals = new CrystalsModule(core, reforges.reforges, masks.splitter);
        this.heroic = new HeroicModule(core);
        this.slots = new SlotsModule(core);
        this.scrolls = new ScrollsModule(core, carriers, souls);  // carrier economy + soul-gem holy re-render (§4)
        this.books = new BooksModule(core, carriers, scrolls); // layers on the carrier economy; the Enchanter's
                                                                // scroll tiles take the white/black scroll mints
        this.useItems = new UseItemsModule(core);          // §3.6 right-click content items
        this.pets = new PetsModule(core, equip);           // ADR-0052 leveling head items (needs the refresher)
        this.bless = new BlessModule(core);          // /bless (CosmicRenewed-compatible first debuff)
        this.traks = new TraksModule(core);
        this.enchants = new EnchantsModule(core);
        this.sets = new SetsModule(core);

        // The ONE mint declaration set (ADR-0047 §7), gathered in registry order — the single source the mint menu,
        // /se give and the self-mint rows all derive from (identical instances to the folded module mintables).
        List<Mintable> allMintables = new ArrayList<>();
        allMintables.addAll(souls.mints);
        allMintables.addAll(carriers.mints);
        allMintables.addAll(crystals.mints);
        allMintables.addAll(heroic.mints);
        allMintables.addAll(slots.mints);
        allMintables.addAll(books.mints);
        allMintables.addAll(useItems.mints);
        allMintables.addAll(pets.mints);
        allMintables.addAll(masks.mints); // ADR-0053
        allMintables.addAll(reforges.mints); // ADR-0070
        allMintables.addAll(scrolls.mints);
        allMintables.addAll(traks.mints);
        allMintables.addAll(sets.mints);
        List<Mintable> mintables = List.copyOf(allMintables);

        // reload is CONSTRUCTED before menus (the operator console needs the reloader), but the REGISTRY keeps
        // menus at 17 and reload at 18 — registry order governs the fold; construction order is this ctor.
        this.reload = new ReloadModule(core, equip);
        this.menus = new MenusModule(core, reload, scrolls, mintables);
        this.commands = new CommandsModule(core, reload, menus, crystals, heroic, slots, books, scrolls, traks,
                bless, mintables, foldReport);

        this.registry = List.of(combat.module(), equip.module(), souls.module(), triggers.module(),
                controls.module(), stores.module(), guard.module(), carriers.module(), crystals.module(),
                heroic.module(), slots.module(), books.module(), useItems.module(), pets.module(),
                masks.module(), // ADR-0053 directly after pets
                reforges.module(), // ADR-0070 directly after masks
                scrolls.module(), traks.module(), bless.module(), enchants.module(), sets.module(), menus.module(),
                reload.module(), commands.module());
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
