package feature.menu;

import compile.load.ContentHolder;
import compile.load.SetDef;
import compile.load.TierRegistry;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The read-only armour-set browser + preview (docs/v3-directives.md §K — "Armor-sets browser + preview"):
 * a flat paged list of every armour set; each icon's tooltip is the per-set preview — its description, the
 * rarity tier, the number of worn pieces that completes the set ({@code Required-Items} threshold), and the
 * item groups its pieces may sit on. The {@code SetDef} catalog carries no member-piece list or set-bonus
 * text (the bonus is a single compiled ability keyed by the set), so the preview shows the published set
 * metadata — name / tier / pieces-to-complete / applicable groups.
 */
public final class SetsBrowserMenu extends PagedMenu<SetDef> {

    private final ContentHolder content;

    /** Default-layout form (tests/fixtures). */
    public SetsBrowserMenu(ContentHolder content, Capabilities caps) {
        this(content, caps, compile.load.MenusConfig::empty);
    }

    public SetsBrowserMenu(ContentHolder content, Capabilities caps,
                           java.util.function.Supplier<compile.load.MenusConfig> menus) {
        super("sets", MenuLayout.paged("&3Armour Sets"), caps, menus);
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override
    protected List<SetDef> items(MenuHolder holder) {
        return content.library().sets();
    }

    @Override
    protected ItemStack icon(MenuHolder holder, SetDef def) {
        List<String> lore = new ArrayList<>();
        if (!def.description().isBlank()) {
            lore.add("&7" + def.description());
        }
        lore.add("&8tier: " + tierColor(def.tier()) + tierLabel(def.tier()));
        lore.add("&8completes at: &7" + def.pieces() + " piece" + (def.pieces() == 1 ? "" : "s"));
        lore.add("&8pieces: &7" + String.join(", ", def.appliesTo()));
        return ItemFactory.build(material("DIAMOND_CHESTPLATE", "IRON_CHESTPLATE", "LEATHER_CHESTPLATE"),
                def.display(), lore);
    }

    @Override
    protected void onSelect(MenuClick click, SetDef def) {
        // Read-only: the icon tooltip is the preview.
    }

    private String tierColor(String tier) {
        if (tier == null) {
            return "&7";
        }
        TierRegistry.Tier t = content.library().tiers().tier(tier);
        return t != null && !t.color().isBlank() ? t.color() : "&7";
    }

    private static String tierLabel(String tier) {
        return tier == null || tier.isBlank() ? "—" : tier;
    }
}
