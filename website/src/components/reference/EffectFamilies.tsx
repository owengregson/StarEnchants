import type {ReactNode} from 'react';
import catalog from '@site/src/data/catalog.json';
import type {Catalog, CatalogEffect} from './types';
import EffectCard, {effectSlug} from './EffectCard';
import styles from './styles.module.css';

const data = catalog as unknown as Catalog;

// Effect families: a curated grouping + blurb on top of the flat catalog list.
// Each family lists effect HEADs by name; any effect NOT listed below still
// appears under "Everything else" so the page can never silently drop a new one.
interface Family {
  id: string;
  label: string;
  blurb: string;
  heads: string[];
}

const FAMILIES: Family[] = [
  {
    id: 'combat-damage',
    label: 'Combat & damage',
    blurb:
      'Shape the hit itself — extra damage, the additive damage fold, lightning, explosions, and on-hit kills.',
    heads: [
      'DAMAGE',
      'DAMAGE_MOD',
      'IGNORE_ARMOR',
      'KILL',
      'LIGHTNING',
      'EXPLODE',
      'PROJECTILE',
      'SEEK',
      'IMMUNE',
      'INVINCIBLE',
    ],
  },
  {
    id: 'debuffs-control',
    label: 'Debuffs & control on a target',
    blurb:
      'Apply or strip status on whoever you hit — potions, fire, disarm, armour stripping, teleport-block, and suppressing their enchants.',
    heads: [
      'POTION',
      'REMOVE_POTION',
      'CURE',
      'IGNITE',
      'EXTINGUISH',
      'DISARM',
      'REMOVE_ARMOR',
      'TELEBLOCK',
      'SUPPRESS',
      'KNOCKBACK_CONTROL',
    ],
  },
  {
    id: 'movement',
    label: 'Movement',
    blurb:
      'Move bodies around — flight, walk speed, knockback/velocity, and teleporting.',
    heads: ['FLY', 'MOVEMENT_SPEED', 'VELOCITY', 'TELEPORT'],
  },
  {
    id: 'blocks-world',
    label: 'Blocks & world',
    blurb:
      'Touch the world — break/place blocks, walkers, spawn entities and guardians, fireworks.',
    heads: [
      'BREAK_BLOCK',
      'SET_BLOCK',
      'WALKER',
      'SPAWN_ENTITY',
      'GUARD',
      'SMELT',
      'TELEPORT_DROPS',
    ],
  },
  {
    id: 'economy-items',
    label: 'Economy & items',
    blurb:
      'Give, take, and move resources — items, money, experience, food, durability, and souls.',
    heads: [
      'GIVE_ITEM',
      'REMOVE_ITEM',
      'DROP_ITEM',
      'MODIFY_MONEY',
      'MODIFY_EXP',
      'MODIFY_FOOD',
      'DURABILITY',
      'REMOVE_SOULS',
    ],
  },
  {
    id: 'survival',
    label: 'Health & survival',
    blurb:
      'Keep the wearer alive — heal/lifesteal, max-health buffs, oxygen, and death-keep.',
    heads: ['MODIFY_HEALTH', 'HEALTH', 'FILL_OXYGEN', 'KEEP_ON_DEATH'],
  },
  {
    id: 'cosmetic-feedback',
    label: 'Cosmetic & feedback',
    blurb:
      'Tell the player what happened — messages/titles, sounds, and particles.',
    heads: ['MESSAGE', 'SOUND', 'PARTICLE', 'FIREWORK'],
  },
  {
    id: 'utility-flow',
    label: 'Utility & flow',
    blurb:
      'Wiring and control — set/flip variables for later conditions, cancel the event, run console commands.',
    heads: ['SET_VAR', 'INVERT_VAR', 'CANCEL', 'RUN_COMMAND'],
  },
];

function byHead(): Map<string, CatalogEffect> {
  const m = new Map<string, CatalogEffect>();
  for (const e of data.effects) m.set(e.head, e);
  return m;
}

export default function EffectFamilies(): ReactNode {
  const lookup = byHead();
  const placed = new Set<string>();

  const sections = FAMILIES.map((fam) => {
    const effects = fam.heads
      .map((h) => lookup.get(h))
      .filter((e): e is CatalogEffect => Boolean(e));
    effects.forEach((e) => placed.add(e.head));
    return {fam, effects};
  }).filter((s) => s.effects.length > 0);

  // Catch any effect the curated families haven't placed yet.
  const leftover = data.effects.filter((e) => !placed.has(e.head));

  return (
    <>
      <ul className={styles.familyNav}>
        {sections.map((s) => (
          <li key={s.fam.id}>
            <a className={styles.familyNavLink} href={`#${s.fam.id}`}>
              {s.fam.label}
            </a>
          </li>
        ))}
        {leftover.length > 0 ? (
          <li>
            <a className={styles.familyNavLink} href="#everything-else">
              Everything else
            </a>
          </li>
        ) : null}
      </ul>

      {sections.map((s) => (
        <section key={s.fam.id}>
          <h2 id={s.fam.id}>{s.fam.label}</h2>
          <p className={styles.familyIntro}>{s.fam.blurb}</p>
          {s.effects.map((e) => (
            <EffectCard key={e.head} eff={e} />
          ))}
        </section>
      ))}

      {leftover.length > 0 ? (
        <section>
          <h2 id="everything-else">Everything else</h2>
          <p className={styles.familyIntro}>
            Newer effects that aren&apos;t sorted into a family yet.
          </p>
          {leftover.map((e) => (
            <EffectCard key={e.head} eff={e} />
          ))}
        </section>
      ) : null}
    </>
  );
}

export {effectSlug};
