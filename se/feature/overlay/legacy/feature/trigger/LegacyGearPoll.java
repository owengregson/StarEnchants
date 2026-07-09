package feature.trigger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import platform.sched.Scheduling;

/**
 * The ONE legacy (1.8.9) per-tick gear poll (ADR-0044; docs/legacy-1.8.9-codeshare-design.md §6) — the
 * era-exclusive {@code overlay/legacy} replacement for the three separate polls the fork shipped (ITEM_DAMAGE /
 * heroic-durability / instant-armour-refresh). One repeating global task, one online-players walk, one 5-slot
 * scan per player (held + the four armour pieces: material ordinal, durability value, combat-state identity, and
 * the armour type+count+identity signature). The task starts in the constructor; the legacy bindings then attach
 * the three subscribers.
 *
 * <p><strong>Fixed subscriber order per slot delta</strong>, preserving the shipped semantics exactly:
 * <ol>
 *   <li><b>durability rise on the same item</b> &rarr; fire ITEM_DAMAGE ({@code fireItemDamage}) — BEFORE any
 *       restore, matching the modern "ITEM_DAMAGE at HIGH fires regardless of the heroic cancel" order;</li>
 *   <li><b>heroic save roll</b> ({@code heroicSave}) &rarr; restore the prior durability and mark the slot for
 *       write-back, recording the POST-restore value so next tick sees no phantom delta;</li>
 *   <li><b>armour signature change</b> ({@code refreshEquip}) &rarr; re-resolve worn state (type+count only,
 *       untouched by a restore).</li>
 * </ol>
 *
 * <p>Main-thread only — the 1.8 lane is never Folia (the maps are concurrent purely for defensiveness).
 */
public final class LegacyGearPoll {

    /** Roll a durability hit's heroic save; restore the prior value on a save. Returns whether it restored. */
    public interface HeroicSave {
        boolean trySave(ItemStack item, short priorDamage);
    }

    /** Slots scanned per player: index 0 = held, 1..4 = the four armour pieces. */
    private static final int SLOTS = 5;

    private final Map<UUID, int[]> lastType = new ConcurrentHashMap<>();      // Material ordinal per slot, −1 = empty
    private final Map<UUID, short[]> lastDamage = new ConcurrentHashMap<>();  // durability (damage value) per slot
    private final Map<UUID, String[]> lastIdentity = new ConcurrentHashMap<>(); // combat-state blob per slot, null = plain
    private final Map<UUID, String> lastArmour = new ConcurrentHashMap<>();   // armour type+count+identity signature

    // Material+count alone misses in-place blob rewrites (crystal-extract) and same-material swaps, leaving stale
    // worn bonuses live and free-repairing a heroic item swapped over a fresher same-material one — so every slot
    // also carries its combat-state blob (the 1.8 PDC-equivalent) as an identity, folded into both gates below.
    private final Function<ItemStack, String> identity;

    private Consumer<Player> fireItemDamage; // subscriber 1
    private HeroicSave heroicSave;           // subscriber 2
    private Consumer<Player> refreshEquip;   // subscriber 3

    public LegacyGearPoll(Function<ItemStack, String> identity) {
        this.identity = Objects.requireNonNull(identity, "identity");
        Scheduling.repeatingGlobal(1L, 1L, this::poll);
    }

    /** Subscriber 1: fire ITEM_DAMAGE for the player on each detected durability rise (before any restore). */
    public void fireItemDamage(Consumer<Player> subscriber) {
        this.fireItemDamage = subscriber;
    }

    /** Subscriber 2: roll + restore the specific damaged item's heroic durability save. */
    public void heroicSave(HeroicSave subscriber) {
        this.heroicSave = subscriber;
    }

    /** Subscriber 3: re-resolve the player's worn state when the armour signature changes. */
    public void refreshEquip(Consumer<Player> subscriber) {
        this.refreshEquip = subscriber;
    }

    private void poll() {
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            scan(player);
        }
        lastType.keySet().retainAll(online); // forget players who logged off
        lastDamage.keySet().retainAll(online);
        lastIdentity.keySet().retainAll(online);
        lastArmour.keySet().retainAll(online);
    }

    @SuppressWarnings("deprecation") // getItemInHand/getDurability: the 1.8 held-item + durability accessors
    private void scan(Player player) {
        UUID id = player.getUniqueId();
        PlayerInventory inv = player.getInventory();
        ItemStack held = inv.getItemInHand();
        ItemStack[] armour = inv.getArmorContents();
        ItemStack[] slots = new ItemStack[SLOTS];
        slots[0] = held;
        System.arraycopy(armour, 0, slots, 1, Math.min(armour.length, SLOTS - 1));

        int[] types = lastType.computeIfAbsent(id, k -> emptyTypes());
        short[] dmg = lastDamage.computeIfAbsent(id, k -> new short[SLOTS]);
        String[] ids = lastIdentity.computeIfAbsent(id, k -> new String[SLOTS]);
        boolean heldSaved = false;
        boolean armourSaved = false;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack item = slots[i];
            short max = item == null ? 0 : item.getType().getMaxDurability();
            int type = item == null ? -1 : item.getType().ordinal();
            short dur = (item == null || max <= 0) ? 0 : item.getDurability();
            String ident = item == null ? null : identity.apply(item);
            // Same item still in the slot AND its damage value rose → a durability hit. A type change OR an identity
            // change (blob mismatch) is a swap, not a hit — so a more-damaged same-material item swapped in cannot be
            // misread as wear and free-repaired. A fall is a repair/replace. Only damageable items (max > 0) carry
            // durability. ids[i] is last tick's identity (read before the overwrite below), matching types[i]/dmg[i].
            if (type == types[i] && Objects.equals(ident, ids[i]) && max > 0 && dur > dmg[i]) {
                if (fireItemDamage != null) {
                    fireItemDamage.accept(player); // (1) ITEM_DAMAGE before any restore
                }
                if (heroicSave != null && heroicSave.trySave(item, dmg[i])) { // (2) heroic save → restore
                    dur = dmg[i]; // record the POST-restore value so next tick sees no phantom delta
                    if (i == 0) {
                        heldSaved = true;
                    } else {
                        armourSaved = true;
                    }
                }
            }
            types[i] = type;
            dmg[i] = dur;
            ids[i] = ident;
        }
        if (heldSaved) {
            inv.setItemInHand(held);
        }
        if (armourSaved) {
            inv.setArmorContents(armour);
        }

        // (3) armour signature (type+count+identity) change → re-resolve worn state. The identity term catches a
        // same-material swap or an in-place blob rewrite (crystal extract) that type+count alone would miss, so a
        // stale worn bonus refreshes within one tick. Full blobs, not hashes: a collision can never suppress a
        // refresh (mirrors the ItemViewCache no-collision rule). Untouched by a durability restore (blob unchanged).
        String signature = armourSignature(armour, ids); // ids[1..4] are this tick's armour identities
        String previous = lastArmour.put(id, signature);
        if (refreshEquip != null && previous != null && !previous.equals(signature)) {
            refreshEquip.accept(player);
        }
    }

    /** Material + amount + combat-state identity of the four armour pieces — changes on equip/unequip/type-swap,
     *  a same-material swap, or an in-place blob rewrite, but not on durability loss. {@code ids[j + 1]} is the
     *  identity for {@code armour[j]} (slot 0 is the held item). */
    private static String armourSignature(ItemStack[] armour, String[] ids) {
        StringBuilder sb = new StringBuilder(64);
        for (int j = 0; j < armour.length; j++) {
            ItemStack piece = armour[j];
            if (piece == null) {
                sb.append('-');
            } else {
                String ident = j + 1 < ids.length ? ids[j + 1] : null;
                sb.append(piece.getType().name()).append('x').append(piece.getAmount())
                        .append('#').append(ident == null ? "" : ident);
            }
            sb.append('|');
        }
        return sb.toString();
    }

    private static int[] emptyTypes() {
        int[] a = new int[SLOTS];
        Arrays.fill(a, -1);
        return a;
    }
}
