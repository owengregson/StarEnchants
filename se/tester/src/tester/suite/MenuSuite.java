package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.EnchantDef;
import engine.boot.ContentCompiler;
import feature.apply.ItemEnchanter;
import feature.carrier.CarrierService;
import feature.menu.AdminBrowserMenu;
import feature.menu.EnchantMenu;
import feature.menu.MenuHolder;
import feature.menu.MenuListener;
import item.codec.CarrierCodec;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import platform.caps.Capabilities;
import platform.item.ItemGroups;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * Enchant-apply GUI, live (§7): render + click routing + apply through the real {@link MenuListener}.
 * Mojang-mapped only (fake player + a server-side open inventory).
 */
public final class MenuSuite implements Harness.Scenario {

    // Plain display (no own colour code) + an epic tier, so the rendered icon name shows the TIER colour; a
    // multi-line description proves the icon splits it into separate lore lines.
    private static final String KEEN = """
            display: "Keen"
            description:
              - "&7Strikes fast and"
              - "&7cuts deep."
            applies-to: [SWORD]
            trigger: ATTACK
            tier: epic
            levels:
              1: { chance: 100, effects: [{ MODIFY_HEALTH: { amount: 1 } }] }
            """;

    private static final String TIERS = """
            default-tier: common
            tiers:
              common: { color: "&7", weight: 10, glint: false }
              epic:   { color: "&e", weight: 40, glint: true  }
            """;

    private final Plugin plugin;

    public MenuSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("deprecation") // getDisplayName()/getLore(): the floor-stable String item-meta API the suite asserts against (Component API is Adventure-only).
    public void accept(Harness h) {
        h.expect("menu.clickAppliesEnchant");
        h.expect("menu.iconShowsTierColorAndDescription");
        h.expect("menu.adminDrillsDownTierEnchantLevel");

        CombatCodec codec = new CombatCodec(ItemKeys.of().combat());
        EnchantMenu menu;
        AdminBrowserMenu adminMenu;
        EnchantDef keenDef;
        try {
            Path root = Files.createTempDirectory("se-menu-suite");
            write(root, "enchants/keen.yml", KEEN);
            write(root, "tiers.yml", TIERS);
            Library library = LibraryLoader.load(root, ContentCompiler.production(), 0);
            if (library.hasErrors()) {
                h.fail("menu.clickAppliesEnchant", "content failed to compile: " + library.diagnostics());
                return;
            }
            ContentHolder holder = new ContentHolder(library);
            LoreRenderer lore = new LoreRenderer(LoreStyle.DEFAULT, key -> holder.library().displayNameOf(key));
            ItemEnchanter enchanter = new ItemEnchanter(codec, lore, holder, ItemGroups.standard());
            // caps drives the cross-version title cap
            menu = new EnchantMenu(holder, enchanter, player -> { }, Capabilities.probe(plugin.getServer()));
            // Admin browser (§K) — the 3-level tier → enchant → level drill-down. Use an EE-style book name
            // (bold + underline) so the guard can prove the menu icon name mirrors the book's styling.
            CarrierCodec carrierCodec = new CarrierCodec(ItemKeys.of().carrier(), ItemKeys.of().guarded());
            compile.load.EnchantBookConfig underlined = new compile.load.EnchantBookConfig(
                    "ENCHANTED_BOOK", "{TIER_COLOR}&l&n{ENCHANT} {LEVEL}",
                    compile.load.EnchantBookConfig.defaults().lore(), java.util.List.of(), false, 30);
            CarrierService carriers = new CarrierService(
                    carrierCodec, enchanter, holder, new Random(1), () -> underlined);
            adminMenu = new AdminBrowserMenu(holder, carriers, Capabilities.probe(plugin.getServer()));
            keenDef = holder.library().catalog().stream()
                    .filter(d -> d.key().equals("enchants/keen")).findFirst().orElseThrow();
        } catch (IOException e) {
            h.fail("menu.clickAppliesEnchant", e.toString());
            return;
        }

        MenuListener listener = new MenuListener();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player player;
                try {
                    player = FakePlayers.spawn(world, "se_menu_p");
                } catch (Throwable t) {
                    h.fail("menu.clickAppliesEnchant", "spawn: " + t);
                    HandlerList.unregisterAll(listener);
                    return;
                }
                Scheduling.onEntity(player, () -> {
                    player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
                    MenuHolder menuHolder = new MenuHolder(menu);
                    menu.render(menuHolder);
                    h.guard("menu.iconShowsTierColorAndDescription", () -> {
                        ItemStack icon = menuHolder.getInventory().getItem(0);
                        if (icon == null) {
                            throw new IllegalStateException("no enchant icon rendered at slot 0");
                        }
                        ItemMeta meta = icon.getItemMeta();
                        String name = meta.getDisplayName();
                        if (!name.startsWith("§e")) { // §e — the epic tier colour, applied to the name
                            throw new IllegalStateException("icon name should carry the epic tier colour (§e); got: " + name);
                        }
                        java.util.List<String> iconLore = meta.getLore();
                        long descLines = iconLore == null ? 0 : iconLore.stream()
                                .filter(l -> l.contains("Strikes fast") || l.contains("cuts deep")).count();
                        if (descLines != 2) { // both description lines, each as its OWN lore entry (newlines split)
                            throw new IllegalStateException(
                                    "the two description lines should render as separate lore entries; lore=" + iconLore);
                        }
                    });
                    h.guard("menu.adminDrillsDownTierEnchantLevel", () -> {
                        // Top level: tier groups (KEEN is epic, so the epic group shows), not a flat enchant list.
                        MenuHolder tiers = new MenuHolder(adminMenu);
                        adminMenu.render(tiers);
                        ItemStack tierIcon = tiers.getInventory().getItem(0);
                        if (tierIcon == null || !tierIcon.getItemMeta().getDisplayName().contains("Epic")) {
                            throw new IllegalStateException("admin index should show tier groups; slot0="
                                    + (tierIcon == null ? "null" : tierIcon.getItemMeta().getDisplayName()));
                        }
                        // Drill into the epic tier → its enchants (KEEN).
                        MenuHolder tierEnchants = new MenuHolder(adminMenu);
                        tierEnchants.setView("enchants");
                        tierEnchants.setSelection("epic");
                        adminMenu.render(tierEnchants);
                        ItemStack enchIcon = tierEnchants.getInventory().getItem(0);
                        String enchName = enchIcon == null ? "null" : enchIcon.getItemMeta().getDisplayName();
                        if (enchIcon == null || !enchName.contains("Keen")) {
                            throw new IllegalStateException("tier view should list the tier's enchants; slot0=" + enchName);
                        }
                        // The icon name must mirror the book's name config — here bold (§l) + underline (§n).
                        if (!enchName.contains("§l") || !enchName.contains("§n")) {
                            throw new IllegalStateException(
                                    "enchant icon name should carry the book's styling (§l§n); got: " + enchName);
                        }
                        // Drill into KEEN → one icon per level (KEEN declares a single level).
                        MenuHolder enchantLevels = new MenuHolder(adminMenu);
                        enchantLevels.setView("levels");
                        enchantLevels.setPayload(keenDef);
                        adminMenu.render(enchantLevels);
                        ItemStack levelIcon = enchantLevels.getInventory().getItem(0);
                        java.util.List<String> levelLore = levelIcon == null ? null : levelIcon.getItemMeta().getLore();
                        if (levelLore == null || levelLore.stream().noneMatch(l -> l.contains("guaranteed level"))) {
                            throw new IllegalStateException("enchant view should show per-level books; slot0 lore="
                                    + levelLore);
                        }
                    });
                    InventoryView view = player.openInventory(menuHolder.getInventory());
                    InventoryClickEvent click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                            0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
                    plugin.getServer().getPluginManager().callEvent(click);
                    h.guard("menu.clickAppliesEnchant", () -> {
                        if (!click.isCancelled()) {
                            throw new IllegalStateException("menu click was not cancelled — items could be moved");
                        }
                        CombatState state = codec.read(player.getInventory().getItemInMainHand());
                        if (!state.enchants().containsKey("enchants/keen")) {
                            throw new IllegalStateException("held item did not gain enchants/keen after the click; "
                                    + "enchants=" + state.enchants());
                        }
                    });
                    player.closeInventory();
                    HandlerList.unregisterAll(listener);
                    FakePlayers.despawn(player);
                });
            });
        });
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
