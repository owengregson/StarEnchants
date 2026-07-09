package feature.useitem;

import compile.load.UseItemDef;
import feature.compat.Hands;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import platform.lang.Messages;
import platform.text.Colors;
import platform.text.TimeFormat;

/**
 * The shared use-item activation tail (§3.6, docs/decisions/0048-use-items.md; the is-food design spec): resolve
 * the def a claimed gesture named, gate on its permission, run its abilities through {@link UseItemService#use},
 * render the universal feedback, and consume one item on {@code ACTIVATED} when the def is consumable. Both entry
 * points — the right-click {@link UseItemListener} and the eat-completion {@link UseItemConsumeListener} — funnel
 * through this ONE copy so the click gesture and the eat gesture behave identically. Folia-correct: the caller is
 * already on the player's own region thread and this only touches their held item.
 */
public final class UseItemRunner {

    private final UseItemService service;
    private final Messages messages;
    private final Hands hands;

    public UseItemRunner(UseItemService service, Messages messages, Hands hands) {
        this.service = Objects.requireNonNull(service, "service");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.hands = Objects.requireNonNull(hands, "hands");
    }

    /**
     * Fire use-item {@code key}'s abilities for {@code player} and render the outcome; a stale key (no live def)
     * or a failed permission is an explained no-op, and a successful {@code ACTIVATED} consumes one held item when
     * the def is consumable.
     */
    void activate(Player player, String key) {
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
                    consumeOne(player, key);
                }
                sendUniversal(player, "use-item.success", "NAME", def.name());
            }
            case ON_COOLDOWN -> sendUniversal(player, "use-item.cooldown", "NAME", def.name(),
                    "TIME_FORMATTED", TimeFormat.hmsFromTicks(outcome.cooldownRemainingTicks()));
            case CONDITION_FAILED -> sendFail(player, def.name(), outcome.conditionSource());
            case CHANCE_FAILED, BLOCKED -> { } // the roll just did not land / a non-feedback gate — silent
        }
    }

    // Charge one item AFTER the effects flush (charging first would perturb the condition facts the pipeline read
    // and need refund paths on later gate fails). Re-read the slots, since an effect/command could have moved or
    // refilled the hand, and charge the first stack still stamped with THIS use-item's key: main hand → off-hand
    // (the is-food eat route eats from the off-hand) → an inventory scan (an effect that swapped/refilled the hand).
    // If nothing carries the key the effect genuinely destroyed the item — nothing to charge, and nothing kept, so
    // it is not a free ride. Only a key-stamped stack is ever decremented (never a different item), and an emptied
    // hand never NPEs — this replaces the old fail-open main-hand-only guard (G08).
    private void consumeOne(Player player, String key) {
        ItemStack main = hands.mainHand(player);
        if (main != null && key.equals(service.keyOf(main))) {
            hands.setMainHand(player, decremented(main));
            return;
        }
        ItemStack off = hands.offHand(player); // null on 1.8.9 (no off-hand) → the legacy shell falls through to the scan
        if (off != null && key.equals(service.keyOf(off))) {
            hands.setOffHand(player, decremented(off));
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && key.equals(service.keyOf(stack))) {
                inv.setItem(i, decremented(stack));
                return;
            }
        }
    }

    /** Decrement one from {@code stack}; returns {@code null} when it empties so the caller clears the slot. */
    private static ItemStack decremented(ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
        return stack.getAmount() <= 0 ? null : stack;
    }

    private void sendFail(Player player, String name, String condition) {
        sendUniversal(player, "use-item.fail", "NAME", name, "CONDITION", condition);
    }

    /** Render a UNIVERSAL use-item message: no prefix (fragment), fill tokens, translate colours, send; empty ⇒ silent. */
    private void sendUniversal(Player player, String key, Object... tokens) {
        messages.sendText(player, Colors.translate(messages.fragment(key, tokens)));
    }
}
