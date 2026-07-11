package feature.mask;

import engine.stores.WardStore;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;

/**
 * The SPLASH_HEAL ward guard (ADR-0053 §7): a warded player's share of a splash HEALING potion is boosted by
 * the ward's {@code amount}%. Intensity scales BOTH the instant-heal amount and the regeneration duration each
 * entity receives — both are "more healing". Runs on the potion's own region thread (its splash event).
 */
public final class SplashHealGuard implements Listener {

    /**
     * The instant-heal / regeneration effect NAMES across the whole range. {@code HEAL} is the legacy spelling,
     * {@code INSTANT_HEALTH} the 1.20.5 rename (cross-version-item-api) — both carried so {@code getName()}
     * matches on any era; {@code REGENERATION} is stable. Matched by NAME, never a {@link org.bukkit.potion.PotionEffectType}
     * constant (those were renamed and are registry-backed on modern).
     */
    private static final Set<String> HEALING_NAMES = Set.of("HEAL", "INSTANT_HEALTH", "REGENERATION");

    private final WardStore wards;
    private final LongSupplier nowTicks;

    public SplashHealGuard(WardStore wards, LongSupplier nowTicks) {
        this.wards = Objects.requireNonNull(wards, "wards");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        // Cheapest reject: nobody caught in the splash, or the potion carries no healing effect at all.
        if (event.getAffectedEntities().isEmpty() || !isHealing(event.getPotion())) {
            return;
        }
        long now = nowTicks.getAsLong();
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (!(affected instanceof Player player)) {
                continue;
            }
            double amount = wards.amount(player.getUniqueId(), WardStore.Type.SPLASH_HEAL, now);
            if (amount <= 0.0) {
                continue;
            }
            event.setIntensity(player, event.getIntensity(player) * (1.0 + amount / 100.0));
        }
    }

    /** Whether the thrown potion carries any instant-heal or regeneration effect (null-safe on potion/effects). */
    @SuppressWarnings("deprecation") // PotionEffectType.getName(): deprecated-not-removed across the range (the cross-version name rule).
    private static boolean isHealing(ThrownPotion potion) {
        if (potion == null) {
            return false;
        }
        for (PotionEffect effect : potion.getEffects()) {
            if (effect != null && effect.getType() != null && isHealingName(effect.getType().getName())) {
                return true;
            }
        }
        return false;
    }

    /** Whether an effect NAME is instant-heal or regeneration (case-normalised). Pure, so it is unit-tested directly. */
    static boolean isHealingName(String effectName) {
        return effectName != null && HEALING_NAMES.contains(effectName.toUpperCase(Locale.ROOT));
    }
}
