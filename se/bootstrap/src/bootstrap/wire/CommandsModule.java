package bootstrap.wire;

import bootstrap.PackGate;
import bootstrap.SeCommand;
import engine.boot.RegistryFingerprint;
import feature.menu.Mintable;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.command.PluginCommand;
import pack.PackStore;

/**
 * The operator command surface (ADR-0047): {@code /se} with the full dependency list drawn from core plus the
 * feature modules, the config-pack store and its ADR-0046 pre-flight gate. The mint declarations drive the derived
 * {@code /se give} / self-mint dispatch (the give/self bodies moved verbatim into the module {@code Mints}); the
 * feature services it still holds are for {@code /se item dump} plus the admin enchant/soul-mode commands.
 */
final class CommandsModule {

    private final BootCore core;
    private final SeCommand seCommand;

    CommandsModule(BootCore core, ReloadModule reload, MenusModule menus, CrystalsModule crystals,
                   HeroicModule heroic, SlotsModule slots, BooksModule books, ScrollsModule scrolls,
                   TraksModule traks, BlessModule bless, List<Mintable> mintables,
                   Supplier<ModuleFold.Report> foldReport) {
        this.core = core;
        // Config packs (ADR-0023). /se pack apply pairs the on-disk swap with the transactional reloader; the
        // ADR-0046 gate pre-flights a pack against the live authoring surface first.
        PackStore packs = new PackStore(core.plugin().getDataFolder().toPath());
        PackGate packGate = new PackGate(
                core.compilerFactory(), // the same factory the reloader uses (§9 resolver reuse)
                () -> RegistryFingerprint.hash(core.effectRegistry().get()),
                () -> RegistryFingerprint.summary(core.effectRegistry().get()),
                core.caps()); // R-QC11: a pack's declared min-server floor is checked before the dry-run
        this.seCommand = new SeCommand(reload.reloader, core.enchanter(),
                player -> core.worn().refresh(player, core.content().snapshot()), core.soulService(),
                core.plugin().getDataFolder().toPath().resolve("migrated"), menus.registry, core.content(),
                head -> core.migrateSpecs().lookup(head).orElse(null), crystals.crystals,
                heroic.heroics, slots.slots, scrolls.scrolls, books.unopenedBooks, scrolls.holyScrolls,
                scrolls.nametags, traks.traks, packs, core.codec(), core.carrierCodec(),
                () -> core.master().config().slots().base(), core.messages(), core.contentRoot(), core.store(),
                core.hands(), packGate, core.stores().why(), core.dispatch().damageDebug(),
                core.executor()::quarantinedKeys, core.worn(),
                core.tick()::get, mintables, Give.io(core.messages()),
                foldReport, bless.command); // ADR-0047 derived mint dispatch + /se modules report; ADR-0072 /se bless
    }

    FeatureModule module() {
        return FeatureModule.named("commands")
                .install("/se command", () -> {
                    PluginCommand command = core.plugin().getCommand("se");
                    if (command != null) {
                        command.setExecutor(seCommand);
                        command.setTabCompleter(seCommand);
                    }
                })
                .lang("command")
                .build();
    }
}
