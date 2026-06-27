import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import HomepageFeatures from '@site/src/components/HomepageFeatures';

import styles from './index.module.css';

const RELEASES_URL = 'https://github.com/owengregson/StarEnchants/releases';

function HomepageHeader() {
  const logo = useBaseUrl('img/logo.svg');
  return (
    <header className={styles.hero}>
      <div className={styles.heroGlow} aria-hidden="true" />
      <div className={clsx('container', styles.heroInner)}>
        <img className={styles.heroLogo} src={logo} alt="" aria-hidden="true" />
        <Heading as="h1" className={styles.heroTitle}>
          StarEnchants
        </Heading>
        <p className={styles.heroTagline}>
          Legendary, open-source cosmic enchantments for your server.
        </p>
        <p className={styles.heroSub}>
          Custom enchantments, armor sets, crystals, and a full item economy,
          configured in plain YAML — for Paper 1.17.1-26.1.2 and Folia.
        </p>
        <div className={styles.buttons}>
          <Link className="button button--primary button--lg" to="/docs/intro">
            Get started
          </Link>
          <Link
            className="button button--secondary button--lg"
            to="/creator">
            Enchant Creator
          </Link>
          <Link
            className="button button--secondary button--lg"
            href={RELEASES_URL}>
            Download
          </Link>
        </div>
        <div className={styles.badges}>
          <span className={styles.badge}>✦ 51 effects</span>
          <span className={styles.badge}>✦ Armor sets &amp; crystals</span>
          <span className={styles.badge}>✦ Souls economy</span>
          <span className={styles.badge}>✦ Paper + Folia</span>
        </div>
      </div>
    </header>
  );
}

function WhatIs() {
  return (
    <section className={styles.whatIs}>
      <div className="container">
        <div className={styles.whatIsCard}>
          <Heading as="h2" className={styles.whatIsTitle}>
            What is StarEnchants?
          </Heading>
          <p>
            StarEnchants adds custom enchantments and armor sets to modern
            Minecraft. Enchants,
            set bonuses, Heroic upgrades, and socketable crystals all feed{' '}
            <strong>one effect engine</strong>, so every feature shares the same
            triggers, selectors, conditions, and 40+ live variables.
          </p>
          <p>
            Everything is plain YAML — define an enchant in one file, edit it,
            and run <code>/se reload</code> to hot-swap a freshly compiled,
            immutable config with no restart. Coming from another plugin? The
            built-in migrator imports EliteEnchantments, EliteArmor, and
            AdvancedEnchantments straight into the unified schema.
          </p>
          <div className={styles.whatIsLinks}>
            <Link to="/docs/intro">Read the introduction →</Link>
            <Link to="/docs/configuring">Configure content →</Link>
            <Link to="/docs/reference/effects">Browse the DSL reference →</Link>
          </div>
        </div>
      </div>
    </section>
  );
}

export default function Home(): ReactNode {
  return (
    <Layout
      title="Custom enchantments & armor sets for Paper + Folia"
      description={
        'StarEnchants — a custom-enchantments & armor-sets plugin for ' +
        'Minecraft. For Paper 1.17.1-26.1.2 and Folia, and Minecraft 1.8.x.'
      }>
      <HomepageHeader />
      <main>
        <HomepageFeatures />
        <WhatIs />
      </main>
    </Layout>
  );
}
