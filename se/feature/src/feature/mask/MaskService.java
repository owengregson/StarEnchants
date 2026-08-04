package feature.mask;

import compile.load.ContentHolder;
import compile.load.MaskDef;
import compile.load.MaskItemConfig;
import compile.load.SoundCue;
import feature.apply.ApplyResult;
import feature.apply.ExtractResult;
import feature.apply.GestureOutcome;
import feature.apply.ItemEnchanter;
import item.codec.MaskCodec;
import item.codec.MaskItemData;
import item.head.HeadEquip;
import item.head.TexturedHeads;
import item.mint.ItemFactory;
import item.render.StyledNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import platform.item.ItemGroups;
import platform.lang.Messages;
import platform.text.Tokens;

/**
 * The mask item economy (ADR-0053 §3, §6; ADR-0074) — mints mask heads from the ONE universal likeness, FOLDS
 * masks into composites, APPLIES them onto helmets (unconditional — no success roll, the crystal rule), and
 * REMOVES them back off intact.
 *
 * <p>It is now the crystal shape whole rather than "the crystal shape minus merge/multi": a mask item carries an
 * ordered {@link MaskItemData} child list, mask-onto-mask folds it up to {@code masks.max-merge}, and a helmet's
 * one socket holds the whole entry. What masks still do NOT take from crystals is slots — a helmet has exactly
 * one mask socket, so occupancy stays a boolean and the cap bounds the ENTRY instead.
 *
 * <p>A composite's likeness is drawn from its FIRST child (owner ruling): its head is the face the illusion
 * shows, and its colour styles the compound name — the wearer's own merge order decides which face they wear.
 * The names themselves join through {@link StyledNames}, the same rule the Multi Crystal name uses, so each
 * child reads in its own colour.
 */
public final class MaskService {

    /** Masks are helmets-only by construction (ADR-0053 §1) — the {@code {APPLIES}} label is fixed. */
    private static final String APPLIES = ItemGroups.kindsLabel(List.of("HELMET"));

    private final MaskCodec codec;
    private final ItemEnchanter enchanter;
    private final ContentHolder content;
    private final Supplier<MaskItemConfig> config;
    private final IntSupplier maxMerge; // masks.max-merge — the composite child cap (read live)
    private final TexturedHeads heads;
    private final HeadEquip headEquip; // strips client-side helmet wearability at mint (ADR-0053 masks, 1.8.4)
    private final Messages messages; // §L lang.yml

    public MaskService(MaskCodec codec, ItemEnchanter enchanter, ContentHolder content,
                       Supplier<MaskItemConfig> config, IntSupplier maxMerge, TexturedHeads heads,
                       HeadEquip headEquip, Messages messages) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.enchanter = Objects.requireNonNull(enchanter, "enchanter");
        this.content = Objects.requireNonNull(content, "content");
        this.config = Objects.requireNonNull(config, "config");
        this.maxMerge = Objects.requireNonNull(maxMerge, "maxMerge");
        this.heads = Objects.requireNonNull(heads, "heads");
        this.headEquip = Objects.requireNonNull(headEquip, "headEquip");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public boolean isMask(ItemStack stack) {
        return codec.isMask(stack);
    }

    /**
     * Mint the mask {@code key}: the textured head when this server supports it (the era seam), else the
     * def's fallback material; identity stamped, universal likeness rendered. {@code null} for an unknown key.
     * {@code key} may be a composite ENTRY, which mints the folded mask back whole (the whole-entry convention).
     */
    public ItemStack mint(String key) {
        List<String> children = MaskItemData.componentsOf(key);
        // Decode-tolerant like every other item read (§4.2): an absent, blank or over-long entry — a hand-edited
        // blob, or one from a future cap — mints nothing rather than throwing out of a gesture. The record's
        // own ABSOLUTE_MAX guard is a programming invariant, not an input filter, so the filtering is here.
        if (children.isEmpty() || children.size() > MaskItemData.ABSOLUTE_MAX) {
            return null;
        }
        return mint(new MaskItemData(children));
    }

    /** Mint the mask (or composite) {@code data}; {@code null} when its FIRST child names no live content. */
    public ItemStack mint(MaskItemData data) {
        MaskDef first = content.library().maskDefOf(data.first());
        if (first == null) {
            return null; // the face the item would wear is gone — there is nothing to mint it as
        }
        ItemStack stack = heads.head(first.head());
        if (stack == null) {
            // No texture (blank head / unsupported server): resolve the def's material token by NAME. The
            // fallback constant must exist on BOTH eras (the Material.CLOCK trap) — PAPER, not PLAYER_HEAD,
            // which is absent on 1.8 (there the era seam above already built a SKULL_ITEM head).
            stack = ItemFactory.buildItem(first.material(), Material.PAPER, null, null);
        }
        // A mask activates APPLIED ONTO a helmet (its drag gesture), never worn as the raw head — deny client-side
        // helmet wearability so the client itself refuses the slot (1.8.4). The apply gesture reads the mask codec,
        // not the equippable component, so this never blocks applying the mask onto a helmet.
        headEquip.unwearable(stack);
        codec.stamp(stack, data);
        render(stack, data, first);
        return stack;
    }

    /**
     * Name/lore from the universal likeness — render-from-state (§4.2); the pet token idiom, per-def values.
     * A composite takes the {@code name-multi} template, and its {@code {NAME}}/{@code {DESCRIPTION}} read
     * EVERY child: the names comma-joined in their own colours, the description blocks stacked (ADR-0074).
     */
    private void render(ItemStack stack, MaskItemData data, MaskDef first) {
        MaskItemConfig cfg = config.get();
        List<String> keys = data.keys();
        String nameTemplate = data.isMulti() ? cfg.nameMulti() : cfg.name();
        // A PLAIN mask renders exactly as it always did: {COLOR} is the def's colour and {NAME} its BARE display,
        // because the template supplies the styling ("{COLOR}&l{NAME} Mask"). A composite cannot work that way —
        // one {COLOR} cannot style N children — so there {NAME} carries each child's own colour inline, the Multi
        // Crystal rule. Keeping the two paths separate is what makes every mask minted before this byte-identical.
        Object[] tokens = data.isMulti()
                ? new Object[] {
                        "COLOR", first.color(), // the face it wears styles the compound (owner ruling)
                        "NAME", StyledNames.join(nameTemplate, keys, this::styledDisplay),
                        "NAME_UPPER", StyledNames.join(nameTemplate, keys, this::styledDisplayUpper),
                        "APPLIES", APPLIES,
                }
                : new Object[] {
                        "COLOR", first.color(),
                        "NAME", first.display(),
                        "NAME_UPPER", upper(first.display()), // the SET-BONUS/pets header convention
                        "APPLIES", APPLIES,
                };
        List<String> lore = Tokens.expandLines(cfg.lore(), "DESCRIPTION", descriptionBlock(keys), tokens);
        ItemFactory.decorated(stack, Tokens.sub(nameTemplate, tokens), ItemFactory.wrapLore(lore));
    }

    /** Each child's authored description block, in fold order, separated by ONE blank line (the crystal rule). */
    private List<String> descriptionBlock(List<String> keys) {
        if (keys.size() == 1) {
            MaskDef only = content.library().maskDefOf(keys.get(0));
            return only == null ? List.of() : only.description();
        }
        List<String> out = new ArrayList<>();
        for (String key : keys) {
            MaskDef def = content.library().maskDefOf(key);
            List<String> block = def == null ? List.of() : def.description();
            if (block.isEmpty()) {
                continue; // an ability-less child contributes no block rather than a blank gap or a literal null
            }
            if (!out.isEmpty()) {
                out.add("");
            }
            out.addAll(block);
        }
        return out;
    }

    /** Mask-on-something gesture: target mask → FOLD (up to the cap), else APPLY to the helmet. */
    public GestureOutcome interact(ItemStack cursor, ItemStack target) {
        MaskItemData mask = codec.dataOf(cursor);
        if (mask == null) {
            return GestureOutcome.noop(null);
        }
        MaskItemData targetMask = codec.dataOf(target);
        return targetMask != null ? merge(cursor, mask, target, targetMask) : apply(cursor, mask, target);
    }

    /** Mask-onto-helmet gesture: validate, stamp, spend the cursor — unconditional on an eligible target. */
    public GestureOutcome apply(ItemStack cursor, ItemStack gear) {
        MaskItemData mask = codec.dataOf(cursor);
        return mask == null ? GestureOutcome.noop(null) : apply(cursor, mask, gear);
    }

    private GestureOutcome apply(ItemStack cursor, MaskItemData mask, ItemStack gear) {
        String entry = mask.entry();
        ApplyResult eligible = enchanter.checkMask(gear, entry);
        if (!eligible.ok()) {
            return GestureOutcome.noop(eligible.message()); // never consume on an ineligible target
        }
        enchanter.applyMask(gear, entry);
        consume(cursor);
        return GestureOutcome.committed(gear, GestureOutcome.Cue.sound(applySound(config.get())),
                messages.format("mask.apply-success", "MASK", label(mask.keys())));
    }

    /**
     * Fold two masks (cursor ON TOP of the target) into one composite, capped at {@code masks.max-merge}
     * (ADR-0074). The crystal merge verbatim, minus the stackability clash: a mask declares no
     * {@code stackable}, and two copies of one mask fold to a doubled bonus the additive fold already sums.
     */
    private GestureOutcome merge(ItemStack cursor, MaskItemData cursorMask, ItemStack target, MaskItemData targetMask) {
        if (target.getAmount() > 1) {
            return GestureOutcome.noop(messages.format("mask.merge-single"));
        }
        int cap = maxMerge.getAsInt();
        MaskItemData merged = targetMask.mergeWith(cursorMask, cap); // target keeps the item; cursor lands on top
        if (merged == null) {
            return GestureOutcome.noop(messages.format("mask.merge-cap", "MAX", cap));
        }
        ItemStack multi = mint(merged);
        if (multi == null) {
            return GestureOutcome.noop(messages.format("mask.no-such", "KEY", merged.first()));
        }
        consume(cursor);
        return GestureOutcome.committed(multi, GestureOutcome.Cue.sound(applySound(config.get())),
                messages.format("mask.merge", "MASK", label(merged.keys())));
    }

    /**
     * Split the topmost child off a COMPOSITE mask item: the item becomes the remainder, the child goes back.
     * The Multi Crystal split (ADR-0035 §3) — a fold has to be undoable, or a mis-merge is permanent.
     */
    public GestureOutcome split(ItemStack maskItem) {
        MaskItemData data = codec.dataOf(maskItem);
        if (data == null) {
            return GestureOutcome.noop(null);
        }
        if (maskItem.getAmount() > 1) {
            return GestureOutcome.noop(messages.format("mask.merge-single")); // a stack of >1 is ambiguous
        }
        if (!data.isMulti()) {
            return GestureOutcome.noop(messages.format("mask.split-not-multi")); // a plain mask has nothing to split
        }
        List<String> children = new ArrayList<>(data.keys());
        String popped = children.remove(children.size() - 1); // the topmost (most recently folded) child
        ItemStack single = mint(MaskItemData.single(popped));
        ItemStack remainder = mint(new MaskItemData(children));
        if (single == null || remainder == null) {
            return GestureOutcome.noop(messages.format("mask.no-such", "KEY", popped));
        }
        return GestureOutcome.committed(remainder, single, GestureOutcome.Cue.sound(removeSound(config.get())),
                messages.format("mask.extract-success", "MASK", label(List.of(popped))));
    }

    /** Whether {@code stack} is a mask carrying more than one child — the extractor's split target. */
    public boolean carriesComposite(ItemStack stack) {
        MaskItemData data = codec.dataOf(stack);
        return data != null && data.isMulti();
    }

    /** Pop {@code gear}'s mask and mint it back to the player; no-op when the helmet carries none. */
    public GestureOutcome remove(ItemStack gear) {
        ExtractResult result = enchanter.removeMask(gear);
        if (!result.ok()) {
            return GestureOutcome.noop(result.message());
        }
        // The WHOLE entry pops off intact (ADR-0035's convention, ADR-0074): a composite comes back as ONE
        // composite, which the extractor then splits — never N loose masks the wearer has to re-fold.
        String popped = result.poppedEntry();
        ItemStack minted = mint(popped); // null for stale content — the helmet is still cleaned
        // consumeCursor stays FALSE: this outcome commits from the cursor-LESS right-click gesture
        // (MaskRemoveListener, ADR-0053 §3), so there is no cursor for the committed(...) factories to spend.
        return new GestureOutcome(true, false, gear, minted,
                GestureOutcome.Cue.sound(removeSound(config.get())),
                messages.format("mask.remove-success", "MASK", label(MaskItemData.componentsOf(popped))), null);
    }

    private static SoundCue applySound(MaskItemConfig cfg) {
        return cfg.sounds() ? cfg.soundApply() : null;
    }

    private static SoundCue removeSound(MaskItemConfig cfg) {
        return cfg.sounds() ? cfg.soundRemove() : null;
    }

    /** The colour-styled display(s) for a chat message ({@code &6&lMidas}), joined like the item name. */
    private String label(List<String> keys) {
        return StyledNames.join(config.get().name(), keys, this::styledDisplay);
    }

    /** One child's colour-styled display — the universal bold-name styling; an unknown key falls back to itself. */
    private String styledDisplay(String key) {
        MaskDef def = content.library().maskDefOf(key);
        return def == null ? key : def.color() + "&l" + def.display();
    }

    /**
     * {@link #styledDisplay} with only the WORDS upper-cased. Upper-casing the styled string whole would take its
     * colour codes with it ({@code &l} → {@code &L}), which is a coin-flip on any renderer that is not
     * case-insensitive — so the case change is applied before the styling, never after.
     */
    private String styledDisplayUpper(String key) {
        MaskDef def = content.library().maskDefOf(key);
        return def == null ? upper(key) : def.color() + "&l" + upper(def.display());
    }

    private static String upper(String display) {
        return display == null ? null : display.toUpperCase(Locale.ROOT);
    }

    private static void consume(ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
    }
}
