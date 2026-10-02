import test from 'node:test';
import assert from 'node:assert/strict';
import { assessMemory, memoryPolicy } from '../src/resources.mjs';

test('memory limits retain exact bytes, reject bad counters and enforce both thresholds', () => {
  const policy = { minimumFreeCommitBytes: '10', maximumCommitPercent: 95 };
  assert.equal(assessMemory({ committedBytes: '89', commitLimitBytes: '100' }, policy).pressure, false);
  assert.equal(assessMemory({ committedBytes: '91', commitLimitBytes: '100' }, policy).pressure, true);
  assert.equal(assessMemory({ committedBytes: '950', commitLimitBytes: '1000' }, policy).pressure, true);
  assert.equal(assessMemory({ committedBytes: '949', commitLimitBytes: '1000' }, policy).pressure, false);
  assert.equal(assessMemory({ committedBytes: '9007199254740992', commitLimitBytes: '9007199254741003' }, policy).sample.freeCommitBytes, '11');
  for (const raw of [{}, { committedBytes: 0, commitLimitBytes: 100 }, { committedBytes: '-1', commitLimitBytes: '100' },
    { committedBytes: '101', commitLimitBytes: '100' }, { committedBytes: '0', commitLimitBytes: '0' }]) {
    assert.throws(() => assessMemory(raw, policy));
  }
  assert.throws(() => memoryPolicy({ ...policy, maximumCommitPercent: 100 }));
  assert.throws(() => memoryPolicy({ ...policy, minimumFreeCommitBytes: '0' }));
  assert.throws(() => memoryPolicy({ ...policy, minimumFreeCommitBytes: 10 }));
});
