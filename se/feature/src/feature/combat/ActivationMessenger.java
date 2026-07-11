package feature.combat;

import compile.load.ContentHolder;
import compile.load.MasterConfig;
import compile.load.PetDef;
import compile.model.Ability;
import compile.model.SourceKind;
import engine.run.ActivationContext;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import platform.sched.Scheduling;
import platform.text.Colors;
import platform.text.Tokens;

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
    private final Supplier<MasterConfig.PetsSection> pets;
    private final ContentHolder content;

    public ActivationMessenger(Supplier<MasterConfig.MessageOnActivateSection> config,
                               Supplier<MasterConfig.PetsSection> pets, ContentHolder content) {
        this.config = Objects.requireNonNull(config, "config");
        this.pets = Objects.requireNonNull(pets, "pets");
        this.content = Objects.requireNonNull(content, "content");
    }

    /**
     * Send the configured BY/ON lines for an activation of {@code enchantKey} (a base stable key). A no-op when
     * the feature is off or the activation has no other combat party to name.
     */
    public void onActivate(String enchantKey, Ability ability, ActivationContext context) {
        MasterConfig.MessageOnActivateSection cfg = config.get();
        if (!cfg.byEnabled() && !cfg.onEnabled()) {
            return; // feature off — skip the library lookups
        }
        if (isPassive(ability)) {
            return; // set bonuses + always-on flat buffs never announce (like permanent potion enchants)
        }
        Player actor = context.actor();
        LivingEntity other = context.victim(); // the BY-side target AND the ON-side recipient
        if (actor == null || other == null) {
            return; // a non-combat activation has no other party to name
        }
        if (ability != null && ability.sourceKind() == SourceKind.PET) {
            // A pet's chance-gated on-hit rider (ADR-0052 Anubis): the enchant templates cannot name a pet
            // (no display/tier for a pet stable key — the raw "PET:X/100/A1" line), so the holder gets the
            // pets-owned effect template instead; the victim side stays quiet (the stripped scroll IS the tell).
            if (cfg.byEnabled()) {
                announcePetEffect(enchantKey, actor);
            }
            return;
        }
        String display = displayOf(enchantKey);
        if (cfg.uppercase()) {
            display = display.toUpperCase(Locale.ROOT); // the NAME only — the party names are left as-is
        }
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
        String out = Tokens.sub(template, "ENCHANT", enchant, "TIER_COLOR", tierColor, "VICTIM", victim, "ATTACKER", attacker);
        return Colors.translate(out);
    }

    /**
     * Whether {@code ability} is a passive/always-on source that should NOT announce per activation: any set
     * bonus (a set has its own equip/remove message, and "sets/x/w1" in chat is meaningless), or a guaranteed,
     * cooldown-free, non-repeating flat buff (e.g. a permanent potion or a constant damage modifier) — these
     * fire on every hit and announcing them is just spam, exactly as those enchants send no message today.
     */
    static boolean isPassive(Ability ability) {
        if (ability == null) {
            return false;
        }
        if (ability.sourceKind() == SourceKind.SET) {
            return true;
        }
        return ability.baseChance() >= 100.0 && ability.cooldownTicks() == 0 && ability.repeatTicks() == 0;
    }

    /** The pets-owned on-hit-effect line ({@code {COLOR}}/{@code {NAME}}; the {@code &{COLOR}} spelling folds). */
    private void announcePetEffect(String stableKey, Player actor) {
        MasterConfig.PetsSection cfg = pets.get();
        String template = cfg.messageOnEffect();
        if (template == null || template.isEmpty()) {
            return; // empty = silent (the pets message convention)
        }
        PetDef def = content.library().petDefOf(petKeyOf(stableKey));
        if (def == null) {
            return; // a stale key an old head still carries
        }
        String name = cfg.uppercase() ? def.display().toUpperCase(Locale.ROOT) : def.display();
        actor.sendMessage(Colors.translate(Tokens.sub(template.replace("&{COLOR}", "{COLOR}"),
                "COLOR", def.color(), "NAME", name)));
    }

    /** {@code pet:anubis/100/a1} → {@code anubis} (the codec-stored pet key). */
    static String petKeyOf(String stableKey) {
        String key = stableKey.startsWith("pet:") ? stableKey.substring(4) : stableKey;
        int slash = key.indexOf('/');
        return slash > 0 ? key.substring(0, slash) : key;
    }

    private String displayOf(String enchantKey) {
        String display = content.library().displayNameOf(enchantKey);
        return display != null ? display : enchantKey;
    }

    private String tierColorOf(String enchantKey) {
        return content.library().tiers().colorOf(content.library().tierOf(enchantKey));
    }
}
