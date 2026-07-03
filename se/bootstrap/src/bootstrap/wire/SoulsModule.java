package bootstrap.wire;

import feature.menu.Mintable;
import feature.soul.SoulInteractListener;
import feature.soul.SoulInventoryListener;
import feature.soul.SoulListener;
import feature.soul.SoulParticleDriver;
import feature.soul.SoulService;
import feature.soul.SplitSoulsCommand;
import java.util.List;

/**
 * Souls (§D, ADR-0047): the three soul listeners (BOOT-gated on {@code features.souls}), the {@code /splitsouls}
 * alias, the while-active aura, and the soul-total cache swept on quit. {@code SoulService} itself is the engine
 * spine's (BootCore) — the pipeline/sink/dispatchers consume it — so this module wires over {@code core.soulService()}.
 */
final class SoulsModule {

    private final BootCore core;
    private final SoulParticleDriver soulParticles;
    final List<Mintable> mints;

    SoulsModule(BootCore core) {
        this.core = core;
        this.mints = List.of(Mints.gem(core.soulService()));
        // §N PlaceholderAPI expansion (ADR-0027) — unconditional (not souls-toggle-gated), consumed post-boot.
        core.bindings().registerPlaceholders(core.plugin(), core.master().config().integrations()::enabled,
                player -> core.soulModes().isActive(player.getUniqueId()),
                // §D total souls across ALL carried gems (cached on the holder thread each tick — thread-safe here)
                player -> core.soulService().soulTotal(player.getUniqueId()));
        // §D soul-mode tick: auto-disable soul mode when the active gem is gone/drained, then spawn the aura.
        this.soulParticles = new SoulParticleDriver(core.soulService(), core.soulModes(),
                () -> core.items().config().soulGemOrDefault(), core.particleFx());
    }

    FeatureModule module() {
        SoulService souls = core.soulService();
        return FeatureModule.named("souls")
                .toggle(Toggle.boot("features.souls",
                        () -> core.master().config().features().souls(),
                        "souls feature disabled (config.yml features.souls) — soul listeners not registered"))
                .events(new SoulListener(souls))
                .events(new SoulInteractListener(souls, core.hands()))
                .events(new SoulInventoryListener(souls))
                .command(DynCommand.always("splitsouls",
                        () -> new SplitSoulsCommand("splitsouls", souls, core.messages()),
                        "could not register /splitsouls (use /se split instead)"))
                .store(souls::evictCache) // the soul-total cache (SoulListener.clear flushes owed drain + mode)
                .mints(mints)
                .pluginItem(souls::isGem)
                .boot(soulParticles::start)
                .stop("soul aura task", soulParticles::stop)
                .lang("soul", "command.give.gem")
                .build();
    }
}
