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
import item.head.TexturedHeads;
import item.mint.ItemFactory;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import platform.item.ItemGroups;
import platform.lang.Messages;
import platform.text.Tokens;

/**
 * The mask item economy (ADR-0053 §3, §6) — mints mask heads from the ONE universal likeness, APPLIES them
 * onto helmets (unconditional — no success roll, the crystal rule), and REMOVES them back off intact. The
 * crystal shape minus merge/multi/slots: a mask is a single identity on a helmet, so apply is a boolean
 * occupancy and remove pops the one key. Gear mutation + eligibility delegate to {@link ItemEnchanter};
 * the minted head comes from the {@link TexturedHeads} era seam with the def's material as the untexturable
 * fallback. The likeness tokens ({@code {COLOR}}/{@code {NAME}}/line-expanding {@code {DESCRIPTION}}/
 * {@code {APPLIES}}) follow the pet likeness, not the crystal's {@code CrystalNames} join — a mask has no
 * components to join.
 */
public final class MaskService {

    /** Masks are helmets-only by construction (ADR-0053 §1) — the {@code {APPLIES}} label is fixed. */
    private static final String APPLIES = ItemGroups.kindsLabel(List.of("HELMET"));

    private final MaskCodec codec;
    private final ItemEnchanter enchanter;
    private final ContentHolder content;
    private final Supplier<MaskItemConfig> config;
    private final TexturedHeads heads;
    private final Messages messages; // §L lang.yml

    public MaskService(MaskCodec codec, ItemEnchanter enchanter, ContentHolder content,
                       Supplier<MaskItemConfig> config, TexturedHeads heads, Messages messages) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.enchanter = Objects.requireNonNull(enchanter, "enchanter");
        this.content = Objects.requireNonNull(content, "content");
        this.config = Objects.requireNonNull(config, "config");
        this.heads = Objects.requireNonNull(heads, "heads");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public boolean isMask(ItemStack stack) {
        return codec.isMask(stack);
    }

    /**
     * Mint the mask {@code key}: the textured head when this server supports it (the era seam), else the
     * def's fallback material; identity stamped, universal likeness rendered. {@code null} for an unknown key.
     */
    public ItemStack mint(String key) {
        MaskDef def = content.library().maskDefOf(key);
        if (def == null) {
            return null;
        }
        ItemStack stack = heads.head(def.head());
        if (stack == null) {
            // No texture (blank head / unsupported server): resolve the def's material token by NAME. The
            // fallback constant must exist on BOTH eras (the Material.CLOCK trap) — PAPER, not PLAYER_HEAD,
            // which is absent on 1.8 (there the era seam above already built a SKULL_ITEM head).
            stack = ItemFactory.buildItem(def.material(), Material.PAPER, null, null);
        }
        codec.stamp(stack, key);
        render(stack, def);
        return stack;
    }

    /** Name/lore from the universal likeness — render-from-state (§4.2); the pet token idiom, per-def values. */
    private void render(ItemStack stack, MaskDef def) {
        MaskItemConfig cfg = config.get();
        Object[] tokens = {
                "COLOR", def.color(),
                "NAME", def.display(),
                "APPLIES", APPLIES,
        };
        List<String> lore = Tokens.expandLines(cfg.lore(), "DESCRIPTION", def.description(), tokens);
        ItemFactory.decorated(stack, Tokens.sub(cfg.name(), tokens), ItemFactory.wrapLore(lore));
    }

    /** Mask-onto-helmet gesture: validate, stamp, spend the cursor — unconditional on an eligible target. */
    public GestureOutcome apply(ItemStack cursor, ItemStack gear) {
        String key = codec.keyOf(cursor);
        if (key == null) {
            return GestureOutcome.noop(null);
        }
        ApplyResult eligible = enchanter.checkMask(gear, key);
        if (!eligible.ok()) {
            return GestureOutcome.noop(eligible.message()); // never consume on an ineligible target
        }
        enchanter.applyMask(gear, key);
        consume(cursor);
        return GestureOutcome.committed(gear, GestureOutcome.Cue.sound(applySound(config.get())),
                messages.format("mask.apply-success", "MASK", label(key)));
    }

    /** Pop {@code gear}'s mask and mint it back to the player; no-op when the helmet carries none. */
    public GestureOutcome remove(ItemStack gear) {
        ExtractResult result = enchanter.removeMask(gear);
        if (!result.ok()) {
            return GestureOutcome.noop(result.message());
        }
        String popped = result.poppedEntry();
        ItemStack minted = mint(popped); // null for stale content — the helmet is still cleaned
        // consumeCursor stays FALSE: this outcome commits from the cursor-LESS right-click gesture
        // (MaskRemoveListener, ADR-0053 §3), so there is no cursor for the committed(...) factories to spend.
        return new GestureOutcome(true, false, gear, minted,
                GestureOutcome.Cue.sound(removeSound(config.get())),
                messages.format("mask.remove-success", "MASK", label(popped)), null);
    }

    private static SoundCue applySound(MaskItemConfig cfg) {
        return cfg.sounds() ? cfg.soundApply() : null;
    }

    private static SoundCue removeSound(MaskItemConfig cfg) {
        return cfg.sounds() ? cfg.soundRemove() : null;
    }

    /** The colour-styled display for a chat message ({@code &6&lMidas}) — the universal bold-name styling. */
    private String label(String key) {
        MaskDef def = content.library().maskDefOf(key);
        return def == null ? key : def.color() + "&l" + def.display();
    }

    private static void consume(ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
    }
}
