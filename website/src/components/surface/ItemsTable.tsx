import type {ReactNode} from 'react';
import surface from '@site/src/data/surface.json';
import styles from './styles.module.css';

// Renders surface.items (every physical item file): key · type · material ·
// name. '&' colour codes in the name are shown literally as text — we don't
// colourise them. material/name may be null (an item that derives them).

type Item = {
  key: string;
  type: string;
  material: string | null;
  name: string | null;
};

const items = surface.items as unknown as Item[];

function dash(s: string | null): ReactNode {
  return s ? s : <span className={styles.muted}>&mdash;</span>;
}

export default function ItemsTable(): ReactNode {
  return (
    <table>
      <thead>
        <tr>
          <th>Item key</th>
          <th>Type</th>
          <th>Material</th>
          <th>Name</th>
        </tr>
      </thead>
      <tbody>
        {items.map((it) => (
          <tr key={it.key}>
            <td className={styles.cmd}>{it.key}</td>
            <td className={styles.code}>{it.type}</td>
            <td className={styles.code}>{dash(it.material)}</td>
            <td className={styles.code}>{dash(it.name)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
