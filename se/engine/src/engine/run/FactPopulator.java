package engine.run;

import compile.cond.VarBinding;
import compile.cond.VarKind;
import compile.model.FactMask;
import engine.condition.BuiltinVars;
import engine.condition.WornFactSource;
import engine.condition.EnchantLevels;
import engine.condition.FactBuffer;
import engine.condition.GroundOwnership;
import engine.condition.PotionLevels;
import engine.condition.VarVocabulary;
import engine.selector.kind.Allies;
import engine.sink.FrozenTargets;
import engine.stores.BookRateStore;
import engine.stores.EngineStores;
import engine.stores.HeldSlotStore;
import engine.stores.RageStackStore;
import engine.stores.SoulTotalStore;
import engine.stores.VarStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.UnaryOperator;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import platform.caps.Regions;

/**
 * Fills a condition {@link FactBuffer} from one activation's live context (docs/architecture.md §3.4).
 * Built once at boot from the same vocabulary the compiler lowered {@code %scope.name%} against, so a
 * compiled condition's slot and the buffer agree by construction.
 *
 * <p>One buffer per worker thread, cleared and refilled in place (the SAME instance is returned), keeping
 * the per-hit pipeline allocation-free — safe only because the buffer never escapes the synchronous pass.
 *
 * <p>Folia: every read runs on the firing thread; an entity owned by another region (e.g. a cross-region
 * projectile shooter) fails hard on read, so each entity side is wrapped to leave its facts defaulted
 * rather than abort the activation.
 */
public final class FactPopulator {

    @FunctionalInterface
    private interface ActorD { double read(Player actor); }

    @FunctionalInterface
    private interface ActorB { boolean read(Player actor); }

    @FunctionalInterface
    private interface ActorS { String read(Player actor); }

    @FunctionalInterface
    private interface VictimD { double read(LivingEntity victim); }

    @FunctionalInterface
    private interface VictimB { boolean read(LivingEntity victim); }

    @FunctionalInterface
    private interface VictimS { String read(LivingEntity victim); }

    private record ActorNum(int slot, ActorD src) {}

    private record ActorFlag(int slot, ActorB src) {}

    private record ActorStr(int slot, ActorS src) {}

    private record VictimNum(int slot, VictimD src) {}

    private record VictimFlag(int slot, VictimB src) {}

    private record VictimStr(int slot, VictimS src) {}

    /**
     * The {@code %scope.potion.<effect>%} reader, rebound per activation. One instance per worker thread beside
     * the buffer — a fresh capture per hit would allocate on the pipeline the JMH gate holds at zero.
     */
    private final class PotionBinding implements PotionLevels {
        private Player actor;
        private LivingEntity victim;

        void bind(Player actor, LivingEntity victim) {
            this.actor = actor;
            this.victim = victim;
        }

        @Override
        public int actorLevel(int potionEffectId) {
            return actor == null ? 0 : level(actor, potionEffectId);
        }

        @Override
        public int victimLevel(int potionEffectId) {
            return victim == null ? 0 : level(victim, potionEffectId);
        }

        private int level(LivingEntity entity, int potionEffectId) {
            try {
                return probe.potionLevel(entity, potionEffectId);
            } catch (RuntimeException unreadable) {
                // Folia: the entity belongs to another region — read as "no effect", never abort the gate.
                Regions.swallowed("FactPopulator.potionLevel", unreadable);
                return 0;
            }
        }
    }

    /**
     * The {@code %scope.enchlevel.<key>%} reader, rebound per activation. Holds UUIDs, never entities: the
     * lookup is a pre-flattened worn-state read, so a cross-region victim resolves with no entity access.
     */
    private static final class EnchantBinding implements EnchantLevels {
        private UUID actor;
        private UUID victim;

        void bind(UUID actor, UUID victim) {
            this.actor = actor;
            this.victim = victim;
        }

        @Override
        public int actorLevel(String key) {
            return actor == null ? 0 : wornFactSource.levelOf(actor, key);
        }

        @Override
        public int victimLevel(String key) {
            return victim == null ? 0 : wornFactSource.levelOf(victim, key);
        }
    }

    /** The {@code %scope.crystals.<key>%} reader — the {@link EnchantBinding} shape, off the same source. */
    private static final class CrystalBinding implements engine.condition.CrystalCounts {
        private UUID actor;
        private UUID victim;

        void bind(UUID actor, UUID victim) {
            this.actor = actor;
            this.victim = victim;
        }

        @Override
        public int actorCount(String key) {
            return actor == null ? 0 : wornFactSource.crystalPieces(actor, key);
        }

        @Override
        public int victimCount(String key) {
            return victim == null ? 0 : wornFactSource.crystalPieces(victim, key);
        }
    }

    private final ThreadLocal<FactBuffer> buffer;
    private final ThreadLocal<PotionBinding> potionBinding = ThreadLocal.withInitial(PotionBinding::new);
    private final ThreadLocal<EnchantBinding> enchantBinding = ThreadLocal.withInitial(EnchantBinding::new);
    private final ThreadLocal<CrystalBinding> crystalBinding = ThreadLocal.withInitial(CrystalBinding::new);
    private final VarStore vars;
    private final RageStackStore rageStacks; // §3 %ragestacks% source — an actor-scoped read (mask-gated)
    private final HeldSlotStore heldSlots;   // %heldticks% source — an actor-scoped read (mask-gated)
    private final SoulTotalStore soulTotals; // %actor.souls%/%victim.souls% source — cached totals, never an inventory walk
    private final engine.stores.TeleblockStore teleblock; // %status.teleblock% source — an actor-scoped read (mask-gated)
    private final BookRateStore bookRate;    // %bookrate.generate%/%bookrate.apply% source (mask-gated)
    private final UnaryOperator<String> papiDelegate;
    private final ActorProbe probe; // §3.3 era-specific entity/material reads (swim/glide/isAir/main-hand)
    // rand()'s draw, installed on each activation's buffer. Volatile: written once at boot wiring, read on
    // every firing thread. Defaults to 0 so the engine never invents randomness the composition root didn't give it.
    private volatile DoubleSupplier random = () -> 0.0;

    /** {@code %victim.mobtype%} soft hook (ADR-0027): boot-installed so the engine never references the MythicMobs API. */
    private static volatile java.util.function.Function<org.bukkit.entity.Entity, String> entityTypeResolver =
            entity -> "";

    /** A {@code null} resets to empty. */
    public static void entityTypeResolver(java.util.function.Function<org.bukkit.entity.Entity, String> resolver) {
        entityTypeResolver = resolver == null ? entity -> "" : resolver;
    }

    /**
     * The worn-gear facts' soft hook ({@code %scope.enchlevel.<key>%}, {@code %victim.heroicpieces%}):
     * boot-installed by the composition root, the only layer where the worn-state store is visible
     * ({@code se-engine} has no {@code se-item} dependency).
     */
    private static volatile WornFactSource wornFactSource = WornFactSource.NONE;

    /** A {@code null} resets to the zero source. */
    public static void wornFactSource(WornFactSource source) {
        wornFactSource = source == null ? WornFactSource.NONE : source;
    }

    /**
     * {@code %actor.ownedground%}'s soft hook: boot-installed over the per-boot temp-block ledger, which lives
     * on the sink env the engine's fact layer cannot see. Same installer shape as {@link #wornFactSource}.
     */
    private static volatile GroundOwnership groundOwnership = GroundOwnership.NONE;

    /** A {@code null} resets to the un-owned source. */
    public static void groundOwnership(GroundOwnership source) {
        groundOwnership = source == null ? GroundOwnership.NONE : source;
    }
    private final List<ActorNum> actorNum = new ArrayList<>();
    private final List<ActorFlag> actorFlag = new ArrayList<>();
    private final List<ActorStr> actorStr = new ArrayList<>();
    private final List<VictimNum> victimNum = new ArrayList<>();
    private final List<VictimFlag> victimFlag = new ArrayList<>();
    private final List<VictimStr> victimStr = new ArrayList<>();
    // Context facts come from the event payload, not an actor/victim entity (slot −1 if absent).
    private final int damageSlot;
    private final int blockTypeSlot;
    private final int isBlockSlot;
    private final int worldRainingSlot;
    private final int worldThunderingSlot;
    private final int worldTimeSlot;
    private final int comboSlot;
    private final int distanceSlot;       // actor↔victim distance in blocks (derived, Folia-guarded)
    private final int nearbyEnemiesSlot;  // living entities within NEARBY_RADIUS (derived, Folia-guarded)
    private final int victimInZoneSlot;   // victim inside an actor-owned MARK_ZONE (derived, Folia-guarded)
    // ADR-0049: gank/cause/item-damage context facts (carried on the ActivationContext) + one derived (behindvictim).
    private final int recentAttackersSlot;   // distinct recent attackers (from the context; sourced in CombatDispatch)
    private final int attackerIndexSlot;     // 1-based first-seen order of THIS attacker (from the context)
    private final int damageCauseSlot;       // DamageCause name of the triggering event (from the context)
    private final int itemDamageArmorSlot;   // ITEM_DAMAGE: worn-armor vs held (from the context)
    private final int actorBehindVictimSlot; // actor behind the victim's facing (derived, Folia-guarded)
    private final int actorBelowVictimSlot;  // actor's feet below the victim's (derived, Folia-guarded, ADR-0052)
    private final int rageStacksSlot;        // %ragestacks% (actor-scoped store read, mask-gated)
    private final int nearbyAlliesSlot;      // allied players within NEARBY_RADIUS (derived, shares the enemy scan)
    private final int victimRelationSlot;    // ALLY/ENEMY/NEUTRAL vs the victim (derived, Folia-guarded)
    private final int postHitHealthSlot;     // actor health minus the context's vanilla-final damage (DEFENSE only)
    private final int heldTicksSlot;         // ticks since the actor's last hotbar-slot change (store read, mask-gated)
    private final int actorSoulsSlot;        // the actor's cached cross-gem soul total (store read, mask-gated)
    private final int victimSoulsSlot;       // the victim's cached cross-gem soul total (store read, mask-gated)
    private final int impactHeightSlot;      // projectile height above the struck entity's feet (from the context)
    private final int projectileKindSlot;    // ARROW/FIREBALL/THROWN/OTHER (from the context)
    private final int equipChangeSlot;       // EQUIP/UNEQUIP on an EQUIP_CHANGE activation (from the context)
    private final int proximityTagSlot;      // which nearby event fired a PROXIMITY_EVENT (from the context)
    private final int itemDurabilitySlot;    // ITEM_DAMAGE: the damaged item's remaining durability % (from the context)
    private final int victimHeroicPiecesSlot; // worn heroic armour pieces on the victim (from the worn-fact source)
    private final int actorHeroicPiecesSlot;  // worn heroic armour pieces on the actor (from the worn-fact source)
    private final int statusTeleblockSlot;    // %status.teleblock% (actor-scoped store read, mask-gated)
    private final int bookRateGenerateSlot;   // %bookrate.generate% (actor-scoped store read, mask-gated)
    private final int bookRateApplySlot;      // %bookrate.apply% (actor-scoped store read, mask-gated)
    private final int actorSetWeaponSlot;     // %actor.setweapon% (from the worn-fact source, mask-gated)
    private final int statusFreezeSlot;       // %status.freeze% (actor-scoped registry read, mask-gated)

    /** Search radius for {@code %nearbyenemies%}, in blocks. */
    private static final double NEARBY_RADIUS = 8.0;

    /** No dynamic-var store and no PAPI: unknown tokens resolve to null. */
    public FactPopulator(VarVocabulary vocabulary, ActorProbe probe) {
        this(vocabulary, new VarStore(), t -> null, probe, EngineStores.fresh());
    }

    /** Backed by a shared {@link VarStore} but its own store-backed facts ({@code %ragestacks%} et al. read 0). */
    public FactPopulator(VarVocabulary vocabulary, VarStore vars, UnaryOperator<String> papiDelegate, ActorProbe probe) {
        this(vocabulary, vars, papiDelegate, probe, EngineStores.fresh());
    }

    /**
     * Backed by a shared {@link VarStore} ({@code SET_VAR}/{@code INVERT_VAR} write target), an optional PAPI
     * delegate, and the {@code stores} aggregate that sources the store-backed facts ({@code %ragestacks%},
     * {@code %heldticks%}). Unknown {@code %name%} resolution order: built-in slot → player dynamic var →
     * {@code papiDelegate} → null. {@code probe} is the era-specific entity/material read seam (§3.3).
     *
     * <p>{@code vars} is passed separately from {@code stores} so the pre-aggregate callers that share only a
     * {@link VarStore} keep working; production passes {@code stores.vars()} for both.
     */
    public FactPopulator(VarVocabulary vocabulary, VarStore vars, UnaryOperator<String> papiDelegate, ActorProbe probe,
                         EngineStores stores) {
        Objects.requireNonNull(vocabulary, "vocabulary");
        Objects.requireNonNull(stores, "stores");
        this.vars = Objects.requireNonNull(vars, "vars");
        this.rageStacks = stores.rageStacks();
        this.heldSlots = stores.heldSlots();
        this.soulTotals = stores.soulTotals();
        this.teleblock = stores.teleblock();
        this.bookRate = stores.bookRate();
        this.papiDelegate = papiDelegate == null ? t -> null : papiDelegate;
        this.probe = Objects.requireNonNull(probe, "probe");
        this.buffer = ThreadLocal.withInitial(vocabulary::newFactBuffer);

        addActorNum(vocabulary, "actor.health", Player::getHealth);
        addActorNum(vocabulary, "actor.maxhealth", FactPopulator::maxHealth);
        addActorNum(vocabulary, "actor.food", actor -> actor.getFoodLevel());
        addActorNum(vocabulary, "actor.level", actor -> actor.getLevel());
        addActorNum(vocabulary, "actor.totalexp", actor -> actor.getTotalExperience());
        addActorFlag(vocabulary, "sneaking", Player::isSneaking);
        addActorFlag(vocabulary, "blocking", Player::isBlocking);
        addActorFlag(vocabulary, "flying", Player::isFlying);
        addActorFlag(vocabulary, "sprinting", Player::isSprinting);
        addActorFlag(vocabulary, "swimming", probe::isSwimming);
        addActorFlag(vocabulary, "gliding", probe::isGliding);
        addActorNum(vocabulary, "actor.healthpercent", actor -> healthPercent(actor));
        // %actor.y%: feet Y, not eye Y — a build-height gate reads where the player STANDS, and the eye
        // offset differs by pose (sneak/swim), which would make the same block read two heights.
        addActorNum(vocabulary, "actor.y", actor -> actor.getLocation().getY());
        addActorFlag(vocabulary, "onfire", actor -> actor.getFireTicks() > 0);
        addActorFlag(vocabulary, "onground", FactPopulator::onGround);
        addActorStr(vocabulary, "actor.world", actor -> actor.getWorld().getName());
        addActorStr(vocabulary, "actor.gamemode", actor -> actor.getGameMode().name());
        addActorStr(vocabulary, "actor.helditem", probe::mainHandTypeName);
        addActorStr(vocabulary, "actor.type", actor -> actor.getType().name());
        // The block the actor is standing on (one below its feet), for on-terrain conditions like Frost's "on ice"
        // (ADR-0035). Same-region as the actor, so Folia-safe; guarded with the other actor reads if it ever isn't.
        addActorStr(vocabulary, "actor.groundblock",
                actor -> actor.getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getType().name());
        // %actor.ownedground%: the same block one below the feet as %actor.groundblock%, asked of the ledger
        // instead of the world — "am I standing on ground I laid" rather than "what is under me". Same-region
        // as the actor, so the layer-list read the ledger documents as owning-thread-only holds here.
        addActorFlag(vocabulary, "actor.ownedground", actor -> {
            Location feet = actor.getLocation();
            World world = feet.getWorld();
            return world != null && groundOwnership.ownedBy(actor.getUniqueId(), world.getUID(),
                    feet.getBlockX(), feet.getBlockY() - 1, feet.getBlockZ());
        });

        addVictimNum(vocabulary, "victim.health", LivingEntity::getHealth);
        addVictimNum(vocabulary, "victim.maxhealth", FactPopulator::maxHealth);
        addVictimNum(vocabulary, "victim.healthpercent", FactPopulator::healthPercent);
        addVictimNum(vocabulary, "victim.food", v -> v instanceof Player p ? p.getFoodLevel() : 0);
        addVictimFlag(vocabulary, "victim.sneaking", v -> v instanceof Player p && p.isSneaking());
        addVictimFlag(vocabulary, "victim.blocking", v -> v instanceof Player p && p.isBlocking());
        addVictimFlag(vocabulary, "victim.flying", v -> v instanceof Player p && p.isFlying());
        addVictimFlag(vocabulary, "victim.sprinting", v -> v instanceof Player p && p.isSprinting());
        addVictimFlag(vocabulary, "victim.swimming", v -> v instanceof Player p && probe.isSwimming(p));
        addVictimFlag(vocabulary, "victim.gliding", v -> v instanceof Player p && probe.isGliding(p));
        addVictimFlag(vocabulary, "victim.fromspawner", probe::fromSpawner);
        addVictimStr(vocabulary, "victim.type", v -> v.getType().name());
        addVictimStr(vocabulary, "victim.helditem", probe::mainHandTypeName);
        // §N MythicMob internal name via the soft hook; empty when not a MythicMob / integration absent.
        addVictimStr(vocabulary, "victim.mobtype", v -> entityTypeResolver.apply(v));

        this.damageSlot = slot(vocabulary, "damage", VarKind.NUM);
        this.blockTypeSlot = slot(vocabulary, "block.type", VarKind.STR);
        this.isBlockSlot = slot(vocabulary, "isblock", VarKind.BOOL);
        this.worldRainingSlot = slot(vocabulary, "world.raining", VarKind.BOOL);
        this.worldThunderingSlot = slot(vocabulary, "world.thundering", VarKind.BOOL);
        this.worldTimeSlot = slot(vocabulary, "world.time", VarKind.NUM);
        this.comboSlot = slot(vocabulary, "combo", VarKind.NUM);
        this.distanceSlot = slot(vocabulary, "distance", VarKind.NUM);
        this.nearbyEnemiesSlot = slot(vocabulary, "nearbyenemies", VarKind.NUM);
        this.victimInZoneSlot = slot(vocabulary, "victim.inzone", VarKind.BOOL);
        this.recentAttackersSlot = slot(vocabulary, "recentattackers", VarKind.NUM);
        this.attackerIndexSlot = slot(vocabulary, "attackerindex", VarKind.NUM);
        this.damageCauseSlot = slot(vocabulary, "damagecause", VarKind.STR);
        this.itemDamageArmorSlot = slot(vocabulary, "itemdamage.armor", VarKind.BOOL);
        this.actorBehindVictimSlot = slot(vocabulary, "actor.behindvictim", VarKind.BOOL);
        this.actorBelowVictimSlot = slot(vocabulary, "actor.belowvictim", VarKind.NUM);
        this.rageStacksSlot = slot(vocabulary, "ragestacks", VarKind.NUM);
        this.nearbyAlliesSlot = slot(vocabulary, "nearbyallies", VarKind.NUM);
        this.victimRelationSlot = slot(vocabulary, "victim.relation", VarKind.STR);
        this.postHitHealthSlot = slot(vocabulary, "posthit.health", VarKind.NUM);
        this.heldTicksSlot = slot(vocabulary, "heldticks", VarKind.NUM);
        this.actorSoulsSlot = slot(vocabulary, "actor.souls", VarKind.NUM);
        this.victimSoulsSlot = slot(vocabulary, "victim.souls", VarKind.NUM);
        this.impactHeightSlot = slot(vocabulary, "impactheight", VarKind.NUM);
        this.projectileKindSlot = slot(vocabulary, "projectilekind", VarKind.STR);
        this.equipChangeSlot = slot(vocabulary, "equipchange", VarKind.STR);
        this.proximityTagSlot = slot(vocabulary, "proximityevent", VarKind.STR);
        this.itemDurabilitySlot = slot(vocabulary, "item.durabilitypercent", VarKind.NUM);
        this.victimHeroicPiecesSlot = slot(vocabulary, "victim.heroicpieces", VarKind.NUM);
        this.actorHeroicPiecesSlot = slot(vocabulary, "actor.heroicpieces", VarKind.NUM);
        this.statusTeleblockSlot = slot(vocabulary, "status.teleblock", VarKind.BOOL);
        this.bookRateGenerateSlot = slot(vocabulary, "bookrate.generate", VarKind.BOOL);
        this.bookRateApplySlot = slot(vocabulary, "bookrate.apply", VarKind.BOOL);
        this.actorSetWeaponSlot = slot(vocabulary, "actor.setweapon", VarKind.BOOL);
        this.statusFreezeSlot = slot(vocabulary, "status.freeze", VarKind.BOOL);
    }

    /**
     * The actor's relation to {@code victim} through the ONE installed alliance predicate.
     *
     * <p>{@code MEMBER} is deliberately never produced: the installed bridge is a plain
     * {@code BiPredicate<Player,Player>} (mcMMO party membership), which cannot distinguish a guild member
     * from an ally. Inventing a second axis to fill the value would be exactly the fork this fact exists to
     * avoid — when a bridge that distinguishes them is installed, this is the one place to widen.
     */
    private static String relationOf(org.bukkit.entity.Player actor, LivingEntity victim) {
        if (victim == null) {
            return "";
        }
        if (!(victim instanceof org.bukkit.entity.Player other)) {
            return "NEUTRAL"; // a mob has no alliance axis; authors gate PvP behaviour on ALLY/ENEMY
        }
        return Allies.allied(actor, other) ? "ALLY" : "ENEMY";
    }

    /** A populator over the built-in vocabulary — the production default, paired with the compiler's resolver. */
    public static FactPopulator builtin(ActorProbe probe) {
        return new FactPopulator(BuiltinVars.vocabulary(), probe);
    }

    /** A built-in populator backed by a shared {@link VarStore} so conditions can read {@code SET_VAR} dynamic vars. */
    public static FactPopulator builtin(VarStore vars, ActorProbe probe) {
        return new FactPopulator(BuiltinVars.vocabulary(), vars, t -> null, probe);
    }

    /** The production form: the built-in vocabulary over the SHARED store aggregate, so every store-backed fact
     *  ({@code %ragestacks%}, {@code %heldticks%}) reads the same instance the writers hold. */
    public static FactPopulator builtin(EngineStores stores, ActorProbe probe) {
        return new FactPopulator(BuiltinVars.vocabulary(), stores.vars(), t -> null, probe, stores);
    }

    /** Install the {@code rand(lo,hi)} draw source (the composition root owns the RNG); returns {@code this} to chain. */
    public FactPopulator randomSource(DoubleSupplier source) {
        this.random = source == null ? () -> 0.0 : source;
        return this;
    }

    public FactBuffer populate(ActivationContext context) {
        return populate(context, 0L, FactMask.ALL);
    }

    /** Populate every fact (unmasked) — the compatibility entry for callers with no per-wearer mask. */
    public FactBuffer populate(ActivationContext context, long nowTicks) {
        return populate(context, nowTicks, FactMask.ALL);
    }

    /**
     * The thread-local buffer, cleared and repopulated from {@code context}, computing ONLY the slots
     * {@code mask} marks referenced (ADR-0039) — an unreferenced derived fact (the {@code %nearbyenemies%}
     * entity scan, {@code %distance%}, {@code %victim.inzone%}, {@code %actor.groundblock%}) is skipped
     * entirely. {@link FactBuffer#clear()} still zeroes every slot, so an unreferenced slot reads its
     * default, never a stale value. Returns the shared instance, valid until this method is next called on
     * this thread. The unknown-token resolver reads the activator's dynamic var (at {@code nowTicks}) before
     * PAPI — the read side of {@code SET_VAR}.
     */
    public FactBuffer populate(ActivationContext context, long nowTicks, FactMask mask) {
        FactBuffer facts = buffer.get();
        facts.clear();
        facts.randomSource(random); // clear() resets it, so re-install before any expression can draw
        if (context != null) {
            // The keyed potion families own no fact slot, so they are bound (not populated) and cost nothing
            // until a condition node actually reaches them. Rebinding a thread-local keeps the hit allocation-free.
            PotionBinding potions = potionBinding.get();
            potions.bind(context.actor(), context.victim());
            facts.potionLevels(potions);
            EnchantBinding enchants = enchantBinding.get();
            enchants.bind(uuidOf(context.actor()), uuidOf(context.victim()));
            facts.enchantLevels(enchants);
            CrystalBinding crystals = crystalBinding.get();
            crystals.bind(uuidOf(context.actor()), uuidOf(context.victim()));
            facts.crystalCounts(crystals);
            populateActor(facts, context.actor(), mask);
            populateVictim(facts, context.victim(), mask);
            populateContext(facts, context, mask);
            populateDerived(facts, context, mask);
            Player actor = context.actor();
            if (actor != null) {
                UUID id = actor.getUniqueId();
                // %ragestacks% / %heldticks% / %actor.souls%: actor-scoped store reads, mask-gated (no entity
                // access, so no Folia guard needed).
                if (id != null && rageStacksSlot >= 0 && mask.readsNum(rageStacksSlot)) {
                    facts.setNumber(rageStacksSlot, rageStacks.current(id));
                }
                if (id != null && heldTicksSlot >= 0 && mask.readsNum(heldTicksSlot)) {
                    facts.setNumber(heldTicksSlot, heldSlots.ticksSince(id, nowTicks));
                }
                if (id != null && actorSoulsSlot >= 0 && mask.readsNum(actorSoulsSlot)) {
                    facts.setNumber(actorSoulsSlot, soulTotals.current(id));
                }
                // %actor.heroicpieces%: the count flattened onto the actor's own WornState, by UUID — the same
                // no-entity-read rule as its victim-side twin, so it costs nothing on the hit path.
                if (id != null && actorHeroicPiecesSlot >= 0 && mask.readsNum(actorHeroicPiecesSlot)) {
                    facts.setNumber(actorHeroicPiecesSlot, wornFactSource.heroicPieces(id));
                }
                // %actor.setweapon%: also flattened at equip, so "is my set weapon in hand" is a UUID lookup
                // and never a re-read of the live main hand.
                if (id != null && actorSetWeaponSlot >= 0 && mask.readsFlag(actorSetWeaponSlot)) {
                    facts.setFlag(actorSetWeaponSlot, wornFactSource.holdsSetWeapon(id));
                }
                // %status.teleblock%: a store read by UUID, mask-gated like the rest of this block.
                if (id != null && statusTeleblockSlot >= 0 && mask.readsFlag(statusTeleblockSlot)) {
                    facts.setFlag(statusTeleblockSlot, teleblock.isBlocked(id, nowTicks));
                }
                // %status.freeze%: liveness is the window's TICK budget, not its wall deadline — the same read
                // the freeze chain itself gates on, so the fact and the window can never disagree.
                if (id != null && statusFreezeSlot >= 0 && mask.readsFlag(statusFreezeSlot)) {
                    facts.setFlag(statusFreezeSlot, FrozenTargets.isFrozen(id));
                }
                // %bookrate.*%: the same UUID-keyed store read, one flag per armable site.
                if (id != null && bookRateGenerateSlot >= 0 && mask.readsFlag(bookRateGenerateSlot)) {
                    facts.setFlag(bookRateGenerateSlot, bookRate.armed(id, BookRateStore.GENERATE) > 0);
                }
                if (id != null && bookRateApplySlot >= 0 && mask.readsFlag(bookRateApplySlot)) {
                    facts.setFlag(bookRateApplySlot, bookRate.armed(id, BookRateStore.APPLY) > 0);
                }
                facts.papiResolver(token -> {
                    String value = vars.get(id, token, nowTicks);
                    return value != null ? value : papiDelegate.apply(token);
                });
            } else {
                facts.papiResolver(papiDelegate);
            }
            // %victim.var.<name>%: same store, victim's UUID. No entity read beyond getUniqueId(), so no
            // Folia guard is needed and a cross-region victim still resolves.
            LivingEntity victim = context.victim();
            if (victim != null) {
                UUID victimId = victim.getUniqueId();
                facts.victimVarResolver(name -> vars.get(victimId, name, nowTicks));
                // %victim.souls%: the cached total by UUID — a mob has no entry and reads 0. Same rule as the
                // victim vars above: no entity read beyond getUniqueId(), so a cross-region victim still resolves.
                if (victimId != null && victimSoulsSlot >= 0 && mask.readsNum(victimSoulsSlot)) {
                    facts.setNumber(victimSoulsSlot, soulTotals.current(victimId));
                }
                // %victim.heroicpieces%: the count flattened onto the victim's WornState, by UUID — same rule
                // again, so a heroic-gated ability prices a cross-region victim without touching them.
                if (victimId != null && victimHeroicPiecesSlot >= 0 && mask.readsNum(victimHeroicPiecesSlot)) {
                    facts.setNumber(victimHeroicPiecesSlot, wornFactSource.heroicPieces(victimId));
                }
            }
        }
        return facts;
    }

    /** {@code getUniqueId()} needs no region ownership, so a cross-region entity still yields its id. */
    private static UUID uuidOf(Entity entity) {
        return entity == null ? null : entity.getUniqueId();
    }

    private void populateActor(FactBuffer facts, Player actor, FactMask mask) {
        if (actor == null) {
            return;
        }
        try {
            for (ActorNum f : actorNum) {
                if (mask.readsNum(f.slot())) {
                    facts.setNumber(f.slot(), f.src().read(actor));
                }
            }
            for (ActorFlag f : actorFlag) {
                if (mask.readsFlag(f.slot())) {
                    facts.setFlag(f.slot(), f.src().read(actor));
                }
            }
            for (ActorStr f : actorStr) {
                if (mask.readsStr(f.slot())) {
                    facts.setString(f.slot(), f.src().read(actor)); // e.g. the %actor.groundblock% block lookup
                }
            }
        } catch (RuntimeException unreadable) {
            // Folia: actor owned by another region (cross-region shooter on ATTACK) — default, never abort.
            Regions.swallowed("FactPopulator.populateActor", unreadable);
        }
    }

    private void populateVictim(FactBuffer facts, LivingEntity victim, FactMask mask) {
        if (victim == null) {
            return;
        }
        try {
            for (VictimNum f : victimNum) {
                if (mask.readsNum(f.slot())) {
                    facts.setNumber(f.slot(), f.src().read(victim));
                }
            }
            for (VictimFlag f : victimFlag) {
                if (mask.readsFlag(f.slot())) {
                    facts.setFlag(f.slot(), f.src().read(victim));
                }
            }
            for (VictimStr f : victimStr) {
                if (mask.readsStr(f.slot())) {
                    facts.setString(f.slot(), f.src().read(victim));
                }
            }
        } catch (RuntimeException unreadable) {
            // Cross-region victim (e.g. the attacker exposed on the DEFENSE pass) or a read failure.
            Regions.swallowed("FactPopulator.populateVictim", unreadable);
        }
    }

    // World weather/time are global-region-owned on Folia, so wrapped: a wrong-thread read defaults only those facts.
    private void populateContext(FactBuffer facts, ActivationContext context, FactMask mask) {
        if (damageSlot >= 0 && mask.readsNum(damageSlot)) {
            facts.setNumber(damageSlot, context.damage());
        }
        if (comboSlot >= 0 && mask.readsNum(comboSlot)) {
            facts.setNumber(comboSlot, context.combo());
        }
        // ADR-0049 context facts: the gank counts + cause + item-damage flag are computed by the dispatcher on the
        // firing thread and carried on the context (never a live entity read here), so they need no Folia guard.
        if (recentAttackersSlot >= 0 && mask.readsNum(recentAttackersSlot)) {
            facts.setNumber(recentAttackersSlot, context.recentAttackers());
        }
        if (attackerIndexSlot >= 0 && mask.readsNum(attackerIndexSlot)) {
            facts.setNumber(attackerIndexSlot, context.attackerIndex());
        }
        if (damageCauseSlot >= 0 && mask.readsStr(damageCauseSlot)) {
            facts.setString(damageCauseSlot, context.damageCauseName());
        }
        if (itemDamageArmorSlot >= 0 && mask.readsFlag(itemDamageArmorSlot)) {
            facts.setFlag(itemDamageArmorSlot, context.itemDamageArmor());
        }
        // The durability read is taken at the ITEM_DAMAGE site off the exact damaged stack, so nothing is read
        // from a live item here. NaN means "no damaged item on this activation" (every other trigger, and an
        // item with no durability bar) and the slot keeps its cleared 0 — a reading of 0 means SPENT.
        if (itemDurabilitySlot >= 0 && mask.readsNum(itemDurabilitySlot)
                && !Double.isNaN(context.itemDurabilityPercent())) {
            facts.setNumber(itemDurabilitySlot, context.itemDurabilityPercent());
        }
        // Projectile geometry is likewise differenced by the dispatcher on the firing thread, so no live
        // projectile is read here and no Folia guard is needed.
        if (impactHeightSlot >= 0 && mask.readsNum(impactHeightSlot)) {
            facts.setNumber(impactHeightSlot, context.impactHeight());
        }
        if (projectileKindSlot >= 0 && mask.readsStr(projectileKindSlot)) {
            facts.setString(projectileKindSlot, context.projectileKind());
        }
        if (equipChangeSlot >= 0 && mask.readsStr(equipChangeSlot)) {
            facts.setString(equipChangeSlot, context.equipChange());
        }
        if (proximityTagSlot >= 0 && mask.readsStr(proximityTagSlot)) {
            facts.setString(proximityTagSlot, context.proximityTag());
        }
        boolean wantsBlockType = blockTypeSlot >= 0 && mask.readsStr(blockTypeSlot);
        boolean wantsIsBlock = isBlockSlot >= 0 && mask.readsFlag(isBlockSlot);
        org.bukkit.block.Block block = context.block();
        if (block != null && (wantsBlockType || wantsIsBlock)) {
            try {
                org.bukkit.Material type = block.getType();
                if (wantsBlockType) {
                    facts.setString(blockTypeSlot, type.name());
                }
                if (wantsIsBlock) {
                    facts.setFlag(isBlockSlot, !probe.isAir(type));
                }
            } catch (RuntimeException unreadable) {
                // A block owned by another region — leave the block facts defaulted.
                Regions.swallowed("FactPopulator.populateContext.block", unreadable);
            }
        }
        boolean wantsRaining = worldRainingSlot >= 0 && mask.readsFlag(worldRainingSlot);
        boolean wantsThundering = worldThunderingSlot >= 0 && mask.readsFlag(worldThunderingSlot);
        boolean wantsTime = worldTimeSlot >= 0 && mask.readsNum(worldTimeSlot);
        if (wantsRaining || wantsThundering || wantsTime) {
            try {
                org.bukkit.World world = context.actor() != null ? context.actor().getWorld()
                        : context.location() != null ? context.location().getWorld() : null;
                if (world != null) {
                    if (wantsRaining) {
                        facts.setFlag(worldRainingSlot, world.hasStorm());
                    }
                    if (wantsThundering) {
                        facts.setFlag(worldThunderingSlot, world.isThundering());
                    }
                    if (wantsTime) {
                        facts.setNumber(worldTimeSlot, world.getTime());
                    }
                }
            } catch (RuntimeException unreadable) {
                // Folia: weather/time are global-region-owned; a wrong-thread read defaults only these facts.
                Regions.swallowed("FactPopulator.populateContext.world", unreadable);
            }
        }
    }

    // Derived combat geometry (distance, nearbyenemies, victim.inzone); Folia-wrapped like the entity facts
    // (default cross-region). ADR-0039: each slot is mask-gated, so the expensive %nearbyenemies% entity scan
    // runs ONLY when a worn condition/arg actually reads it.
    private void populateDerived(FactBuffer facts, ActivationContext context, FactMask mask) {
        boolean wantsDistance = distanceSlot >= 0 && mask.readsNum(distanceSlot);
        boolean wantsInZone = victimInZoneSlot >= 0 && mask.readsFlag(victimInZoneSlot);
        boolean wantsNearby = nearbyEnemiesSlot >= 0 && mask.readsNum(nearbyEnemiesSlot);
        boolean wantsBehind = actorBehindVictimSlot >= 0 && mask.readsFlag(actorBehindVictimSlot);
        boolean wantsBelow = actorBelowVictimSlot >= 0 && mask.readsNum(actorBelowVictimSlot);
        boolean wantsAllies = nearbyAlliesSlot >= 0 && mask.readsNum(nearbyAlliesSlot);
        boolean wantsRelation = victimRelationSlot >= 0 && mask.readsStr(victimRelationSlot);
        boolean wantsPostHit = postHitHealthSlot >= 0 && mask.readsNum(postHitHealthSlot);
        if (!wantsDistance && !wantsInZone && !wantsNearby && !wantsBehind && !wantsBelow
                && !wantsAllies && !wantsRelation && !wantsPostHit) {
            return;
        }
        org.bukkit.entity.Player actor = context.actor();
        if (actor == null) {
            return;
        }
        try {
            if (wantsDistance) {
                LivingEntity victim = context.victim();
                if (victim != null && victim.getWorld() == actor.getWorld()) {
                    facts.setNumber(distanceSlot, actor.getLocation().distance(victim.getLocation()));
                }
            }
            if (wantsInZone) {
                LivingEntity victim = context.victim();
                if (victim != null) {
                    // The zone is owned by the activating wearer; true when the victim stands in one of theirs.
                    facts.setFlag(victimInZoneSlot,
                            engine.sink.OwnerZones.contains(actor.getUniqueId(), victim.getLocation()));
                }
            }
            if (wantsNearby || wantsAllies) {
                int living = 0;
                int allies = 0;
                // ONE scan feeds both counts — the entity walk is the expensive part, and asking for allies
                // must not double it for an ability that reads both.
                for (org.bukkit.entity.Entity e : actor.getNearbyEntities(NEARBY_RADIUS, NEARBY_RADIUS, NEARBY_RADIUS)) {
                    if (!(e instanceof LivingEntity) || e.equals(actor)) {
                        continue;
                    }
                    living++;
                    if (wantsAllies && e instanceof org.bukkit.entity.Player nearby && Allies.allied(actor, nearby)) {
                        allies++;
                    }
                }
                if (wantsNearby) {
                    facts.setNumber(nearbyEnemiesSlot, living);
                }
                if (wantsAllies) {
                    facts.setNumber(nearbyAlliesSlot, allies);
                }
            }
            if (wantsRelation) {
                LivingEntity victim = context.victim();
                facts.setString(victimRelationSlot, relationOf(actor, victim));
            }
            if (wantsPostHit && !Double.isNaN(context.vanillaFinalDamage())) {
                // NaN means "no hit is pending on this activation" (every non-DEFENSE context), and the slot
                // keeps its cleared 0 — distinct from a real 0-damage hit, which prices at full health.
                facts.setNumber(postHitHealthSlot, actor.getHealth() - context.vanillaFinalDamage());
            }
            if (wantsBehind) {
                LivingEntity victim = context.victim();
                if (victim != null && victim.getWorld() == actor.getWorld()) {
                    facts.setFlag(actorBehindVictimSlot, behindVictim(actor, victim));
                }
            }
            if (wantsBelow) {
                LivingEntity victim = context.victim();
                if (victim != null && victim.getWorld() == actor.getWorld()) {
                    // How far the actor's feet sit BELOW the victim's (ADR-0052 Eagle) — the threshold is
                    // authored on the condition (e.g. %actor.belowvictim% > 1.5), never hardcoded here.
                    facts.setNumber(actorBelowVictimSlot,
                            victim.getLocation().getY() - actor.getLocation().getY());
                }
            }
        } catch (RuntimeException unreadable) {
            // Cross-region actor (Folia) or a read failure — leave the derived facts defaulted.
            Regions.swallowed("FactPopulator.populateDerived", unreadable);
        }
    }

    /**
     * Whether {@code actor} stands behind {@code victim}'s body facing (ADR-0049 Rogue): the horizontal angle
     * between the victim's facing (yaw → direction, y flattened) and the victim→actor vector exceeds 90°, i.e.
     * their dot is negative. Only the SIGN matters, so neither vector is normalised (a zero vector — same column,
     * or a straight-up look — yields dot 0 = not behind).
     */
    private static boolean behindVictim(Player actor, LivingEntity victim) {
        org.bukkit.util.Vector facing = victim.getLocation().getDirection().setY(0);
        org.bukkit.util.Vector toActor = actor.getLocation().toVector()
                .subtract(victim.getLocation().toVector()).setY(0);
        return facing.dot(toActor) < 0;
    }

    /** Cross-version-stable {@code getMaxHealth()} (the Attribute API flipped at 1.21.3). */
    @SuppressWarnings("deprecation")
    private static double maxHealth(LivingEntity entity) {
        return entity.getMaxHealth();
    }

    /** Health as a percentage of max (0–100); 0 when max health is non-positive. */
    private static double healthPercent(LivingEntity entity) {
        double max = maxHealth(entity);
        return max > 0 ? 100.0 * entity.getHealth() / max : 0.0;
    }

    /** Cross-version-stable {@code isOnGround()}; deprecated-not-removed (client-reported) across the range. */
    @SuppressWarnings("deprecation")
    private static boolean onGround(Player player) {
        return player.isOnGround();
    }

    private void addActorNum(VarVocabulary v, String key, ActorD src) {
        int slot = slot(v, key, VarKind.NUM);
        if (slot >= 0) {
            actorNum.add(new ActorNum(slot, src));
        }
    }

    private void addActorFlag(VarVocabulary v, String key, ActorB src) {
        int slot = slot(v, key, VarKind.BOOL);
        if (slot >= 0) {
            actorFlag.add(new ActorFlag(slot, src));
        }
    }

    private void addActorStr(VarVocabulary v, String key, ActorS src) {
        int slot = slot(v, key, VarKind.STR);
        if (slot >= 0) {
            actorStr.add(new ActorStr(slot, src));
        }
    }

    private void addVictimNum(VarVocabulary v, String key, VictimD src) {
        int slot = slot(v, key, VarKind.NUM);
        if (slot >= 0) {
            victimNum.add(new VictimNum(slot, src));
        }
    }

    private void addVictimFlag(VarVocabulary v, String key, VictimB src) {
        int slot = slot(v, key, VarKind.BOOL);
        if (slot >= 0) {
            victimFlag.add(new VictimFlag(slot, src));
        }
    }

    private void addVictimStr(VarVocabulary v, String key, VictimS src) {
        int slot = slot(v, key, VarKind.STR);
        if (slot >= 0) {
            victimStr.add(new VictimStr(slot, src));
        }
    }

    /** Resolve a {@code scope.name} (or bare {@code name}) key to its slot for {@code kind}, or −1 if absent. */
    private static int slot(VarVocabulary v, String key, VarKind kind) {
        int dot = key.indexOf('.');
        String scope = dot < 0 ? null : key.substring(0, dot);
        String name = dot < 0 ? key : key.substring(dot + 1);
        return v.lookup(scope, name).filter(b -> b.kind() == kind).map(VarBinding::slot).orElse(-1);
    }
}
