package feature.pet;

import compile.load.MasterConfig;
import compile.load.SoundCue;
import feature.compat.Sounds;
import feature.fx.ParticleFx;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/**
 * The universal pet level-up cue (ADR-0059): the configured sound + particle played to a pet's holder
 * whenever a pet gains a level from ANY source (kills, vanilla XP, use-XP, passive time, Pet Food) — once per
 * gain event, so a multi-level Pet Food plays one cue. Every leveling path already runs on the holder's own
 * region thread (the kill credit hops first), so playback needs no scheduling.
 */
public final class PetLevelCue {

    private final Supplier<MasterConfig.PetsSection> pets;
    private final ParticleFx particles;
    private final Sounds sounds;

    public PetLevelCue(Supplier<MasterConfig.PetsSection> pets, ParticleFx particles, Sounds sounds) {
        this.pets = Objects.requireNonNull(pets, "pets");
        this.particles = Objects.requireNonNull(particles, "particles");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
    }

    /** Play at {@code player} — must already be on their region thread; blank sound / empty particle = silent. */
    public void play(Player player) {
        MasterConfig.PetsSection cfg = pets.get();
        SoundCue sound = cfg.levelUpSound();
        sounds.play(player, player.getLocation(), sound.name(), sound.volume(), sound.pitch());
        particles.spawn(player, cfg.levelUpParticle());
    }
}
