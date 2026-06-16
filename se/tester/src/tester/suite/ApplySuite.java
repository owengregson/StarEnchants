package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import feature.apply.ItemEnchanter;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import platform.item.ItemGroups;
import tester.harness.Harness;

/**
 * The item-application path, proven live (docs/architecture.md §4.2): the {@link ItemEnchanter}
 * validates against compiled content, writes the {@link CombatState} into a real item's PDC, and
 * re-renders the lore — and rejects an ineligible material without mutating. Item-only (no fake
 * player), so it runs across the WHOLE range including the spigot-mapped floor. This closes the loop
 * an operator uses: {@code /se enchant} on a held item.
 *
 * <ul>
 *   <li>{@code item.apply.enchant} — apply an enchant: PDC carries it and the lore renders the name.</li>
 *   <li>{@code item.apply.crystal} — apply a crystal: it lands in the (stacking) crystal list.</li>
 *   <li>{@code item.apply.appliesTo} — an enchant rejects an ineligible material, leaving the item untouched.</li>
 * </ul>
 */
public final class ApplySuite implements Harness.Scenario {

    private static final String KEEN = """
            display: Keen
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["HEAL:1"] }
              2: { chance: 100, effects: ["HEAL:2"] }
              3: { chance: 100, effects: ["HEAL:3"] }
            """;

    private static final String SPARK = """
            display: Spark
            applies-to: [WEAPON]
            trigger: ATTACK
            chance: 100
            effects: ["HEAL:1"]
            """;

    private final Plugin plugin;

    public ApplySuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("item.apply.enchant");
        h.expect("item.apply.crystal");
        h.expect("item.apply.appliesTo");

        ItemEnchanter enchanter;
        CombatCodec codec = new CombatCodec(ItemKeys.of(plugin).combat());
        try {
            Path root = Files.createTempDirectory("se-apply-suite");
            write(root, "enchants/keen.yml", KEEN);
            write(root, "crystals/spark.yml", SPARK);
            Compiler compiler = ContentCompiler.production();
            Library library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail("item.apply.enchant", "content failed to compile: " + library.diagnostics());
                return;
            }
            ContentHolder holder = new ContentHolder(library);
            LoreRenderer lore = new LoreRenderer(LoreStyle.DEFAULT, key -> holder.library().displayNameOf(key));
            enchanter = new ItemEnchanter(codec, lore, holder, ItemGroups.standard());
        } catch (IOException e) {
            h.fail("item.apply.enchant", e.toString());
            return;
        }

        h.guard("item.apply.enchant", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            if (!enchanter.applyEnchant(sword, "enchants/keen", 2).ok()) {
                throw new IllegalStateException("apply of a valid enchant was rejected");
            }
            Integer level = codec.read(sword).enchants().get("enchants/keen");
            if (level == null || level != 2) {
                throw new IllegalStateException("PDC did not record the enchant at level 2: " + level);
            }
            if (!renderedLoreMentions(sword, "Keen")) {
                throw new IllegalStateException("lore was not rendered with the enchant name");
            }
        });

        h.guard("item.apply.crystal", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            if (!enchanter.applyCrystal(sword, "crystals/spark").ok()) {
                throw new IllegalStateException("apply of a valid crystal was rejected");
            }
            if (!codec.read(sword).crystals().contains("crystals/spark")) {
                throw new IllegalStateException("PDC did not record the crystal");
            }
        });

        h.guard("item.apply.appliesTo", () -> {
            ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
            if (enchanter.applyEnchant(helmet, "enchants/keen", 1).ok()) {
                throw new IllegalStateException("a SWORD enchant was wrongly applied to a helmet");
            }
            if (!codec.read(helmet).isEmpty()) {
                throw new IllegalStateException("a rejected apply still mutated the item");
            }
        });
    }

    @SuppressWarnings("deprecation") // getLore(): deprecated-not-removed across the whole range.
    private static boolean renderedLoreMentions(ItemStack stack, String fragment) {
        ItemMeta meta = stack.getItemMeta();
        List<String> lore = meta == null ? null : meta.getLore();
        if (lore == null) {
            return false;
        }
        return lore.stream().anyMatch(line -> line.contains(fragment));
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
