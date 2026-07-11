import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');

test('NEI runtime browser snapshot suppresses export-only recipe stacks from the right browser', () => {
  const source = readSource(
    'src/main/java/com/github/dcysteine/nesql/exporter/local/CanonicalBrowserLayoutIndexWriter.java',
  );

  assert.match(
    source,
    /When the runtime NEI ItemPanel is available, it is the browser source of truth\./,
    'browser layout writer should document the NEI-visible browser authority',
  );
  assert.match(
    source,
    /int suppressedExportOnlyItems = 0;/,
    'writer should count export-only stacks without appending them to browser entries',
  );
  assert.match(
    source,
    /index\.exportOnlyItemCount = suppressedExportOnlyItems;/,
    'export-only stacks should remain diagnostics, not browser rows',
  );
  assert.match(
    source,
    /index\.exportOnlyItemsSuppressed = true;/,
    'runtime snapshots should expose that export-only stacks were deliberately suppressed',
  );
  assert.doesNotMatch(
    source,
    /Keep augmented\/export-only stacks available after the true NEI list/,
    'old append-after-NEI behavior should not return',
  );
});
