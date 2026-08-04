package testfx;

import engine.sink.GearProtection;
import engine.sink.PlayerVisibility;
import engine.sink.SinkEnv;
import engine.sink.SoulDebit;
import engine.sink.SummonPayloads;
import engine.stores.BatteryStore;
import engine.stores.ComboStore;
import engine.stores.CooldownStore;
import engine.stores.DamageCapStore;
import engine.stores.DisarmWindowStore;
import engine.stores.EngineStores;
import engine.stores.HeldSlotStore;
import engine.stores.HitTempoStore;
import engine.stores.ImmuneStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.OutgoingDebuffStore;
import engine.stores.RageStackStore;
import engine.stores.RecentAttackersStore;
import engine.stores.ReflectMarksStore;
import engine.stores.SoulTotalStore;
import engine.stores.SuppressionStore;
import engine.stores.TeleblockStore;
import engine.stores.VarStore;
import engine.stores.WardStore;
import engine.stores.WhyStore;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.ToDoubleFunction;
import platform.economy.EconomyService;

/**
 * Fluent builder for a {@link SinkEnv} with per-store slots — a test overrides only the store it exercises and
 * gets fresh instances for the rest. {@link SinkEnvBuilder#stores(EngineStores)} fully overrides the aggregate
 * (the per-store slots are then ignored).
 */
public final class Envs {

    private Envs() {
    }

    /** A {@link SinkEnv} with no economy/souls, tick 0, and a fresh store for each slot. */
    public static SinkEnvBuilder sink() {
        return new SinkEnvBuilder();
    }

    public static final class SinkEnvBuilder {
        private EconomyService economy = EconomyService.NONE;
        private SoulDebit souls = SoulDebit.NONE;
        private LongSupplier nowTicks = () -> 0L;
        private VarStore vars = new VarStore();
        private SuppressionStore suppression = new SuppressionStore();
        private KnockbackControlStore knockback = new KnockbackControlStore();
        private KeepOnDeathStore keepOnDeath = new KeepOnDeathStore();
        private TeleblockStore teleblock = new TeleblockStore();
        private ImmuneStore immune = new ImmuneStore();
        private CooldownStore cooldowns = new CooldownStore();
        private ComboStore combo = new ComboStore();
        private WhyStore why = new WhyStore();
        private RecentAttackersStore recentAttackers = new RecentAttackersStore();
        private ReflectMarksStore reflectMarks = new ReflectMarksStore();
        private OutgoingDebuffStore outgoingDebuff = new OutgoingDebuffStore();
        private DamageCapStore damageCap = new DamageCapStore();
        private RageStackStore rageStacks = new RageStackStore();
        private WardStore ward = new WardStore();
        private ToDoubleFunction<UUID> lightningBoost = id -> 0.0;
        private engine.sink.PermanentPotions permanentPotions = engine.sink.PermanentPotions.NONE;
        private SummonPayloads payloads = SummonPayloads.NONE;
        private EngineStores storesOverride = null;

        public SinkEnvBuilder economy(EconomyService economy) {
            this.economy = economy;
            return this;
        }

        public SinkEnvBuilder souls(SoulDebit souls) {
            this.souls = souls;
            return this;
        }

        public SinkEnvBuilder nowTicks(LongSupplier nowTicks) {
            this.nowTicks = nowTicks;
            return this;
        }

        public SinkEnvBuilder vars(VarStore vars) {
            this.vars = vars;
            return this;
        }

        public SinkEnvBuilder suppression(SuppressionStore suppression) {
            this.suppression = suppression;
            return this;
        }

        public SinkEnvBuilder knockback(KnockbackControlStore knockback) {
            this.knockback = knockback;
            return this;
        }

        public SinkEnvBuilder keepOnDeath(KeepOnDeathStore keepOnDeath) {
            this.keepOnDeath = keepOnDeath;
            return this;
        }

        public SinkEnvBuilder teleblock(TeleblockStore teleblock) {
            this.teleblock = teleblock;
            return this;
        }

        public SinkEnvBuilder immune(ImmuneStore immune) {
            this.immune = immune;
            return this;
        }

        public SinkEnvBuilder cooldowns(CooldownStore cooldowns) {
            this.cooldowns = cooldowns;
            return this;
        }

        public SinkEnvBuilder combo(ComboStore combo) {
            this.combo = combo;
            return this;
        }

        public SinkEnvBuilder why(WhyStore why) {
            this.why = why;
            return this;
        }

        public SinkEnvBuilder recentAttackers(RecentAttackersStore recentAttackers) {
            this.recentAttackers = recentAttackers;
            return this;
        }

        public SinkEnvBuilder reflectMarks(ReflectMarksStore reflectMarks) {
            this.reflectMarks = reflectMarks;
            return this;
        }

        public SinkEnvBuilder outgoingDebuff(OutgoingDebuffStore outgoingDebuff) {
            this.outgoingDebuff = outgoingDebuff;
            return this;
        }

        public SinkEnvBuilder damageCap(DamageCapStore damageCap) {
            this.damageCap = damageCap;
            return this;
        }

        public SinkEnvBuilder rageStacks(RageStackStore rageStacks) {
            this.rageStacks = rageStacks;
            return this;
        }

        public SinkEnvBuilder ward(WardStore ward) {
            this.ward = ward;
            return this;
        }

        /** The worn LIGHTNING_MOD channel read at bolt emit (ADR-0063); default no boost. */
        /** ADR-0072: the wearer's own permanent-while-worn potions, which a HARMFUL cleanse must spare. */
        public SinkEnvBuilder permanentPotions(engine.sink.PermanentPotions permanentPotions) {
            this.permanentPotions = permanentPotions;
            return this;
        }

        public SinkEnvBuilder lightningBoost(ToDoubleFunction<UUID> lightningBoost) {
            this.lightningBoost = lightningBoost;
            return this;
        }

        /** The SUMMON_PAYLOAD seam a periodic summon pulses through; default fires nothing. */
        public SinkEnvBuilder payloads(SummonPayloads payloads) {
            this.payloads = payloads;
            return this;
        }

        /** Fully override the aggregate; the per-store slots are then ignored. */
        public SinkEnvBuilder stores(EngineStores stores) {
            this.storesOverride = stores;
            return this;
        }

        public SinkEnv build() {
            EngineStores stores = storesOverride != null ? storesOverride
                    : new EngineStores(vars, suppression, knockback, keepOnDeath, teleblock, immune, cooldowns,
                            combo, why, recentAttackers, reflectMarks, outgoingDebuff, damageCap, rageStacks, ward,
                            new HitTempoStore(), new BatteryStore(), new DisarmWindowStore(),
                            new HeldSlotStore(), new SoulTotalStore(), new engine.stores.DotAmplifyStore(),
                            new engine.stores.HeadTrophyStore(), new engine.stores.FoodWindowStore(),
                            new engine.stores.MessageThrottleStore(), new engine.stores.SoulEscalationStore(),
                            new engine.stores.DotSuppressionStore(), new engine.stores.ReboundStore());
            return SinkEnv.of(economy, souls, stores, nowTicks, player -> { }, () -> 0,
                    GearProtection.NONE, lightningBoost, PlayerVisibility.NONE, permanentPotions, payloads);
        }
    }
}
