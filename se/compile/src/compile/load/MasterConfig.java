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
 * @param slots        base enchant-slot capacity (§H)
 * @param souls        cross-cutting soul toggles (§D) — per-kill amounts/colours stay in {@code items/soul-gem.yml}
 * @param crystals     per-item crystal-slot capacity + the multi-crystal sanity cap (§E)
 * @param heroic       the heroic multiplicative-stage clamp ceiling (§F)
 * @param lore         the lore render style (colours, numerals, unknown-key label)
 * @param integrations boot-time discovery toggles for the protection/economy/integration providers (§N)
 * @param reload       reload behaviour (re-resolve players, optional auto-reload interval)
 * @param diagnostics  every diagnostic raised loading {@code config.yml}
 */
public record MasterConfig(SlotsSection slots, SoulsSection souls, CrystalsSection crystals,
                           HeroicSection heroic, LoreSection lore, IntegrationsSection integrations,
                           ReloadSection reload, List<Diagnostic> diagnostics) {

    public MasterConfig {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(souls, "souls");
        Objects.requireNonNull(crystals, "crystals");
        Objects.requireNonNull(heroic, "heroic");
        Objects.requireNonNull(lore, "lore");
        Objects.requireNonNull(integrations, "integrations");
        Objects.requireNonNull(reload, "reload");
        diagnostics = List.copyOf(diagnostics);
    }

    /** The built-in master config — every section at its default; used when {@code config.yml} is absent. */
    public static MasterConfig defaults() {
        return new MasterConfig(SlotsSection.defaults(), SoulsSection.defaults(), CrystalsSection.defaults(),
                HeroicSection.defaults(), LoreSection.defaults(), IntegrationsSection.defaults(),
                ReloadSection.defaults(), List.of());
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
}
