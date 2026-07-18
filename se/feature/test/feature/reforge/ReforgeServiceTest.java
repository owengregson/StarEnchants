package feature.reforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.Compiler;
import compile.MapSpecRegistry;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.MasterConfig;
import compile.load.ReforgeItemConfig;
import compile.load.SoundCue;
import feature.apply.FakeItemStateStore;
import feature.apply.GestureOutcome;
import feature.apply.ItemEnchanter;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.codec.ReforgeCodec;
import item.mint.ItemFactory;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import platform.item.ItemGroups;
import platform.lang.Messages;
import platform.text.Colors;
import platform.text.TimeFormat;
import schema.spec.D;
import schema.spec.ParamSpec;

/**
 * The reforge service's mint token rendering + gesture outcome shapes (ADR-0070) over a real compiled
 * {@link Library}, real codecs on the in-memory store — no server (the {@code MaskServiceTest} clone). The
 * likeness template is TEST-OWNED (the {@code {COLOR}}/{@code {NAME}}/{@code {DESCRIPTION}}/{@code {APPLIES}}/
 * {@code {TIME_FORMATTED}} substitution is the algorithm under test, writing-tests Rule 1); expected messages
 * come from the same {@link Messages} instance the service formats with. The per-reforge icon material is
 * captured through the custom-item resolver hook so no live Material registry is touched.
 */
@SuppressWarnings("deprecation") // setDisplayName/setLore: the floor-stable item-meta path production decorates with
class ReforgeServiceTest {

    private static final String REFORGE = "reforges/testforge";

    /** Test-owned likeness — distinctive shapes so a template/token mix-up cannot pass. */
    private static final ReforgeItemConfig LIKENESS = new ReforgeItemConfig(
            "{COLOR}[{NAME}]",
            List.of("{DESCRIPTION}", "applies {APPLIES}", "bonus {NAME_UPPER}", "cd {TIME_FORMATTED}"),
            "on {NAME}",
            true,
            new SoundCue("cue.apply", 1.0f, 1.0f),
            new SoundCue("cue.remove", 1.0f, 1.0f));

    @TempDir
    Path root;

    private final Messages messages = Messages.defaults();
    private FakeItemStateStore store;
    private ReforgeCodec reforgeCodec;
    private CombatCodec combatCodec;
    private ContentHolder holder;

    @BeforeEach
    void build() throws IOException {
        Path file = root.resolve("reforges/testforge.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            display: "Testforge"
            color: "&6"
            icon: SUGAR
            description:
              - "&6&lTESTFORGE"
              - "&6* A test reforge."
            cooldown: 200
            effects: [{ HEAL: { amount: 1 } }]
            """, StandardCharsets.UTF_8);
        Library lib = LibraryLoader.load(root,
                Compiler.of(MapSpecRegistry.of(ParamSpec.of("HEAL").param("amount", D.DOUBLE.min(0)).build())), 1);
        holder = new ContentHolder(lib);
        store = new FakeItemStateStore();
        reforgeCodec = new ReforgeCodec(ItemKeys.of(), store);
        combatCodec = new CombatCodec("combat", store);
    }

    @AfterEach
    void restoreItemFactory() {
        ItemFactory.customItemResolver(null); // the inert default back, so other tests aren't perturbed
    }

    private ReforgeService service() {
        return service(LIKENESS);
    }

    private ReforgeService service(ReforgeItemConfig cfg) {
        LoreRenderer lore = new LoreRenderer(
                LoreRenderer.Config.of(LoreStyle.DEFAULT, key -> holder.library().displayNameOf(key)), store);
        ItemEnchanter enchanter = new ItemEnchanter(combatCodec, lore, holder, ItemGroups.standard(),
                () -> ItemEnchanter.DEFAULT_BASE_SLOTS, () -> ItemEnchanter.DEFAULT_CRYSTAL_SLOTS,
                () -> ItemEnchanter.DEFAULT_MAX_MERGE,
                () -> MasterConfig.ReforgesSection.defaults().weaponGroups(),
                messages, item.mint.VanillaEnchants.NONE);
        return new ReforgeService(reforgeCodec, combatCodec, enchanter, holder, () -> cfg,
                () -> MasterConfig.ReforgesSection.defaults().weaponGroups(), messages);
    }

    /** Route the icon token through a fixed stack (clone → itself) so mint captures the token, not a live Material. */
    private ItemStack routeIcon(ItemMeta meta) {
        ItemStack icon = mock(ItemStack.class);
        when(icon.getItemMeta()).thenReturn(meta);
        when(icon.clone()).thenReturn(icon);
        ItemFactory.customItemResolver(token -> icon);
        return icon;
    }

    private static ItemStack gear(Material type, int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.getAmount()).thenReturn(amount);
        return stack;
    }

    /** A stamped reforge-item cursor (amount 1) — what the apply gesture holds. */
    private ItemStack reforgeCursor() {
        ItemStack cursor = mock(ItemStack.class);
        when(cursor.getAmount()).thenReturn(1);
        reforgeCodec.stamp(cursor, REFORGE);
        return cursor;
    }

    @Test
    void mintRendersEveryLikenessToken() {
        ItemMeta meta = mock(ItemMeta.class);
        ItemStack icon = routeIcon(meta);

        ItemStack minted = service().mint(REFORGE);

        assertSame(icon, minted, "mint builds from the per-reforge icon material");
        assertEquals(REFORGE, reforgeCodec.keyOf(minted), "identity stamped");
        // {COLOR}[{NAME}] over the authored def; {DESCRIPTION} line-expands; {APPLIES} is the weapon-groups
        // label; {NAME_UPPER} upper-cases the bare display; {TIME_FORMATTED} is the a0 cooldown humanised.
        verify(meta).setDisplayName(Colors.translate("&6[Testforge]"));
        verify(meta).setLore(List.of(
                Colors.translate("&6&lTESTFORGE"),
                Colors.translate("&6* A test reforge."),
                "applies " + ItemGroups.kindsLabel(List.of("SWORD", "AXE")),
                "bonus TESTFORGE",
                "cd " + TimeFormat.hmsFromTicks(200)));
    }

    @Test
    void mintOfAnUnknownKeyIsNull() {
        assertNull(service().mint("reforges/ghost"));
    }

    @Test
    void applyStampsTheWeaponAndSpendsTheCursor() {
        ReforgeService service = service();
        ItemStack cursor = reforgeCursor();
        ItemStack sword = gear(Material.DIAMOND_SWORD, 1);

        GestureOutcome out = service.apply(cursor, sword);

        assertTrue(out.commit());
        assertTrue(out.consumeCursor());
        assertSame(sword, out.newTarget());
        assertNull(out.produced());
        assertEquals(LIKENESS.soundApply(), out.cue().sound());
        assertEquals(messages.format("reforge.apply-success", "REFORGE", "&6&lTestforge"), out.message());
        verify(cursor).setAmount(0);
        assertEquals(REFORGE, combatCodec.read(sword).reforgeKey());
    }

    @Test
    void ineligibleTargetsNoopAndNeverTouchTheCursor() {
        ReforgeService service = service();
        ItemStack cursor = reforgeCursor();

        // not a weapon
        GestureOutcome helmet = service.apply(cursor, gear(Material.DIAMOND_HELMET, 1));
        assertFalse(helmet.commit());
        assertFalse(helmet.consumeCursor());
        assertEquals(messages.format("reforge.not-weapon", "DISPLAY", "Testforge"), helmet.message());

        // an occupied socket
        ItemStack reforged = gear(Material.DIAMOND_SWORD, 1);
        combatCodec.write(reforged, CombatState.EMPTY.withReforge(REFORGE));
        GestureOutcome already = service.apply(cursor, reforged);
        assertFalse(already.commit());
        assertEquals(messages.format("reforge.occupied"), already.message());

        verify(cursor, never()).setAmount(anyInt());
    }

    @Test
    void carriesAndExtractDelegateThroughTheEnchanter() {
        ItemMeta meta = mock(ItemMeta.class);
        routeIcon(meta); // the extract mints a reforge item back through the icon route
        ReforgeService service = service();

        assertFalse(service.carries(gear(Material.DIAMOND_SWORD, 1)), "a bare weapon carries no reforge");

        ItemStack sword = gear(Material.DIAMOND_SWORD, 1);
        combatCodec.write(sword, CombatState.EMPTY.withReforge(REFORGE));
        assertTrue(service.carries(sword));

        ItemStack cursor = mock(ItemStack.class);
        when(cursor.getAmount()).thenReturn(1);
        GestureOutcome out = service.extract(cursor, sword);

        assertTrue(out.commit());
        assertSame(sword, out.newTarget());
        assertEquals(REFORGE, reforgeCodec.keyOf(out.produced()), "the popped reforge mints back as its item");
        assertEquals(LIKENESS.soundRemove(), out.cue().sound());
        assertEquals(messages.format("reforge.extract-success", "REFORGE", "&6&lTestforge"), out.message());
        assertNull(combatCodec.read(sword).reforgeKey(), "the weapon is cleaned");
        verify(cursor).setAmount(0);
    }

    @Test
    void mutedSoundsProduceNoCue() {
        ReforgeItemConfig muted = new ReforgeItemConfig(LIKENESS.name(), LIKENESS.lore(), LIKENESS.loreWhileOnItem(),
                false, LIKENESS.soundApply(), LIKENESS.soundRemove());
        ReforgeService service = service(muted);

        GestureOutcome out = service.apply(reforgeCursor(), gear(Material.DIAMOND_SWORD, 1));
        assertTrue(out.commit());
        assertNull(out.cue(), "sounds.enabled=false gates the cue entirely (the crystal rule)");
    }
}
