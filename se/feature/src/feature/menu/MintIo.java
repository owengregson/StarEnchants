package feature.menu;

import java.util.Objects;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.lang.Messages;

/**
 * The small support handle a {@link Mintable}'s moved give/self body needs (ADR-0047): the message policy
 * seam and the hoisted delivery routine (target-inventory-or-drop + the {@code command.give.*} feedback),
 * so the moved {@code SeCommand} helpers keep their exact behaviour without depending on {@code bootstrap}.
 */
public record MintIo(Messages messages, Deliver deliver) {

    public MintIo {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(deliver, "deliver");
    }

    /** Give {@code stack} to {@code target} (inventory or drop) and tell {@code sender} via {@code messageKey}. */
    @FunctionalInterface
    public interface Deliver {
        void to(CommandSender sender, Player target, ItemStack stack, String messageKey, String label);
    }
}
