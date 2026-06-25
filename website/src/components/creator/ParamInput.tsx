import type {ReactNode} from 'react';
import clsx from 'clsx';

import type {CatalogParam} from './types';
import {handleHint} from './catalog';
import styles from './creator.module.css';

interface ParamInputProps {
  param: CatalogParam;
  value: string | undefined;
  onChange: (value: string) => void;
  error?: string;
}

/** One input matched to a catalog param's kind (DOUBLE/INT/TICKS/ENUM/BOOL/HANDLE/STRING). */
export default function ParamInput({
  param,
  value,
  onChange,
  error,
}: ParamInputProps): ReactNode {
  const placeholder =
    param.default !== null && param.default !== ''
      ? `default ${param.default}`
      : param.kind === 'HANDLE'
        ? handleHint(param.handle)
        : '';

  const control = renderControl(param, value, onChange, placeholder, !!error);

  // BOOL renders its own label inline; everything else gets a stacked label.
  if (param.kind === 'BOOL') {
    return (
      <div className={styles.field}>
        <label className={styles.checkboxRow}>
          {control}
          <span className={styles.label}>
            {param.name}
            {param.required && <span className={styles.required}>*</span>}
          </span>
        </label>
        {param.doc && <span className={styles.hint}>{param.doc}</span>}
        {error && <span className={styles.errorText}>{error}</span>}
      </div>
    );
  }

  return (
    <div className={styles.field}>
      <label className={styles.label}>
        {param.name}
        {param.required && <span className={styles.required}>*</span>}
        <span className={styles.hint}> ({param.label})</span>
      </label>
      {control}
      {param.doc && <span className={styles.hint}>{param.doc}</span>}
      {error && <span className={styles.errorText}>{error}</span>}
    </div>
  );
}

function renderControl(
  param: CatalogParam,
  value: string | undefined,
  onChange: (value: string) => void,
  placeholder: string,
  hasError: boolean,
): ReactNode {
  const v = value ?? '';

  switch (param.kind) {
    case 'ENUM':
      return (
        <select
          className={clsx(styles.select, hasError && styles.inputError)}
          value={v}
          onChange={(e) => onChange(e.target.value)}>
          <option value="">
            {param.required ? '— select —' : `default (${param.default ?? ''})`}
          </option>
          {param.enum.map((opt) => (
            <option key={opt} value={opt}>
              {opt}
            </option>
          ))}
        </select>
      );

    case 'BOOL':
      return (
        <input
          type="checkbox"
          checked={v === 'true'}
          onChange={(e) => onChange(e.target.checked ? 'true' : 'false')}
        />
      );

    case 'INT':
    case 'TICKS':
    case 'DOUBLE':
      return (
        <input
          className={clsx(styles.input, hasError && styles.inputError)}
          type="number"
          inputMode={param.kind === 'DOUBLE' ? 'decimal' : 'numeric'}
          step={param.kind === 'DOUBLE' ? 'any' : 1}
          min={param.min ?? undefined}
          max={param.max ?? undefined}
          value={v}
          placeholder={placeholder}
          onChange={(e) => onChange(e.target.value)}
        />
      );

    case 'HANDLE':
    case 'STRING':
    default:
      return (
        <input
          className={clsx(styles.input, hasError && styles.inputError)}
          type="text"
          value={v}
          placeholder={placeholder}
          onChange={(e) => onChange(e.target.value)}
        />
      );
  }
}
