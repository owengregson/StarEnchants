package feature.trigger;

import compile.load.ChatColorRgb;
import compile.load.MasterConfig;
import compile.load.ParticleSpec;
import compile.load.SetDef;
import compile.load.SoundCue;
import feature.compat.Sounds;
import feature.fx.ParticleFx;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/**
 * The universal armour-set equip/unequip feedback (§6.6): plays the configured sound list and spawns the
 * configured particle at the player whenever a completed set is equipped or drops below its threshold. ONE
 * config for ALL sets ({@link MasterConfig.SetsSection}) — not per-set. When {@code use-set-color} is on, the
 * (dust) particle is tinted to the set's own {@code &}-colour, read from its display name, so each set's cloud
 * matches its identity. Runs on the player's own region thread (the {@link SetMessageDriver} calls it there).
 */
public final class SetEquipEffects implements SetMessageDriver.SetTransition {

    private final Supplier<MasterConfig.SetsSection> config;
    private final ParticleFx particles;

    public SetEquipEffects(Supplier<MasterConfig.SetsSection> config, ParticleFx particles) {
        this.config = Objects.requireNonNull(config, "config");
        this.particles = Objects.requireNonNull(particles, "particles");
    }

    @Override
    public void onTransition(Player player, SetDef def, boolean equipped) {
        MasterConfig.SetsSection cfg = config.get();
        List<SoundCue> cues = equipped ? cfg.equipSound() : cfg.unequipSound();
        for (SoundCue cue : cues) {
            Sounds.play(player, player.getLocation(), cue.name(), cue.volume(), cue.pitch());
        }
        particles.spawn(player, particleFor(cfg, def, equipped));
    }

    /** The equip/unequip particle, its dust colour overridden to the set's {@code &}-colour when configured. */
    static ParticleSpec particleFor(MasterConfig.SetsSection cfg, SetDef def, boolean equipped) {
        ParticleSpec spec = equipped ? cfg.equipParticle() : cfg.unequipParticle();
        if (cfg.useSetColor()) {
            int[] rgb = ChatColorRgb.of(def.display());
            if (rgb != null) {
                return new ParticleSpec(spec.type(), rgb[0], rgb[1], rgb[2], spec.amount(), spec.spread(),
                        spec.yOffset());
            }
        }
        return spec;
    }
}
