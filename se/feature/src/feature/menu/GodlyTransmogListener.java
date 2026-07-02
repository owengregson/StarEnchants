package feature.menu;

import feature.apply.ApplyGestureListener;
import feature.apply.GestureOutcome;
import feature.compat.Sounds;
import feature.scroll.ScrollService;
import item.codec.CombatCodec;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.lang.Messages;

/**
 * The physical godly-transmog gesture (§I/§K): drag the tool onto enchanted gear in your own inventory to open
 * the reorder GUI bound to that piece. A thin leaf of the shared {@link ApplyGestureListener} (ADR-0041);
 * unlike the transmog scroll (one-shot random, consumed), the tool just opens the menu and is NOT consumed.
 */
public final class GodlyTransmogListener extends ApplyGestureListener {

    private final ScrollService scrolls;
    private final GodlyTransmogMenu menu;
    private final CombatCodec combat;

    public GodlyTransmogListener(ScrollService scrolls, GodlyTransmogMenu menu, CombatCodec combat, Messages messages, Sounds sounds) {
        super(messages, sounds);
        this.scrolls = Objects.requireNonNull(scrolls, "scrolls");
        this.menu = Objects.requireNonNull(menu, "menu");
        this.combat = Objects.requireNonNull(combat, "combat");
    }

    @Override
    protected boolean claimsCursor(ItemStack cursor) {
        return scrolls.isGodlyTransmog(cursor);
    }

    @Override
    protected boolean claimsTarget(ItemStack cursor, ItemStack target) {
        // No enchants = nothing to reorder; leaving it unclaimed falls through to the vanilla click, keeping the
        // tool on the cursor for another piece (the base already filtered AIR).
        return !combat.read(target).enchants().isEmpty();
    }

    @Override
    protected GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot) {
        // Own the interaction (the base cancelled it); the tool is NOT consumed — just open the reorder GUI
        // bound to the clicked slot (it region-hops).
        return GestureOutcome.noop(null).andThen(() -> menu.open(player, slot));
    }
}
