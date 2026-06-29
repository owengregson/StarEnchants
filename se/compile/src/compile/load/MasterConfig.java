package compile.load;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import schema.diag.Diagnostic;

/**
 * Compiled snapshot of the master {@code config.yml} (§L) — cross-cutting knobs, swapped by reference in
 * the same atomic {@code /se reload} transaction as content + items. Every section is mandatory and resolves
 * to some value: absent/unreadable yields {@link #defaults()}; a malformed file yields a diagnostic and
 * defaults for the faulted section.
 */
public record MasterConfig(FeaturesSection features, CombatSection combat, MessagesSection messages,
                           BooksSection books, SlotsSection slots, SoulsSection souls, CrystalsSection crystals,
                           HeroicSection heroic, LoreSection lore, IntegrationsSection integrations,
                           ReloadSection reload, CommandTriggerSection commandTrigger,
                           MessageOnActivateSection messageOnActivate, SetsSection sets,
                           List<Diagnostic> diagnostics) {

    public MasterConfig {
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(combat, "combat");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(books, "books");
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(souls, "souls");
        Objects.requireNonNull(crystals, "crystals");
        Objects.requireNonNull(heroic, "heroic");
        Objects.requireNonNull(lore, "lore");
        Objects.requireNonNull(integrations, "integrations");
        Objects.requireNonNull(reload, "reload");
        Objects.requireNonNull(commandTrigger, "commandTrigger");
        Objects.requireNonNull(messageOnActivate, "messageOnActivate");
        Objects.requireNonNull(sets, "sets");
        diagnostics = List.copyOf(diagnostics);
    }

    /** The built-in master config — every section at its default; used when {@code config.yml} is absent. */
    public static MasterConfig defaults() {
        return new MasterConfig(FeaturesSection.defaults(), CombatSection.defaults(), MessagesSection.defaults(),
                BooksSection.defaults(), SlotsSection.defaults(), SoulsSection.defaults(), CrystalsSection.defaults(),
                HeroicSection.defaults(), LoreSection.defaults(), IntegrationsSection.defaults(),
                ReloadSection.defaults(), CommandTriggerSection.defaults(), MessageOnActivateSection.defaults(),
                SetsSection.defaults(), List.of());
    }

    /**
     * Global enchant-book success ceiling (§I). The book likeness lives in {@code items/enchant-book.yml};
     * this is the one cross-cutting knob.
     *
     * @param maxSuccess maximum success % a book may reach via RANDOMISED minting (unopened book / randomizer
     *                   scroll), the black scroll's conversion roll, or Magic Dust (which snaps to it). Clamped
     *                   to {@code [0, 100]}; {@code 100} = no practical cap. Guaranteed books ({@code /se book},
     *                   the admin browser) and an explicit {@code /se give book <success>} are admin overrides
     *                   and are NOT capped.
     */
    public record BooksSection(int maxSuccess) {
        public BooksSection {
            maxSuccess = Math.max(0, Math.min(100, maxSuccess));
        }

        public static BooksSection defaults() {
            return new BooksSection(100);
        }
    }

    /** Whether any blocking diagnostic was raised loading {@code config.yml}. */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::blocking);
    }

    /**
     * Base enchant-slot capacity (§H). The hard cap on TOTAL slots (base + purchased) lives instead in
     * the expander's {@code items/slots.yml} ({@code SlotConfig.hardCap}).
     *
     * @param base base enchant slots every item starts with (≥ 0)
     */
    public record SlotsSection(int base) {
        public SlotsSection {
            base = Math.max(0, base);
        }

        public static SlotsSection defaults() {
            return new SlotsSection(9);
        }
    }

    /**
     * Cross-cutting soul toggle (§D); the concrete soul economy lives in {@code items/soul-gem.yml}.
     *
     * @param depositOnAnyKill souls deposit into a carried gem on ANY kill; {@code false} disables
     *                         deposit-on-kill entirely (give/combine/split still work)
     */
    public record SoulsSection(boolean depositOnAnyKill) {
        public static SoulsSection defaults() {
            return new SoulsSection(true);
        }
    }

    /**
     * Crystal-slot capacity (§E). Crystal slots are a SEPARATE ledger from enchant slots. {@code maxStack}
     * is a PDC-bloat sanity guard distinct from the slot count.
     *
     * @param slots    crystal slots every item has (≥ 0)
     * @param maxStack absolute maximum crystals one item may hold (≥ 1)
     */
    public record CrystalsSection(int slots, int maxStack) {
        public CrystalsSection {
            slots = Math.max(0, slots);
            maxStack = Math.max(1, maxStack);
        }

        public static CrystalsSection defaults() {
            return new CrystalsSection(1, 16);
        }
    }

    /**
     * Heroic bounded-multiplicative clamp ceiling (§F/ADR-0021). Heroic is a distinct multiplicative stage
     * AFTER the additive fold: outgoing {@code ×clamp(1+Σ, 0, maxOutgoingFactor)}.
     *
     * @param maxOutgoingFactor ceiling on the heroic outgoing-damage multiplier (≥ 1.0; default 4.0)
     */
    public record HeroicSection(double maxOutgoingFactor) {
        public HeroicSection {
            maxOutgoingFactor = Math.max(1.0, maxOutgoingFactor);
        }

        public static HeroicSection defaults() {
            return new HeroicSection(4.0);
        }
    }

    /**
     * Lore render style (§L). Mirrors {@code item.render.LoreStyle} field-for-field; bridged at the
     * composition root since {@code compile} does not depend on {@code item}.
     *
     * @param enchantColor enchant-name colour prefix (legacy {@code &} code)
     * @param levelColor   level-numeral colour prefix (legacy {@code &} code)
     * @param crystalColor crystal-line colour prefix (legacy {@code &} code)
     * @param roman        levels render as Roman numerals (else Arabic)
     * @param unknownLabel name rendered for a stored key no longer in the catalog (§5.3)
     * @param itemWrap     auto-wrap width (visible chars, colour codes excluded) for AUTHORED economy/identity
     *                     item lore — scrolls, the orb, dust, gems, the trak gems, … — applied once on the
     *                     {@code item.mint.ItemFactory} mint path so authors write one long line and it
     *                     word-wraps. {@code 0} disables. NOT part of the {@code LoreStyle} bridge (it governs
     *                     economy-item lore, not the on-gear enchant lore); the enchant book keeps its own
     *                     per-file {@code wrap} (items/enchant-book.yml) so its templated {@code DESCRIPTION}
     *                     never double-wraps.
     */
    public record LoreSection(String enchantColor, String levelColor, String crystalColor,
                              boolean roman, String unknownLabel, int itemWrap) {
        public LoreSection {
            Objects.requireNonNull(enchantColor, "enchantColor");
            Objects.requireNonNull(levelColor, "levelColor");
            Objects.requireNonNull(crystalColor, "crystalColor");
            Objects.requireNonNull(unknownLabel, "unknownLabel");
            itemWrap = Math.max(0, itemWrap);
        }

        public static LoreSection defaults() {
            return new LoreSection("&7", "&f", "&b", true, "&8Unknown Enchant", 30);
        }
    }

    /**
     * Boot-time discovery toggles (§N). Read at {@code onEnable} — un-discovering a provider mid-run is not
     * clean — so a change takes effect on the next server start, NOT a {@code /se reload}.
     *
     * @param protection discover {@code ProtectionProvider}s (gate 2)
     * @param economy    discover an {@code EconomyProvider} (the money effects)
     * @param named      per-id enable flags for named integrations; absent ⇒ enabled
     */
    public record IntegrationsSection(boolean protection, boolean economy, Map<String, Boolean> named) {
        public IntegrationsSection {
            named = Map.copyOf(named);
        }

        public static IntegrationsSection defaults() {
            return new IntegrationsSection(true, true, Map.of());
        }

        /** Whether the named integration {@code id} is enabled (case-insensitive); an unlisted id is enabled. */
        public boolean enabled(String id) {
            if (id == null) {
                return true;
            }
            Boolean flag = named.get(id.toLowerCase(Locale.ROOT));
            return flag == null || flag;
        }
    }

    /**
     * Reload behaviour (§L).
     *
     * @param reResolvePlayers re-resolve every online player's worn state after a content swap (default true)
     * @param autoSeconds      auto-reload interval in seconds (≤ 0 disables; armed once at boot, global thread)
     */
    public record ReloadSection(boolean reResolvePlayers, int autoSeconds) {
        public static ReloadSection defaults() {
            return new ReloadSection(true, 0);
        }
    }

    /**
     * Configurable command that fires the §B {@code COMMAND} trigger (ADR-0022). Registered ONCE at
     * {@code onEnable} (a command name cannot be re-bound mid-run cleanly), so a change takes effect on the
     * next server start, NOT a {@code /se reload}.
     *
     * @param enabled     register the command-trigger command at boot
     * @param name        command name players run (no leading slash); default {@code cast}
     * @param description help description shown in tab/help listings
     */
    public record CommandTriggerSection(boolean enabled, String name, String description) {
        public CommandTriggerSection {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
        }

        public static CommandTriggerSection defaults() {
            return new CommandTriggerSection(true, "cast", "Trigger your COMMAND enchantments.");
        }
    }

    /**
     * Per-feature master on/off switches (default-all-on). FOOTGUN: {@code enchants/sets/crystals/heroic}
     * are read LIVE (dropped from worn state on resolve, re-read on reload); {@code slots/souls/scrolls}
     * gate listeners at boot, so a change takes effect only on the next server start.
     *
     * @param enchants enchant sources contribute to worn state
     * @param sets     armour-set bonuses contribute to worn state
     * @param crystals crystal sources contribute to worn state
     * @param heroic   heroic flat stats contribute to worn state (the heroic damage stage)
     * @param slots    register the slot-expander apply gesture
     * @param souls    register the soul system (deposit, soul mode, gem inventory)
     * @param scrolls  register the scroll-family interactions (black/randomizer/transmog/holy/nametag/godly)
     */
    public record FeaturesSection(boolean enchants, boolean sets, boolean crystals, boolean heroic,
                                  boolean slots, boolean souls, boolean scrolls) {
        public static FeaturesSection defaults() {
            return new FeaturesSection(true, true, true, true, true, true, true);
        }
    }

    /**
     * Cross-cutting combat caps, all read live (ADR-0012). Disabling a PvP/PvE context makes that side
     * contribute nothing to the additive fold.
     *
     * @param maxBonusDamage    ceiling on the summed outgoing-damage fraction (e.g. {@code 5.0} = +500% max); {@code < 0} = uncapped
     * @param maxBonusReduction ceiling on the summed damage-reduction fraction (e.g. {@code 0.8} = 80% max); {@code < 0} = uncapped
     * @param pvp               combat effects apply in player-vs-player hits
     * @param pve               combat effects apply in player-vs-environment hits
     */
    public record CombatSection(double maxBonusDamage, double maxBonusReduction, boolean pvp, boolean pve) {
        public static CombatSection defaults() {
            return new CombatSection(-1.0, -1.0, true, true);
        }
    }

    /**
     * Player-feedback chat style (§L), read live on reload. {@code prefix} applies to {@code Messages}-facade
     * chat only — never item names or lore. {@code feedback=false} silences the keyed gameplay-feedback
     * channel ({@code Messages#send}) but admin command echoes still print.
     *
     * @param prefix   prepended to facade-rendered messages (legacy {@code &} colour codes); may be empty
     * @param feedback send keyed gameplay-feedback messages to players
     */
    public record MessagesSection(String prefix, boolean feedback) {
        public MessagesSection {
            Objects.requireNonNull(prefix, "prefix");
        }

        public static MessagesSection defaults() {
            return new MessagesSection("", true);
        }
    }

    /**
     * Global enchant message-on-activate (§L). When an enchant fires, the holder ("BY you") and the other
     * combat party ("ON you") each get a configurable chat line; the two sides are independently toggled, so a
     * pack can enable one, both, or neither. Templates take {@code {ENCHANT}} (display name), {@code {TIER_COLOR}}
     * (the tier's {@code &} colour code), {@code {VICTIM}} (the BY-side target's name) and {@code {ATTACKER}}
     * (the ON-side source's name). Off by default; a content pack opts in (and replaces any per-enchant
     * self-announce {@code MESSAGE} effect). A non-combat activation (no other party) sends nothing.
     *
     * @param byEnabled  send the holder a line when their enchant fires
     * @param byTemplate the BY-you template (names {@code {VICTIM}})
     * @param onEnabled  send the other party a line when an enchant fires on them
     * @param onTemplate the ON-you template (names {@code {ATTACKER}})
     * @param uppercase  render the {@code {ENCHANT}} name in UPPERCASE (the names only; party names are untouched)
     */
    public record MessageOnActivateSection(boolean byEnabled, String byTemplate,
                                           boolean onEnabled, String onTemplate, boolean uppercase) {
        public MessageOnActivateSection {
            Objects.requireNonNull(byTemplate, "byTemplate");
            Objects.requireNonNull(onTemplate, "onTemplate");
        }

        public static MessageOnActivateSection defaults() {
            return new MessageOnActivateSection(
                    false,
                    "{TIER_COLOR}&l** {ENCHANT} &r&7[&f{VICTIM}&7] {TIER_COLOR}&l**",
                    false,
                    "{TIER_COLOR}&l** {ENCHANT} &7FROM &r&7[&c{ATTACKER}&7] {TIER_COLOR}&l **",
                    false);
        }
    }

    /**
     * Universal armour-set equip/unequip feedback (§6.6) — ONE config for ALL sets (not per-set). Equipping or
     * removing a completed set plays the matching sound list and spawns the particle at the player, independent
     * of whether the set has an announce message. When {@link #useSetColor} is on, only the EQUIP (dust)
     * particle's colour is overridden by the set's own {@code &}-colour at runtime, so each set's cloud matches
     * its identity; the UNEQUIP particle always keeps its configured colour. {@link #messageUppercase}
     * auto-capitalises the set equip/remove message (colour codes preserved).
     *
     * @param messageUppercase uppercase the set equip/remove message's visible text
     * @param useSetColor       tint the EQUIP dust to the set's own colour (the unequip dust stays as configured)
     * @param equipSound        sounds played on completing a set
     * @param unequipSound      sounds played on dropping below a set's threshold
     * @param equipParticle     particle spawned on completing a set
     * @param unequipParticle   particle spawned on dropping below a set's threshold
     */
    public record SetsSection(boolean messageUppercase, boolean useSetColor,
                              List<SoundCue> equipSound, List<SoundCue> unequipSound,
                              ParticleSpec equipParticle, ParticleSpec unequipParticle) {
        public SetsSection {
            equipSound = List.copyOf(equipSound);
            unequipSound = List.copyOf(unequipSound);
            equipParticle = equipParticle == null ? ParticleSpec.none() : equipParticle;
            unequipParticle = unequipParticle == null ? ParticleSpec.none() : unequipParticle;
        }

        public static SetsSection defaults() {
            return new SetsSection(false, true, List.of(), List.of(), ParticleSpec.none(), ParticleSpec.none());
        }
    }
}
