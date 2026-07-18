package feature.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import compile.load.PetDef;
import compile.load.PetFoodConfig;
import compile.load.PetItemConfig;
import engine.run.UseAttempt;
import engine.stores.TeleblockStore;
import feature.apply.FakeItemStateStore;
import feature.trigger.TriggerDispatch;
import item.codec.ItemKeys;
import item.codec.PetCodec;
import item.head.HeadEquip;
import item.head.TexturedHeads;
import item.mint.VanillaEnchants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import platform.lang.Messages;
import schema.spec.D;
import schema.spec.ParamSpec;

/**
 * The 2s shared pet-use gate (ADR-0070 rider) at the service layer, under the owner ruling R1 (2026-07-18): a
 * successful active use / dig ARMS the gate, and a DIFFERENT pet's use inside the window is refused — but a
 * pending home RECALL is EXEMPT (resolved before the gate check) and itself arms the gate. Real compiled pet
 * content + real stores; {@link TriggerDispatch}/{@link PetMessenger}/cues are mocked (their outcomes are the
 * axis under test, and the recall-success path touches no scheduler, so it stays server-free).
 */
class PetServiceSharedGateTest {

    private static final UUID PLAYER = UUID.randomUUID();

    @TempDir
    Path root;

    private final AtomicLong clock = new AtomicLong(0);
    private ContentHolder holder;
    private PetCodec codec;
    private TriggerDispatch dispatch;
    private PetMessenger messenger;
    private PetSharedUseStore sharedGate;
    private PetHomeStore homes;
    private PetService pets;

    private ItemStack activeA;
    private ItemStack activeB;
    private ItemStack digger;

    private static Compiler compiler() {
        return Compiler.of(MapSpecRegistry.of(
                ParamSpec.of("HEAL").param("amount", D.DOUBLE.min(0)).build(),
                ParamSpec.of("DIG_HOME").param("window", D.TICKS).param("range", D.DOUBLE.min(0)).build()));
    }

    @BeforeEach
    void build() throws IOException {
        write("pets/activea.yml", """
            display: "Active A"
            type: ACTIVE
            levels:
              1: { cooldown: 100, effects: [ { HEAL: { amount: 1 } } ] }
            """);
        write("pets/activeb.yml", """
            display: "Active B"
            type: ACTIVE
            levels:
              1: { cooldown: 100, effects: [ { HEAL: { amount: 1 } } ] }
            """);
        write("pets/digger.yml", """
            display: "Digger"
            type: ACTIVE
            levels:
              1: { cooldown: 100, effects: [ { DIG_HOME: { window: 600, range: 50 } } ] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);
        holder = new ContentHolder(lib);

        FakeItemStateStore store = new FakeItemStateStore();
        codec = new PetCodec(ItemKeys.of(), store);
        activeA = stampedPet(store, "activea");
        activeB = stampedPet(store, "activeb");
        digger = stampedPet(store, "digger");

        dispatch = mock(TriggerDispatch.class);
        messenger = mock(PetMessenger.class);
        sharedGate = new PetSharedUseStore();
        homes = new PetHomeStore();
        pets = new PetService(holder, codec, dispatch, TexturedHeads.NONE, HeadEquip.NONE, VanillaEnchants.NONE,
                messenger, new PetArmedStore(), sharedGate, MasterConfig.PetsSection::defaults,
                PetItemConfig::defaults, PetFoodConfig::defaults, p -> {
                }, clock::get, m -> m == Material.AIR, mock(PetLevelCue.class), new Random(42),
                homes, new TeleblockStore(), mock(PetHomeVisuals.class));
    }

    private void write(String rel, String yaml) throws IOException {
        Path file = root.resolve(rel);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    /** A stacked (amount 2) pet stack so {@code creditUseExp} short-circuits — the gate wiring is the target. */
    private ItemStack stampedPet(FakeItemStateStore store, String key) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getAmount()).thenReturn(2);
        codec.stamp(stack, key, 1);
        return stack;
    }

    private Player player() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(PLAYER);
        return p;
    }

    private static UseAttempt activated() {
        return new UseAttempt(true, false, 0, -1, false);
    }

    @Test
    void aSuccessfulUseArmsTheGateAndBlocksADifferentPetForTwoSeconds() {
        Player p = player();
        when(dispatch.fireUse(any(), any())).thenReturn(activated());
        PetDef defA = holder.library().petDefOf("activea");
        PetDef defB = holder.library().petDefOf("activeb");

        pets.use(p, activeA); // activates → arms the shared gate until tick 40
        verify(messenger).activated(p, defA);

        pets.use(p, activeB); // inside the window → refused, never fires
        verify(messenger).sharedCooldown(p, 40L);
        verify(messenger, never()).activated(p, defB);

        clock.set(40); // window elapsed
        pets.use(p, activeB);
        verify(messenger).activated(p, defB);
    }

    @Test
    void aFailedActivationDoesNotArmTheGate() {
        Player p = player();
        // first use is blocked at the cooldown gate, the second activates
        when(dispatch.fireUse(any(), any())).thenReturn(new UseAttempt(false, true, 100, -1, false), activated());
        PetDef defA = holder.library().petDefOf("activea");
        PetDef defB = holder.library().petDefOf("activeb");

        pets.use(p, activeA); // on cooldown → no activation, so the shared gate must NOT arm
        verify(messenger).onCooldown(p, defA, 100L);

        pets.use(p, activeB); // the gate is still open → this one activates
        verify(messenger).activated(p, defB);
        verify(messenger, never()).sharedCooldown(any(), anyLong());
    }

    @Test
    void aPendingRecallIsExemptFromTheGateAndArmsIt() {
        Player p = player();
        World world = mock(World.class);
        UUID worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);
        when(p.getWorld()).thenReturn(world);
        when(p.getLocation()).thenReturn(new Location(world, 10, 64, 10, 0f, 0f));

        // A live home window AND a shut shared gate: the recall must ignore the gate entirely (owner ruling R1).
        homes.arm(PLAYER, worldId, 10, 64, 10, 0f, 0f, 50, 600);
        sharedGate.arm(PLAYER, 20); // gate open until tick 20 (remaining 20 at now=0)
        PetDef diggerDef = holder.library().petDefOf("digger");

        pets.use(p, digger); // a digger's click on a live home is the recall — resolved BEFORE the gate check

        verify(dispatch).teleport(eq(p), any(Location.class)); // it teleported despite the shut gate
        verify(messenger).ended(p, diggerDef);
        verify(messenger, never()).sharedCooldown(any(), anyLong()); // the gate never refused the return trip
        verify(dispatch, never()).fireUse(any(), any());             // a recall never re-fires the dig
        assertEquals(40, sharedGate.remaining(PLAYER, 0), "a successful recall arms the shared gate (was 20 → 40)");
    }
}
