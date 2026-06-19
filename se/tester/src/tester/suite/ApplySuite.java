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
 *   <li>{@code item.apply.removeEnchant} — the §J inverse: a removed enchant leaves the PDC + lore (and frees
 *       its slot), and removing one the item lacks is a clean no-op.</li>
 * </ul>
 */
public final class ApplySuite implements Harness.Scenario {

    private static final String KEEN = """
            display: Keen
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["MODIFY_HEALTH:1"] }
              2: { chance: 100, effects: ["MODIFY_HEALTH:2"] }
              3: { chance: 100, effects: ["MODIFY_HEALTH:3"] }
            """;

    private static final String SPARK = """
            display: Spark
            applies-to: [WEAPON]
            trigger: ATTACK
            chance: 100
            effects: ["MODIFY_HEALTH:1"]
            """;

    private static final String BASE = """
            display: Base
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["MODIFY_HEALTH:1"] }
            """;

    // §G removes-required: the superior enchant requires Base and strips it on a successful apply (net-zero slot).
    private static final String SUPERIOR = """
            display: Superior
            applies-to: [SWORD]
            trigger: ATTACK
            requires: ["enchants/base"]
            removes-required: true
            levels:
              1: { chance: 100, effects: ["MODIFY_HEALTH:2"] }
            """;

    // §J set-piece: an armour-only set; a minted piece carries sets/titan and the bonus fires once 4 are worn.
    private static final String TITAN = """
            display: "&bTitan"
            pieces: 4
            trigger: DEFENSE
            applies-to: [HELMET, CHESTPLATE, LEGGINGS, BOOTS]
            effects: ["MODIFY_HEALTH:1"]
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
        h.expect("item.apply.removeEnchant");
        h.expect("item.apply.extractCrystal");
        h.expect("item.apply.removesRequired");
        h.expect("item.apply.removesRequired.netZeroSlots");
        h.expect("item.apply.mintSet");

        ItemEnchanter enchanter;
        ItemEnchanter capped; // a 1-slot enchanter for the net-zero-slot case
        CombatCodec codec = new CombatCodec(ItemKeys.of(plugin).combat());
        try {
            Path root = Files.createTempDirectory("se-apply-suite");
            write(root, "enchants/keen.yml", KEEN);
            write(root, "crystals/spark.yml", SPARK);
            write(root, "enchants/base.yml", BASE);
            write(root, "enchants/superior.yml", SUPERIOR);
            write(root, "sets/titan.yml", TITAN);
            Compiler compiler = ContentCompiler.production();
            Library library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail("item.apply.enchant", "content failed to compile: " + library.diagnostics());
                return;
            }
            ContentHolder holder = new ContentHolder(library);
            LoreRenderer lore = new LoreRenderer(LoreStyle.DEFAULT, key -> holder.library().displayNameOf(key));
            enchanter = new ItemEnchanter(codec, lore, holder, ItemGroups.standard());
            capped = new ItemEnchanter(codec, lore, holder, ItemGroups.standard(), 1); // base = 1 enchant slot
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

        h.guard("item.apply.removeEnchant", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            enchanter.applyEnchant(sword, "enchants/keen", 2);
            if (!enchanter.removeEnchant(sword, "enchants/keen").ok()) {
                throw new IllegalStateException("removeEnchant rejected an enchant the item carries");
            }
            if (codec.read(sword).enchants().containsKey("enchants/keen")) {
                throw new IllegalStateException("PDC still carries the removed enchant");
            }
            if (renderedLoreMentions(sword, "Keen")) {
                throw new IllegalStateException("lore still renders the removed enchant");
            }
            if (enchanter.removeEnchant(sword, "enchants/keen").ok()) {
                throw new IllegalStateException("removing an absent enchant should be a clean no-op fail");
            }
        });

        h.guard("item.apply.extractCrystal", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            enchanter.applyCrystal(sword, "crystals/spark");
            if (!codec.read(sword).crystals().contains("crystals/spark")) {
                throw new IllegalStateException("setup: crystal was not applied");
            }
            var extracted = enchanter.extractCrystal(sword);
            if (!extracted.ok() || !"crystals/spark".equals(extracted.poppedEntry())) {
                throw new IllegalStateException("extractCrystal did not return the popped entry: " + extracted.poppedEntry());
            }
            if (codec.read(sword).crystals().contains("crystals/spark")) {
                throw new IllegalStateException("PDC still carries the extracted crystal");
            }
            if (enchanter.extractCrystal(sword).ok()) {
                throw new IllegalStateException("extracting from a crystal-less item should be a clean no-op fail");
            }
        });

        h.guard("item.apply.removesRequired", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            if (!enchanter.applyEnchant(sword, "enchants/base", 1).ok()) {
                throw new IllegalStateException("setup: base did not apply");
            }
            if (!enchanter.applyEnchant(sword, "enchants/superior", 1).ok()) {
                throw new IllegalStateException("the removes-required upgrade did not apply over its prerequisite");
            }
            var enchants = codec.read(sword).enchants();
            if (!enchants.containsKey("enchants/superior")) {
                throw new IllegalStateException("the upgrade was not recorded");
            }
            if (enchants.containsKey("enchants/base")) {
                throw new IllegalStateException("removes-required did not strip the prerequisite");
            }
        });

        h.guard("item.apply.removesRequired.netZeroSlots", () -> {
            // A 1-slot item: base fills it; the removes-required upgrade frees base, so it nets zero and fits.
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            if (!capped.applyEnchant(sword, "enchants/base", 1).ok()) {
                throw new IllegalStateException("setup: base did not fill the single slot");
            }
            if (!capped.applyEnchant(sword, "enchants/superior", 1).ok()) {
                throw new IllegalStateException("a removes-required upgrade must fit at capacity (net-zero slot)");
            }
            var enchants = codec.read(sword).enchants();
            if (enchants.size() != 1 || !enchants.containsKey("enchants/superior")
                    || enchants.containsKey("enchants/base")) {
                throw new IllegalStateException("expected exactly the upgrade after a net-zero swap: " + enchants.keySet());
            }
        });

        h.guard("item.apply.mintSet", () -> {
            var minted = enchanter.mintSetPiece("sets/titan", "HELMET");
            if (minted.isEmpty()) {
                throw new IllegalStateException("a set HELMET piece should mint");
            }
            ItemStack piece = minted.get();
            if (!"sets/titan".equals(codec.read(piece).setKey())) {
                throw new IllegalStateException("the minted piece did not carry the set key");
            }
            if (!piece.getType().name().endsWith("HELMET")) {
                throw new IllegalStateException("the minted piece is not a helmet: " + piece.getType());
            }
            if (enchanter.mintSetPiece("sets/titan", "SWORD").isPresent()) {
                throw new IllegalStateException("a weapon piece must fail on an armour-only set");
            }
            if (enchanter.mintSetPiece("sets/ghost", "HELMET").isPresent()) {
                throw new IllegalStateException("an unknown set must fail to mint");
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
