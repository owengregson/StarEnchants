package feature.menu;

import compile.load.MenusConfig;
import item.lang.Messages;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import platform.caps.Capabilities;
import platform.content.ContentReloader;
import platform.content.ReloadResult;
import platform.sched.Scheduling;

/**
 * The operator landing hub (ADR-0030) — the {@code starenchants.admin} GUI reached by {@code /se menu}. From
 * here an operator can do everything: grant guaranteed enchant books, mint any item, drill into armour sets
 * and mint each piece, mint crystals, force-apply an enchant, browse the catalogues and the DSL reference, and
 * reload the content (left-click) or validate it (right-click) without leaving the game. Heavy/destructive
 * surfaces (pack apply, migrate, import) intentionally stay command-only and are signposted, not buttoned.
 */
public final class OperatorConsoleMenu extends HubMenu {

    private final MenuRegistry registry;
    private final ContentReloader reloader;
    private final Messages messages;

    public OperatorConsoleMenu(MenuRegistry registry, ContentReloader reloader, Messages messages,
                               Capabilities caps, Supplier<MenusConfig> menus) {
        super("console", "starenchants.admin", MenuLayout.sized(6, "&c&lOperator Console"), caps, menus);
        this.registry = registry;
        this.reloader = Objects.requireNonNull(reloader, "reloader");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    protected String infoTitle() {
        return "&c&lOperator Console";
    }

    @Override
    protected List<String> infoLore() {
        return List.of("&7Everything an operator needs.",
                "&8Pack / migrate / import stay on &7/se&8.");
    }

    @Override
    protected void layoutTiles(MenuHolder holder) {
        // Row 1 — grant & mint.
        tile(holder, 10, MenuIcons.tile("ENCHANTED_BOOK", Material.BOOK, "&d&lGrant Books",
                List.of("&7Browse every enchant by tier and", "&7mint a guaranteed book of any level."),
                "&eClick to open."), open("admin"));
        tile(holder, 12, MenuIcons.tile("CHEST", Material.CHEST, "&6&lMint Items",
                List.of("&7Mint any plugin item to yourself —", "&7gems, orbs, scrolls, dust, traks…"),
                "&eClick to open."), open("mint"));
        tile(holder, 14, MenuIcons.tile("DIAMOND_CHESTPLATE", Material.IRON_CHESTPLATE, "&b&lArmour Sets",
                List.of("&7Drill into any set and mint each", "&7piece — helmet to weapon."),
                "&eClick to open."), open("sets"));
        tile(holder, 16, MenuIcons.tile("AMETHYST_SHARD", Material.QUARTZ, "&5&lCrystals",
                List.of("&7Browse every crystal and mint", "&7one to yourself to socket."),
                "&eClick to open."), open("crystals"));

        // Row 3 — tools & reference.
        tile(holder, 28, MenuIcons.tile("ANVIL", Material.ANVIL, "&a&lApply Enchant",
                List.of("&7Apply any enchant straight onto", "&7your held item (force-give)."),
                "&eClick to open."), open("apply"));
        tile(holder, 30, MenuIcons.tile("WRITABLE_BOOK", Material.BOOK, "&3&lEnchant Catalogue",
                List.of("&7Browse every custom enchant,", "&7grouped by rarity tier."),
                "&eClick to browse."), open("enchants"));
        tile(holder, 32, MenuIcons.tile("KNOWLEDGE_BOOK", Material.BOOK, "&e&lDSL Reference",
                List.of("&7Effects, selectors, triggers,", "&7conditions and variables."),
                "&eClick to browse."), open("reference"));
        tile(holder, 34, MenuIcons.tile("CLOCK", Material.PAPER, "&2&lReload Content",
                List.of("&7Rebuild and hot-swap the content", "&7library off-thread.", "",
                        "&8Right-click to validate only (dry run)."),
                "&eClick to reload."), reload());
    }

    private ClickAction open(String name) {
        return click -> registry.get(name).ifPresent(menu -> openMenu(click, menu));
    }

    /** Left-click reloads, right-click dry-runs; the off-thread result is reported back on the player's thread. */
    private ClickAction reload() {
        return click -> {
            Player player = click.player();
            boolean dry = click.right();
            messages.send(player, "menu.console.reload-start", "MODE", dry ? "validating" : "reloading");
            Consumer<ReloadResult> report = result -> Scheduling.onEntity(player, () -> {
                if (result.errorCount() == 0) {
                    messages.send(player, "menu.console.reload-ok",
                            "VERB", result.dryRun() ? "would load" : "loaded",
                            "COUNT", result.abilityCount(), "GEN", result.generation());
                } else {
                    messages.send(player, "menu.console.reload-fail", "N", result.errorCount());
                }
            });
            if (dry) {
                reloader.dryRun(report);
            } else {
                reloader.reload(report);
            }
        };
    }
}
