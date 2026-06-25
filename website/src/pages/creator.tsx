import type {ReactNode} from 'react';
import Layout from '@theme/Layout';
import BrowserOnly from '@docusaurus/BrowserOnly';

import styles from '@site/src/components/creator/creator.module.css';

/**
 * The /creator route. The builder is wrapped in <BrowserOnly> because it
 * encodes with pako and touches clipboard/Blob/window APIs — none of which
 * exist during Docusaurus' static prerender. The page shell renders on the
 * server; the interactive builder mounts only in the browser.
 */
export default function CreatorPage(): ReactNode {
  return (
    <Layout
      title="Enchant Creator"
      description="Build a StarEnchants custom enchant in your browser and import it with one /se command.">
      <main className={styles.creator}>
        <header className={styles.intro}>
          <h1 className={styles.title}>Enchant Creator</h1>
          <p className={styles.subtitle}>
            Design a custom enchant visually, then copy a single{' '}
            <code>/se import</code> command into your server — no YAML editing
            required. Already have a code? Paste it in to keep editing.
          </p>
        </header>

        <BrowserOnly fallback={<p>Loading the builder…</p>}>
          {() => {
            const Creator = require('@site/src/components/creator/Creator').default;
            return <Creator />;
          }}
        </BrowserOnly>
      </main>
    </Layout>
  );
}
