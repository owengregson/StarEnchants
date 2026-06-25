import type {ReactNode} from 'react';
import type {CatalogParam} from './types';
import {humanType, rangeText, defaultText} from './format';
import styles from './styles.module.css';

// Renders an effect's / selector's argument list as a friendly table.
// Driven entirely by the catalog param schema, so new params show up for free.

function dash(s: string): ReactNode {
  return s === '' ? <span className={styles.muted}>&mdash;</span> : s;
}

export default function ParamTable({
  params,
}: {
  params: CatalogParam[];
}): ReactNode {
  if (!params || params.length === 0) {
    return <p className={styles.noParams}>Takes no arguments.</p>;
  }
  return (
    <table className={styles.paramTable}>
      <thead>
        <tr>
          <th>Argument</th>
          <th>What to put</th>
          <th>Required?</th>
          <th>Default</th>
          <th>Allowed</th>
        </tr>
      </thead>
      <tbody>
        {params.map((p) => (
          <tr key={p.name}>
            <td className={styles.paramName}>{p.name}</td>
            <td>
              {humanType(p)}
              {p.doc ? (
                <>
                  {' '}
                  <span className={styles.muted}>&mdash; {p.doc}</span>
                </>
              ) : null}
            </td>
            <td>
              {p.required ? (
                <span className={styles.req}>required</span>
              ) : (
                <span className={styles.opt}>optional</span>
              )}
            </td>
            <td className={styles.defaultVal}>{dash(defaultText(p))}</td>
            <td className={styles.range}>{dash(rangeText(p))}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
