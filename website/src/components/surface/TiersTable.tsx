import type {ReactNode} from 'react';
import surface from '@site/src/data/surface.json';
import styles from './styles.module.css';

// Renders surface.tiers.list (id · colour · weight · glint) and notes the
// default tier. The colour swatch previews the leading '&' colour code; the
// raw code is also shown as text (we don't recolour the id itself).

type Tier = {id: string; color: string; weight: number; glint: boolean};

const tiers = surface.tiers as unknown as {default: string; list: Tier[]};

// Minecraft legacy '&' colour codes -> hex, for the swatch preview only.
const SWATCH: Record<string, string> = {
  '0': '#000000',
  '1': '#0000aa',
  '2': '#00aa00',
  '3': '#00aaaa',
  '4': '#aa0000',
  '5': '#aa00aa',
  '6': '#ffaa00',
  '7': '#aaaaaa',
  '8': '#555555',
  '9': '#5555ff',
  a: '#55ff55',
  b: '#55ffff',
  c: '#ff5555',
  d: '#ff55ff',
  e: '#ffff55',
  f: '#ffffff',
};

function swatchColor(code: string): string | null {
  // Take the first colour code in the string (ignore style codes like &l).
  const m = code.toLowerCase().match(/&([0-9a-f])/);
  return m ? SWATCH[m[1]] ?? null : null;
}

function yn(v: boolean): ReactNode {
  return v ? (
    <span aria-label="yes">Yes</span>
  ) : (
    <span className={styles.muted} aria-label="no">
      &mdash;
    </span>
  );
}

function Row({t, isDefault}: {t: Tier; isDefault: boolean}): ReactNode {
  const hex = swatchColor(t.color);
  return (
    <tr>
      <td className={styles.cmd}>
        {t.id}
        {isDefault ? <span className={styles.muted}> (default)</span> : null}
      </td>
      <td>
        <span className={styles.swatch}>
          {hex ? (
            <span
              className={styles.swatchChip}
              style={{background: hex}}
              aria-hidden="true"
            />
          ) : null}
          <span className={styles.swatchCode}>{t.color}</span>
        </span>
      </td>
      <td className={styles.center}>{t.weight}</td>
      <td className={styles.center}>{yn(t.glint)}</td>
    </tr>
  );
}

export default function TiersTable(): ReactNode {
  return (
    <table>
      <thead>
        <tr>
          <th>Tier</th>
          <th>Colour</th>
          <th>Weight</th>
          <th>Glint</th>
        </tr>
      </thead>
      <tbody>
        {tiers.list.map((t) => (
          <Row key={t.id} t={t} isDefault={t.id === tiers.default} />
        ))}
      </tbody>
    </table>
  );
}
