package tester.suite;

import engine.run.ActorProbe;
import engine.run.ModernActorProbe;
import feature.compat.DropControl;
import feature.compat.Hands;
import feature.compat.ModernDropControl;
import feature.compat.ModernHands;
import feature.compat.ModernProjectiles;
import feature.compat.Projectiles;
import feature.compat.Sounds;
import item.codec.ItemStateStore;
import item.codec.PdcItemStateStore;
import item.render.LoreRenderer;
import item.worn.EquipSource;
import item.worn.ModernEquipSource;

/**
 * The modern-era item-data + equipment seams for the live suites (ADR-0044). These suites boot on a real Paper
 * server, so the injected {@link ItemStateStore} is the PDC impl and the {@link EquipSource} is the 6-slot
 * (incl. off-hand) read — the same seams the shipped plugin wires. The legacy smoke supplies its own NBT/5-slot
 * impls (tester/legacy). One place so a codec-arity change stays a single edit.
 */
final class Stores {

    private Stores() {
    }

    static ItemStateStore state() {
        return new PdcItemStateStore();
    }

    static EquipSource equip() {
        return new ModernEquipSource();
    }

    static ActorProbe probe() {
        return new ModernActorProbe();
    }

    static Hands hands() {
        return new ModernHands();
    }

    static DropControl dropControl() {
        return new ModernDropControl();
    }

    /** The entity's REAL effective max health (attribute value incl. modifiers) — the worn-bonus oracle. */
    static double maxHealthOf(org.bukkit.entity.LivingEntity entity) {
        org.bukkit.attribute.AttributeInstance attribute =
                entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        return attribute != null ? attribute.getValue() : entity.getMaxHealth();
    }

    static Projectiles projectiles() {
        return new ModernProjectiles();
    }

    static Sounds sounds() {
        return new Sounds((player, at, key, volume, pitch) -> player.playSound(at, key, volume, pitch));
    }

    static LoreRenderer lore(LoreRenderer.Config config) {
        return new LoreRenderer(config, state());
    }
}
