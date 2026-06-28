package testfx;

import compile.def.AbilityDef;
import compile.model.SourceKind;
import schema.diag.Source;
import schema.grammar.EffectLine;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builders for the compiler's pre-compilation shapes. The 18-field {@link AbilityDef} constructor
 * was re-declared as a private {@code def(...)} helper in every stage test, so a new record field forced an
 * edit across all of them; routing construction through one builder makes a record-arity change a one-place
 * change. Each setter overrides a sensible default (an ENCHANT, level 1, 100% chance, single ATTACK trigger,
 * no cooldown/condition), so a test states only what it cares about.
 */
public final class Defs {

    private Defs() {
    }

    public static AbilityBuilder ability() {
        return new AbilityBuilder();
    }

    public static final class AbilityBuilder {
        private SourceKind sourceKind = SourceKind.ENCHANT;
        private String stableKey = "enchants/test";
        private int defId = 1;
        private int level = 1;
        private double baseChance = 100.0;
        private int cooldownTicks = 0;
        private int soulCost = 0;
        private List<String> triggers = new ArrayList<>(List.of("ATTACK"));
        private List<String> worldBlacklist = new ArrayList<>();
        private String conditionExpr = null;
        private List<EffectLine> effects = new ArrayList<>();
        private List<String> rawEffects = null;
        private String suppressKey = null;
        private String cdScopeEnchant = null;
        private String cdScopeGroup = null;
        private String cdScopeType = null;
        private int repeatTicks = 0;
        private Source source = Source.UNKNOWN;
        private int setPieces = 0;

        public AbilityBuilder sourceKind(SourceKind sourceKind) {
            this.sourceKind = sourceKind;
            return this;
        }

        public AbilityBuilder stableKey(String stableKey) {
            this.stableKey = stableKey;
            return this;
        }

        public AbilityBuilder defId(int defId) {
            this.defId = defId;
            return this;
        }

        public AbilityBuilder level(int level) {
            this.level = level;
            return this;
        }

        public AbilityBuilder chance(double baseChance) {
            this.baseChance = baseChance;
            return this;
        }

        public AbilityBuilder cooldown(int cooldownTicks) {
            this.cooldownTicks = cooldownTicks;
            return this;
        }

        public AbilityBuilder soulCost(int soulCost) {
            this.soulCost = soulCost;
            return this;
        }

        public AbilityBuilder triggers(String... triggers) {
            this.triggers = new ArrayList<>(List.of(triggers));
            return this;
        }

        public AbilityBuilder worldBlacklist(String... worlds) {
            this.worldBlacklist = new ArrayList<>(List.of(worlds));
            return this;
        }

        public AbilityBuilder condition(String conditionExpr) {
            this.conditionExpr = conditionExpr;
            return this;
        }

        /** Pre-parsed effect lines. */
        public AbilityBuilder effects(EffectLine... effects) {
            this.effects = new ArrayList<>(List.of(effects));
            this.rawEffects = null;
            return this;
        }

        /** Raw effect-line text, lexed against {@link #source(Source)} at {@link #build()}. */
        public AbilityBuilder effectLines(String... raw) {
            this.rawEffects = new ArrayList<>(List.of(raw));
            return this;
        }

        public AbilityBuilder suppressKey(String suppressKey) {
            this.suppressKey = suppressKey;
            return this;
        }

        public AbilityBuilder cooldownScope(String enchant, String group, String type) {
            this.cdScopeEnchant = enchant;
            this.cdScopeGroup = group;
            this.cdScopeType = type;
            return this;
        }

        public AbilityBuilder repeatTicks(int repeatTicks) {
            this.repeatTicks = repeatTicks;
            return this;
        }

        public AbilityBuilder source(Source source) {
            this.source = source;
            return this;
        }

        public AbilityBuilder setPieces(int setPieces) {
            this.setPieces = setPieces;
            return this;
        }

        public AbilityDef build() {
            List<EffectLine> lines = effects;
            if (rawEffects != null) {
                lines = new ArrayList<>();
                for (String raw : rawEffects) {
                    lines.add(EffectLine.parse(raw, source));
                }
            }
            return new AbilityDef(sourceKind, stableKey, defId, level, baseChance, cooldownTicks, soulCost,
                    triggers, worldBlacklist, conditionExpr, lines, suppressKey, cdScopeEnchant, cdScopeGroup,
                    cdScopeType, repeatTicks, source, setPieces);
        }
    }
}
