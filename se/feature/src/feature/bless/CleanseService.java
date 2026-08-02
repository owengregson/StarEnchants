package feature.bless;

import engine.sink.DamageMarks;
import engine.sink.DotParkLedger;
import engine.sink.FrozenTargets;
import engine.sink.PotionCategories;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * What a BLESS actually does: strip every debuff an opponent landed on a player, and nothing else. The one
 * definition of the cleanse, so {@code /bless} and any later caller (a set passive, an effect kind) can never
 * disagree about what "cleansed" means.
 *
 * <p>Four surfaces carry a landed debuff, and this clears all four:
 * <ul>
 *   <li><strong>Harmful potion effects</strong> — classified by {@link PotionCategories}, so the version-volatile
 *       renames (SLOW/SLOWNESS, SLOW_DIGGING/MINING_FATIGUE) resolve the same across the range.</li>
 *   <li><strong>Freeze windows</strong> ({@link FrozenTargets}) — the Ice Aspect DoT chain plus its slow, torn
 *       down through the window's own idempotent teardown rather than by deleting the entry.</li>
 *   <li><strong>Parked DoT</strong> ({@link DotParkLedger}) — damage already banked against the player that would
 *       otherwise land moments after the cleanse, which would read as the bless simply not working.</li>
 *   <li><strong>Marks</strong> ({@link DamageMarks}) — Mark of the Reaper and any other {@code MARK} an attacker
 *       holds on them.</li>
 * </ul>
 *
 * <p>Burning is cleared too: fire ticks are a damage-over-time the player did not choose, and no other surface
 * owns them.
 *
 * <p><strong>What it deliberately spares.</strong> A PERMANENT debuff the player carries by their own choice —
 * a helmet granting mining fatigue — is never stripped. Two independent rules protect it: the effect is skipped
 * when the {@link PassivePotions} authority maintains it (SE's own permanent-while-worn grants, which the driver
 * would re-apply on its next refresh regardless), and when its remaining duration is effectively permanent
 * ({@link #PERMANENT_FLOOR_TICKS}), which catches another plugin's permanent grant that SE knows nothing about.
 * Beneficial and neutral effects are untouched at any duration.
 *
 * <p>Must run on the target player's own region thread (Folia): it reads and writes live entity state.
 */
public final class CleanseService {

    /**
     * At or above this remaining duration an effect is treated as PERMANENT and spared. Well above the longest
     * real debuff — vanilla Bad Omen runs 100 minutes (120 000 ticks) and is still cleansable — and well below
     * the {@code 1 000 000} ticks SE's passive driver applies, so a worn grant stays spared however long it has
     * been since the driver last refreshed it.
     */
    public static final int PERMANENT_FLOOR_TICKS = 20 * 60 * 60 * 4; // 4 hours

    /** Whether an effect type on a player is a permanent-while-worn grant SE itself maintains. */
    @FunctionalInterface
    public interface PassivePotions {
        boolean maintains(Player player, PotionEffectType type);
    }

    /** What one cleanse actually removed — drives the player's feedback and lets tests assert precisely. */
    public record Report(int potions, boolean unfroze, boolean clearedParkedDot, boolean extinguished,
                         boolean clearedMarks) {

        /** Whether anything at all was removed (a bless on a clean player still succeeds, but says so). */
        public boolean anything() {
            return potions > 0 || unfroze || clearedParkedDot || extinguished || clearedMarks;
        }
    }

    private final PassivePotions passives;
    private final DotParkLedger dotPark;

    public CleanseService(PassivePotions passives, DotParkLedger dotPark) {
        this.passives = Objects.requireNonNull(passives, "passives");
        this.dotPark = Objects.requireNonNull(dotPark, "dotPark");
    }

    /** Strip every landed debuff from {@code player}. Runs on the player's own thread. */
    public Report cleanse(Player player) {
        UUID id = player.getUniqueId();

        int potions = 0;
        // Copy first: removePotionEffect mutates the live collection this iterates.
        for (PotionEffect effect : List.copyOf(player.getActivePotionEffects())) {
            if (cleansable(player, effect)) {
                player.removePotionEffect(effect.getType());
                potions++;
            }
        }

        boolean unfroze = FrozenTargets.breakNow(id);
        boolean parked = dotPark.hasParked(id);
        if (parked) {
            dotPark.clear(id);
        }
        boolean burning = player.getFireTicks() > 0;
        if (burning) {
            player.setFireTicks(0);
        }
        boolean marked = DamageMarks.anyOn(id);
        if (marked) {
            DamageMarks.clear(id);
        }
        return new Report(potions, unfroze, parked, burning, marked);
    }

    /** Whether one active effect is a landed debuff rather than something the player carries by choice. */
    private boolean cleansable(Player player, PotionEffect effect) {
        if (!PotionCategories.matches(PotionCategories.HARMFUL, effect.getType())) {
            return false; // buffs and neutrals are never touched
        }
        if (passives.maintains(player, effect.getType())) {
            return false; // SE's own permanent-while-worn grant
        }
        // Negative = the infinite marker (1.19.4+); a huge finite duration is another plugin's permanent grant.
        return effect.getDuration() >= 0 && effect.getDuration() < PERMANENT_FLOOR_TICKS;
    }
}
