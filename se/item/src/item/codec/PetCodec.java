package item.codec;

import org.bukkit.inventory.ItemStack;

/**
 * Marks / reads / mutates a PET head and a Pet Food item (ADR-0052). A pet stores its def key as a PDC
 * {@code STRING} under {@link ItemKeys#pet()} (presence <em>is</em> the identity, the {@link UseItemCodec}
 * shape) plus two integer counters — {@link ItemKeys#petLevel()} and {@link ItemKeys#petExp()} — kept OUT of
 * the combat blob (the trak rule, §5.2: frequent counter writes must never thrash the {@code ItemView}
 * content-hash cache). Pet Food bakes its {@code +levels} amount as a PDC integer under
 * {@link ItemKeys#petFood()} at mint (the dust {@code successBonus} pattern), so an already-minted food keeps
 * its printed value across config re-tunes.
 *
 * <p>Pure state: level math (clamping, exp-to-level rollover) lives in the pet service — this codec never
 * reads config. Decode is tolerant (absent/corrupt reads as level 1 / exp 0), never throws.
 */
public final class PetCodec {

    private final String petKey;
    private final String levelKey;
    private final String expKey;
    private final String expFracKey;
    private final String foodKey;
    private final String xpGateKey;
    private final ItemStateStore store;

    public PetCodec(ItemKeys keys, ItemStateStore store) {
        this.petKey = keys.pet();
        this.levelKey = keys.petLevel();
        this.expKey = keys.petExp();
        this.expFracKey = keys.petExpFrac();
        this.foodKey = keys.petFood();
        this.xpGateKey = keys.petXpGate();
        this.store = store;
    }

    public boolean isPet(ItemStack stack) {
        return keyOf(stack) != null;
    }

    /** The pet def key {@code stack} carries, or {@code null} if it is not a pet. */
    public String keyOf(ItemStack stack) {
        String raw = store.read(stack, petKey);
        return raw == null || raw.isBlank() ? null : raw;
    }

    /** The pet's stored level; a fresh or corrupt pet reads as level 1. */
    public int level(ItemStack stack) {
        return Math.max(1, store.readInt(stack, levelKey, 1));
    }

    /** The pet's exp toward its next level; never negative. */
    public int exp(ItemStack stack) {
        return Math.max(0, store.readInt(stack, expKey, 0));
    }

    /** The passive-accrual carry in 1/60000-exp units (ADR-0059); absent/corrupt reads 0. */
    public int expFrac(ItemStack stack) {
        return Math.max(0, store.readInt(stack, expFracKey, 0));
    }

    public void writeExpFrac(ItemStack stack, int frac) {
        store.writeInt(stack, expFracKey, Math.max(0, frac));
    }

    /** Stamp a fresh pet identity at {@code level} with zero exp and no carry (mint / admin give). */
    public void stamp(ItemStack stack, String defKey, int level) {
        store.write(stack, petKey, defKey);
        store.writeInt(stack, levelKey, Math.max(1, level));
        store.writeInt(stack, expKey, 0);
        store.writeInt(stack, expFracKey, 0);
    }

    /** Write the (already-clamped) level + exp pair the service computed. */
    public void writeProgress(ItemStack stack, int level, int exp) {
        store.writeInt(stack, levelKey, Math.max(1, level));
        store.writeInt(stack, expKey, Math.max(0, exp));
    }

    /** The +levels a Pet Food item grants, or {@code 0} if {@code stack} is not pet food. */
    public int foodLevels(ItemStack stack) {
        return Math.max(0, store.readInt(stack, foodKey, 0));
    }

    public boolean isPetFood(ItemStack stack) {
        return foodLevels(stack) > 0;
    }

    /** Stamp a Pet Food item with its baked +levels amount (mint). */
    public void stampFood(ItemStack stack, int levels) {
        store.writeInt(stack, foodKey, Math.max(1, levels));
    }

    /**
     * {@code ITEM_XP_TRACK}'s per-item window stamp, in MINUTES since the epoch, or {@code 0} for a pet that
     * has never earned gated XP (a freshly minted pet is therefore due immediately).
     *
     * <p>Minutes, not milliseconds: {@link ItemStateStore} carries no 64-bit surface, and an ms epoch does not
     * fit an int. A minute stamp fits until the year ~6000 and the coarsest window anyone gates on is 24 h, so
     * the resolution costs nothing — whereas a seconds stamp would overflow in 2038 and a hi/lo int pair would
     * add a second key and a torn-write case to a counter that travels with traded items.
     */
    public int xpGateMinutes(ItemStack stack) {
        return Math.max(0, store.readInt(stack, xpGateKey, 0));
    }

    public void writeXpGateMinutes(ItemStack stack, int minutes) {
        store.writeInt(stack, xpGateKey, Math.max(0, minutes));
    }
}
