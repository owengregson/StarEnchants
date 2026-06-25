import type {ReactNode} from 'react';

import type {EffectState, TargetState} from './types';
import {EFFECTS, SELECTORS, findEffect, findSelector, defaultTargets} from './catalog';
import {renderSelector} from './yaml';
import ParamInput from './ParamInput';
import styles from './creator.module.css';

interface EffectEditorProps {
  effect: EffectState;
  index: number;
  count: number;
  /** error paths scoped to this effect, e.g. "param.amount", "target.who.r" */
  errors: Map<string, string>;
  onChange: (effect: EffectState) => void;
  onRemove: () => void;
  onMove: (dir: -1 | 1) => void;
}

export default function EffectEditor({
  effect,
  index,
  count,
  errors,
  onChange,
  onRemove,
  onMove,
}: EffectEditorProps): ReactNode {
  const def = findEffect(effect.head);

  function setHead(head: string): void {
    // reset params + targets to the new head's schema
    onChange({...effect, head, params: {}, targets: defaultTargets(head)});
  }

  function setParam(name: string, value: string): void {
    onChange({...effect, params: {...effect.params, [name]: value}});
  }

  function setTarget(slot: string, next: TargetState): void {
    onChange({
      ...effect,
      targets: effect.targets.map((t) => (t.slot === slot ? next : t)),
    });
  }

  return (
    <div className={styles.effect}>
      <div className={styles.effectHeader}>
        <select
          className={styles.select}
          value={effect.head}
          onChange={(e) => setHead(e.target.value)}
          aria-label="Effect">
          {EFFECTS.map((e) => (
            <option key={e.head} value={e.head}>
              {e.head}
            </option>
          ))}
        </select>
        <button
          type="button"
          className={`${styles.btn} ${styles.btnIcon}`}
          onClick={() => onMove(-1)}
          disabled={index === 0}
          title="Move up"
          aria-label="Move effect up">
          ↑
        </button>
        <button
          type="button"
          className={`${styles.btn} ${styles.btnIcon}`}
          onClick={() => onMove(1)}
          disabled={index === count - 1}
          title="Move down"
          aria-label="Move effect down">
          ↓
        </button>
        <button
          type="button"
          className={`${styles.btn} ${styles.btnIcon} ${styles.btnDanger}`}
          onClick={onRemove}
          title="Remove effect"
          aria-label="Remove effect">
          ✕
        </button>
      </div>

      {def?.doc && <p className={styles.effectDoc}>{def.doc}</p>}

      {def && def.params.length > 0 && (
        <div className={styles.paramGrid}>
          {def.params.map((p) => (
            <ParamInput
              key={p.name}
              param={p}
              value={effect.params[p.name]}
              onChange={(v) => setParam(p.name, v)}
              error={errors.get(`param.${p.name}`)}
            />
          ))}
        </div>
      )}

      {def && def.params.length === 0 && def.targets.length === 0 && (
        <p className={styles.emptyHint}>This effect takes no arguments.</p>
      )}

      {effect.targets.map((t) => (
        <TargetEditor
          key={t.slot}
          target={t}
          errors={errors}
          onChange={(next) => setTarget(t.slot, next)}
        />
      ))}
    </div>
  );
}

interface TargetEditorProps {
  target: TargetState;
  errors: Map<string, string>;
  onChange: (target: TargetState) => void;
}

function TargetEditor({target, errors, onChange}: TargetEditorProps): ReactNode {
  const sel = findSelector(target.selector);

  function setSelector(selector: string): void {
    onChange({...target, selector, params: {}});
  }

  function setParam(name: string, value: string): void {
    onChange({...target, params: {...target.params, [name]: value}});
  }

  return (
    <div className={styles.targetBlock}>
      <div className={styles.targetLabel}>
        <span>
          target: <code>{target.slot}</code>
        </span>
        <span className={styles.targetToken}>{renderSelector(target)}</span>
      </div>
      <div className={styles.field}>
        <select
          className={styles.select}
          value={target.selector}
          onChange={(e) => setSelector(e.target.value)}
          aria-label={`Selector for ${target.slot}`}>
          {SELECTORS.map((s) => (
            <option key={s.head} value={s.head}>
              {'@' + s.head}
            </option>
          ))}
        </select>
        {sel?.doc && <span className={styles.hint}>{sel.doc}</span>}
      </div>
      {sel && sel.params.length > 0 && (
        <div className={styles.paramGrid} style={{marginTop: '0.5rem'}}>
          {sel.params.map((p) => (
            <ParamInput
              key={p.name}
              param={p}
              value={target.params[p.name]}
              onChange={(v) => setParam(p.name, v)}
              error={errors.get(`target.${target.slot}.${p.name}`)}
            />
          ))}
        </div>
      )}
    </div>
  );
}
