import assert from 'node:assert/strict';
import { artifact } from './plan.mjs';

/** Expand for readers; the original report bytes remain the authoritative receipt. */
export async function resolveReport(report, directory, evidence = async () => {}) {
  const result = { ...report };
  for (const name of ['environment', 'handlers']) {
    const reference = report[name + 'Ref'];
    if (!reference) continue;
    assert.equal(report[name], undefined, `Ambiguous ${name} evidence`);
    assert.match(reference.path, /^shared\/[a-f0-9]{64}\.json$/);
    const checked = await artifact(directory, reference, 16 * 1024 * 1024);
    result[name] = JSON.parse(checked.bytes.toString('utf8'));
    assert.ok(name === 'handlers' ? Array.isArray(result[name]) : result[name] && typeof result[name] === 'object' && !Array.isArray(result[name]), `Invalid ${name} evidence`);
    if (name === 'environment' && report.provenance) assert.equal(reference.sha256, report.provenance.environment, 'Environment provenance mismatch');
    await evidence(reference, checked.bytes);
  }
  return result;
}
