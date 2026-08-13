package feature.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import compile.load.Lang;
import compile.load.Library;
import compile.load.ScrollsConfig;
import compile.load.SoundCue;
import compile.load.TierRegistry;
import engine.stores.BookRateStore;
import feature.apply.FakeItemStateStore;
import feature.carrier.CarrierService;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.codec.ScrollCodec;
import item.mint.ItemFactory;
import item.mint.VanillaEnchants;
import item.render.LoreRenderer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import platform.item.ItemGroups;
import platform.lang.Messages;

/** Service-level Heroic Black Scroll contract: range roll, state/lore mutation, and safe no-eligible refusal. */
class ScrollServiceHeroicBlackTest {

    @Test
    void mintUsesConfiguredDriedKelpAndForcesHiddenGlintWhileRemainingAGuardedScroll() {
        FakeItemStateStore store = new FakeItemStateStore();
        ItemKeys keys = ItemKeys.of();
        ScrollCodec scrolls = new ScrollCodec(keys.scroll(), keys.scrollConvert(), keys.scrollHeroicMin(),
                keys.scrollHeroicMax(), store);
        ItemStack icon = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        Enchantment unbreaking = mock(Enchantment.class);
        when(icon.clone()).thenReturn(icon);
        when(icon.getItemMeta()).thenReturn(meta);
        ItemFactory.customItemResolver(token -> "DRIED_KELP".equals(token) ? icon : null);

        try {
            ScrollService service = new ScrollService(scrolls, new CombatCodec(keys.combat(), store),
                    mock(LoreRenderer.class), mock(CarrierService.class), mock(ContentHolder.class),
                    ScrollsConfig::defaults, new Random(1), Messages.defaults(), null, ItemGroups.standard(),
                    new BookRateStore(), new VanillaEnchants(name -> unbreaking));

            ItemStack minted = service.mintHeroicBlack();

            assertSame(icon, minted, "the configured DRIED_KELP token is the minted likeness");
            assertTrue(service.isScroll(minted), "the plugin-item guard recognizes it and blocks vanilla eating");
            assertEquals(10, scrolls.heroicRangeOf(minted, 0, 0).min());
            assertEquals(35, scrolls.heroicRangeOf(minted, 0, 0).max());
            verify(icon).addUnsafeEnchantment(unbreaking, 1);
            verify(meta).addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } finally {
            ItemFactory.customItemResolver(null);
        }
    }

    @Test
    void eachScrollGlintFollowsItsOwnPackFlag() {
        FakeItemStateStore store = new FakeItemStateStore();
        ItemKeys keys = ItemKeys.of();
        ScrollCodec scrolls = new ScrollCodec(keys.scroll(), keys.scrollConvert(), keys.scrollHeroicMin(),
                keys.scrollHeroicMax(), store);
        ItemStack normalIcon = mock(ItemStack.class);
        ItemStack higherIcon = mock(ItemStack.class);
        ItemMeta normalMeta = mock(ItemMeta.class);
        Enchantment unbreaking = mock(Enchantment.class);
        when(normalIcon.clone()).thenReturn(normalIcon);
        when(higherIcon.clone()).thenReturn(higherIcon);
        when(normalIcon.getItemMeta()).thenReturn(normalMeta);
        ItemFactory.customItemResolver(token -> switch (token) {
            case "COAL" -> normalIcon;
            case "DRIED_KELP" -> higherIcon;
            default -> null;
        });

        ScrollsConfig defaults = ScrollsConfig.defaults();
        ScrollsConfig.Black normal = new ScrollsConfig.Black(
                "COAL", true, "Black", List.of(), 10, 20,
                List.of("WEAPON"), List.of("common"), null, List.of());
        ScrollsConfig.HeroicBlack higher = new ScrollsConfig.HeroicBlack(
                "DRIED_KELP", false, "Ascended", List.of(), 10, 35,
                List.of("WEAPON"), List.of("mythic"), null, List.of());
        ScrollsConfig config = new ScrollsConfig(normal, higher, defaults.randomizer(), defaults.transmog(),
                defaults.holy(), defaults.nametag(), defaults.godly());

        try {
            ScrollService service = new ScrollService(scrolls, new CombatCodec(keys.combat(), store),
                    mock(LoreRenderer.class), mock(CarrierService.class), mock(ContentHolder.class),
                    () -> config, new Random(1), Messages.defaults(), null, ItemGroups.standard(),
                    new BookRateStore(), new VanillaEnchants(name -> unbreaking));

            assertSame(normalIcon, service.mintBlack());
            assertSame(higherIcon, service.mintHeroicBlack());
            verify(normalIcon).addUnsafeEnchantment(unbreaking, 1);
            verify(normalMeta).addItemFlags(ItemFlag.HIDE_ENCHANTS);
            verify(higherIcon, never()).addUnsafeEnchantment(unbreaking, 1);
        } finally {
            ItemFactory.customItemResolver(null);
        }
    }

    @Test
    void selectionUsesTierPolicyAndConfiguredNameWhilePreservingRangeLevelAndCodecBehavior() {
        FakeItemStateStore store = new FakeItemStateStore();
        ItemKeys keys = ItemKeys.of();
        ScrollCodec scrolls = new ScrollCodec(keys.scroll(), keys.scrollConvert(), keys.scrollHeroicMin(),
                keys.scrollHeroicMax(), store);
        CombatCodec combat = new CombatCodec(keys.combat(), store);
        LoreRenderer lore = mock(LoreRenderer.class);
        CarrierService carriers = mock(CarrierService.class);
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        when(carriers.capBookSuccess(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        when(carriers.mintBook(anyString(), anyInt(), anyInt())).thenReturn(book);

        Library library = mock(Library.class);
        TierRegistry tiers = mock(TierRegistry.class);
        when(library.tiers()).thenReturn(tiers);
        when(tiers.tiers()).thenReturn(List.of(
                new TierRegistry.Tier("common", "&7", 10, false, -1),
                new TierRegistry.Tier("mythic", "&d", 60, true, -1),
                new TierRegistry.Tier("godly", "&4", 80, true, -1)));
        when(library.tierOf("enchants/common")).thenReturn("common");
        when(library.tierOf("enchants/heroic")).thenReturn("mythic");
        when(library.tierOf("enchants/mastery")).thenReturn("godly");
        when(library.displayNameOf("enchants/heroic")).thenReturn("Heroic Test");
        ContentHolder content = mock(ContentHolder.class);
        when(content.library()).thenReturn(library);

        ScrollsConfig defaults = ScrollsConfig.defaults();
        ScrollsConfig.Black normal = new ScrollsConfig.Black("INK_SAC", "Black", List.of(), 10, 20,
                List.of("WEAPON"), List.of("common"), null, List.of());
        ScrollsConfig.HeroicBlack heroic = new ScrollsConfig.HeroicBlack(
                "INK_SAC", "&5Ascended Extraction Scroll", List.of(), 10, 35,
                List.of("WEAPON"), List.of("common", "mythic"),
                new SoundCue("entity.player.levelup", 1.0f, 1.0f), List.of("WITCH"));
        ScrollsConfig config = new ScrollsConfig(normal, heroic, defaults.randomizer(), defaults.transmog(),
                defaults.holy(), defaults.nametag(), defaults.godly());

        ItemStack cursor = mock(ItemStack.class);
        when(cursor.getAmount()).thenReturn(1);
        scrolls.mark(cursor, ScrollService.HEROIC_BLACK);
        scrolls.markHeroicRange(cursor, 22, 24);

        ItemStack gear = mock(ItemStack.class);
        when(gear.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(gear.getAmount()).thenReturn(1);
        Map<String, Integer> enchants = new LinkedHashMap<>();
        enchants.put("enchants/heroic", 4);
        enchants.put("enchants/mastery", 7);
        CombatState before = new CombatState(enchants, List.of());
        combat.write(gear, before);

        // Candidate index 0 selects the Heroic-tier entry; the next draw (2 of [22,24]) yields 24%.
        Random random = sequence(0, 2);
        ScrollService service = new ScrollService(scrolls, combat, lore, carriers, content, () -> config, random,
                messages(), null, ItemGroups.standard());

        var outcome = service.interact(cursor, gear);

        assertTrue(outcome.commit());
        assertTrue(outcome.consumeCursor());
        assertSame(book, outcome.produced());
        assertEquals(Map.of("enchants/mastery", 7), combat.read(gear).enchants());
        verify(carriers).mintBook("enchants/heroic", 4, 24);
        verify(lore).apply(eq(gear), eq(before.withEnchants(Map.of("enchants/mastery", 7))));
        verify(cursor).setAmount(0);
        assertEquals(new SoundCue("entity.player.levelup", 1.0f, 1.0f), outcome.cue().sound());
        assertEquals(List.of("WITCH"), outcome.cue().particles());
        assertEquals("§aUsed §5Ascended Extraction Scroll§r§a for Heroic Test", outcome.message());
    }

    @Test
    void heroicScrollWithOnlyMasteryLeavesBothItemsUntouched() {
        FakeItemStateStore store = new FakeItemStateStore();
        ItemKeys keys = ItemKeys.of();
        ScrollCodec scrolls = new ScrollCodec(keys.scroll(), keys.scrollConvert(), keys.scrollHeroicMin(),
                keys.scrollHeroicMax(), store);
        CombatCodec combat = new CombatCodec(keys.combat(), store);
        LoreRenderer lore = mock(LoreRenderer.class);
        CarrierService carriers = mock(CarrierService.class);
        Library library = mock(Library.class);
        TierRegistry tiers = mock(TierRegistry.class);
        when(library.tiers()).thenReturn(tiers);
        when(tiers.tiers()).thenReturn(List.of(new TierRegistry.Tier("godly", "&4", 80, true, -1)));
        when(library.tierOf("enchants/mastery")).thenReturn("godly");
        ContentHolder content = mock(ContentHolder.class);
        when(content.library()).thenReturn(library);
        ScrollsConfig defaults = ScrollsConfig.defaults();
        ScrollsConfig.HeroicBlack heroic = new ScrollsConfig.HeroicBlack(
                "INK_SAC", "&5Ascended Extraction Scroll", List.of(), 10, 35,
                List.of("WEAPON"), List.of("mythic"), null, List.of());
        ScrollsConfig config = new ScrollsConfig(defaults.black(), heroic, defaults.randomizer(), defaults.transmog(),
                defaults.holy(), defaults.nametag(), defaults.godly());

        ItemStack cursor = mock(ItemStack.class);
        when(cursor.getAmount()).thenReturn(1);
        scrolls.mark(cursor, ScrollService.HEROIC_BLACK);
        ItemStack gear = mock(ItemStack.class);
        when(gear.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(gear.getAmount()).thenReturn(1);
        CombatState before = new CombatState(Map.of("enchants/mastery", 7), List.of());
        combat.write(gear, before);

        ScrollService service = new ScrollService(scrolls, combat, lore, carriers, content, () -> config,
                new Random(1), messages(), null, ItemGroups.standard());
        var outcome = service.interact(cursor, gear);

        assertFalse(outcome.commit());
        assertFalse(outcome.consumeCursor());
        assertEquals(before, combat.read(gear));
        verify(cursor, never()).setAmount(anyInt());
        verify(carriers, never()).mintBook(anyString(), anyInt(), anyInt());
        verify(lore, never()).apply(eq(gear), eq(before));
        assertEquals("§cNo match for §5Ascended Extraction Scroll§r§c.", outcome.message());
    }

    private static Messages messages() {
        Lang lang = new Lang(Map.of(
                "scroll.heroic-black.success", "&aUsed {SCROLL}&r&a for {ENCHANT}",
                "scroll.heroic-black.no-eligible", "&cNo match for {SCROLL}&r&c."), Map.of(), List.of());
        return new Messages(() -> lang);
    }

    private static Random sequence(int... values) {
        return new Random() {
            private int index;

            @Override
            public int nextInt(int bound) {
                return values[Math.min(index++, values.length - 1)] % bound;
            }
        };
    }
}
