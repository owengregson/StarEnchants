import type {ReactNode} from 'react';
import catalog from '@site/src/data/catalog.json';
import type {Catalog} from './types';
import styles from './styles.module.css';

const data = catalog as unknown as Catalog;

// Plain-language gloss for each operator/flow token. Keyed by the catalog
// symbol/token; missing entries fall back to the catalog's own name/doc.
const OP_MEANING: Record<string, string> = {
  '==': 'equal to',
  '!=': 'not equal to',
  '<': 'less than',
  '<=': 'less than or equal to',
  '>': 'greater than',
  '>=': 'greater than or equal to',
  contains: 'text contains the substring',
  matchesregex: 'text matches the regular expression',
};

export function RelationalOperators(): ReactNode {
  return (
    <table>
      <thead>
        <tr>
          <th>Operator</th>
          <th>Means</th>
        </tr>
      </thead>
      <tbody>
        {data.conditions.relational.map((op) => (
          <tr key={op.symbol}>
            <td>
              <code>{op.symbol}</code>
            </td>
            <td>{OP_MEANING[op.symbol] ?? op.name}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function StringOperators(): ReactNode {
  return (
    <table>
      <thead>
        <tr>
          <th>Operator</th>
          <th>Means</th>
        </tr>
      </thead>
      <tbody>
        {data.conditions.string.map((op) => (
          <tr key={op.symbol}>
            <td>
              <code>{op.symbol}</code>
            </td>
            <td>{OP_MEANING[op.symbol] ?? op.name}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function FlowClauses(): ReactNode {
  return (
    <table>
      <thead>
        <tr>
          <th>Clause</th>
          <th>What it does when the test is true</th>
        </tr>
      </thead>
      <tbody>
        {data.conditions.flow.map((c) => (
          <tr key={c.token}>
            <td>
              <code>{c.token}</code>
            </td>
            <td>{c.doc}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

// ---- Variables, grouped by scope -------------------------------------------

const TYPE_TEXT: Record<string, string> = {
  NUM: 'number',
  STR: 'text',
  BOOL: 'true / false',
};

interface VarGroup {
  id: string;
  label: string;
  blurb: string;
  // A variable belongs here if its name starts with this prefix.
  prefix: string;
}

// Bare (prefix-less) facts get a dedicated group; everything else is by scope.
const SCOPED: VarGroup[] = [
  {
    id: 'actor',
    label: 'actor. — the player who triggered it',
    blurb: 'Facts about the player wearing/using the enchant.',
    prefix: 'actor.',
  },
  {
    id: 'victim',
    label: 'victim. — the entity they hit',
    blurb:
      'Facts about the combat victim. Only meaningful on combat triggers (ATTACK, DEFENSE, KILL, …).',
    prefix: 'victim.',
  },
  {
    id: 'block',
    label: 'block. — the block in play',
    blurb: 'Facts about the block (e.g. on a MINE/BREAK trigger).',
    prefix: 'block.',
  },
  {
    id: 'world',
    label: 'world. — the world & weather',
    blurb: 'Facts about the world the activation happened in.',
    prefix: 'world.',
  },
];

function VarTable({names}: {names: {name: string; type: string}[]}): ReactNode {
  return (
    <table>
      <thead>
        <tr>
          <th>Variable</th>
          <th>Type</th>
        </tr>
      </thead>
      <tbody>
        {names.map((v) => (
          <tr key={v.name}>
            <td>
              <code>%{v.name}%</code>
            </td>
            <td>{TYPE_TEXT[v.type] ?? v.type.toLowerCase()}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function VariableTables(): ReactNode {
  const all = data.variables;

  const scopedSections = SCOPED.map((g) => ({
    group: g,
    vars: all.filter((v) => v.name.startsWith(g.prefix)),
  })).filter((s) => s.vars.length > 0);

  // Bare combat/state facts: no "scope." prefix at all.
  const bare = all.filter((v) => !v.name.includes('.'));

  return (
    <>
      {scopedSections.map((s) => (
        <section key={s.group.id}>
          <h3>
            <code>{s.group.id}.</code> &mdash;{' '}
            {s.group.label.split('—')[1]?.trim()}
          </h3>
          <p className={styles.familyIntro}>{s.group.blurb}</p>
          <VarTable names={s.vars} />
        </section>
      ))}

      {bare.length > 0 ? (
        <section>
          <h3>Combat &amp; state facts (no scope prefix)</h3>
          <p className={styles.familyIntro}>
            Bare facts about the current activation and the player&apos;s state.
          </p>
          <VarTable names={bare} />
        </section>
      ) : null}
    </>
  );
}
