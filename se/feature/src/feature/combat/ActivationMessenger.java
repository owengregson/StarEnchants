package feature.combat;

import compile.load.ContentHolder;
import compile.load.MasterConfig;
import compile.load.TierRegistry;
import engine.run.ActivationContext;
import item.mint.ItemFactory;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import platform.sched.Scheduling;

/**
 * The global message-on-activate sender (§L). When an enchant fires, the holder gets the "BY you" line (naming
 * the other combat party) and that other party — if a player — gets the "ON you" line (naming the holder).
 * Either side is independently toggled in {@link MasterConfig.MessageOnActivateSection}. The line is rendered
 * from the enchant's display name + tier colour plus the two party names. This replaces per-enchant self-announce
 * {@code MESSAGE} effects, which is why a content pack removes those when it enables this.
 *
 * <p><strong>Threading.</strong> The activation runs on the holder's region thread, so the holder is messaged
 * inline; the other party may be in a different region, so it is messaged through its own entity scheduler.
 */
public final class ActivationMessenger {

    private final Supplier<MasterConfig.MessageOnActivateSection> config;
    private final ContentHolder content;

    public ActivationMessenger(Supplier<MasterConfig.MessageOnActivateSection> config, ContentHolder content) {
        this.config = Objects.requireNonNull(config, "config");
        this.content = Objects.requireNonNull(content, "content");
    }

    /**
     * Send the configured BY/ON lines for an activation of {@code enchantKey} (a base stable key). A no-op when
     * the feature is off or the activation has no other combat party to name.
     */
    public void onActivate(String enchantKey, ActivationContext context) {
        MasterConfig.MessageOnActivateSection cfg = config.get();
        if (!cfg.byEnabled() && !cfg.onEnabled()) {
            return; // feature off — skip the library lookups
        }
        Player actor = context.actor();
        LivingEntity other = context.victim(); // the BY-side target AND the ON-side recipient
        if (actor == null || other == null) {
            return; // a non-combat activation has no other party to name
        }
        String display = displayOf(enchantKey);
        String tierColor = tierColorOf(enchantKey);

        if (cfg.byEnabled()) {
            actor.sendMessage(render(cfg.byTemplate(), display, tierColor, other.getName(), actor.getName()));
        }
        if (cfg.onEnabled() && other instanceof Player target && !target.equals(actor)) {
            String line = render(cfg.onTemplate(), display, tierColor, target.getName(), actor.getName());
            Scheduling.onEntity(target, () -> target.sendMessage(line)); // cross-region safe
        }
    }

    /**
     * Substitute the message tokens and translate {@code &} colours — pure (no server), so the template
     * contract is unit-testable. {@code {TIER_COLOR}} expands to a colour code that the translate then applies.
     */
    public static String render(String template, String enchant, String tierColor, String victim, String attacker) {
        String out = template
                .replace("{ENCHANT}", enchant)
                .replace("{TIER_COLOR}", tierColor)
                .replace("{TIER-COLOR}", tierColor) // tolerate either spelling (cf. the carrier book lore)
                .replace("{VICTIM}", victim)
                .replace("{ATTACKER}", attacker);
        return ItemFactory.color(out);
    }

    private String displayOf(String enchantKey) {
        String display = content.library().displayNameOf(enchantKey);
        return display != null ? display : enchantKey;
    }

    private String tierColorOf(String enchantKey) {
        String tier = content.library().tierOf(enchantKey);
        if (tier == null) {
            return "&7";
        }
        TierRegistry.Tier t = content.library().tiers().tier(tier);
        return t != null && !t.color().isBlank() ? t.color() : "&7";
    }
}
