package feature.heroic;

import item.codec.CombatCodec;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import platform.sched.Scheduling;

/**
 * Legacy (1.8.9) heroic durability save (docs/legacy-1.8.9-codeshare-design.md §6, Item 3). 1.8 has no
 * {@code PlayerItemDamageEvent} to cancel, so a per-tick poll detects durability loss on each player's held +
 * worn items and, per the SPECIFIC damaged item's heroic chance, RESTORES the lost durability — the post-hoc
 * equivalent of the modern pre-cancel (the item ends the tick undamaged either way). Same-FQN counterpart to
 * the modern listener; the NMS-free degrade restored to parity. Main-thread only — the 1.8 lane is never Folia.
 *
 * <p>The restored stack is written back ({@code setItemInHand}/{@code setArmorContents}) rather than relying on
 * 1.8's get-armour mirror semantics, so the save persists regardless of copy-vs-mirror behaviour.
 */
public final class HeroicDurabilityListener implements Listener {

    /** Slots polled per player: index 0 = held, 1..4 = the four armour pieces. */
    private static final int SLOTS = 5;

    private final CombatCodec codec;
    private final Random random;
    private final Map<UUID, int[]> lastType = new ConcurrentHashMap<>();
    private final Map<UUID, short[]> lastDamage = new ConcurrentHashMap<>();

    public HeroicDurabilityListener(CombatCodec codec, Random random) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.random = Objects.requireNonNull(random, "random");
        Scheduling.repeatingGlobal(1L, 1L, this::poll);
    }

    private void poll() {
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            scan(player);
        }
        lastType.keySet().retainAll(online);
        lastDamage.keySet().retainAll(online);
    }

    private void scan(Player player) {
        UUID id = player.getUniqueId();
        PlayerInventory inv = player.getInventory();
        ItemStack held = inv.getItemInHand();
        ItemStack[] armour = inv.getArmorContents();
        int[] types = lastType.computeIfAbsent(id, k -> emptyTypes());
        short[] dmg = lastDamage.computeIfAbsent(id, k -> new short[SLOTS]);

        boolean heldSaved = checkSlot(0, held, types, dmg);
        boolean armourSaved = false;
        for (int a = 0; a < armour.length && a < SLOTS - 1; a++) {
            armourSaved |= checkSlot(a + 1, armour[a], types, dmg);
        }
        if (heldSaved) {
            inv.setItemInHand(held);
        }
        if (armourSaved) {
            inv.setArmorContents(armour);
        }
    }

    /** Records the slot's new state; on a detected durability hit, rolls the item's heroic chance and, on a
     * save, restores the prior durability. Returns whether this slot's stack was restored (needs write-back). */
    private boolean checkSlot(int i, ItemStack item, int[] types, short[] dmg) {
        short max = item == null ? 0 : item.getType().getMaxDurability();
        int type = item == null ? -1 : item.getType().ordinal();
        short dur = (item == null || max <= 0) ? 0 : item.getDurability();
        boolean restored = false;
        if (type == types[i] && max > 0 && dur > dmg[i]) {
            double chance = codec.read(item).heroic().durability(); // EMPTY → NONE → 0.0 (fast no-op)
            if (chance > 0.0 && random.nextDouble() < chance) {
                item.setDurability(dmg[i]); // undo the loss — the heroic save
                dur = dmg[i];
                restored = true;
            }
        }
        types[i] = type;
        dmg[i] = dur;
        return restored;
    }

    private static int[] emptyTypes() {
        int[] a = new int[SLOTS];
        Arrays.fill(a, -1);
        return a;
    }
}
