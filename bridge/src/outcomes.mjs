import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { lstat, open, mkdir, copyFile, rename } from 'node:fs/promises';
import { constants } from 'node:fs';
import { createGunzip } from 'node:zlib';
import path from 'node:path';

const maximum = 8 * 1024 * 1024 * 1024;
const lineLimit = 1024 * 1024 + 256;
const object = value => value && typeof value === 'object' && !Array.isArray(value);
const integer = value => Number.isSafeInteger(value) && value >= 0;
const decimal = value => typeof value === 'string' && /^(0|[1-9]\d*)$/.test(value) && Number.isSafeInteger(Number(value));

async function safeFile(root, relative, temporary = false) {
  assert.ok(typeof relative === 'string' && /^[A-Za-z0-9_-]{1,80}-outcomes\/[a-f0-9]{64}\.jsonl\.gz$/.test(relative), 'Invalid outcome evidence path');
  let file = path.resolve(root);
  const parts = relative.split('/'); if (temporary) parts[parts.length - 1] += '.tmp';
  for (const part of parts) {
    file = path.join(file, part);
    const state = await lstat(file);
    assert.ok(!state.isSymbolicLink(), 'Outcome evidence cannot traverse links');
  }
  return file;
}

/** Bounded streaming verification, including gzip integrity and every native disposition. */
export async function verifyOutcomes(root, row, { temporary = false, allowIncomplete = false } = {}) {
  assert.equal(row.accountingRevision, 1, 'Unsupported diagnostic accounting protocol');
  const receipt = row.captureOutcomeFile;
  assert.ok(object(receipt), 'Current diagnostics need a full native outcome ledger');
  assert.equal(receipt.format, 'nesql.capture-outcomes'); assert.equal(receipt.revision, 1);
  for (const field of ['bytes', 'logicalBytes', 'rows']) assert.ok(decimal(receipt[field]), `Invalid outcome ${field}`);
  assert.match(receipt.sha256, /^[a-f0-9]{64}$/);
  assert.ok(Number(receipt.bytes) > 0 && Number(receipt.bytes) <= maximum && Number(receipt.logicalBytes) <= maximum, 'Outcome evidence exceeds its budget');
  assert.equal(typeof receipt.rangeComplete, 'boolean');
  assert.ok(receipt.rangeComplete || allowIncomplete && row.status === 'failed', 'Diagnostic outcome range did not complete');
  assert.ok(integer(row.offset) && integer(row.checkedRecipes) && row.checkedRecipes <= 4096, 'Invalid diagnostic range');
  assert.ok(Array.isArray(row.failedRecipes) && new Set(row.failedRecipes).size === row.failedRecipes.length
    && row.failedRecipes.every(index => integer(index) && index >= row.offset && index < row.offset + row.checkedRecipes), 'Invalid native failure indices');
  const file = await safeFile(root, receipt.path, temporary), before = await lstat(file);
  assert.ok(before.isFile() && before.size === Number(receipt.bytes), 'Outcome evidence length changed');
  const hash = createHash('sha256'), handle = await open(file, 'r'), source = handle.createReadStream(), decoded = createGunzip();
  let compressed = 0, logical = 0, tail = Buffer.alloc(0), count = 0, previous = -1;
  const counts = { captured: 0, excluded: 0, duplicate: 0, reference: 0, emitted: 0, duplicates: 0 };
  const capturedEntries = new Map();
  const excluded = new Set(), duplicates = new Set(), failed = new Set(row.failedRecipes);
  source.on('data', bytes => { compressed += bytes.length; hash.update(bytes); if (compressed > Number(receipt.bytes)) source.destroy(new Error('Outcome evidence grew')); });
  source.on('error', error => decoded.destroy(error)); source.pipe(decoded);
  const line = bytes => {
    assert.ok(bytes.length <= lineLimit && bytes.length > 0, 'Invalid outcome record size');
    const entry = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes)), value = entry.outcome;
    assert.ok(integer(entry.index) && entry.index >= row.offset && entry.index < row.offset + row.checkedRecipes && entry.index > previous && !failed.has(entry.index), 'Outcome indices are missing, repeated or failed');
    previous = entry.index; assert.ok(object(value), 'Invalid native disposition');
    for (const field of ['emitted', 'duplicates', 'references']) assert.ok(integer(value[field]), `Invalid disposition ${field}`);
    const keys = ['kind', 'emitted', 'duplicates', 'references'];
    if (value.kind === 'captured' || value.kind === 'duplicate') {
      keys.push('duplicateOrigins');
      assert.ok(Array.isArray(value.duplicateOrigins) && value.duplicateOrigins.length === value.duplicates && value.references === 0
        && value.emitted + value.duplicates > 0 && value.emitted + value.duplicates <= 65536, 'Native branch accounting changed');
      assert.equal(value.kind === 'captured', value.emitted > 0, 'Invalid captured/duplicate disposition');
      const seenBranches = new Set();
      for (const origin of value.duplicateOrigins) {
        assert.ok(object(origin) && Object.keys(origin).length === 4, 'Invalid duplicate provenance');
        assert.match(origin.recipe, /^recipe_[a-f0-9]{64}$/);
        assert.ok(integer(origin.firstIndex) && integer(origin.firstBranch) && integer(origin.branch)
          && (origin.firstIndex < entry.index || origin.firstIndex === entry.index && origin.firstBranch < origin.branch), 'Duplicate lost its earlier native origin');
        assert.ok(origin.branch < value.emitted + value.duplicates && !seenBranches.has(origin.branch), 'Duplicate branch origin is invalid');
        seenBranches.add(origin.branch);
        if (origin.firstIndex !== entry.index)
          assert.ok(capturedEntries.has(origin.firstIndex) && origin.firstBranch < capturedEntries.get(origin.firstIndex), 'Duplicate origin points to an entry that never emitted a recipe');
      }
      if (value.kind === 'duplicate') duplicates.add(entry.index);
      else capturedEntries.set(entry.index, value.emitted + value.duplicates);
    } else if (value.kind === 'excluded') {
      keys.push('proof'); assert.ok(object(value.proof) && typeof value.proof.reason === 'string' && value.proof.reason.length > 0, 'Native exclusion lacks evidence');
      assert.equal(value.emitted + value.duplicates + value.references, 0); excluded.add(entry.index);
    } else {
      assert.equal(value.kind, 'reference', 'Unknown native disposition');
      assert.equal(value.emitted + value.duplicates, 0); assert.equal(value.references, 1);
    }
    assert.deepEqual(Object.keys(value).sort(), keys.sort(), 'Unknown native disposition fields');
    counts[value.kind]++; counts.emitted += value.emitted; counts.duplicates += value.duplicates; count++;
  };
  try {
    for await (const chunk of decoded) {
      logical += chunk.length; assert.ok(logical <= Number(receipt.logicalBytes), 'Outcome decompression exceeds its budget');
      tail = Buffer.concat([tail, chunk]);
      let end;
      while ((end = tail.indexOf(10)) !== -1) { line(tail.subarray(0, end)); tail = tail.subarray(end + 1); }
      assert.ok(tail.length <= lineLimit, 'Outcome record exceeds its byte budget');
    }
    assert.equal(tail.length, 0, 'Outcome ledger has an unfinished record');
    assert.equal(logical, Number(receipt.logicalBytes)); assert.equal(count, Number(receipt.rows));
    assert.equal(compressed, Number(receipt.bytes)); assert.equal(hash.digest('hex'), receipt.sha256, 'Outcome evidence digest mismatch');
    assert.equal(count + failed.size, row.checkedRecipes, 'Native dispositions do not cover the attempted range');
    assert.deepEqual([...excluded], row.excludedRecipes); assert.deepEqual([...duplicates], row.duplicateRecipes);
    assert.equal(counts.captured, row.passedRecipes); assert.equal(counts.reference, row.referenceRows);
    assert.equal(counts.emitted, row.emittedBranches); assert.equal(counts.duplicates, row.duplicateBranches);
    if (row.recordKind === 'references') assert.equal(counts.reference, count, 'Reference handler emitted recipe dispositions');
    else if (row.recordKind === 'recipes') assert.equal(counts.reference, 0, 'Recipe handler emitted reference dispositions');
    const after = await lstat(file);
    assert.ok(after.isFile() && !after.isSymbolicLink() && after.size === before.size && after.mtimeMs === before.mtimeMs && after.ino === before.ino, 'Outcome evidence changed while reading');
    return { file, receipt, counts };
  } finally { source.destroy(); decoded.destroy(); await handle.close(); }
}

export async function archiveOutcomes(root, archive, row) {
  const checked = await verifyOutcomes(root, row, { allowIncomplete: true }), target = path.join(archive, checked.receipt.path);
  await mkdir(path.dirname(target), { recursive: true });
  try { await lstat(target); await verifyOutcomes(archive, row, { allowIncomplete: true }); }
  catch (error) {
    if (error.code !== 'ENOENT') throw error;
    const temporary = target + '.tmp';
    await copyFile(checked.file, temporary, constants.COPYFILE_EXCL);
    // Verify the copied bytes before promotion, with the same bounded reader.
    await verifyOutcomes(archive, row, { temporary: true, allowIncomplete: true });
    await rename(temporary, target);
  }
  return { ...checked.receipt, archivePath: target, counts: checked.counts };
}
