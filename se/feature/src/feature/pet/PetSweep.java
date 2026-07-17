package feature.pet;

import engine.stores.PlayerScoped;
import item.codec.PetCodec;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * The per-minute pets sweep (ADR-0052/0059), run for each online player on their own region thread: credits
 * one ONLINE minute of passive accrual to every main-inventory pet, and reconciles hotbar DRIFT the event listeners cannot see —
 * item pickup has no era-stable event (1.8 lacks {@code EntityPickupItemEvent}), and effects/commands can
 * move stacks silently — by fingerprinting the hotbar's (pet key, level) pairs and requesting the standard
 * worn refresh when it changed (the F09 hand-signature shape). The fingerprint map is per-player transient
 * state, cleared on quit via the module's {@link PlayerScoped} sweep.
 */
public final class PetSweep implements PlayerScoped {

    /** Hotbar slots 0-8 (matches {@link PetWornSource}). */
    private static final int HOTBAR_SLOTS = 9;

    private final PetCodec codec;
    private final PetLevelListener leveler;
    private final Consumer<Player> refresh;
    private final BooleanSupplier enabled;
    private final Map<UUID, Integer> fingerprints = new ConcurrentHashMap<>();

    public PetSweep(PetCodec codec, PetLevelListener leveler, Consumer<Player> refresh, BooleanSupplier enabled) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.leveler = Objects.requireNonNull(leveler, "leveler");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    /** One player's minute tick — must run on their own region thread (the accrual math assumes ONE minute). */
    public void run(Player player) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        leveler.creditPassiveMinute(player); // refreshes itself on a bracket cross
        int fingerprint = fingerprint(player);
        Integer last = fingerprints.put(player.getUniqueId(), fingerprint);
        if (last != null && last != fingerprint) {
            refresh.accept(player); // a pet drifted in/out since the last sweep with no event we saw
        }
    }

    /** Order-sensitive hash of the hotbar's (pet key, level) pairs; armed windows refresh on their own. */
    private int fingerprint(Player player) {
        PlayerInventory inventory = player.getInventory();
        int hash = 1;
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            String key = stack == null ? null : codec.keyOf(stack);
            hash = 31 * hash + (key == null ? 0 : key.hashCode() * 31 + codec.level(stack));
        }
        return hash;
    }

    @Override
    public void clear(UUID player) {
        fingerprints.remove(player);
    }
}
