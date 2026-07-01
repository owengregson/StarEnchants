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

/** Item-application path, live (§4.2). Item-only (no fake player), so it runs the spigot-mapped floor too. */
public final class ApplySuite implements Harness.Scenario {

    private static final String KEEN = """
            display: Keen
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ MODIFY_HEALTH: { amount: 1 } }] }
              2: { chance: 100, effects: [{ MODIFY_HEALTH: { amount: 2 } }] }
              3: { chance: 100, effects: [{ MODIFY_HEALTH: { amount: 3 } }] }
            """;

    private static final String SPARK = """
            display: Spark
            applies-to: [WEAPON]
            trigger: ATTACK
            chance: 100
            effects: [{ MODIFY_HEALTH: { amount: 1 } }]
            """;

    private static final String GLINT = """
            display: Glint
            applies-to: [WEAPON]
            trigger: ATTACK
            chance: 100
            effects: [{ MODIFY_HEALTH: { amount: 1 } }]
            """;

    private static final String BASE = """
            display: Base
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ MODIFY_HEALTH: { amount: 1 } }] }
            """;

    // §G removes-required: requires Base and strips it on apply (net-zero slot).
    private static final String SUPERIOR = """
            display: Superior
            applies-to: [SWORD]
            trigger: ATTACK
            requires: ["enchants/base"]
            removes-required: true
            levels:
              1: { chance: 100, effects: [{ MODIFY_HEALTH: { amount: 2 } }] }
            """;

    // §6.6 set: 4 armour members complete the bonus; the weapon member adds its own while the set is held.
    private static final String TITAN = """
            display: "&bTitan"
            complete: 4
            armor:
              lore: ["&7Titan Set"]
              pieces:
                helmet:     { material: DIAMOND_HELMET,     name: "&bTitan Helm" }
                chestplate: { material: DIAMOND_CHESTPLATE, name: "&bTitan Chestplate" }
                leggings:   { material: DIAMOND_LEGGINGS,   name: "&bTitan Leggings" }
                boots:      { material: DIAMOND_BOOTS,      name: "&bTitan Boots" }
            weapon:
              material: DIAMOND_SWORD
              name: "&bTitan Blade"
              lore: ["&7Titan Weapon"]
            bonuses:
              - on: armor
                trigger: DEFENSE
                effects: [{ MODIFY_HEALTH: { amount: 1 } }]
              - on: weapon
                trigger: ATTACK
                effects: [{ MODIFY_HEALTH: { amount: 1 } }]
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
        h.expect("item.apply.extractCrystalTopmost");
        h.expect("item.apply.removesRequired");
        h.expect("item.apply.removesRequired.netZeroSlots");
        h.expect("item.apply.mintSet");

        ItemEnchanter enchanter;
        ItemEnchanter capped; // 1-slot, for the net-zero-slot case
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat());
        try {
            Path root = Files.createTempDirectory("se-apply-suite");
            write(root, "enchants/keen.yml", KEEN);
            write(root, "crystals/spark.yml", SPARK);
            write(root, "crystals/glint.yml", GLINT);
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
            capped = new ItemEnchanter(codec, lore, holder, ItemGroups.standard(), 1);
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

        h.guard("item.apply.extractCrystalTopmost", () -> {
            // A multi-crystal entry (spark+glint): the extractor pops the TOPMOST single (glint), leaving the
            // rest (spark) on gear (ADR-0034 §4) — proved against the real PDC round-trip.
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            enchanter.applyCrystalEntry(sword, List.of("crystals/spark", "crystals/glint"), true);
            if (!codec.read(sword).crystals().contains("crystals/spark+crystals/glint")) {
                throw new IllegalStateException("setup: multi-crystal entry not recorded: " + codec.read(sword).crystals());
            }
            var extracted = enchanter.extractCrystal(sword);
            if (!extracted.ok() || !"crystals/glint".equals(extracted.poppedEntry())) {
                throw new IllegalStateException("extract did not pop the topmost component: " + extracted.poppedEntry());
            }
            if (!codec.read(sword).crystals().equals(List.of("crystals/spark"))) {
                throw new IllegalStateException("the remainder should be the single spark, was: " + codec.read(sword).crystals());
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
            // Upgrade frees base as it lands, so it nets zero slots and fits at capacity.
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
            var minted = enchanter.mintSetPiece("sets/titan", "helmet");
            if (minted.isEmpty()) {
                throw new IllegalStateException("a set helmet member should mint");
            }
            ItemStack piece = minted.get();
            if (!"sets/titan".equals(codec.read(piece).setKey())) {
                throw new IllegalStateException("the minted armour member did not carry the set key");
            }
            if (!piece.getType().name().endsWith("HELMET")) {
                throw new IllegalStateException("the minted member is not a helmet: " + piece.getType());
            }
            // Weapon carries setWeaponKey not setKey, so it never counts toward completion.
            var weapon = enchanter.mintSetPiece("sets/titan", "weapon");
            if (weapon.isEmpty()) {
                throw new IllegalStateException("the set weapon member should mint");
            }
            CombatState weaponState = codec.read(weapon.get());
            if (!"sets/titan".equals(weaponState.setWeaponKey()) || weaponState.setKey() != null) {
                throw new IllegalStateException("the weapon must carry setWeaponKey and no setKey: " + weaponState);
            }
            if (enchanter.mintSetPiece("sets/titan", "trident").isPresent()) {
                throw new IllegalStateException("an undeclared member must fail to mint");
            }
            if (enchanter.mintSetPiece("sets/ghost", "helmet").isPresent()) {
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
