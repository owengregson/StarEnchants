package feature.reforge;

import compile.load.MasterConfig;
import compile.load.ReforgeDef;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import platform.lang.Messages;
import platform.text.Colors;
import platform.text.TimeFormat;
import platform.text.Tokens;

/**
 * The universal reforge messages — the pets convention 1:1 (ADR-0052 shape on the ADR-0070 family):
 * activated / ended / on-cooldown / fail rendered from the LIVE master-config {@code reforges:} templates
 * (config templates, not lang keys, because they carry the pack's likeness) and sent PREFIX-FREE through
 * the feedback-gated text channel. Tokens: {@code {COLOR}} the reforge's colour codes, {@code {NAME}} its
 * display name — uppercased in the activate/end templates when the {@code uppercase} knob is on — and
 * {@code {TIME_FORMATTED}} the remaining cooldown. An empty template is silent.
 */
public final class ReforgeMessenger {

    private final Messages messages;
    private final Supplier<MasterConfig.ReforgesSection> reforges;

    public ReforgeMessenger(Messages messages, Supplier<MasterConfig.ReforgesSection> reforges) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.reforges = Objects.requireNonNull(reforges, "reforges");
    }

    public void activated(Player player, ReforgeDef def) {
        MasterConfig.ReforgesSection cfg = reforges.get();
        send(player, cfg.messageOnActivate(), def, cfg.uppercase(), 0);
    }

    public void ended(Player player, ReforgeDef def) {
        MasterConfig.ReforgesSection cfg = reforges.get();
        send(player, cfg.messageOnEnd(), def, cfg.uppercase(), 0);
    }

    public void onCooldown(Player player, ReforgeDef def, long remainingTicks) {
        // The cooldown/fail lines name the reforge conversationally — never uppercased (the pets rule).
        send(player, reforges.get().messageOnCooldown(), def, false, remainingTicks);
    }

    public void failed(Player player, ReforgeDef def) {
        send(player, reforges.get().messageOnFail(), def, false, 0);
    }

    private void send(Player player, String template, ReforgeDef def, boolean uppercase, long remainingTicks) {
        if (template == null || template.isEmpty()) {
            return; // empty = silent (the use-item.success convention)
        }
        String name = uppercase ? def.display().toUpperCase(Locale.ROOT) : def.display();
        String line = Tokens.sub(Tokens.colorTolerant(template),
                "COLOR", def.color(),
                "NAME", name,
                "TIME_FORMATTED", TimeFormat.hmsFromTicks(remainingTicks));
        messages.sendText(player, Colors.translate(line));
    }
}
