package feature.mask;

import item.codec.CombatCodec;
import java.util.Objects;
import java.util.function.Function;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.item.Inventories;

/**
 * Breaks a spent masked helmet and pops its mask back off intact (ADR-0053 follow-up): when a worn helmet's
 * durability is used up, the whole helmet breaks like vanilla and the mask lands in the wearer's inventory
 * (else drops at their feet) instead of being destroyed with the helmet. Era-agnostic — the modern
 * {@code PlayerItemDamageEvent} listener and the 1.8 gear poll both decide "the helmet is spent" in their own
 * way, then call this to carry out the break-and-salvage. Depends only on a {@code minter} ({@code MaskService::mint}
 * in production) rather than the whole service, so it stays trivially testable.
 */
public final class MaskBreakSalvage {

    private final CombatCodec codec;
    private final Function<String, ItemStack> minter; // stem → a fresh mask item (null for stale content)

    public MaskBreakSalvage(CombatCodec codec, Function<String, ItemStack> minter) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.minter = Objects.requireNonNull(minter, "minter");
    }

    /** The mask stamped on {@code helmet}, or {@code null} when it carries none — the fast worn-hit guard. */
    public String maskOf(ItemStack helmet) {
        return helmet == null ? null : codec.read(helmet).maskKey();
    }

    /**
     * Break {@code player}'s spent worn helmet and salvage its mask: mint the mask back to the player (inventory,
     * else drop) and clear the helmet slot. Caller has already established the helmet is masked and out of
     * durability. {@code helmet} is assumed to be the worn helmet (masks are helmet-only, so a worn hit is the
     * only durability source).
     */
    public void breakAndSalvage(Player player, ItemStack helmet) {
        String maskKey = codec.read(helmet).maskKey();
        if (maskKey == null) {
            return;
        }
        ItemStack mask = minter.apply(maskKey); // null for stale content — the helmet still breaks, nothing to pop
        if (mask != null) {
            Inventories.giveOrDrop(player, mask);
        }
        player.getInventory().setHelmet(null); // the whole helmet breaks, like vanilla
    }
}
