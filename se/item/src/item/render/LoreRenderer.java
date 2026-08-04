package item.render;

import item.codec.CombatState;
import item.codec.ComposerMark;
import item.codec.ItemKeys;
import item.codec.ItemStateStore;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The Bukkit shell over {@link LoreComposer} (ADR-0040): reads an item's state feed (material, marker-derived
 * protection lines, existing lore), asks the composer for the full ordered lore, and writes it back — plus the
 * §I enchant-count name suffix. Lore is rendered from {@link CombatState}, never parsed back (§4.2); an unknown
 * stored key renders as the style's {@code unknownLabel}, never crashing (§5.3). {@link #lines} stays pure and
 * server-free (it is the composer's body pass); only {@link #apply} touches Bukkit.
 */
public final class LoreRenderer {

    private final Config config;
    private final LoreComposer composer;
    private final ComposerMark mark;

    /**
     * The wiring for a renderer (ADR-0040): the presentation deps and the per-section templates, as NAMED
     * fields instead of a positional constructor ladder. Build one with {@link #of(Supplier, Function)} (or the
     * {@link LoreStyle} overload) and the fluent {@code with*} setters for whichever optional sections a pack
     * uses; every unset field is a safe no-op default (no colour override, no set/protection/trak/orb/heroic/
     * crystal section, base slots 0).
     *
     * @param style            re-read per render so a {@code /se reload} takes effect next render
     * @param displayNameOf    stable key → display name (covers enchants AND crystals); {@code null} → {@code unknownLabel}
     * @param enchantColorOf   per-enchant rarity-tier colour; {@code null}/blank → the style's {@code enchantColor}
     * @param setLore          set-member authored lore, read live from state (§6.6)
     * @param protectionLines  applied-scroll PROTECTED lines from an item's marker state (§I); empty when unprotected
     * @param trakLines        applied-trak count lines from an item's marker + counter state (§I); empty when none
     * @param legacyLoreLine   MIGRATION-ONLY (ADR-0040): recognises a pre-composer managed line by its visible text
     *                         so the one-time shim can drop it; consulted only for unmarked items, never the permanent path
     * @param countSuffix      §I transmog enchant-count name-suffix template ({@code {COUNT}}); {@code null}/blank → off
     * @param baseSlots        §H base enchant slots, for the orb "Enchantment Slots" line total
     * @param slotsLine        §H orb slots-line template ({@code {TOTAL}}/{@code {ADDED}}); {@code null}/blank → no line
     * @param heroicLine       §F heroic line template ({@code {TYPE}}/{@code {+/-}}/{@code {AMOUNT}}); blank → plain marker
     * @param crystalLine      §E on-gear crystal line template ({@code {CRYSTAL}}); {@code null}/blank → style fallback
     * @param crystalLineMulti §E on-gear line for a MERGED crystal (ADR-0035); defaults to {@code crystalLine}
     * @param maskLine         ADR-0053 on-gear mask line template ({@code {NAME}}); {@code null}/blank → no line
     * @param reforgeLine      ADR-0070 on-weapon reforge line template ({@code {NAME}}); {@code null}/blank → no line
     */
    public record Config(
            Supplier<LoreStyle> style,
            Function<String, String> displayNameOf,
            Function<String, String> enchantColorOf,
            SetLore setLore,
            Function<ItemStack, List<String>> protectionLines,
            Function<ItemStack, List<String>> trakLines,
            Predicate<String> legacyLoreLine,
            Supplier<String> countSuffix,
            IntSupplier baseSlots,
            Supplier<String> slotsLine,
            Supplier<String> heroicLine,
            Supplier<String> crystalLine,
            Supplier<String> crystalLineMulti,
            Supplier<String> maskLine,
            Supplier<String> reforgeLine) {

        public Config {
            Objects.requireNonNull(style, "style");
            Objects.requireNonNull(displayNameOf, "displayNameOf");
            Objects.requireNonNull(enchantColorOf, "enchantColorOf");
            Objects.requireNonNull(setLore, "setLore");
            Objects.requireNonNull(protectionLines, "protectionLines");
            Objects.requireNonNull(trakLines, "trakLines");
            Objects.requireNonNull(legacyLoreLine, "legacyLoreLine");
            Objects.requireNonNull(countSuffix, "countSuffix");
            Objects.requireNonNull(baseSlots, "baseSlots");
            Objects.requireNonNull(slotsLine, "slotsLine");
            Objects.requireNonNull(heroicLine, "heroicLine");
            Objects.requireNonNull(crystalLine, "crystalLine");
            Objects.requireNonNull(crystalLineMulti, "crystalLineMulti");
            Objects.requireNonNull(maskLine, "maskLine");
            Objects.requireNonNull(reforgeLine, "reforgeLine");
        }

        /** A minimal config: a fixed style + a name lookup, every optional section defaulted off. */
        public static Config of(LoreStyle style, Function<String, String> displayNameOf) {
            Objects.requireNonNull(style, "style");
            return of(() -> style, displayNameOf);
        }

        /** A minimal config: a live style supplier + a name lookup, every optional section defaulted off. */
        public static Config of(Supplier<LoreStyle> style, Function<String, String> displayNameOf) {
            return new Config(style, displayNameOf, key -> null, SetLore.NONE, stack -> List.of(),
                    stack -> List.of(), line -> false, () -> null, () -> 0, () -> null, () -> null, () -> null, () -> null,
                    () -> null, () -> null);
        }

        public Config withEnchantColorOf(Function<String, String> enchantColorOf) {
            return new Config(style, displayNameOf, enchantColorOf, setLore, protectionLines, trakLines, legacyLoreLine,
                    countSuffix, baseSlots, slotsLine, heroicLine, crystalLine, crystalLineMulti, maskLine, reforgeLine);
        }

        public Config withSetLore(SetLore setLore) {
            return new Config(style, displayNameOf, enchantColorOf, setLore, protectionLines, trakLines, legacyLoreLine,
                    countSuffix, baseSlots, slotsLine, heroicLine, crystalLine, crystalLineMulti, maskLine, reforgeLine);
        }

        public Config withProtectionLines(Function<ItemStack, List<String>> protectionLines) {
            return new Config(style, displayNameOf, enchantColorOf, setLore, protectionLines, trakLines, legacyLoreLine,
                    countSuffix, baseSlots, slotsLine, heroicLine, crystalLine, crystalLineMulti, maskLine, reforgeLine);
        }

        /** The applied-trak count lines rendered from marker + counter state (§I); replaces the old preserve-by-text seam. */
        public Config withTrakLines(Function<ItemStack, List<String>> trakLines) {
            return new Config(style, displayNameOf, enchantColorOf, setLore, protectionLines, trakLines, legacyLoreLine,
                    countSuffix, baseSlots, slotsLine, heroicLine, crystalLine, crystalLineMulti, maskLine, reforgeLine);
        }

        /** MIGRATION-ONLY (ADR-0040): the recogniser the one-time legacy shim uses on unmarked items. */
        public Config withLegacyLoreLine(Predicate<String> legacyLoreLine) {
            return new Config(style, displayNameOf, enchantColorOf, setLore, protectionLines, trakLines, legacyLoreLine,
                    countSuffix, baseSlots, slotsLine, heroicLine, crystalLine, crystalLineMulti, maskLine, reforgeLine);
        }

        public Config withCountSuffix(Supplier<String> countSuffix) {
            return new Config(style, displayNameOf, enchantColorOf, setLore, protectionLines, trakLines, legacyLoreLine,
                    countSuffix, baseSlots, slotsLine, heroicLine, crystalLine, crystalLineMulti, maskLine, reforgeLine);
        }

        public Config withBaseSlots(IntSupplier baseSlots) {
            return new Config(style, displayNameOf, enchantColorOf, setLore, protectionLines, trakLines, legacyLoreLine,
                    countSuffix, baseSlots, slotsLine, heroicLine, crystalLine, crystalLineMulti, maskLine, reforgeLine);
        }

        public Config withSlotsLine(Supplier<String> slotsLine) {
            return new Config(style, displayNameOf, enchantColorOf, setLore, protectionLines, trakLines, legacyLoreLine,
                    countSuffix, baseSlots, slotsLine, heroicLine, crystalLine, crystalLineMulti, maskLine, reforgeLine);
        }

        public Config withHeroicLine(Supplier<String> heroicLine) {
            return new Config(style, displayNameOf, enchantColorOf, setLore, protectionLines, trakLines, legacyLoreLine,
                    countSuffix, baseSlots, slotsLine, heroicLine, crystalLine, crystalLineMulti, maskLine, reforgeLine);
        }

        /**
         * Set the §E on-gear crystal line template. The MERGED-crystal template defaults to this same template
         * (no separate "Multi Crystal" line) — mirroring the old ladder — until {@link #withCrystalLineMulti}
         * overrides it.
         */
        public Config withCrystalLine(Supplier<String> crystalLine) {
            return new Config(style, displayNameOf, enchantColorOf, setLore, protectionLines, trakLines, legacyLoreLine,
                    countSuffix, baseSlots, slotsLine, heroicLine, crystalLine, crystalLine, maskLine, reforgeLine);
        }

        public Config withCrystalLineMulti(Supplier<String> crystalLineMulti) {
            return new Config(style, displayNameOf, enchantColorOf, setLore, protectionLines, trakLines, legacyLoreLine,
                    countSuffix, baseSlots, slotsLine, heroicLine, crystalLine, crystalLineMulti, maskLine, reforgeLine);
        }

        /** Set the ADR-0053 on-gear mask line template ({@code {NAME}} → the mask's styled display). */
        public Config withMaskLine(Supplier<String> maskLine) {
            return new Config(style, displayNameOf, enchantColorOf, setLore, protectionLines, trakLines, legacyLoreLine,
                    countSuffix, baseSlots, slotsLine, heroicLine, crystalLine, crystalLineMulti, maskLine, reforgeLine);
        }

        /** Set the ADR-0070 on-weapon reforge line template ({@code {NAME}} → the reforge's styled display). */
        public Config withReforgeLine(Supplier<String> reforgeLine) {
            return new Config(style, displayNameOf, enchantColorOf, setLore, protectionLines, trakLines, legacyLoreLine,
                    countSuffix, baseSlots, slotsLine, heroicLine, crystalLine, crystalLineMulti, maskLine, reforgeLine);
        }
    }

    /**
     * Set members' authored lore, looked up from state at render time so a worn piece keeps its flavour lore
     * after it is enchanted (render-from-scratch). Wiring uses a {@code Library}-backed lookup; tests {@link #NONE}.
     */
    public interface SetLore {
        /** The lore shared by every armour piece of {@code setKey} (empty if none / unknown). */
        List<String> armor(String setKey);

        /**
         * The lore for ONE armour piece of {@code setKey}: that slot's own flavour lines followed by the
         * shared block. {@code slotToken} is the item's gear kind ({@code HELMET}, {@code BOOTS}, …), which
         * is the slot name for every piece a set can mint — so the discriminator is the material itself and
         * nothing has to be stamped on the item to carry it. Defaults to the shared block, which is what a
         * set saying nothing per piece renders and what {@link #armor(String)} has always returned.
         */
        default List<String> armor(String setKey, String slotToken) {
            return armor(setKey);
        }

        /** The weapon's own lore for {@code setKey} (empty if none / unknown). */
        List<String> weapon(String setKey);

        /** A lookup that renders no member lore at all. */
        SetLore NONE = new SetLore() {
            @Override public List<String> armor(String setKey) {
                return List.of();
            }

            @Override public List<String> weapon(String setKey) {
                return List.of();
            }
        };
    }

    public LoreRenderer(Config config, ItemStateStore store) {
        this.config = Objects.requireNonNull(config, "config");
        this.composer = new LoreComposer(config);
        this.mark = new ComposerMark(ItemKeys.of().loreComposer(), store);
    }

    /** Body lore lines: one per enchant ({@code name level}), set lore, the orb slots line, then one per crystal. */
    public List<String> lines(CombatState state) {
        return composer.body(state);
    }

    /** {@link #lines(CombatState)} for a known gear kind, so a set piece renders ITS slot's lore (§6.6). */
    public List<String> lines(CombatState state, String kind) {
        return composer.body(state, kind);
    }

    /**
     * Render onto {@code stack} in place; clears lore when state is empty. False if the item can't carry meta (air).
     * Every section — including protection + trak lines — is composed from state, so this is the ONE recompose seam
     * feature systems call after mutating PDC. An item that predates the composer is migrated ONCE here
     * (marker-gated, {@link LegacyLoreShim}); marked items compose straight from state, never touching a classifier.
     */
    @SuppressWarnings("deprecation") // get/setLore(List<String>): deprecated-not-removed across the whole range.
    public boolean apply(ItemStack stack, CombatState state) {
        if (stack == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        List<String> lore = composer.compose(state, kindOf(stack.getType()),
                config.protectionLines().apply(stack), config.trakLines().apply(stack));
        if (!mark.isCurrent(stack)) {
            lore = LegacyLoreShim.migrate(meta.hasLore() ? meta.getLore() : List.of(), lore, config.legacyLoreLine());
        }
        meta.setLore(lore.isEmpty() ? null : lore);
        stampCountSuffix(stack, meta, state.enchants().size());
        stack.setItemMeta(meta);
        mark.stamp(stack); // flips the item to composer-owned so the shim runs at most once (bounded, non-looping)
        return true;
    }

    /**
     * §I stamp the CUSTOM-enchant count onto the display name (the transmog suffix) so it is a fixed part of the
     * name on any enchanted item — rebuilt from state here, so adding/removing an enchant refreshes it and a
     * drop to ZERO custom enchants strips it. Vanilla enchants never count (the count is {@code state.enchants}
     * only). An item with no custom name DERIVES a readable name from its material so its identity survives
     * ("Diamond Sword [3]"); at zero enchants a previously-derived name reverts to the bare vanilla item.
     */
    @SuppressWarnings("deprecation") // get/setDisplayName: the floor-stable item-meta path (matches #apply)
    private void stampCountSuffix(ItemStack stack, ItemMeta meta, int count) {
        String template = config.countSuffix().get();
        if (template == null || template.isBlank()) {
            return; // suffix feature off (the no-suffix test/fixture configs)
        }
        String current = meta.hasDisplayName() ? meta.getDisplayName() : null;
        String readable = readableName(stack.getType());
        if (count > 0) {
            String base = current != null ? EnchantCountSuffix.strip(current, template) : readable;
            meta.setDisplayName(EnchantCountSuffix.nameFor(base, template, count));
        } else if (current != null) {
            String base = EnchantCountSuffix.strip(current, template);
            // revert a name we previously DERIVED back to the vanilla item; keep a genuine custom name.
            meta.setDisplayName(base.isEmpty() || base.equals(readable) ? null : base);
        }
    }

    /** The gear kind label from a material — the last underscore segment ({@code DIAMOND_SWORD → "SWORD"}). */
    private static String kindOf(Material material) {
        String name = material.name();
        int underscore = name.lastIndexOf('_');
        return underscore >= 0 ? name.substring(underscore + 1) : name;
    }

    /** A readable display name derived from a material ({@code DIAMOND_SWORD → "Diamond Sword"}). */
    private static String readableName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
