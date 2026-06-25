import {useMemo, useState} from 'react';
import type {ReactNode} from 'react';

import type {EnchantState, LevelState} from './types';
import {newEnchant, newLevel} from './catalog';
import {buildContent, renderYaml, contentToState} from './yaml';
import {validate} from './validation';
import {encode, decode, SeCodecError} from '@site/src/lib/se-codec';
import MetaPanel from './MetaPanel';
import LevelEditor from './LevelEditor';
import OutputPanel from './OutputPanel';
import styles from './creator.module.css';

/**
 * The stateful builder. Rendered client-only (see creator.tsx's BrowserOnly
 * wrap) so it can encode with pako and touch the clipboard/Blob APIs freely.
 */
export default function Creator(): ReactNode {
  const [state, setState] = useState<EnchantState>(() => seededEnchant());
  const [importText, setImportText] = useState('');
  const [importError, setImportError] = useState<string | null>(null);

  const issues = useMemo(() => validate(state), [state]);
  const yaml = useMemo(() => renderYaml(state), [state]);
  const importCode = useMemo(() => {
    try {
      return encode({
        v: 1,
        kind: 'enchant',
        key: state.key || 'unnamed',
        content: buildContent(state),
      });
    } catch {
      return '';
    }
  }, [state]);

  const metaErrors = useMemo(() => {
    const map = new Map<string, string>();
    for (const i of issues) {
      if (i.path.startsWith('meta.')) map.set(i.path, i.message);
    }
    return map;
  }, [issues]);

  /** issue paths scoped to one level at idx, with the "level.<idx>." prefix stripped. */
  function levelErrors(idx: number): Map<string, string> {
    const prefix = `level.${idx}.`;
    const out = new Map<string, string>();
    for (const i of issues) {
      if (i.path.startsWith(prefix)) out.set(i.path.slice(prefix.length), i.message);
    }
    return out;
  }

  function updateLevel(idx: number, next: LevelState): void {
    setState((s) => ({
      ...s,
      levels: s.levels.map((l, i) => (i === idx ? next : l)),
    }));
  }

  function addLevel(): void {
    setState((s) => ({...s, levels: [...s.levels, newLevel()]}));
  }

  function removeLevel(idx: number): void {
    setState((s) =>
      s.levels.length === 1
        ? s
        : {...s, levels: s.levels.filter((_, i) => i !== idx)},
    );
  }

  function moveLevel(idx: number, dir: -1 | 1): void {
    setState((s) => {
      const target = idx + dir;
      if (target < 0 || target >= s.levels.length) return s;
      const next = [...s.levels];
      [next[idx], next[target]] = [next[target], next[idx]];
      return {...s, levels: next};
    });
  }

  function handleImport(): void {
    setImportError(null);
    try {
      const env = decode(importText);
      setState(contentToState(env.key, env.content));
      setImportText('');
    } catch (e) {
      const msg =
        e instanceof SeCodecError
          ? e.message
          : 'That code could not be read. Make sure it is a complete SE1: code.';
      setImportError(msg);
    }
  }

  function reset(): void {
    setState(newEnchant());
    setImportText('');
    setImportError(null);
  }

  const fileName = `${state.key || 'unnamed'}.yml`;

  return (
    <div className={styles.layout}>
      <div className={styles.column}>
        <MetaPanel state={state} errors={metaErrors} onChange={setState} />

        <section className={styles.panel}>
          <div className={styles.panelHeader}>
            <h2 className={styles.panelTitle}>Levels</h2>
            <button
              type="button"
              className={`${styles.btn} ${styles.btnSmall}`}
              onClick={addLevel}>
              + Add level
            </button>
          </div>
          {state.levels.map((level, idx) => (
            <LevelEditor
              key={level.id}
              level={level}
              index={idx}
              count={state.levels.length}
              errors={levelErrors(idx)}
              onChange={(next) => updateLevel(idx, next)}
              onRemove={() => removeLevel(idx)}
              onMove={(dir) => moveLevel(idx, dir)}
            />
          ))}
        </section>
      </div>

      <div className={styles.column}>
        <div className={styles.outputColumn}>
          <OutputPanel
            yaml={yaml}
            importCode={importCode}
            fileName={fileName}
            issues={issues}
            importText={importText}
            importError={importError}
            onImportTextChange={(t) => {
              setImportText(t);
              if (importError) setImportError(null);
            }}
            onImport={handleImport}
            onReset={reset}
          />
        </div>
      </div>
    </div>
  );
}

/** A friendly starting point so the page isn't blank on first load. */
function seededEnchant(): EnchantState {
  const s = newEnchant();
  s.display = '&bFrostbite';
  s.key = 'frostbite';
  s.description = 'Chill and slow the enemy you strike.';
  s.tier = 'uncommon';
  s.trigger = 'ATTACK';
  s.appliesTo = ['SWORD', 'AXE'];
  s.group = 'combat';
  const lvl = newLevel();
  lvl.chance = '25';
  s.levels = [lvl];
  return s;
}
