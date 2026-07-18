package feature.reforge;

import compile.load.ContentHolder;
import compile.load.ReforgeDef;
import compile.load.ReforgeItemConfig;
import compile.load.SoundCue;
import feature.apply.ApplyResult;
import feature.apply.ExtractResult;
import feature.apply.GestureOutcome;
import feature.apply.ItemEnchanter;
import feature.crystal.ReforgeExtractor;
import item.codec.CombatCodec;
import item.codec.ReforgeCodec;
import item.mint.ItemFactory;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import platform.item.ItemGroups;
import platform.lang.Messages;
import platform.text.TimeFormat;
import platform.text.Tokens;

/**
 * The reforge item economy (ADR-0070) — mints reforge items from the ONE universal likeness, APPLIES them onto
 * a WEAPON (unconditional — no success roll, the crystal/mask rule), and — as the {@link ReforgeExtractor} hook
 * the ONE Item Extractor calls — pops a reforge back off intact BEFORE any crystal. The mask shape minus the
 * textured-head seam: a reforge is a single identity on a weapon, so apply is a boolean occupancy and extract
 * pops the one key. Gear mutation + eligibility delegate to {@link ItemEnchanter}; the item MATERIAL is the
 * per-reforge {@code icon} (cosmetic catalogue identity), resolved cross-version through the one mint path.
 */
public final class ReforgeService implements ReforgeExtractor {

    private final ReforgeCodec itemCodec;     // the reforge ITEM's identity (pre-apply)
    private final CombatCodec combatCodec;     // the gear-side read for carries() ONLY
    private final ItemEnchanter enchanter;
    private final ContentHolder content;
    private final Supplier<ReforgeItemConfig> config;
    private final Supplier<List<String>> weaponGroups; // config.yml reforges.weapon-groups, read live
    private final Messages messages;

    public ReforgeService(ReforgeCodec itemCodec, CombatCodec combatCodec, ItemEnchanter enchanter,
                          ContentHolder content, Supplier<ReforgeItemConfig> config,
                          Supplier<List<String>> weaponGroups, Messages messages) {
        this.itemCodec = Objects.requireNonNull(itemCodec, "itemCodec");
        this.combatCodec = Objects.requireNonNull(combatCodec, "combatCodec");
        this.enchanter = Objects.requireNonNull(enchanter, "enchanter");
        this.content = Objects.requireNonNull(content, "content");
        this.config = Objects.requireNonNull(config, "config");
        this.weaponGroups = Objects.requireNonNull(weaponGroups, "weaponGroups");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public boolean isReforge(ItemStack stack) {
        return itemCodec.isReforge(stack);
    }

    /**
     * Mint the reforge {@code key} as its catalogue item: the per-reforge {@code icon} material (cosmetic
     * identity), resolved by NAME through the one cross-version mint path; identity stamped, likeness rendered.
     * {@code null} for an unknown key.
     */
    public ItemStack mint(String key) {
        ReforgeDef def = content.library().reforgeDefOf(key);
        if (def == null) {
            return null;
        }
        // The icon is per-reforge cosmetic catalogue identity (§1); resolved by NAME through the one
        // cross-version mint path (LEGACY_FALLBACK degrades the 1.8 lane). PAPER = the both-era last resort.
        ItemStack stack = ItemFactory.buildItem(def.icon(), Material.PAPER, null, null);
        itemCodec.stamp(stack, key);
        render(stack, def);
        return stack;
    }

    /** Name/lore from the universal likeness — render-from-state (§4.2); the mask token idiom + the USE tokens. */
    private void render(ItemStack stack, ReforgeDef def) {
        ReforgeItemConfig cfg = config.get();
        Object[] tokens = {
                "COLOR", def.color(),
                "NAME", def.display(),
                "NAME_UPPER", def.display().toUpperCase(Locale.ROOT),
                "APPLIES", ItemGroups.kindsLabel(weaponGroups.get()),   // live weapon-groups label
                "TIME_FORMATTED", TimeFormat.hmsFromTicks(def.cooldownTicks()),
        };
        List<String> lore = Tokens.expandLines(cfg.lore(), "DESCRIPTION", def.description(), tokens);
        ItemFactory.decorated(stack, Tokens.sub(cfg.name(), tokens), ItemFactory.wrapLore(lore));
    }

    /** Reforge-onto-weapon gesture: validate, stamp, spend the cursor — unconditional on an eligible target. */
    public GestureOutcome apply(ItemStack cursor, ItemStack gear) {
        String key = itemCodec.keyOf(cursor);
        if (key == null) {
            return GestureOutcome.noop(null);
        }
        ApplyResult eligible = enchanter.checkReforge(gear, key);
        if (!eligible.ok()) {
            return GestureOutcome.noop(eligible.message()); // never consume on an ineligible target
        }
        enchanter.applyReforge(gear, key);
        consume(cursor);
        return GestureOutcome.committed(gear, GestureOutcome.Cue.sound(applySound(config.get())),
                messages.format("reforge.apply-success", "REFORGE", label(key)));
    }

    @Override
    public boolean carries(ItemStack gear) {
        return gear != null && !feature.compat.Mats.isAir(gear.getType())
                && combatCodec.read(gear).reforgeKey() != null;
    }

    /** The Item Extractor hook (ADR-0070): pop {@code gear}'s reforge and mint it back; spends the cursor. */
    @Override
    public GestureOutcome extract(ItemStack cursor, ItemStack gear) {
        ExtractResult result = enchanter.extractReforge(gear);
        if (!result.ok()) {
            return GestureOutcome.noop(result.message());
        }
        String popped = result.poppedEntry();
        ItemStack minted = mint(popped); // null-tolerant for stale content — the weapon is still cleaned
        consume(cursor);
        return GestureOutcome.committed(gear, minted, GestureOutcome.Cue.sound(removeSound(config.get())),
                messages.format("reforge.extract-success", "REFORGE", label(popped)));
    }

    private static SoundCue applySound(ReforgeItemConfig cfg) {
        return cfg.sounds() ? cfg.soundApply() : null;
    }

    private static SoundCue removeSound(ReforgeItemConfig cfg) {
        return cfg.sounds() ? cfg.soundRemove() : null;
    }

    /** The colour-styled display for a chat message ({@code &6&lTestforge}) — the universal bold-name styling. */
    private String label(String key) {
        ReforgeDef def = content.library().reforgeDefOf(key);
        return def == null ? key : def.color() + "&l" + def.display();
    }

    private static void consume(ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
    }
}
