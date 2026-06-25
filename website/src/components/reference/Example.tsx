import type {ReactNode} from 'react';
import styles from './styles.module.css';

// A single copy-pasteable example token, rendered inline with a small label.
export default function Example({code}: {code: string}): ReactNode {
  if (!code) return null;
  return (
    <div className={styles.exampleRow}>
      <span className={styles.exampleLabel}>Example</span>
      <code className={styles.exampleCode}>{code}</code>
    </div>
  );
}
