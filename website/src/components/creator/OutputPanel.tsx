import {useState} from 'react';
import type {ReactNode} from 'react';
import clsx from 'clsx';

import type {Issue} from './validation';
import styles from './creator.module.css';

interface OutputPanelProps {
  yaml: string;
  importCode: string;
  fileName: string;
  issues: Issue[];
  /** raw textarea text the user pasted to import */
  importText: string;
  importError: string | null;
  onImportTextChange: (text: string) => void;
  onImport: () => void;
  onReset: () => void;
}

type Tab = 'code' | 'yaml' | 'import';

export default function OutputPanel({
  yaml,
  importCode,
  fileName,
  issues,
  importText,
  importError,
  onImportTextChange,
  onImport,
  onReset,
}: OutputPanelProps): ReactNode {
  const [tab, setTab] = useState<Tab>('code');
  const [copied, setCopied] = useState<string | null>(null);

  const valid = issues.length === 0;

  function copy(text: string, label: string): void {
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      navigator.clipboard.writeText(text).then(
        () => {
          setCopied(label);
          window.setTimeout(() => setCopied(null), 1600);
        },
        () => setCopied('Copy failed'),
      );
    }
  }

  function download(): void {
    const blob = new Blob([yaml], {type: 'text/yaml'});
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  const command = `/se import ${importCode}`;

  return (
    <section className={styles.panel}>
      <div className={styles.panelHeader}>
        <h2 className={styles.panelTitle}>Output</h2>
        <button
          type="button"
          className={`${styles.btn} ${styles.btnSmall}`}
          onClick={onReset}>
          New enchant
        </button>
      </div>

      <ValidationBanner valid={valid} issues={issues} />

      <div className={styles.tabRow}>
        <button
          type="button"
          className={clsx(styles.tab, tab === 'code' && styles.tabActive)}
          onClick={() => setTab('code')}>
          Import code
        </button>
        <button
          type="button"
          className={clsx(styles.tab, tab === 'yaml' && styles.tabActive)}
          onClick={() => setTab('yaml')}>
          YAML
        </button>
        <button
          type="button"
          className={clsx(styles.tab, tab === 'import' && styles.tabActive)}
          onClick={() => setTab('import')}>
          Import existing
        </button>
      </div>

      {tab === 'code' && (
        <div>
          <pre className={styles.importCode}>{command}</pre>
          <p className={styles.codeMeta}>
            Run this in-game (op / <code>starenchants.admin</code>). Code length:{' '}
            {importCode.length} chars.
          </p>
          <div className={styles.outputActions}>
            <button
              type="button"
              className={`${styles.btn} ${styles.btnPrimary}`}
              onClick={() => copy(command, 'command')}>
              {copied === 'command' ? '✓ Copied' : 'Copy /se import'}
            </button>
            <button
              type="button"
              className={styles.btn}
              onClick={() => copy(importCode, 'code')}>
              {copied === 'code' ? '✓ Copied' : 'Copy SE1 code only'}
            </button>
          </div>
          {!valid && (
            <p className={styles.codeMeta}>
              ⚠ This enchant has validation issues — the code may be rejected
              by the server. Fix them above before importing.
            </p>
          )}
        </div>
      )}

      {tab === 'yaml' && (
        <div>
          <pre className={styles.codeBlock}>{yaml}</pre>
          <div className={styles.outputActions}>
            <button
              type="button"
              className={`${styles.btn} ${styles.btnPrimary}`}
              onClick={() => copy(yaml, 'yaml')}>
              {copied === 'yaml' ? '✓ Copied' : 'Copy YAML'}
            </button>
            <button type="button" className={styles.btn} onClick={download}>
              Download {fileName}
            </button>
          </div>
          <p className={styles.codeMeta}>
            Drop into <code>content/enchants/{fileName}</code> and run{' '}
            <code>/se reload</code> as a fallback to the import code.
          </p>
        </div>
      )}

      {tab === 'import' && (
        <div className={styles.importRow}>
          <label className={styles.label}>
            Paste an <code>SE1:</code> code to load it into the form
          </label>
          <textarea
            className={clsx(styles.textarea, importError && styles.inputError)}
            value={importText}
            placeholder="SE1:…"
            rows={4}
            onChange={(e) => onImportTextChange(e.target.value)}
          />
          {importError && <span className={styles.errorText}>{importError}</span>}
          <div className={styles.outputActions}>
            <button
              type="button"
              className={`${styles.btn} ${styles.btnPrimary}`}
              onClick={onImport}
              disabled={importText.trim() === ''}>
              Load into form
            </button>
          </div>
          <p className={styles.codeMeta}>
            Editing an existing enchant? Paste its code here, tweak, then
            re-emit from the other tabs.
          </p>
        </div>
      )}
    </section>
  );
}

function ValidationBanner({
  valid,
  issues,
}: {
  valid: boolean;
  issues: Issue[];
}): ReactNode {
  if (valid) {
    return (
      <div className={clsx(styles.validBanner, styles.validOk)}>
        <span>✓</span>
        <span>Looks good — ready to import.</span>
      </div>
    );
  }
  return (
    <div className={clsx(styles.validBanner, styles.validWarn)}>
      <span>⚠</span>
      <div>
        <strong>
          {issues.length} issue{issues.length === 1 ? '' : 's'} to fix:
        </strong>
        <ul className={styles.issueList}>
          {issues.slice(0, 8).map((i, idx) => (
            <li key={`${i.path}-${idx}`}>{i.message}</li>
          ))}
          {issues.length > 8 && <li>…and {issues.length - 8} more.</li>}
        </ul>
      </div>
    </div>
  );
}
