import type {ReactNode} from 'react';
import clsx from 'clsx';

import type {LevelState, EffectState} from './types';
import {EFFECTS, newEffect} from './catalog';
import EffectEditor from './EffectEditor';
import styles from './creator.module.css';

interface LevelEditorProps {
  level: LevelState;
  index: number;
  count: number;
  /** issue paths scoped to this level (e.g. "chance", "effect.0.param.x") */
  errors: Map<string, string>;
  onChange: (level: LevelState) => void;
  onRemove: () => void;
  onMove: (dir: -1 | 1) => void;
}

export default function LevelEditor({
  level,
  index,
  count,
  errors,
  onChange,
  onRemove,
  onMove,
}: LevelEditorProps): ReactNode {
  function setField(field: keyof LevelState, value: string): void {
    onChange({...level, [field]: value});
  }

  function addEffect(): void {
    const head = EFFECTS[0]?.head ?? 'MESSAGE';
    onChange({...level, effects: [...level.effects, newEffect(head)]});
  }

  function updateEffect(idx: number, next: EffectState): void {
    onChange({
      ...level,
      effects: level.effects.map((e, i) => (i === idx ? next : e)),
    });
  }

  function removeEffect(idx: number): void {
    onChange({...level, effects: level.effects.filter((_, i) => i !== idx)});
  }

  function moveEffect(idx: number, dir: -1 | 1): void {
    const target = idx + dir;
    if (target < 0 || target >= level.effects.length) return;
    const next = [...level.effects];
    [next[idx], next[target]] = [next[target], next[idx]];
    onChange({...level, effects: next});
  }

  /** error paths scoped to one effect at idx, with the prefix stripped. */
  function effectErrors(idx: number): Map<string, string> {
    const prefix = `effect.${idx}.`;
    const out = new Map<string, string>();
    for (const [path, msg] of errors) {
      if (path.startsWith(prefix)) out.set(path.slice(prefix.length), msg);
    }
    return out;
  }

  return (
    <div className={styles.level}>
      <div className={styles.levelHeader}>
        <span className={styles.levelBadge}>Level {index + 1}</span>
        <div className={styles.headerActions}>
          <button
            type="button"
            className={`${styles.btn} ${styles.btnIcon}`}
            onClick={() => onMove(-1)}
            disabled={index === 0}
            title="Move level up"
            aria-label="Move level up">
            ↑
          </button>
          <button
            type="button"
            className={`${styles.btn} ${styles.btnIcon}`}
            onClick={() => onMove(1)}
            disabled={index === count - 1}
            title="Move level down"
            aria-label="Move level down">
            ↓
          </button>
          <button
            type="button"
            className={`${styles.btn} ${styles.btnIcon} ${styles.btnDanger}`}
            onClick={onRemove}
            disabled={count === 1}
            title="Remove level"
            aria-label="Remove level">
            ✕
          </button>
        </div>
      </div>

      <div className={styles.grid2}>
        <div className={styles.field}>
          <label className={styles.label}>
            chance<span className={styles.required}>*</span>
            <span className={styles.hint}> (0–100%)</span>
          </label>
          <input
            className={clsx(styles.input, errors.has('chance') && styles.inputError)}
            type="number"
            min={0}
            max={100}
            value={level.chance}
            onChange={(e) => setField('chance', e.target.value)}
          />
          {errors.get('chance') && (
            <span className={styles.errorText}>{errors.get('chance')}</span>
          )}
        </div>

        <div className={styles.field}>
          <label className={styles.label}>
            cooldown<span className={styles.hint}> (seconds, optional)</span>
          </label>
          <input
            className={clsx(styles.input, errors.has('cooldown') && styles.inputError)}
            type="number"
            min={0}
            value={level.cooldown}
            placeholder="none"
            onChange={(e) => setField('cooldown', e.target.value)}
          />
          {errors.get('cooldown') && (
            <span className={styles.errorText}>{errors.get('cooldown')}</span>
          )}
        </div>

        <div className={styles.field}>
          <label className={styles.label}>
            souls<span className={styles.hint}> (cost, optional)</span>
          </label>
          <input
            className={clsx(styles.input, errors.has('souls') && styles.inputError)}
            type="number"
            min={0}
            value={level.souls}
            placeholder="none"
            onChange={(e) => setField('souls', e.target.value)}
          />
          {errors.get('souls') && (
            <span className={styles.errorText}>{errors.get('souls')}</span>
          )}
        </div>

        <div className={styles.field}>
          <label className={styles.label}>
            condition<span className={styles.hint}> (optional)</span>
          </label>
          <input
            className={styles.input}
            type="text"
            value={level.condition}
            placeholder="e.g. %victim.healthpercent% < 30 %force%"
            onChange={(e) => setField('condition', e.target.value)}
          />
        </div>
      </div>

      <div style={{marginTop: '0.85rem'}}>
        <div className={styles.targetLabel}>
          <span>effects</span>
        </div>
        {level.effects.length === 0 && (
          <p
            className={clsx(
              styles.emptyHint,
              errors.has('effects') && styles.errorText,
            )}>
            {errors.get('effects') ?? 'No effects yet — add one below.'}
          </p>
        )}
        {level.effects.map((effect, idx) => (
          <EffectEditor
            key={effect.id}
            effect={effect}
            index={idx}
            count={level.effects.length}
            errors={effectErrors(idx)}
            onChange={(next) => updateEffect(idx, next)}
            onRemove={() => removeEffect(idx)}
            onMove={(dir) => moveEffect(idx, dir)}
          />
        ))}
        <button
          type="button"
          className={`${styles.btn} ${styles.btnSmall}`}
          onClick={addEffect}>
          + Add effect
        </button>
      </div>
    </div>
  );
}
