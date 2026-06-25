import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  icon: string;
  to?: string;
  description: ReactNode;
};

// Every feature pulls from the README so the marketing copy stays in lock-step.
const FeatureList: FeatureItem[] = [
  {
    title: 'One unified engine',
    icon: 'img/icons/engine.svg',
    to: '/docs/reference/effects',
    description: (
      <>
        Enchantments, armor-set bonuses, and crystals all feed <em>one</em>{' '}
        engine — <strong>51 effects</strong>, <strong>21 triggers</strong>,{' '}
        <strong>17 selectors</strong>, and a conditions DSL over{' '}
        <strong>40 live variables</strong>.
      </>
    ),
  },
  {
    title: 'Armor sets & crystals',
    icon: 'img/icons/armor.svg',
    to: '/docs/concepts/armor-sets',
    description: (
      <>
        Full armor sets with completion bonuses and matched weapons, Heroic
        upgrades, and socketable crystals — each just another source of effects.
      </>
    ),
  },
  {
    title: 'Souls economy',
    icon: 'img/icons/souls.svg',
    to: '/docs/concepts/souls-economy',
    description: (
      <>
        A built-in souls economy with soul gems that bank kills, plus slot orbs,
        success dust, and a full item progression loop.
      </>
    ),
  },
  {
    title: 'Items & scrolls',
    icon: 'img/icons/book.svg',
    to: '/docs/configuring',
    description: (
      <>
        Enchant books, white &amp; holy-white scrolls, black, transmog, and
        randomizer scrolls, dust, nametags, and slot orbs — each its own YAML.
      </>
    ),
  },
  {
    title: 'Built-in migrator',
    icon: 'img/icons/migrator.svg',
    to: '/docs/configuring',
    description: (
      <>
        Bring EliteEnchantments, EliteArmor, and AdvancedEnchantments configs
        straight into the unified schema with <code>/se migrate</code>.
      </>
    ),
  },
  {
    title: 'Paper + Folia, one jar',
    icon: 'img/icons/install.svg',
    to: '/docs/intro',
    description: (
      <>
        One universal jar runs Paper <strong>1.17.1 → 26.1.x</strong> and Folia
        — version-agnostic core, Folia-safe scheduling, zero per-version builds.
      </>
    ),
  },
];

function Feature({title, icon, description, to}: FeatureItem) {
  const iconUrl = useBaseUrl(icon);
  const card = (
    <div className={styles.card}>
      <img className={styles.cardIcon} src={iconUrl} alt="" aria-hidden="true" />
      <Heading as="h3" className={styles.cardTitle}>
        {title}
      </Heading>
      <p className={styles.cardText}>{description}</p>
    </div>
  );
  return (
    <div className={clsx('col col--4', styles.col)}>
      {to ? (
        <Link to={to} className={styles.cardLink}>
          {card}
        </Link>
      ) : (
        card
      )}
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className={styles.sectionHead}>
          <Heading as="h2" className={styles.sectionTitle}>
            Everything in one drop-in jar
          </Heading>
          <p className={styles.sectionSubtitle}>
            Custom enchantments, armor sets, crystals, and a full item economy
            under one config schema — with a built-in migrator.
          </p>
        </div>
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
