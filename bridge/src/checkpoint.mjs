import { readFile, rename, open } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';
const empty = '0'.repeat(64);
const digest = value => createHash('sha256').update(JSON.stringify(value)).digest('hex');
const fields = state => Object.fromEntries(Object.entries(state).filter(([key, value]) => key !== 'handlers' && key !== '_journal' && value !== undefined));
const remember = object => new Map(Object.entries(object).map(([key, value]) => [key, JSON.stringify(value)]));
function diff(before, object) {
  const set = Object.fromEntries(Object.entries(object).filter(([key, value]) => before.get(key) !== JSON.stringify(value)));
  return { set, remove: [...before.keys()].filter(key => !Object.hasOwn(object, key)) };
}
function apply(target, patch) {
  for (const key of patch.remove) delete target[key];
  for (const [key, value] of Object.entries(patch.set)) Object.defineProperty(target, key, { value, writable: true, configurable: true, enumerable: true });
}
async function durable(file, bytes, flag) {
  const handle = await open(file, flag);
  try { await handle.writeFile(bytes); await handle.sync(); } finally { await handle.close(); }
}
export async function readCheckpoint(file, { repairTail = false } = {}) {
  const state = JSON.parse(await readFile(file, 'utf8'));
  let journal;
  try { journal = await readFile(file + '.journal'); } catch (error) { if (error.code === 'ENOENT') return state; throw error; }
  let head = state._journal ?? { sequence: 0, sha256: empty }, offset = 0;
  for (;;) {
    const newline = journal.indexOf(10, offset);
    if (newline < 0) break;
    const entry = JSON.parse(journal.subarray(offset, newline).toString('utf8')); offset = newline + 1;
    if (entry.sequence <= head.sequence) continue; // Already covered by the atomic snapshot.
    const body = { sequence: entry.sequence, previous: entry.previous, patch: entry.patch };
    assert.equal(entry.sequence, head.sequence + 1, 'Checkpoint journal sequence mismatch');
    assert.equal(entry.previous, head.sha256, 'Checkpoint journal chain mismatch');
    assert.equal(entry.sha256, digest(body), 'Checkpoint journal SHA256 mismatch');
    apply(state, entry.patch.fields); state.handlers ??= {}; apply(state.handlers, entry.patch.handlers);
    head = { sequence: entry.sequence, sha256: entry.sha256 };
  }
  state._journal = head;
  if (repairTail && offset < journal.length) {
    const handle = await open(file + '.journal', 'r+');
    try { await handle.truncate(offset); await handle.sync(); } finally { await handle.close(); }
  }
  return state;
}
export class CheckpointLog {
  constructor(file, state) {
    this.file = file; this.head = state?._journal ?? { sequence: 0, sha256: empty }; this.updates = state ? 64 : 0; this.bytes = 0;
    if (state) this.remember(state);
  }
  remember(state) { this.fields = remember(fields(state)); this.handlers = remember(state.handlers ?? {}); }
  async save(state, force = false) {
    if (!this.fields) force = true;
    else {
      const patch = { fields: diff(this.fields, fields(state)), handlers: diff(this.handlers, state.handlers ?? {}) };
      const body = { sequence: this.head.sequence + 1, previous: this.head.sha256, patch };
      const sha256 = digest(body), bytes = Buffer.from(JSON.stringify({ ...body, sha256 }) + '\n');
      await durable(this.file + '.journal', bytes, 'a');
      this.head = { sequence: body.sequence, sha256 }; this.updates++; this.bytes += bytes.length;
    }
    state._journal = this.head;
    if (force || this.updates >= 64 || this.bytes >= 8 * 1024 * 1024) {
      await durable(this.file + '.tmp', JSON.stringify(state, null, 2), 'w');
      await rename(this.file + '.tmp', this.file);
      await durable(this.file + '.journal', '', 'w');
      this.updates = this.bytes = 0;
    }
    this.remember(state);
  }
}
