import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
const execute = promisify(execFile);
const decimal = value => typeof value === 'string' && /^(0|[1-9][0-9]{0,24})$/.test(value);

export function memoryPolicy(policy) {
  if (policy === undefined) return null;
  assert.ok(policy && typeof policy === 'object' && !Array.isArray(policy), 'Invalid memory policy');
  assert.deepEqual(Object.keys(policy).sort(), ['maximumCommitPercent', 'minimumFreeCommitBytes']);
  assert.ok(decimal(policy.minimumFreeCommitBytes) && BigInt(policy.minimumFreeCommitBytes) > 0n, 'Invalid minimum free commit bytes');
  assert.ok(Number.isInteger(policy.maximumCommitPercent) && policy.maximumCommitPercent > 0 && policy.maximumCommitPercent < 100, 'Invalid maximum commit percent');
  return policy;
}

export function assessMemory(raw, policy) {
  memoryPolicy(policy);
  assert.ok(raw && decimal(raw.committedBytes) && decimal(raw.commitLimitBytes), 'Missing commit-memory counters');
  const used = BigInt(raw.committedBytes), limit = BigInt(raw.commitLimitBytes);
  assert.ok(limit > 0n && used <= limit, 'Invalid commit-memory counters');
  const sample = { committedBytes: used.toString(), commitLimitBytes: limit.toString(), freeCommitBytes: (limit - used).toString() };
  return { sample, pressure: limit - used < BigInt(policy.minimumFreeCommitBytes)
    || used * 100n >= limit * BigInt(policy.maximumCommitPercent) };
}

/** System commit, not process RSS or free physical RAM. No disk/process traversal. */
export async function readMemory() {
  if (process.platform !== 'win32') throw new Error('Configured commit-memory guard requires Windows counters');
  const script = '$ErrorActionPreference="Stop"; $m=Get-CimInstance Win32_PerfRawData_PerfOS_Memory; '
    + '[pscustomobject]@{committedBytes=[string]$m.CommittedBytes;commitLimitBytes=[string]$m.CommitLimit} | ConvertTo-Json -Compress';
  const { stdout } = await execute('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', script],
    { windowsHide: true, timeout: 15000, maxBuffer: 8192 });
  return JSON.parse(stdout.replace(/^\uFEFF/, '').trim());
}
