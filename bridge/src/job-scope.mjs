import assert from 'node:assert/strict';
import { canonical, digest } from './plan.mjs';

export const JOB_ISOLATION_REVISION = 1;
const defaults = [{ count: 1, channels: {} }];
const json = value => JSON.stringify(canonical(value));

function timestamp(value) {
  assert.ok(typeof value === 'string' && Number.isFinite(Date.parse(value)), 'Missing or invalid creation time');
  return Date.parse(value);
}

/** Match the producer's ordering/defaults, while keeping every request field. */
function nativeRequest(request, check = false) {
  const selection = check ? request : request.check;
  const probes = (request.probes ?? defaults).map(probe => ({ count: probe.count, channels: probe.channels ?? {} }));
  probes.sort((a, b) => {
    if (a.count !== b.count) return a.count - b.count;
    const left = Object.entries(a.channels).sort(), right = Object.entries(b.channels).sort();
    for (let i = 0; i < Math.min(left.length, right.length); i++) {
      if (left[i][0] !== right[i][0]) return left[i][0] < right[i][0] ? -1 : 1;
      if (left[i][1] !== right[i][1]) return left[i][1] - right[i][1];
    }
    return left.length - right.length;
  });
  return { key: request.key, name: check ? 'check' : request.name, profile: check ? 'data' : request.profile,
    world: request.world ?? null, handlers: [...(request.handlers ?? [])].sort(), probes,
    scope: request.scope ?? null, resume: request.resume ?? null,
    check: selection ? { domain: selection.domain, offset: selection.offset ?? 0, limit: selection.limit ?? 128,
      controllers: [...(selection.controllers ?? [])].sort((a, b) => a - b), resources: [...(selection.resources ?? [])].sort(),
      render: selection.render ?? false, contentAudit: selection.contentAudit ?? false } : null };
}

/** Stable within one checkpoint, distinct for new runs, sessions or candidates.
 * Java keeps historical retry keys durable; never truncate identity to a suffix. */
export function scopedJobKey(checkpoint, runId, phase, request, label = null) {
  assert.ok(typeof runId === 'string' && runId.length, 'A complete run ID is required');
  assert.ok(typeof checkpoint.fingerprint === 'string' && checkpoint.fingerprint.length, 'A checkpoint fingerprint is required');
  timestamp(checkpoint.createdAt);
  assert.ok(['chk', 'vis', 'src'].includes(phase), 'Unknown job phase');
  const { key: ignored, ...content } = request;
  return `${phase}-${digest(json({ revision: JOB_ISOLATION_REVISION, runId, fingerprint: checkpoint.fingerprint,
    createdAt: checkpoint.createdAt, phase, label, request: content }))}`;
}

function scoped(job, check) {
  try { check(); }
  catch (cause) {
    const error = new Error(`Job scope mismatch: ${cause.message}`, { cause });
    error.code = 'job_scope_mismatch'; error.job = job?.id ?? null;
    throw error;
  }
}

function provenance(value) {
  assert.equal(value?.revision, 1, 'Missing provenance revision');
  for (const field of ['environment', 'runtime', 'session', 'selection']) {
    assert.match(value[field] ?? '', /^[a-f0-9]{64}$/, `Missing provenance ${field}`);
  }
}

/** Validate before interpreting checked/failed/cancelled, including early faults
 * which legitimately have no capture provenance yet. */
export function assertJobScope(job, id, request, checkpoint, transportSession) {
  scoped(job, () => {
    if (checkpoint.transportSession !== undefined) assert.equal(transportSession, checkpoint.transportSession, 'Game transport session changed');
    assert.equal(job?.id, id, 'Returned Job ID differs');
    assert.ok(timestamp(job.created) >= timestamp(checkpoint.createdAt), 'Job predates the current checkpoint');
    assert.ok(job.request && json(nativeRequest(job.request)) === json(nativeRequest(request, request.domain !== undefined)), 'Job request differs');
    if (['checked', 'succeeded'].includes(job.state)) provenance(job.provenance);
  });
}

/** Hash verification proves byte integrity; these checks prove whose bytes they
 * are. Transport UUID and native world-epoch SHA are intentionally not compared. */
export function assertReportScope(report, job, request, checkpoint, plan) {
  scoped(job, () => {
    assert.equal(report.job, job.id, 'Report belongs to another Job');
    assert.equal(report.status, 'complete', 'Report is incomplete');
    assert.ok(timestamp(report.created) >= timestamp(job.created), 'Report predates its Job');
    assert.ok(report.request && json(nativeRequest(report.request)) === json(nativeRequest(request, true)), 'Report request differs');
    provenance(report.provenance);
    assert.ok(json(report.provenance) === json(job.provenance), 'Report provenance differs from Job');
    const mod = report.environment?.mods?.find(row => row.id === 'nesql-exporter' || row.id === 'nesql');
    assert.equal(mod?.sha256, plan.modSha256, 'Report exporter SHA256 differs from verified candidate');
    assert.equal(mod?.version, plan.expectedExporter ?? '0.15.0', 'Report exporter version differs');
    const native = { runtime: report.provenance.runtime, session: report.provenance.session };
    if (checkpoint.nativeJobScope) assert.ok(json(native) === json(checkpoint.nativeJobScope), 'Native session/runtime changed between reports');
    checkpoint.nativeJobScope = native;
  });
}
