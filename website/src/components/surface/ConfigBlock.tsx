import type {ReactNode} from 'react';
import CodeBlock from '@theme/CodeBlock';
import surface from '@site/src/data/surface.json';

// Renders the verbatim annotated config.yml (surface.config, a single string
// with newlines preserved) in a Docusaurus YAML code block — so the docs can
// never drift from the shipped config.

export default function ConfigBlock(): ReactNode {
  return <CodeBlock language="yaml">{surface.config}</CodeBlock>;
}
