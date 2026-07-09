package feature.useitem;

import compile.load.ContentHolder;
import compile.load.UseItemDef;
import engine.run.UseAttempt;
import feature.menu.MenuIcons;
import feature.trigger.TriggerDispatch;
import item.codec.UseItemCodec;
import item.mint.ItemFactory;
import item.mint.VanillaEnchants;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.item.EdibleItems;
import platform.text.TimeFormat;
import platform.text.Tokens;

/**
 * The use-item cold path (§3.6, docs/decisions/0048-use-items.md) — mints a right-click item from its
 * {@link UseItemDef} likeness and, on a right-click, runs the def's abilities through the SAME pipeline every
 * other source uses (via {@link TriggerDispatch#fireUse}), so a use-item passes the full gate sequence and
 * interacts with every other feature for free. Pure glue: identity is the {@link UseItemCodec} PDC key, the run
 * is the shared engine machinery, and the outcome is a compact {@link UseOutcome} the listener renders feedback
 * from. Bukkit-thin; a right-click is a cold event, so the {@code {TIME_FORMATTED}} lore render and the full
 * fact mask carry no hot-path cost.
 */
public final class UseItemService {

    private final ContentHolder content;
    private final UseItemCodec codec;
    private final TriggerDispatch dispatch;
    private final VanillaEnchants vanilla; // §3.5 glint lives at the feature layer (the menus' glow helper)
    private final EdibleItems edibleItems; // is-food edibility seam (no-op below 1.20.5 — degrades to one-click)

    public UseItemService(ContentHolder content, UseItemCodec codec, TriggerDispatch dispatch,
                          VanillaEnchants vanilla, EdibleItems edibleItems) {
        this.content = Objects.requireNonNull(content, "content");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.vanilla = Objects.requireNonNull(vanilla, "vanilla");
        this.edibleItems = Objects.requireNonNull(edibleItems, "edibleItems");
    }

    public boolean isUseItem(ItemStack stack) {
        return codec.isUseItem(stack);
    }

    /** The def key {@code stack} carries, or {@code null} if it is not a use-item. */
    public String keyOf(ItemStack stack) {
        return codec.keyOf(stack);
    }

    /** The def for {@code key} in the live library, or {@code null} (a stale key an old item still carries). */
    public UseItemDef defOf(String key) {
        return content.library().useItemDefOf(key);
    }

    /**
     * Mint the use-item {@code key} from its def likeness: {@code {TIME_FORMATTED}} in the lore renders a0's
     * cooldown (h/m/s), the codec stamps identity, a {@code shiny} def gains the feature-layer enchant glint, and
     * an {@code is-food} def is forced edible (1.20.5+; a no-op below). {@code null} for an unknown key.
     */
    public ItemStack mint(String key) {
        UseItemDef def = content.library().useItemDefOf(key);
        if (def == null) {
            return null;
        }
        String time = TimeFormat.hmsFromTicks(def.cooldownTicks());
        ItemStack stack = ItemFactory.buildItem(def.material(), Material.PAPER, def.name(),
                Tokens.subLines(def.lore(), "TIME_FORMATTED", time));
        codec.stamp(stack, key);
        if (def.shiny()) {
            MenuIcons.glow(vanilla, stack); // cosmetic-only; a refusing server just leaves it un-glinted
        }
        applyEdible(def, stack);
        return stack;
    }

    /**
     * Force {@code stack} edible when {@code def} is {@code is-food} (the eat gesture, the is-food design spec);
     * a no-op otherwise. {@link EdibleItems#makeEdible} itself no-ops below 1.20.5, so an {@code is-food} item on
     * an older server just stays a one-click use-item. Separated from {@link #mint} so the routing is unit-testable
     * without the server-bound item build.
     */
    void applyEdible(UseItemDef def, ItemStack stack) {
        if (def.isFood()) {
            edibleItems.makeEdible(stack); // 1.20.5+ forces the eat gesture; a no-op below (degrades to one-click)
        }
    }

    /**
     * Run {@code def}'s abilities for {@code player}'s right-click and collapse the engine's {@link UseAttempt} to
     * a {@link UseOutcome} by the §3.6 precedence. A condition stop names its authored source (aligned to the def's
     * ability order) so the fail message can render {@code {CONDITION}}.
     */
    public UseOutcome use(Player player, UseItemDef def) {
        UseAttempt attempt = dispatch.fireUse(player, def.stableKeys());
        if (attempt.activated()) {
            return UseOutcome.activated();
        }
        if (attempt.onCooldown()) {
            return UseOutcome.onCooldown(attempt.cooldownRemainingTicks());
        }
        int index = attempt.conditionCandidateIndex();
        if (index >= 0) {
            String source = index < def.conditionSources().size() ? def.conditionSources().get(index) : "";
            return UseOutcome.conditionFailed(source);
        }
        if (attempt.chanceFailed()) {
            return UseOutcome.chanceFailed();
        }
        return UseOutcome.blocked();
    }
}
