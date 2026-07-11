package feature.pet;

import compile.load.MasterConfig;
import compile.load.PetDef;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import platform.lang.Messages;
import platform.text.Colors;
import platform.text.TimeFormat;
import platform.text.Tokens;

/**
 * The universal pet messages (ADR-0052): activated / ended / on-cooldown / fail, rendered from the LIVE
 * master-config {@code pets:} templates (the {@code message-on-activate} precedent — config templates, not
 * lang keys, because they carry the pack's likeness) and sent PREFIX-FREE through the feedback-gated text
 * channel (the use-item trio's send shape). Tokens: {@code {COLOR}} the pet's colour codes, {@code {NAME}}
 * its display name — uppercased in the activate/end templates when the {@code uppercase} knob is on —
 * and {@code {TIME_FORMATTED}} the remaining cooldown. An empty template is silent.
 */
public final class PetMessenger {

    private final Messages messages;
    private final Supplier<MasterConfig.PetsSection> pets;

    public PetMessenger(Messages messages, Supplier<MasterConfig.PetsSection> pets) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.pets = Objects.requireNonNull(pets, "pets");
    }

    public void activated(Player player, PetDef def) {
        MasterConfig.PetsSection cfg = pets.get();
        send(player, cfg.messageOnActivate(), def, cfg.uppercase(), 0);
    }

    public void ended(Player player, PetDef def) {
        MasterConfig.PetsSection cfg = pets.get();
        send(player, cfg.messageOnEnd(), def, cfg.uppercase(), 0);
    }

    public void onCooldown(Player player, PetDef def, long remainingTicks) {
        // The cooldown/fail lines name the pet conversationally — never uppercased (the authored likeness).
        send(player, pets.get().messageOnCooldown(), def, false, remainingTicks);
    }

    public void failed(Player player, PetDef def) {
        send(player, pets.get().messageOnFail(), def, false, 0);
    }

    private void send(Player player, String template, PetDef def, boolean uppercase, long remainingTicks) {
        if (template == null || template.isEmpty()) {
            return; // empty = silent (the use-item.success convention)
        }
        String name = uppercase ? def.display().toUpperCase(Locale.ROOT) : def.display();
        String line = Tokens.sub(template,
                "COLOR", def.color(),
                "NAME", name,
                "TIME_FORMATTED", TimeFormat.hmsFromTicks(remainingTicks));
        messages.sendText(player, Colors.translate(line));
    }
}
