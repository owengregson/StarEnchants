import type {ReactNode} from 'react';
import catalog from '@site/src/data/catalog.json';
import type {Catalog, CatalogSelector} from './types';
import ParamTable from './ParamTable';
import Example from './Example';
import styles from './styles.module.css';

const data = catalog as unknown as Catalog;

function slug(head: string): string {
  return head.toLowerCase().replace(/_/g, '-');
}

// The friendly @-form a selector is written as (HERE -> @Here, AOE -> @Aoe).
function atForm(head: string): string {
  return '@' + head.charAt(0) + head.slice(1).toLowerCase();
}

function SelectorCard({sel}: {sel: CatalogSelector}): ReactNode {
  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <h3 className={styles.cardTitle} id={slug(sel.head)}>
          {atForm(sel.head)}
        </h3>
      </div>
      <p className={styles.cardDesc}>{sel.doc}</p>
      <ParamTable params={sel.params} />
      <Example code={sel.example} />
    </div>
  );
}

export default function SelectorList(): ReactNode {
  const sorted = [...data.selectors].sort((a, b) =>
    a.head.localeCompare(b.head),
  );
  return (
    <>
      {sorted.map((s) => (
        <SelectorCard key={s.head} sel={s} />
      ))}
    </>
  );
}
