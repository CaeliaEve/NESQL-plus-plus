import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const source = readFileSync(
  new URL(
    './src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportRepositoryFactStreamer.java',
    import.meta.url,
  ),
  'utf8',
);

test('raw-export repository streaming uses stable id keyset pagination', () => {
  assert.doesNotMatch(source, /\.setFirstResult\s*\(/);
  assert.doesNotMatch(source, /\bint\s+offset\s*=/);

  for (const alias of ['i', 'f', 'r']) {
    assert.match(source, new RegExp(`WHERE ${alias}\\.id > :lastId ORDER BY ${alias}\\.id`));
  }

  assert.equal((source.match(/query\.setParameter\("lastId", lastId\)/g) ?? []).length, 3);
  assert.match(source, /lastId = items\.get\(items\.size\(\) - 1\)\.getId\(\)/);
  assert.match(source, /lastId = fluids\.get\(fluids\.size\(\) - 1\)\.getId\(\)/);
  assert.match(source, /lastId = recipes\.get\(recipes\.size\(\) - 1\)\.getId\(\)/);
});

test('raw-export repository hot paths avoid JSON trees and bound shard writers', () => {
  assert.doesNotMatch(source, /toJsonTree\(mapped, CanonicalItem\.class\)/);
  assert.doesNotMatch(source, /toJsonTree\(mapped, CanonicalFluid\.class\)/);
  assert.match(source, /primary\.write\(mapped, CanonicalItem\.class\)/);
  assert.match(source, /primary\.write\(mapped, CanonicalFluid\.class\)/);
  assert.match(source, /new RawExportRecipeShardWriterPool\(rawDir, gson\)/);
  assert.match(source, /shardWriters\.finish\(shards\.values\(\), new RawExportRecipeShardWriterPool\.IndexPublisher\(\)/);
  assert.match(source, /shardWriters\.write\(shard, element\)/);
});
