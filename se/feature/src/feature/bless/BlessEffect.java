package feature.bless;

import engine.sink.PermanentPotions;
import feature.compat.Sounds;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** CosmicRenewed-compatible gameplay body for {@code /bless}. Runs on the blessed player's entity thread. */
public final class BlessEffect {

    /** The exact debuff family Renewed's first-active lookup accepts, with old/new Bukkit names paired. */
    private static final Set<String> DEBUFFS = Set.of(
            "BLINDNESS", "CONFUSION", "NAUSEA", "HARM", "INSTANT_DAMAGE", "HUNGER", "POISON",
            "SLOW", "SLOWNESS", "SLOW_DIGGING", "MINING_FATIGUE", "WEAKNESS", "WITHER");
    private final PermanentPotions permanentPotions;
    private final Sounds sounds;

    public BlessEffect(PermanentPotions permanentPotions, Sounds sounds) {
        this.permanentPotions = Objects.requireNonNull(permanentPotions, "permanentPotions");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
    }

    /**
     * Play Renewed's splash cue, then remove only the first matching non-permanent debuff. Returns whether that
     * debuff removal occurred (Renewed shows BLESSED only in that case).
     */
    public boolean apply(Player player) {
        List<PotionEffect> active = List.copyOf(player.getActivePotionEffects());

        // ENTITY_GENERIC_SPLASH is the modern constant; SPLASH is its 1.8 name. playFirst emits exactly one.
        sounds.playFirst(player, player.getLocation(), 1.2f, 2.0f, "ENTITY_GENERIC_SPLASH", "SPLASH");

        PotionEffect debuff = first(active, DEBUFFS, player);
        if (debuff == null) {
            return false;
        }
        player.removePotionEffect(debuff.getType());
        return true;
    }

    private PotionEffect first(List<PotionEffect> active, Set<String> names, Player player) {
        for (PotionEffect effect : active) {
            if (!names.contains(nameOf(effect.getType()))) {
                continue;
            }
            if (!permanentPotions.spares(player, effect)) {
                return effect;
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation") // getName is the one PotionEffectType accessor shared by 1.8 through 26.x.
    private static String nameOf(PotionEffectType type) {
        return type == null || type.getName() == null ? "" : type.getName().toUpperCase(Locale.ROOT);
    }
}
