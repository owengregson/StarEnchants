package testfx;

import engine.sink.SinkEnv;
import engine.sink.SoulDebit;
import engine.stores.ComboStore;
import engine.stores.CooldownStore;
import engine.stores.DamageCapStore;
import engine.stores.EngineStores;
import engine.stores.ImmuneStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.OutgoingDebuffStore;
import engine.stores.RecentAttackersStore;
import engine.stores.ReflectMarksStore;
import engine.stores.SuppressionStore;
import engine.stores.TeleblockStore;
import engine.stores.VarStore;
import engine.stores.WhyStore;
import java.util.function.LongSupplier;
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

        /** Fully override the aggregate; the per-store slots are then ignored. */
        public SinkEnvBuilder stores(EngineStores stores) {
            this.storesOverride = stores;
            return this;
        }

        public SinkEnv build() {
            EngineStores stores = storesOverride != null ? storesOverride
                    : new EngineStores(vars, suppression, knockback, keepOnDeath, teleblock, immune, cooldowns,
                            combo, why, recentAttackers, reflectMarks, outgoingDebuff, damageCap);
            return SinkEnv.of(economy, souls, stores, nowTicks);
        }
    }
}
