package feature.pet;

import compile.load.Library;
import compile.load.PetBracket;
import compile.load.PetDef;
import item.codec.PetCodec;
import item.worn.PetSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * The pets' {@link PetSource} (ADR-0052): scans the player's HOTBAR at worn-resolve time and yields the
 * stable keys of each pet's live bracket — a PASSIVE pet's always, an ACTIVE pet's only while its armed
 * window is open. One pet KEY contributes once no matter how many copies sit in the hotbar (the ADR-0035
 * non-stackable rule — two Shield pets are not double reduction). Runs on the player's own thread (resolve
 * time), never per hit; the {@code features.pets} toggle is read live so a reload re-tunes it.
 */
public final class PetWornSource implements PetSource {

    /** Hotbar slots 0-8 — the pet activation zone (the spec's "hold in hotbar"). */
    private static final int HOTBAR_SLOTS = 9;

    private final BooleanSupplier enabled;
    private final PetCodec codec;
    private final Supplier<Library> library;
    private final PetArmedStore armed;
    private final LongSupplier nowTicks;

    public PetWornSource(BooleanSupplier enabled, PetCodec codec, Supplier<Library> library, PetArmedStore armed,
                         LongSupplier nowTicks) {
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.library = Objects.requireNonNull(library, "library");
        this.armed = Objects.requireNonNull(armed, "armed");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    @Override
    public List<String> liveKeys(LivingEntity entity) {
        if (!enabled.getAsBoolean() || !(entity instanceof Player player)) {
            return List.of();
        }
        PlayerInventory inventory = player.getInventory();
        List<String> keys = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        long now = nowTicks.getAsLong();
        Library lib = library.get();
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            String petKey = stack == null ? null : codec.keyOf(stack);
            if (petKey == null || !seen.add(petKey)) {
                continue; // not a pet, or a second copy of one already counted
            }
            PetDef def = lib.petDefOf(petKey);
            if (def == null) {
                continue; // a stale key an old head still carries — inert until content returns
            }
            PetBracket bracket = def.bracketFor(codec.level(stack));
            if (bracket == null) {
                continue;
            }
            if (!def.active() || armed.isArmed(player.getUniqueId(), petKey, now)) {
                keys.addAll(bracket.wornStableKeys());
            }
        }
        return keys;
    }
}
