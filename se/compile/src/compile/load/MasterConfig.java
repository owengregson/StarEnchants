package compile.load;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import schema.diag.Diagnostic;

/**
 * The compiled snapshot of the master {@code config.yml} (docs/v3-directives.md §L) — the sectioned
 * cross-cutting knobs that are NOT a single item's "physical likeness" (those live in {@code items/}) and
 * NOT messages (those live in {@code lang.yml}). Loaded as a parallel immutable reference to the content
 * {@link Library} and swapped in the SAME atomic {@code /se reload} transaction. Pure (no Bukkit); readers
 * always see a fully-built snapshot.
 *
 * <p>Seven sections, matching the directive's list ({@code slots:, souls:, crystals:, heroic:, lore:,
 * integrations:, reload:}). Unlike {@link ItemsConfig} (one optional record per item file), every section
 * is mandatory and always resolves to <em>some</em> value, so each section is a plain nested record with a
 * {@code defaults()} factory rather than an {@link java.util.Optional}. An absent or unreadable
 * {@code config.yml} yields {@link #defaults()}; a malformed file yields a diagnostic and defaults for the
 * faulted section.
 *
 * <p>Distinction from {@code items/}: per-feature likeness (a slot expander's material, a soul gem's lore,
 * heroic's granted percents) stays in {@code items/}; the genuinely cross-cutting ceilings live here — base
 * enchant slots (§H; the hard cap stays in the expander's {@code items/slots.yml} per §H), the per-item
 * crystal-slot default (§E), the heroic bounded-multiplicative clamp ceiling (§F/ADR-0021), the lore render
 * style, integration discovery toggles, and reload behaviour.
 *
 * @param features     per-feature master on/off toggles (enchants/sets/crystals/heroic/slots/souls/scrolls)
 * @param combat       cross-cutting combat caps (additive bonus ceilings + PvP/PvE gates)
 * @param messages     the player-feedback chat style (prefix + feedback channel toggle)
 * @param slots        base enchant-slot capacity (§H)
 * @param souls        cross-cutting soul toggles (§D) — per-kill amounts/colours stay in {@code items/soul-gem.yml}
 * @param crystals     per-item crystal-slot capacity + the multi-crystal sanity cap (§E)
 * @param heroic       the heroic multiplicative-stage clamp ceiling (§F)
 * @param lore         the lore render style (colours, numerals, unknown-key label)
 * @param integrations boot-time discovery toggles for the protection/economy/integration providers (§N)
 * @param reload       reload behaviour (re-resolve players, optional auto-reload interval)
 * @param commandTrigger the configurable command that fires the §B {@code COMMAND} trigger
 * @param diagnostics  every diagnostic raised loading {@code config.yml}
 */
public record MasterConfig(FeaturesSection features, CombatSection combat, MessagesSection messages,
                           SlotsSection slots, SoulsSection souls, CrystalsSection crystals,
                           HeroicSection heroic, LoreSection lore, IntegrationsSection integrations,
                           ReloadSection reload, CommandTriggerSection commandTrigger,
                           List<Diagnostic> diagnostics) {

    public MasterConfig {
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(combat, "combat");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(souls, "souls");
        Objects.requireNonNull(crystals, "crystals");
        Objects.requireNonNull(heroic, "heroic");
        Objects.requireNonNull(lore, "lore");
        Objects.requireNonNull(integrations, "integrations");
        Objects.requireNonNull(reload, "reload");
        Objects.requireNonNull(commandTrigger, "commandTrigger");
        diagnostics = List.copyOf(diagnostics);
    }

    /** The built-in master config — every section at its default; used when {@code config.yml} is absent. */
    public static MasterConfig defaults() {
        return new MasterConfig(FeaturesSection.defaults(), CombatSection.defaults(), MessagesSection.defaults(),
                SlotsSection.defaults(), SoulsSection.defaults(), CrystalsSection.defaults(),
                HeroicSection.defaults(), LoreSection.defaults(), IntegrationsSection.defaults(),
                ReloadSection.defaults(), CommandTriggerSection.defaults(), List.of());
    }

    /** Whether any blocking diagnostic was raised loading {@code config.yml}. */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::blocking);
    }

    /**
     * Base enchant-slot capacity (§H). The hard cap on TOTAL slots (base + purchased) lives in the slot
     * expander's {@code items/slots.yml} ({@code SlotConfig.hardCap}) per §H ("a hard universal maximum
     * total-slot cap defined in the expander's config") — only the base is cross-cutting.
     *
     * @param base the base number of enchant slots every item starts with (≥ 0)
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
     * Cross-cutting soul toggles (§D). The concrete soul economy (per-kill amount, per-mob overrides,
     * colour tiers, sounds, particles, messages) is the gem's likeness in {@code items/soul-gem.yml}; only
     * the master deposit toggle is cross-cutting.
     *
     * @param depositOnAnyKill whether souls deposit into a carried gem on ANY kill (§D); {@code false}
     *                         disables the deposit-on-kill mechanic entirely (give/combine/split still work)
     */
    public record SoulsSection(boolean depositOnAnyKill) {
        public static SoulsSection defaults() {
            return new SoulsSection(true);
        }
    }

    /**
     * Crystal-slot capacity (§E). Per-item crystal slots are a SEPARATE ledger from enchant slots; the
     * default is 1 (configurable here, never in {@code items/crystal.yml} which carries only the crystal
     * item's likeness). {@code maxStack} bounds how many crystals a single item may ever hold (a PDC-bloat
     * sanity guard distinct from the slot count).
     *
     * @param slots    the number of crystal slots every item has (≥ 0)
     * @param maxStack the absolute maximum crystals one item may hold (≥ 1)
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
     * The heroic bounded-multiplicative clamp ceiling (§F/ADR-0021). Heroic is a distinct multiplicative
     * stage applied AFTER the additive damage fold: outgoing {@code ×clamp(1+Σ, 0, maxOutgoingFactor)}. This
     * is the only cross-cutting heroic knob; the granted percents/success/material map live in
     * {@code items/heroic.yml}.
     *
     * @param maxOutgoingFactor the ceiling on the heroic outgoing-damage multiplier (≥ 1.0; default 4.0)
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
     * The lore render style (§L) — mirrors {@code item.render.LoreStyle} field-for-field (the bridge to that
     * type is built at the composition root, since {@code compile} does not depend on {@code item}). Swapped
     * live on reload, so a colour edit takes effect on the next lore render.
     *
     * @param enchantColor colour prefix for an enchant's display name (legacy {@code &} code)
     * @param levelColor   colour prefix for the level numeral
     * @param crystalColor colour prefix for a crystal line
     * @param roman        whether levels render as Roman numerals (else Arabic)
     * @param unknownLabel the name rendered for a stored key no longer in the catalog (§5.3)
     */
    public record LoreSection(String enchantColor, String levelColor, String crystalColor,
                              boolean roman, String unknownLabel) {
        public LoreSection {
            Objects.requireNonNull(enchantColor, "enchantColor");
            Objects.requireNonNull(levelColor, "levelColor");
            Objects.requireNonNull(crystalColor, "crystalColor");
            Objects.requireNonNull(unknownLabel, "unknownLabel");
        }

        public static LoreSection defaults() {
            return new LoreSection("&7", "&f", "&b", true, "&8Unknown Enchant");
        }
    }

    /**
     * Boot-time discovery toggles (§N). Each named provider can be disabled so the plugin does not register
     * against it; the two core seams (protection, economy) have explicit toggles, and named integrations
     * (worldguard, vault, …) read through {@link #enabled} (default-true: an unlisted id is enabled). These
     * are read at {@code onEnable} — un-discovering a provider mid-run is not a clean operation — so a change
     * takes effect on the next server start, not a {@code /se reload}.
     *
     * @param protection whether to discover {@code ProtectionProvider}s (gate 2)
     * @param economy    whether to discover an {@code EconomyProvider} (the money effects)
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
     * Reload behaviour (§L). {@link #reResolvePlayers} (default true) gates the per-player worn-state
     * re-resolve in the reload transaction; {@link #autoSeconds} (default 0 = off) optionally schedules a
     * recurring {@code /se reload} on the global thread.
     *
     * @param reResolvePlayers re-resolve every online player's worn state after a content swap
     * @param autoSeconds      auto-reload interval in seconds (≤ 0 disables; armed once at boot)
     */
    public record ReloadSection(boolean reResolvePlayers, int autoSeconds) {
        public static ReloadSection defaults() {
            return new ReloadSection(true, 0);
        }
    }

    /**
     * The configurable command that fires the §B {@code COMMAND} trigger (docs/v3-directives.md §B,
     * ADR-0022). When {@link #enabled}, a standalone command named {@link #name} is registered at boot; a
     * player running it fires their worn {@code COMMAND}-trigger abilities through the full gate sequence.
     * Registered ONCE at {@code onEnable} (a command name cannot be re-bound mid-run cleanly), so a change to
     * {@code name}/{@code enabled} takes effect on the next server start, not a {@code /se reload} — like the
     * integration toggles.
     *
     * @param enabled     whether to register the command-trigger command at boot
     * @param name        the command name players run (no leading slash); default {@code cast}
     * @param description the command's help description (shown in tab/help listings)
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
     * Per-feature master on/off switches. Each toggle disables a whole subsystem at its entry point:
     * {@link #enchants}/{@link #sets}/{@link #crystals}/{@link #heroic} drop that source when a player's
     * worn state is resolved (so the feature's combat + trigger effects go inert, re-read live on reload),
     * while {@link #slots}/{@link #souls}/{@link #scrolls} gate their apply/interaction listeners at boot
     * (a change takes effect on the next server start, like the integration toggles). Default-all-on, so an
     * absent {@code features:} section changes nothing.
     *
     * @param enchants whether enchant sources contribute to worn state (their effects fire)
     * @param sets     whether armour-set bonuses contribute to worn state
     * @param crystals whether crystal sources contribute to worn state
     * @param heroic   whether heroic flat stats contribute to worn state (the heroic damage stage)
     * @param slots    whether the slot-expander apply gesture is registered
     * @param souls    whether the soul system (deposit, soul mode, gem inventory) is registered
     * @param scrolls  whether the scroll-family interactions (black/randomizer/transmog/holy/nametag/godly) are registered
     */
    public record FeaturesSection(boolean enchants, boolean sets, boolean crystals, boolean heroic,
                                  boolean slots, boolean souls, boolean scrolls) {
        public static FeaturesSection defaults() {
            return new FeaturesSection(true, true, true, true, true, true, true);
        }
    }

    /**
     * Cross-cutting combat caps. {@link #maxBonusDamage}/{@link #maxBonusReduction} ceil the ADDITIVE fold's
     * summed outgoing/reduction fractions (ADR-0012) — e.g. {@code maxBonusReduction = 0.8} forbids more than
     * 80% damage reduction (no immunity stacking); a negative value means "no cap" (the default, preserving
     * current behaviour). {@link #pvp}/{@link #pve} gate whether StarEnchants combat effects apply at all when
     * a player fights another player (PvP) or a non-player (PvE) — disabling a context makes that side
     * contribute nothing to the fold. All read live, so a {@code /se reload} re-tunes them.
     *
     * @param maxBonusDamage    ceiling on the summed outgoing-damage fraction (e.g. {@code 5.0} = +500% max); {@code < 0} = uncapped
     * @param maxBonusReduction ceiling on the summed damage-reduction fraction (e.g. {@code 0.8} = 80% max); {@code < 0} = uncapped
     * @param pvp               whether combat effects apply in player-vs-player hits
     * @param pve               whether combat effects apply in player-vs-environment hits
     */
    public record CombatSection(double maxBonusDamage, double maxBonusReduction, boolean pvp, boolean pve) {
        public static CombatSection defaults() {
            return new CombatSection(-1.0, -1.0, true, true);
        }
    }

    /**
     * The player-feedback chat style (§L). {@link #prefix} is prepended to every message rendered through the
     * {@code Messages} facade (chat feedback only — never item names or lore). {@link #feedback} toggles the
     * keyed gameplay-feedback channel ({@code Messages#send}, e.g. menu confirmations): {@code false} silences
     * it for a quiet server (admin command echoes still print). Both read live on reload.
     *
     * @param prefix   text prepended to facade-rendered messages (legacy {@code &} colour codes); may be empty
     * @param feedback whether keyed gameplay-feedback messages are sent to players
     */
    public record MessagesSection(String prefix, boolean feedback) {
        public MessagesSection {
            Objects.requireNonNull(prefix, "prefix");
        }

        public static MessagesSection defaults() {
            return new MessagesSection("", true);
        }
    }
}
