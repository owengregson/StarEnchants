package tester.suite;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.model.Ability;
import engine.boot.ContentCompiler;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornState;
import item.worn.WornStateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * Armour-set resolution, live (§6.6; ADR-0014): at the set threshold the bonus joins the player's
 * {@link WornState} (active + in the DEFENSE union), below it not — over the full stamp→equip→
 * {@code WornResolver}→{@code SetResolver} path on a real entity. Mojang-mapped only (fake player).
 *
 * <p>The second half is the per-piece surface end to end: a piece MINTS with its own likeness, a member
 * declaring {@code heroic: true} carries real heroic stats out of the minter, and the worn state a heroic
 * gate reads ({@code %victim.heroicpieces%}, {@code %actor.setweapon%}) reports the pieces actually on the
 * body. Only a booted server can prove that chain — it runs through real PDC, real armour slots and the real
 * resolver — and every link in it was inert before the surface existed.
 */
public final class SetSuite implements Harness.Scenario {

    private static final String YETI = """
            display: Yeti
            complete: 2
            armor:
              pieces:
                helmet:     { material: DIAMOND_HELMET }
                chestplate: { material: DIAMOND_CHESTPLATE }
                leggings:   { material: DIAMOND_LEGGINGS }
                boots:      { material: DIAMOND_BOOTS }
            bonuses:
              - on: armor
                trigger: DEFENSE
                effects: [{ POTION: { effect: REGENERATION, level: 1, duration: 80, who: "@Self" } }]
            """;

    /** A set whose pieces each say something of their own — the whole per-piece surface in one file. */
    private static final String WRAITH = """
            display: Wraith
            complete: 2
            armor:
              lore: ["&7WRAITH SET BONUS"]
              enchants:
                PROTECTION: 4
              pieces:
                helmet: { material: DIAMOND_HELMET, name: "&3Wraith Hood", heroic: true }
                chestplate:
                  material: LEATHER_CHESTPLATE
                  name: "&3Wraith Shroud"
                  color: "#808080"
                  heroic: true
                  lore: ["&7&oIt remembers."]
                boots: { material: DIAMOND_BOOTS }
            weapon:
              material: DIAMOND_SWORD
              name: "&3Wraith Edge"
            bonuses:
              - on: armor
                trigger: DEFENSE
                effects: [{ HEAL: { amount: 1, who: "@Self" } }]
              - on: weapon
                trigger: ATTACK
                effects: [{ DAMAGE_MOD: { side: attack, mode: add, amount: 5 } }]
            """;

    private final Plugin plugin;

    public SetSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("set.inactiveBelowThreshold");
        h.expect("set.activatesWhenWorn");
        h.expect("set.pieceMintsWithItsOwnLikeness");
        h.expect("set.mintedHeroicPiecesAreCounted");
        h.expect("set.heldSetWeaponIsFlagged");

        Compiler compiler = ContentCompiler.production();
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat(), Stores.state());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        int defenseId = triggers.idOf("DEFENSE").orElseThrow();

        Library library;
        int bonusId;
        try {
            Path root = Files.createTempDirectory("se-set-suite");
            write(root, "sets/yeti.yml", YETI);
            write(root, "sets/wraith.yml", WRAITH);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail("set.activatesWhenWorn", "yeti set failed to compile: " + library.diagnostics());
                return;
            }
            Ability bonus = library.snapshot().byStableKey("sets/yeti");
            if (bonus == null) {
                h.fail("set.activatesWhenWorn", "set bonus ability did not compile");
                return;
            }
            bonusId = bonus.id();
        } catch (IOException e) {
            h.fail("set.activatesWhenWorn", e);
            return;
        }

        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        WornStateStore worn = new WornStateStore(
                new WornResolver(Stores.equip(), itemViews, triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers())::resolve);

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        codec.write(helmet, new CombatState(Map.of(), List.of(), "sets/yeti", false));
        ItemStack chestplate = new ItemStack(Material.DIAMOND_CHESTPLATE);
        codec.write(chestplate, new CombatState(Map.of(), List.of(), "sets/yeti", false));

        // The per-piece surface goes through the REAL minter, so what is equipped below is what a
        // `/se give set` hands a player — not a hand-stamped stand-in that could agree with a broken mint.
        item.render.LoreRenderer renderer = new item.render.LoreRenderer(
                item.render.LoreRenderer.Config
                        .of(item.render.LoreStyle.DEFAULT, library::displayNameOf)
                        .withSetLore(new item.render.LoreRenderer.SetLore() {
                            @Override public List<String> armor(String setKey) {
                                compile.load.SetDef def = library.setDefOf(setKey);
                                return def != null ? def.armorLore() : List.of();
                            }

                            @Override public List<String> armor(String setKey, String slotToken) {
                                compile.load.SetDef def = library.setDefOf(setKey);
                                return def != null ? def.armorLoreFor(slotToken) : List.of();
                            }

                            @Override public List<String> weapon(String setKey) {
                                compile.load.SetDef def = library.setDefOf(setKey);
                                return def != null ? def.weaponLore() : List.of();
                            }
                        }),
                Stores.state());
        feature.heroic.HeroicStamp stamp = new feature.heroic.HeroicStamp(
                compile.load.HeroicConfig::defaults, feature.heroic.VanillaStats.NONE, codec, renderer);
        feature.apply.ItemEnchanter enchanter = new feature.apply.ItemEnchanter(codec, renderer,
                new compile.load.ContentHolder(library), platform.item.ItemGroups.standard(),
                () -> feature.apply.ItemEnchanter.DEFAULT_BASE_SLOTS,
                () -> feature.apply.ItemEnchanter.DEFAULT_CRYSTAL_SLOTS,
                () -> feature.apply.ItemEnchanter.DEFAULT_MAX_MERGE,
                () -> compile.load.MasterConfig.ReforgesSection.defaults().weaponGroups(),
                platform.lang.Messages.defaults(), item.mint.VanillaEnchants.NONE,
                new java.util.Random(), stamp);

        ItemStack wraithHelmet = enchanter.mintSetPiece("sets/wraith", "helmet").orElse(null);
        ItemStack wraithChest = enchanter.mintSetPiece("sets/wraith", "chestplate").orElse(null);
        ItemStack wraithBoots = enchanter.mintSetPiece("sets/wraith", "boots").orElse(null);
        ItemStack wraithSword = enchanter.mintSetPiece("sets/wraith", "weapon").orElse(null);

        h.guard("set.pieceMintsWithItsOwnLikeness", () -> {
            if (wraithChest == null || wraithBoots == null) {
                throw new IllegalStateException("a declared member failed to mint");
            }
            if (wraithChest.getType() != Material.LEATHER_CHESTPLATE) {
                throw new IllegalStateException("the piece did not mint as its own material: " + wraithChest.getType());
            }
            // The dye is the per-piece knob a set def could not previously carry at all — assert the STATE
            // (that a colour was written), never a rebuilt display string (Bukkit normalises those).
            if (!(wraithChest.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta dyed)
                    || dyed.getColor().asRGB() != 0x808080) {
                throw new IllegalStateException("the authored leather dye did not reach the minted piece");
            }
            // Per-piece lore renders ABOVE the shared block, and a piece with none of its own gets the block
            // alone — the two halves of "refines rather than replaces", read back off real items. Compared on
            // colour-STRIPPED text against this suite's own fixture, so Bukkit's colour normalisation of a
            // rendered line cannot make a correct render look like a failure.
            List<String> chestLore = strippedLore(wraithChest);
            List<String> bootLore = strippedLore(wraithBoots);
            if (chestLore.indexOf("It remembers.") != 0) {
                throw new IllegalStateException("the chestplate's own flavour must be its FIRST line: " + chestLore);
            }
            if (!chestLore.contains("WRAITH SET BONUS")) {
                throw new IllegalStateException("the shared block must still render below it: " + chestLore);
            }
            if (bootLore.contains("It remembers.") || !bootLore.contains("WRAITH SET BONUS")) {
                throw new IllegalStateException("a slot with no lore of its own gets exactly the shared block: "
                        + bootLore);
            }
        });

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player wearer;
                try {
                    wearer = FakePlayers.spawn(world, "se_set_wearer");
                } catch (Throwable t) {
                    h.fail("set.activatesWhenWorn", "fake-player spawn: " + t);
                    return;
                }
                Scheduling.onEntity(wearer, () -> {
                    // One piece — below the 2-piece threshold → the set must be inactive.
                    wearer.getInventory().setHelmet(helmet);
                    wearer.getInventory().setChestplate(null);
                    worn.refresh(wearer, library.snapshot());
                    h.guard("set.inactiveBelowThreshold", () -> {
                        WornState ws = worn.get(wearer.getUniqueId());
                        if (ws == null || ws.isSetActive(bonusId)) {
                            throw new IllegalStateException("set active with only 1 of 2 pieces");
                        }
                    });

                    // Second piece — threshold met → active + in the DEFENSE union.
                    wearer.getInventory().setChestplate(chestplate);
                    worn.refresh(wearer, library.snapshot());
                    h.guard("set.activatesWhenWorn", () -> {
                        WornState ws = worn.get(wearer.getUniqueId());
                        if (ws == null || !ws.isSetActive(bonusId)) {
                            throw new IllegalStateException("set not active with both pieces worn");
                        }
                        if (!contains(ws.byTrigger(defenseId), bonusId)) {
                            throw new IllegalStateException("active set bonus is not in the DEFENSE trigger union");
                        }
                    });

                    // ── the per-piece chain, on the body ───────────────────────────────────────────
                    // Two MINTED heroic pieces plus one that is not: the count has to be 2, which is the
                    // number %victim.heroicpieces% reports. Nothing could mint heroic before this, so the
                    // fact read 0 on every server and hero-killer's gate was inert by construction.
                    wearer.getInventory().setHelmet(wraithHelmet);
                    wearer.getInventory().setChestplate(wraithChest);
                    wearer.getInventory().setBoots(wraithBoots);
                    wearer.getInventory().setLeggings(null);
                    wearer.getInventory().setItemInMainHand(null);
                    worn.refresh(wearer, library.snapshot());
                    h.guard("set.mintedHeroicPiecesAreCounted", () -> {
                        WornState ws = worn.get(wearer.getUniqueId());
                        if (ws == null) {
                            throw new IllegalStateException("no worn state resolved for the wearer");
                        }
                        if (ws.heroicPieces() != 2) {
                            throw new IllegalStateException("expected 2 minted heroic pieces worn, got "
                                    + ws.heroicPieces());
                        }
                        if (ws.heroic().isZero()) {
                            throw new IllegalStateException("the minted heroic stats did not reach the worn fold");
                        }
                        if (ws.holdsSetWeapon()) {
                            throw new IllegalStateException("no set weapon is held, so the flag must be false");
                        }
                    });

                    // The set weapon in the main hand, with the set complete → the flag `on: weapon` gates on.
                    wearer.getInventory().setItemInMainHand(wraithSword);
                    worn.refresh(wearer, library.snapshot());
                    h.guard("set.heldSetWeaponIsFlagged", () -> {
                        WornState ws = worn.get(wearer.getUniqueId());
                        if (ws == null || !ws.holdsSetWeapon()) {
                            throw new IllegalStateException("a completed set's weapon in hand must raise the flag");
                        }
                    });
                    FakePlayers.despawn(wearer);
                });
            });
        });
    }

    /** A rendered item's lore with every colour code removed — the stable half of a live lore read. */
    @SuppressWarnings("deprecation") // getLore(): deprecated-not-removed across the whole range.
    private static List<String> strippedLore(ItemStack stack) {
        org.bukkit.inventory.meta.ItemMeta meta = stack == null ? null : stack.getItemMeta();
        List<String> lore = meta == null ? null : meta.getLore();
        if (lore == null) {
            return List.of();
        }
        List<String> out = new java.util.ArrayList<>(lore.size());
        for (String line : lore) {
            out.add(line.replaceAll("(?i)§[0-9A-FK-OR]", ""));
        }
        return out;
    }

    private static boolean contains(int[] ids, int id) {
        for (int value : ids) {
            if (value == id) {
                return true;
            }
        }
        return false;
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
