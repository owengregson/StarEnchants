package bootstrap.wire;

import feature.menu.MintIo;
import java.util.OptionalInt;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.item.Inventories;
import platform.lang.Messages;
import platform.sched.Scheduling;

/**
 * Shared mint-delivery helpers moved verbatim from {@code SeCommand} (ADR-0047): the generic give-to-target
 * delivery, the simple self-give, optional-percent / dust-percent parsing, and the tell / self-vs-other guards.
 * The module {@code Mints} reuse these so the give/self bodies stay byte-for-byte with the old command surface.
 * All {@code sendMessage} here is the same operator/admin-echo exemption {@code SeCommand} documents (ADR-0041).
 */
final class Give {

    private Give() {
    }

    /** The production {@link MintIo}: the message policy plus the verbatim {@link #deliver} body. */
    static MintIo io(Messages messages) {
        return new MintIo(messages, (sender, target, stack, messageKey, label) ->
                deliver(messages, sender, target, stack, messageKey, label));
    }

    /** Deliver {@code item} on the target's own region thread (overflow → feet); confirm to a distinct sender. */
    static void deliver(Messages messages, CommandSender sender, Player target, ItemStack item,
                        String targetMsgKey, String itemLabel) {
        Scheduling.onEntity(target, () -> {
            Inventories.giveOrDrop(target, item);
            target.sendMessage(messages.format(targetMsgKey, "KEY", itemLabel, "KIND", itemLabel,
                    "TIER", itemLabel, "LEVEL", "", "ID", itemLabel));
        });
        if (!(sender instanceof Player p) || !p.getUniqueId().equals(target.getUniqueId())) {
            tell(sender, messages.format("command.give.delivered", "ITEM", itemLabel, "PLAYER", target.getName()));
        }
    }

    /** Give a pre-minted item to the sender on their own thread (overflow drops at feet), with a message. */
    static void giveSimpleItem(Messages messages, CommandSender sender, ItemStack item, String message) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return;
        }
        Scheduling.onEntity(player, () -> {
            Inventories.giveOrDrop(player, item);
            player.sendMessage(message);
        });
    }

    /** Parse an optional {@code args[3]} percent: {@code null} when absent OR malformed (the latter is messaged). */
    static Integer optionalPercent(Messages messages, CommandSender sender, String[] args) {
        if (args.length < 4) {
            return null;
        }
        try {
            return Integer.parseInt(args[3]);
        } catch (NumberFormatException bad) {
            sender.sendMessage(messages.format("command.error.bad-number", "ARG", args[3]));
            return null;
        }
    }

    /** Parse an optional dust percent at {@code args[idx]}; absent or non-numeric ⇒ empty (a random dust). */
    static OptionalInt dustPercent(String[] args, int idx) {
        if (args.length <= idx) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(args[idx].trim()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /** Message the sender, routing a {@link Player} to its own region thread (Folia-correct). */
    static void tell(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            Scheduling.onEntity(player, () -> player.sendMessage(message));
        } else {
            sender.sendMessage(message);
        }
    }

    /** True when {@code sender} is not the same player as {@code target} (drives the distinct-sender confirmation). */
    static boolean notSelf(CommandSender sender, Player target) {
        return !(sender instanceof Player p) || !p.getUniqueId().equals(target.getUniqueId());
    }

    /** Prepend the namespace prefix when the operator typed a short key (no {@code /}). */
    static String normalize(String key, String prefix) {
        return key.indexOf('/') >= 0 ? key : prefix + key;
    }
}
