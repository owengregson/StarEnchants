import type {ReactNode} from 'react';
import surface from '@site/src/data/surface.json';
import styles from './styles.module.css';

// Renders surface.commands (the generated /se subcommand list) as a table.
// Aliases render as a compact muted sub-row beneath the table flow so a newly
// added command/alias shows up for free — nothing here is hand-maintained.

type Command = {
  name: string;
  args: string;
  description: string;
  alias: boolean;
};

const commands = surface.commands as unknown as Command[];

function aliasTarget(description: string): string | null {
  // Alias descriptions read "Alias of /se removeenchant." — surface the target.
  const m = description.match(/Alias of\s+(.+?)\.?$/i);
  return m ? m[1] : null;
}

function Row({c}: {c: Command}): ReactNode {
  if (c.alias) {
    const target = aliasTarget(c.description);
    return (
      <tr className={styles.aliasRow}>
        <td className={styles.cmd}>/se {c.name}</td>
        <td>
          <span className={styles.aliasNote}>
            alias of {target ?? c.description}
          </span>
        </td>
      </tr>
    );
  }
  return (
    <tr>
      <td className={styles.cmd}>
        /se {c.name}
        {c.args ? <span className={styles.cmdArgs}> {c.args}</span> : null}
      </td>
      <td>{c.description}</td>
    </tr>
  );
}

export default function CommandsTable(): ReactNode {
  return (
    <table>
      <thead>
        <tr>
          <th>Command</th>
          <th>What it does</th>
        </tr>
      </thead>
      <tbody>
        {commands.map((c) => (
          <Row key={c.name} c={c} />
        ))}
      </tbody>
    </table>
  );
}
