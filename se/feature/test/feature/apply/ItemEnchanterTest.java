package feature.apply;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.MapSpecRegistry;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import item.codec.CombatCodec;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import platform.item.ItemGroups;
import schema.spec.D;
import schema.spec.ParamSpec;

/**
 * The pure validation of {@link ItemEnchanter#checkEnchant} / {@link ItemEnchanter#checkCrystal} —
 * verified over a real compiled {@link Library} with no server (the apply mutation itself, which
 * touches {@code ItemStack}, is matrix-verified live). {@link Material} is a plain enum on the floor.
 */
class ItemEnchanterTest {

    private static Compiler compiler() {
        return Compiler.of(MapSpecRegistry.of(ParamSpec.of("HEAL").param("amount", D.DOUBLE.min(0)).build()));
    }

    private static ItemEnchanter over(Library lib) {
        ContentHolder holder = new ContentHolder(lib);
        CombatCodec codec = new CombatCodec(new NamespacedKey("starenchants", "combat"));
        LoreRenderer lore = new LoreRenderer(LoreStyle.DEFAULT, key -> holder.library().displayNameOf(key));
        return new ItemEnchanter(codec, lore, holder, ItemGroups.standard());
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    @Test
    void validatesEnchantKeyLevelAndAppliesTo(@TempDir Path root) throws IOException {
        write(root, "enchants/blaze.yml", """
            display: "&cBlaze"
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["HEAL:1"] }
              2: { chance: 100, effects: ["HEAL:2"] }
            """);
        ItemEnchanter e = over(LibraryLoader.load(root, compiler(), 1));

        assertTrue(e.checkEnchant(Material.DIAMOND_SWORD, "enchants/blaze", 1).ok());
        assertTrue(e.checkEnchant(Material.DIAMOND_SWORD, "enchants/blaze", 2).ok());
        assertFalse(e.checkEnchant(Material.DIAMOND_SWORD, "enchants/blaze", 3).ok(), "level above max");
        assertFalse(e.checkEnchant(Material.DIAMOND_SWORD, "enchants/blaze", 0).ok(), "level below 1");
        assertFalse(e.checkEnchant(Material.DIAMOND_HELMET, "enchants/blaze", 1).ok(), "applies-to SWORD only");
        assertFalse(e.checkEnchant(Material.DIAMOND_SWORD, "enchants/ghost", 1).ok(), "unknown enchant");
    }

    @Test
    void enchantSlotsCapNewEnchantsButAllowReapply(@TempDir Path root) throws IOException {
        ItemEnchanter e = over(LibraryLoader.load(root, compiler(), 1)); // catalog is irrelevant to slot math

        // Six is the v1 capacity: an item already holding six distinct enchants has no free slot.
        java.util.Map<String, Integer> six = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            six.put("enchants/e" + i, 1);
        }
        item.codec.CombatState full = new item.codec.CombatState(six, java.util.List.of());
        assertFalse(e.checkSlots(full, "enchants/new").ok(), "no free slot for a seventh enchant");
        assertTrue(e.checkSlots(full, "enchants/e0").ok(), "re-applying an existing enchant needs no new slot");

        item.codec.CombatState room = new item.codec.CombatState(java.util.Map.of("enchants/e0", 1), java.util.List.of());
        assertTrue(e.checkSlots(room, "enchants/new").ok(), "a near-empty item has free slots");
    }

    @Test
    void validatesCrystalKeyAndAppliesTo(@TempDir Path root) throws IOException {
        write(root, "crystals/jolt.yml", """
            display: "&bJolt"
            applies-to: [WEAPON]
            trigger: ATTACK
            chance: 100
            effects: ["HEAL:1"]
            """);
        ItemEnchanter e = over(LibraryLoader.load(root, compiler(), 1));

        assertTrue(e.checkCrystal(Material.DIAMOND_SWORD, "crystals/jolt").ok());
        assertTrue(e.checkCrystal(Material.IRON_AXE, "crystals/jolt").ok(), "axe is a weapon");
        assertFalse(e.checkCrystal(Material.DIAMOND_HELMET, "crystals/jolt").ok(), "applies-to WEAPON only");
        assertFalse(e.checkCrystal(Material.DIAMOND_SWORD, "crystals/missing").ok(), "unknown crystal");
    }
}
