package bootstrap.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import feature.menu.Mintable;
import feature.menu.MintIo;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** The builder round-trip + the compact ctor's defensive copying (the record's only logic). */
final class FeatureModuleTest {

    private static final Listener LISTENER = new Listener() { };

    private static final Mintable GEM = new Mintable() {
        @Override
        public String type() {
            return "gem";
        }

        @Override
        public void give(CommandSender sender, Player target, String[] args, MintIo io) {
        }
    };

    @Test
    void builderRoundTripsEveryAxis() {
        Runnable boot = () -> { };
        Runnable stop = () -> { };
        FeatureModule module = FeatureModule.named("souls")
                .toggle(Toggle.boot("features.souls", () -> true, "off"))
                .events(LISTENER)
                .install("hook", () -> { })
                .command(DynCommand.always("splitsouls", () -> null, "fail"))
                .mint(GEM)
                .store(id -> { })
                .pluginItem(stack -> true)
                .lang("soul", "command.give.gem")
                .boot(boot)
                .stop("aura", stop)
                .build();

        assertEquals("souls", module.name());
        assertEquals(Toggle.Depth.BOOT, module.toggle().depth());
        assertEquals(2, module.wires().size());  // the events wire + the install wire
        assertEquals(1, module.commands().size());
        assertSame(GEM, module.mintables().get(0));
        assertEquals(List.of("soul", "command.give.gem"), module.langRoots());
        assertEquals(1, module.playerStores().size());
        assertEquals(1, module.boots().size());
        assertEquals("aura", module.stops().get(0).what());
        assertTrue(module.pluginItem().test(new ItemStack(Material.STONE)));
    }

    @Test
    void wireAndInstallConveniencesAppendInOrder() {
        FeatureModule module = FeatureModule.named("m")
                .events(LISTENER)
                .install("x", () -> { })
                .pluginItemGuard()
                .quitSweep()
                .build();
        List<Wire> wires = module.wires();
        assertTrue(wires.get(0) instanceof Wire.Events);
        assertTrue(wires.get(1) instanceof Wire.Install);
        assertTrue(wires.get(2) instanceof Wire.PluginItemGuard);
        assertTrue(wires.get(3) instanceof Wire.QuitSweep);
    }

    @Test
    void defaultsAreAlwaysOnEmptyAndGuardNothing() {
        FeatureModule module = FeatureModule.named("bare").build();
        assertEquals(Toggle.Depth.NONE, module.toggle().depth());
        assertTrue(module.wires().isEmpty());
        assertTrue(module.mintables().isEmpty());
        assertFalse(module.pluginItem().test(new ItemStack(Material.STONE)));
    }

    @Test
    void compactCtorCopiesSoTheRecordIsImmutable() {
        FeatureModule module = FeatureModule.named("m").events(LISTENER).build();
        assertThrows(UnsupportedOperationException.class,
                () -> module.wires().add(new Wire.Events(LISTENER)));
    }

    @Test
    void nameAndToggleAndPluginItemAreNullChecked() {
        assertThrows(NullPointerException.class, () -> new FeatureModule(null, Toggle.ALWAYS,
                List.of(), List.of(), List.of(), List.of(), List.of(), stack -> false,
                List.of(), List.of(), List.of()));
    }
}
