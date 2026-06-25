import type {ReactNode} from 'react';
import catalog from '@site/src/data/catalog.json';
import type {Catalog, CatalogTrigger} from './types';
import styles from './styles.module.css';

const data = catalog as unknown as Catalog;

// Plain-language "when does this fire?" for each trigger. Keyed by trigger name;
// any trigger without an entry still renders (with a generic note) so a newly
// added trigger never disappears from the page.
const WHEN: Record<string, string> = {
  ATTACK: 'You land a melee hit on a living target.',
  BOW: 'Your fired arrow hits a target (the hit, not the shot).',
  TRIDENT: 'Your thrown/melee trident hits a target.',
  KILL: 'Your hit kills the target.',
  BOW_FIRE: 'You release a bow shot (the moment the arrow leaves).',
  DEFENSE: 'Something deals damage to you.',
  FALL: 'You take fall damage.',
  FIRE: 'You take fire/burning damage.',
  PASSIVE: 'Always on while the item is worn (no event needed).',
  MINE: 'You break a block while mining.',
  DEATH: 'You die.',
  HELD: 'The item is in your hand (held), checked continuously.',
  BREAK: 'You break a block with this held item.',
  ITEM_DAMAGE: 'This held item loses durability.',
  EAT: 'You finish eating/drinking while holding this item.',
  FISHING: 'You reel in a catch with this fishing rod.',
  INTERACT: 'You right- or left-click with this held item.',
  INTERACT_LEFT: 'You left-click with this held item.',
  INTERACT_RIGHT: 'You right-click with this held item.',
  REPEATING: 'On a fixed timer while worn (good for always-on upkeep effects).',
  COMMAND: 'Fired explicitly by a command, not a game event.',
};

const DIRECTION_BADGE: Record<string, string> = {
  ATTACK: 'badgeAttack',
  DEFENSE: 'badgeDefense',
  NEUTRAL: 'badgeNeutral',
};

function yn(v: boolean): ReactNode {
  return v ? (
    <span aria-label="yes">Yes</span>
  ) : (
    <span className={styles.muted} aria-label="no">
      &mdash;
    </span>
  );
}

function Row({t}: {t: CatalogTrigger}): ReactNode {
  const badge = DIRECTION_BADGE[t.direction] ?? 'badgeNeutral';
  const dir = t.direction.charAt(0) + t.direction.slice(1).toLowerCase();
  return (
    <tr>
      <td className={styles.paramName}>{t.name}</td>
      <td>
        <span className={`${styles.badge} ${styles[badge]}`}>{dir}</span>
      </td>
      <td>{WHEN[t.name] ?? 'Fires on its matching game event.'}</td>
      <td style={{textAlign: 'center'}}>{yn(t.usesHeld)}</td>
      <td style={{textAlign: 'center'}}>{yn(t.scansEquipment)}</td>
      <td style={{textAlign: 'center'}}>{yn(t.needsTarget)}</td>
    </tr>
  );
}

export default function TriggerTable(): ReactNode {
  return (
    <table>
      <thead>
        <tr>
          <th>Trigger</th>
          <th>Side</th>
          <th>When does it fire?</th>
          <th>Held item</th>
          <th>Scans armour</th>
          <th>Needs target</th>
        </tr>
      </thead>
      <tbody>
        {data.triggers.map((t) => (
          <Row key={t.name} t={t} />
        ))}
      </tbody>
    </table>
  );
}
