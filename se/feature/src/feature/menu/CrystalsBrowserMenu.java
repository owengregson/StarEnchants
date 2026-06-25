package feature.menu;

import compile.load.ContentHolder;
import compile.load.CrystalDef;
import compile.load.TierRegistry;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The read-only crystals/modifiers browser (§K; a Cosmic Enchants-style plugin called crystals "modifiers").
 * Browse-only: applying / extracting / merging stays a drag gesture ({@code CrystalListener}, §E).
 */
public final class CrystalsBrowserMenu extends PagedMenu<CrystalDef> {

    private final ContentHolder content;

    /** Default-layout form (tests/fixtures). */
    public CrystalsBrowserMenu(ContentHolder content, Capabilities caps) {
        this(content, caps, compile.load.MenusConfig::empty);
    }

    public CrystalsBrowserMenu(ContentHolder content, Capabilities caps,
                               java.util.function.Supplier<compile.load.MenusConfig> menus) {
        super("crystals", MenuLayout.paged("&3Crystals"), caps, menus);
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override
    protected List<CrystalDef> items(MenuHolder holder) {
        return content.library().crystals();
    }

    @Override
    protected ItemStack icon(MenuHolder holder, CrystalDef def) {
        List<String> lore = new ArrayList<>();
        if (!def.description().isBlank()) {
            lore.add("&7" + def.description());
        }
        lore.add("&8tier: " + tierColor(def.tier()) + tierLabel(def.tier()));
        lore.add("&8applies to: &7" + String.join(", ", def.appliesTo()));
        lore.add("&8Apply by dragging the crystal onto gear.");
        return ItemFactory.build(material("AMETHYST_SHARD", "PRISMARINE_CRYSTALS", "NETHER_STAR", "QUARTZ"),
                def.display(), lore);
    }

    @Override
    protected void onSelect(MenuClick click, CrystalDef def) {
        // Read-only catalog: apply/extract/merge are drag gestures, not menu clicks (§K/§E).
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
