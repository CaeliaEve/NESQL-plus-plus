import assert from 'node:assert/strict';
import test from 'node:test';
import { selection } from '../../acceptance/scripts/selection.mjs';

test('explicit export selection never widens an invalid or empty list to all handlers', () => {
  const entry = (key, supported = true) => ({ id: 'category_' + key.repeat(64), supported, name: key, source: { handler: 'example', key } });
  const state = { handlers: [entry('a'), entry('b'), entry('c', false)] };
  const selected = selection(state, 'visuals', state.handlers[1].id);
  assert.deepEqual(selected.selected, [state.handlers[1]]);
  assert.equal(selected.complete, false);
  assert.equal(selected.scope, 'selection');
  for (const ids of ['', state.handlers[2].id, 'category_' + 'd'.repeat(64), `${state.handlers[0].id},${state.handlers[0].id}`, 'wrong']) {
    assert.throws(() => selection(state, 'visuals', ids), undefined, ids);
  }
});
