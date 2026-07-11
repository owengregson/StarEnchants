package feature.mask;

import feature.apply.GestureOutcome;
import feature.compat.Mats;
import feature.compat.MenuClicks;
import feature.compat.Sounds;
import item.codec.CombatCodec;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import platform.item.Inventories;
import platform.lang.Messages;

/**
 * The mask REMOVE gesture (ADR-0053 §3) — right-click a masked helmet in the player's OWN inventory with an
 * EMPTY cursor to pop the mask back off intact. The third documented non-template gesture (joining the
 * soul-gem merge and unopened-book exemptions from ADR-0041): the shared {@code ApplyGestureListener}
 * requires a family item ON the cursor, and this gesture is defined by the cursor being empty, so it cannot
 * be a leaf — it re-applies the base's guard order and commit protocol by hand instead. Folia-correct:
 * {@code InventoryClickEvent} fires on the clicking player's own region thread.
 */
public final class MaskRemoveListener implements Listener {

    private final MaskService service;
    private final CombatCodec combat;
    private final Messages messages;
    private final Sounds sounds;
    private final BooleanSupplier enabled; // features.masks — LIVE, so the gesture obeys a /se reload flip

    public MaskRemoveListener(MaskService service, CombatCodec combat, Messages messages, Sounds sounds,
                              BooleanSupplier enabled) {
        this.service = Objects.requireNonNull(service, "service");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation") // getCursor/getView/updateInventory: the floor-stable cursor/view path
    public void onMaskedHelmetRightClick(InventoryClickEvent event) {
        if (!enabled.getAsBoolean()) {
            return; // disabled masks keep vanilla right-click pickup untouched
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != ClickType.RIGHT) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (cursor != null && !Mats.isAir(cursor.getType())) {
            return; // a held item means some OTHER gesture (or a vanilla place) — this one is cursor-less
        }
        if (MenuClicks.clickedInventory(event) == null
                || MenuClicks.clickedInventory(event) != event.getView().getBottomInventory()) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        // Cold path (one right-click): the full combat-blob decode is fine — no ItemView cache needed here.
        if (clicked == null || combat.read(clicked).maskKey() == null) {
            return;
        }

        // This right-click would otherwise vanilla-pick-up the helmet onto the cursor — cancel it; the click
        // now MEANS "pop the mask off" and nothing else.
        event.setCancelled(true);
        commit(player, event, service.remove(clicked));
    }

    /**
     * The ADR-0041 commit protocol re-applied for a cursor-less gesture — the base's order minus the cursor
     * write-back (there is no cursor to spend): target → give → refresh → cue → message → followUp.
     */
    @SuppressWarnings("deprecation") // updateInventory: the floor-stable refresh, as the base listener uses
    private void commit(Player player, InventoryClickEvent event, GestureOutcome result) {
        if (result.commit()) {
            ItemStack next = result.newTarget();
            event.setCurrentItem(next == null || next.getAmount() <= 0 ? null : next);
        }
        if (result.produced() != null) {
            Inventories.giveOrDrop(player, result.produced());
        }
        if (result.commit()) {
            player.updateInventory();
        }
        GestureOutcome.Cue cue = result.cue();
        if (cue != null && cue.sound() != null) {
            sounds.play(player, player.getLocation(), cue.sound().name(), cue.sound().volume(), cue.sound().pitch());
        }
        if (result.message() != null) {
            messages.sendText(player, result.message());
        }
        if (result.followUp() != null) {
            result.followUp().run();
        }
    }
}
