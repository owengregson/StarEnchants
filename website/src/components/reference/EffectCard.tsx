import type {ReactNode} from 'react';
import type {CatalogEffect} from './types';
import {affinityInfo} from './format';
import ParamTable from './ParamTable';
import Example from './Example';
import styles from './styles.module.css';

// Slug used as the heading id, so the sidebar TOC and deep links work.
export function effectSlug(head: string): string {
  return head.toLowerCase().replace(/_/g, '-');
}

// Where an effect aims by default, in plain words (from its declared targets).
function targetHint(eff: CatalogEffect): ReactNode {
  if (!eff.targets || eff.targets.length === 0) return null;
  const parts = eff.targets.map((t) => `${t.name} -> @${title(t.selector)}`);
  return (
    <>
      {' '}
      Default target{eff.targets.length > 1 ? 's' : ''}:{' '}
      <code>{parts.join(', ')}</code> (override in the effect token).
    </>
  );
}

function title(selector: string): string {
  // ATTACKER -> Attacker, NEARESTPLAYER -> Nearestplayer (matches @Selector form)
  return selector.charAt(0) + selector.slice(1).toLowerCase();
}

export default function EffectCard({eff}: {eff: CatalogEffect}): ReactNode {
  const aff = affinityInfo(eff.affinity);
  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <h3 className={styles.cardTitle} id={effectSlug(eff.head)}>
          {eff.head}
        </h3>
        <span className={`${styles.badge} ${styles[aff.badgeClass]}`}>
          {aff.label}
        </span>
      </div>

      <p className={styles.cardDesc}>{eff.doc}</p>

      {aff.hint ? (
        <p className={styles.hint}>
          <strong>How it runs:</strong> {aff.hint}
          {targetHint(eff)}
        </p>
      ) : null}

      <ParamTable params={eff.params} />
      <Example code={eff.example} />
    </div>
  );
}
