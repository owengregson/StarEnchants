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

    /**
     * The digger no-home line (ADR-0067): a recall-capable pet used with no live home window and a
     * dig that could not fire (the plain non-sneaking click). Authored per-pet
     * ({@code message-on-no-home}); an un-authored def falls back to the universal fail line.
     */
    public void noHome(Player player, PetDef def) {
        String template = def.messageOnNoHome();
        if (template == null || template.isEmpty()) {
            failed(player, def);
            return;
        }
        send(player, template, def, false, 0);
    }

    /**
     * The UNIVERSAL out-of-range line (ADR-0061) — a plugin-wide lang key rendered PREFIX-FREE (the use-item
     * fragment+sendText idiom), so any future content can reuse the same message. The Mole recall sends it
     * when the player is beyond the recall range or in another world; the window stays alive for a retry.
     */
    public void outOfRange(Player player) {
        messages.sendText(player, Colors.translate(messages.fragment("feedback.out-of-range")));
    }

    private void send(Player player, String template, PetDef def, boolean uppercase, long remainingTicks) {
        if (template == null || template.isEmpty()) {
            return; // empty = silent (the use-item.success convention)
        }
        String name = uppercase ? def.display().toUpperCase(Locale.ROOT) : def.display();
        String line = Tokens.sub(PetTokens.colorTolerant(template),
                "COLOR", def.color(),
                "NAME", name,
                "TIME_FORMATTED", TimeFormat.hmsFromTicks(remainingTicks));
        messages.sendText(player, Colors.translate(line));
    }
}
