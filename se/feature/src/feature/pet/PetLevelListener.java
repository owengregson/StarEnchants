package feature.pet;

import compile.load.MasterConfig;
import item.codec.PetCodec;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import platform.sched.Scheduling;

/**
 * Pet exp from play (ADR-0052, passive accrual per ADR-0059): every hotbar pet is credited on a mob kill (the souls deposit-on-any-kill
 * attribution — plain {@code getKiller()}, MONITOR so a cancelled death awards nothing) and on vanilla XP
 * gain (a DEDICATED {@code PlayerExpChangeEvent} hook at MONITOR, reading the post-{@code EXP_MULTIPLY}
 * amount — the engine's {@code fireExp} only scales in place and exposes nothing). Kill credits hop to the
 * KILLER's region thread before touching their inventory (the death event fires on the victim's); XP fires
 * on the player's own thread already. Mutated stacks are written back to their slot — the codec only mutates
 * the in-memory copy.
 */
public final class PetLevelListener implements Listener {

    /** Hotbar slots 0-8 — where kill/XP credits land (matches {@link PetWornSource}). */
    private static final int HOTBAR_SLOTS = 9;
    /** Main-inventory slots 0-35 — the passive-accrual zone on BOTH eras (armour/off-hand excluded). */
    private static final int STORAGE_SLOTS = 36;

    private final PetService service;
    private final PetCodec codec;
    private final Supplier<MasterConfig.PetsSection> pets;
    private final Consumer<Player> refresh;
    private final BooleanSupplier enabled;

    public PetLevelListener(PetService service, PetCodec codec, Supplier<MasterConfig.PetsSection> pets,
                            Consumer<Player> refresh, BooleanSupplier enabled) {
        this.service = Objects.requireNonNull(service, "service");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.pets = Objects.requireNonNull(pets, "pets");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return; // mob kills only (the spec); player deaths have their own handler list anyway
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null || !enabled.getAsBoolean()) {
            return;
        }
        int amount = pets.get().expPerMobKill();
        if (amount <= 0) {
            return;
        }
        // The death fired on the VICTIM's region; the killer's inventory is theirs — hop first (trak rule).
        Scheduling.onEntity(killer, () -> creditHotbar(killer, amount));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        // MONITOR sees the FINAL amount (after the engine's EXP_MULTIPLY scaling at HIGH).
        int amount = (int) Math.round(event.getAmount() * pets.get().expPerXpPoint());
        if (amount > 0) {
            creditHotbar(event.getPlayer(), amount); // already on the player's own thread
        }
    }

    /** Credit every hotbar pet and write each mutated stack back; one worn refresh if any bracket crossed. */
    void creditHotbar(Player player, int amount) {
        PlayerInventory inventory = player.getInventory();
        boolean crossed = false;
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || !codec.isPet(stack) || stack.getAmount() > 1) {
                continue; // a STACK of identical heads shares one meta — crediting it would level every copy
            }
            PetService.Progress progress = service.gainExp(player, stack, amount);
            if (progress.changed()) {
                inventory.setItem(slot, stack); // persist — the codec mutated only the in-memory copy
                crossed |= progress.bracketCrossed();
            }
        }
        if (crossed) {
            refresh.accept(player); // a new bracket = new ability set; re-resolve once for all slots
        }
    }

    /**
     * One ONLINE minute of passive accrual (ADR-0059) for every pet in the main inventory — hotbar slots flag
     * the PASSIVE-pet double rate. Called by the sweep on the player's own region thread; container/ender
     * inventories are never scanned (this reads only {@code PlayerInventory} slots 0-35).
     */
    void creditPassiveMinute(Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean crossed = false;
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || !codec.isPet(stack) || stack.getAmount() > 1) {
                continue; // a STACK of identical heads shares one meta — crediting it would level every copy
            }
            PetService.Progress progress = service.accruePassive(player, stack, slot < HOTBAR_SLOTS);
            if (progress.changed()) {
                inventory.setItem(slot, stack); // persist — the codec mutated only the in-memory copy
                crossed |= progress.bracketCrossed();
            }
        }
        if (crossed) {
            refresh.accept(player);
        }
    }
}
