import {useState} from 'react';
import type {ReactNode} from 'react';
import clsx from 'clsx';

import type {EnchantState} from './types';
import {TIERS, ITEM_CATEGORIES, TRIGGERS} from './catalog';
import {sanitizeKey, keyFromDisplay} from '@site/src/lib/se-codec';
import styles from './creator.module.css';

interface MetaPanelProps {
  state: EnchantState;
  errors: Map<string, string>;
  onChange: (next: EnchantState) => void;
}

export default function MetaPanel({
  state,
  errors,
  onChange,
}: MetaPanelProps): ReactNode {
  const [customCat, setCustomCat] = useState('');

  function setDisplay(display: string): void {
    // key tracks the display name until the user edits it directly
    const key = state.keyTouched ? state.key : keyFromDisplay(display);
    onChange({...state, display, key});
  }

  function setKey(raw: string): void {
    onChange({...state, key: sanitizeKey(raw), keyTouched: true});
  }

  function toggleCategory(cat: string): void {
    const has = state.appliesTo.includes(cat);
    onChange({
      ...state,
      appliesTo: has
        ? state.appliesTo.filter((c) => c !== cat)
        : [...state.appliesTo, cat],
    });
  }

  function addCustomCategory(): void {
    const cat = customCat.trim().toUpperCase().replace(/[^A-Z0-9_]/g, '_');
    if (cat && !state.appliesTo.includes(cat)) {
      onChange({...state, appliesTo: [...state.appliesTo, cat]});
    }
    setCustomCat('');
  }

  const customCats = state.appliesTo.filter(
    (c) => !ITEM_CATEGORIES.includes(c as never),
  );

  return (
    <section className={styles.panel}>
      <div className={styles.panelHeader}>
        <h2 className={styles.panelTitle}>Enchant details</h2>
      </div>

      <div className={styles.grid2}>
        <div className={styles.field}>
          <label className={styles.label}>
            Display name<span className={styles.required}>*</span>
          </label>
          <input
            className={clsx(
              styles.input,
              errors.has('meta.display') && styles.inputError,
            )}
            type="text"
            value={state.display}
            placeholder="&bFrostbite"
            onChange={(e) => setDisplay(e.target.value)}
          />
          <span className={styles.hint}>
            Use <code>&amp;</code>-codes to colour it (e.g.{' '}
            <code>&amp;bFrostbite</code> is aqua).
          </span>
          {errors.get('meta.display') && (
            <span className={styles.errorText}>{errors.get('meta.display')}</span>
          )}
        </div>

        <div className={styles.field}>
          <label className={styles.label}>
            Key<span className={styles.required}>*</span>
          </label>
          <input
            className={clsx(
              styles.input,
              errors.has('meta.key') && styles.inputError,
            )}
            type="text"
            value={state.key}
            placeholder="frostbite"
            onChange={(e) => setKey(e.target.value)}
          />
          <span className={styles.hint}>
            File name &amp; identity: <code>content/enchants/{state.key || '…'}.yml</code>
          </span>
          {errors.get('meta.key') && (
            <span className={styles.errorText}>{errors.get('meta.key')}</span>
          )}
        </div>

        <div className={clsx(styles.field, styles.fieldFull)}>
          <label className={styles.label}>Description</label>
          <input
            className={styles.input}
            type="text"
            value={state.description}
            placeholder="Chill and slow the enemy you strike."
            onChange={(e) => onChange({...state, description: e.target.value})}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label}>Tier</label>
          <select
            className={styles.select}
            value={state.tier}
            onChange={(e) => onChange({...state, tier: e.target.value})}>
            {TIERS.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </div>

        <div className={styles.field}>
          <label className={styles.label}>Trigger</label>
          <select
            className={styles.select}
            value={state.trigger}
            onChange={(e) => onChange({...state, trigger: e.target.value})}>
            {TRIGGERS.map((t) => (
              <option key={t.name} value={t.name}>
                {t.name}
              </option>
            ))}
          </select>
          <span className={styles.hint}>
            When the enchant fires (the in-game event).
          </span>
        </div>

        <div className={styles.field}>
          <label className={styles.label}>Group</label>
          <input
            className={styles.input}
            type="text"
            value={state.group}
            placeholder="combat"
            onChange={(e) => onChange({...state, group: e.target.value})}
          />
          <span className={styles.hint}>
            Optional family for suppression/sorting.
          </span>
        </div>

        <div className={clsx(styles.field, styles.fieldFull)}>
          <label className={styles.label}>
            Applies to<span className={styles.required}>*</span>
          </label>
          <div className={styles.chips}>
            {ITEM_CATEGORIES.map((cat) => (
              <button
                type="button"
                key={cat}
                className={clsx(
                  styles.chip,
                  state.appliesTo.includes(cat) && styles.chipActive,
                )}
                onClick={() => toggleCategory(cat)}>
                {cat}
              </button>
            ))}
            {customCats.map((cat) => (
              <button
                type="button"
                key={cat}
                className={clsx(styles.chip, styles.chipActive)}
                onClick={() => toggleCategory(cat)}
                title="Custom category — click to remove">
                {cat} ✕
              </button>
            ))}
          </div>
          <div className={styles.customChipRow}>
            <input
              className={styles.input}
              type="text"
              value={customCat}
              placeholder="custom category"
              onChange={(e) => setCustomCat(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  addCustomCategory();
                }
              }}
            />
            <button
              type="button"
              className={`${styles.btn} ${styles.btnSmall}`}
              onClick={addCustomCategory}>
              Add
            </button>
          </div>
          {errors.get('meta.appliesTo') && (
            <span className={styles.errorText}>
              {errors.get('meta.appliesTo')}
            </span>
          )}
        </div>
      </div>
    </section>
  );
}
