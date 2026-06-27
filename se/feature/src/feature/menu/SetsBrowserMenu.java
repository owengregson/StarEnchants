package feature.menu;

import compile.load.ContentHolder;
import compile.load.SetDef;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import platform.caps.Capabilities;

/**
 * The read-only armour-set browser (docs/v3-directives.md §K): a flat paged list of every set, each icon's
 * tooltip the per-set preview. The {@code SetDef} catalog carries no member-piece list or bonus text (the
 * bonus is one compiled ability keyed by the set), so the preview shows the published metadata only.
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
        List<String> lore = new ArrayList<>(MenuText.describe(def.description(), "&7"));
        lore.add("&8completes at: &7" + def.armorComplete() + " armour piece" + (def.armorComplete() == 1 ? "" : "s"));
        lore.add("&8armour: &7" + String.join(", ", def.appliesTo()));
        if (def.hasWeapon()) {
            lore.add("&8weapon: &7" + (def.weapon().name() != null ? def.weapon().name() : def.weapon().material()));
        }
        return ItemFactory.build(material("DIAMOND_CHESTPLATE", "IRON_CHESTPLATE", "LEATHER_CHESTPLATE"),
                def.display(), lore);
    }

    @Override
    protected void onSelect(MenuClick click, SetDef def) {
        // Read-only: the icon tooltip is the preview.
    }
}
