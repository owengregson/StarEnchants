package feature.heroic;

import feature.trigger.LegacyGearPoll;
import item.codec.CombatCodec;
import java.util.Objects;
import java.util.Random;
import org.bukkit.inventory.ItemStack;

/**
 * Legacy (1.8.9) heroic durability save (ADR-0044; docs/legacy-1.8.9-codeshare-design.md §6, Item 3) — the
 * era-exclusive {@code overlay/legacy} {@link LegacyGearPoll.HeroicSave} subscriber. 1.8 has no
 * {@code PlayerItemDamageEvent} to cancel, so on a detected durability rise it rolls the SPECIFIC damaged item's
 * heroic chance and RESTORES the lost durability — the post-hoc equivalent of the modern pre-cancel (the item
 * ends the tick undamaged either way). The wear-cancel is scaled off the material max (1.8 has no vanilla-stats
 * override), so a sub-diamond-max heroic piece lasts like diamond.
 */
public final class LegacyHeroicSave implements LegacyGearPoll.HeroicSave {

    private final CombatCodec codec;
    private final Random random;

    public LegacyHeroicSave(CombatCodec codec, Random random) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    @SuppressWarnings("deprecation") // setDurability: the 1.8 durability accessor
    public boolean trySave(ItemStack item, short priorDamage) {
        double base = codec.read(item).heroic().durability(); // EMPTY → NONE → 0.0 (fast no-op)
        if (base <= 0.0) {
            return false;
        }
        double chance = HeroicDiamond.scaledWearCancel(item.getType().getMaxDurability(), item.getType(), base);
        if (random.nextDouble() < chance) {
            item.setDurability(priorDamage); // undo the loss — the heroic save
            return true;
        }
        return false;
    }
}
