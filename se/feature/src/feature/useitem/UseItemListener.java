package feature.useitem;

import compile.load.UseItemDef;
import feature.compat.Hands;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import platform.lang.Messages;
import platform.text.Colors;
import platform.text.TimeFormat;

/**
 * Right-click a held use-item to fire its abilities (§3.6, docs/decisions/0048-use-items.md). Bukkit-thin glue —
 * logic is in {@link UseItemService}; this clones {@link feature.book.UnopenedBookListener}'s guard structure
 * (priority LOW, NOT ignoreCancelled, main-hand only, RIGHT_CLICK_AIR|BLOCK) and renders the UNIVERSAL use-item
 * feedback (shared by every use-item, keyed in lang.yml, sent WITHOUT the global message prefix). Folia-correct:
 * fires on the player's own region thread, touching only their held item.
 */
public final class UseItemListener implements Listener {

    private final UseItemService service;
    private final Messages messages;
    private final Hands hands;
    private final BooleanSupplier enabled; // §L features.use-items — live; a disabled flag leaves the item inert

    public UseItemListener(UseItemService service, Messages messages, Hands hands, BooleanSupplier enabled) {
        this.service = Objects.requireNonNull(service, "service");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.hands = Objects.requireNonNull(hands, "hands");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    // priority LOW, NOT ignoreCancelled (a RIGHT_CLICK_BLOCK often arrives pre-cancelled) — mirrors the unopened
    // book so a use-item claims its right-click ahead of the INTERACT triggers (TriggerListeners at HIGH).
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (!hands.isMainHand(event)) {
            return; // main-hand only — the off-hand pass of a two-hand interact would double-fire
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        // Read the main hand directly (not event.getItem(), which is null on an air-click).
        ItemStack used = hands.mainHand(player);
        String key = used == null ? null : service.keyOf(used);
        if (key == null) {
            return; // not a use-item
        }
        if (!enabled.getAsBoolean()) {
            return; // feature disabled: leave the item inert (do not claim the gesture)
        }
        event.setCancelled(true); // claim the gesture: a use-item does nothing else on right-click

        UseItemDef def = service.defOf(key);
        if (def == null) {
            sendFail(player, "", ""); // a stale key an old item still carries — inert, but explain the failure
            return;
        }
        if (!def.permission().isEmpty() && !player.hasPermission(def.permission())) {
            sendFail(player, def.name(), "");
            return;
        }
        UseOutcome outcome = service.use(player, def);
        switch (outcome.result()) {
            case ACTIVATED -> {
                if (def.consumable()) {
                    consumeMainHand(player, key);
                }
                sendUniversal(player, "use-item.success", "NAME", def.name());
            }
            case ON_COOLDOWN -> sendUniversal(player, "use-item.cooldown", "NAME", def.name(),
                    "TIME_FORMATTED", TimeFormat.hmsFromTicks(outcome.cooldownRemainingTicks()));
            case CONDITION_FAILED -> sendFail(player, def.name(), outcome.conditionSource());
            case CHANCE_FAILED, BLOCKED -> { } // the roll just did not land / a non-feedback gate — silent
        }
    }

    // Re-read after the effects flush: a pathological ability could have emptied or swapped the held slot
    // (e.g. commands: ["clear {PLAYER}"] or a disarm effect). Only decrement if the same use-item is still held,
    // so we never NPE on an emptied hand nor charge an item the abilities replaced it with.
    private void consumeMainHand(Player player, String key) {
        ItemStack hand = hands.mainHand(player);
        if (hand == null || !key.equals(service.keyOf(hand))) {
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        hands.setMainHand(player, hand.getAmount() <= 0 ? null : hand);
    }

    private void sendFail(Player player, String name, String condition) {
        sendUniversal(player, "use-item.fail", "NAME", name, "CONDITION", condition);
    }

    /** Render a UNIVERSAL use-item message: no prefix (fragment), fill tokens, translate colours, send; empty ⇒ silent. */
    private void sendUniversal(Player player, String key, Object... tokens) {
        messages.sendText(player, Colors.translate(messages.fragment(key, tokens)));
    }
}
