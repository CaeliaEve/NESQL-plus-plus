import assert from 'node:assert/strict';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { canonical, digest } from './plan.mjs';
import { resolveReport } from './reports.mjs';
import { scopedJobKey, assertJobScope, assertReportScope } from './job-scope.mjs';

// Matches VisualProbe.describe: preview pixels and native-program dependencies
// are separate evidence. A text-only native entry legitimately has no draws.
function verifiedContent(sample) {
  if (!Number.isSafeInteger(sample.icons) || sample.icons < 0
    || !Number.isSafeInteger(sample.pictures) || sample.pictures < 0) return false;
  const names = ['pixels', 'visiblePixels', 'programs', 'programNodes', 'programImages', 'programPixels'];
  const values = {};
  for (const name of names) {
    const value = sample[name];
    if (typeof value !== 'string' || !/^(0|[1-9][0-9]{0,18})$/.test(value)) return false;
    values[name] = BigInt(value);
    if (values[name] > 9223372036854775807n) return false;
  }
  const draws = BigInt(sample.icons) + BigInt(sample.pictures);
  if (values.visiblePixels > values.pixels || values.programs > draws) return false;
  if (draws === 0n) return names.every(name => values[name] === 0n);
  if (values.pixels === 0n) return false;
  if (values.programs === 0n) {
    return values.programNodes === 0n && values.programImages === 0n && values.programPixels === 0n;
  }
  return values.programNodes >= values.programs;
}

/** Bounded native rendering, separate from data diagnostics and Source publication. */
async function runChecks(runner, plan, checkpoint, fatal, { diagnostic = false } = {}) {
  const option = plan.stages.source.preflight;
  assert.ok(option === true || option && typeof option === 'object' && !Array.isArray(option), 'Invalid rendering preflight options');
  const extra = option === true ? {} : option.samples ?? {};
  const full = diagnostic && plan.diagnostics?.visualCoverage === 'all';
  const selected = plan.stages.source.handlers, targets = [], empty = [], blocked = [];
  for (const id of Object.keys(extra)) assert.ok(selected.includes(id), 'Rendering samples widen the selected scope');
  for (const id of selected) {
    const state = checkpoint.handlers[id];
    if (diagnostic && !(state?.status === 'passed' && Number.isSafeInteger(state.total) && state.total >= 0
      && state.checked === state.total && state.offset === state.total && state.unexamined === 0
      && !state.error && !state.hasFailures && !state.failed?.length && !state.failures?.length
      && !state.failuresOmitted && !state.failureSamplesOmitted && Array.isArray(state.excluded))) {
      blocked.push({ handler: id, status: state?.status ?? 'unexamined',
        reason: 'Complete data diagnostics are required before rendering this Handler', error: state?.error ?? null });
      continue;
    }
    assert.ok(state?.status === 'passed' && state.checked === state.total && state.unexamined === 0, 'Rendering requires complete data diagnostics');
    const excluded = new Set(state.excluded), total = state.total;
    let first = 0, last = total - 1;
    while (first < total && excluded.has(first)) first++;
    while (last >= first && excluded.has(last)) last--;
    const indices = extra[id] ?? [];
    assert.ok(Array.isArray(indices) && indices.length <= 14, 'At most 14 additional risk indices per Handler');
    for (const index of indices) assert.ok(Number.isInteger(index) && index >= 0 && index < total && !excluded.has(index), 'Invalid or excluded rendering risk index');
    if (full && total > 0) {
      for (let offset = 0; offset < total; offset += 4096) {
        targets.push({ handler: id, offset, total, limit: Math.min(4096, total - offset),
          excluded: [...excluded].filter(index => index >= offset && index < offset + 4096).sort((a, b) => a - b) });
      }
      continue;
    }
    if (first === total) { empty.push({ handler: id, status: 'not_applicable', reason: 'No non-excluded entries', total }); continue; }
    for (const offset of [...new Set([first, last, ...indices])].sort((a, b) => a - b)) targets.push({ handler: id, offset, total });
  }
  const fingerprint = digest(JSON.stringify(canonical({ environment: checkpoint.fingerprint, targets, key: option.key ?? null, ...(diagnostic?{diagnostic:true,blocked}:{}) })));
  checkpoint.stages ??= {};
  const stageName = diagnostic ? 'diagnosticVisual' : 'preflight';
  let stage = checkpoint.stages[stageName];
  if (!stage || stage.fingerprint !== fingerprint) {
    if (stage) { checkpoint.preflightAttempts ??= []; checkpoint.preflightAttempts.push(stage); }
    stage = checkpoint.stages[stageName] = { fingerprint, status: 'running', samples: [], empty, ...(diagnostic?{blocked,coverage:full?'all-native-entries-with-passed-data':'representative-only',sourceGateSatisfied:false}:{}) };
  }
  assert.notEqual(stage.status, 'stopped', 'Rendering preflight stopped; a new explicit attempt is required');
  for (const target of targets) {
    const previous = stage.samples.find(sample => sample.handler === target.handler && sample.offset === target.offset);
    if (previous) {
      if (previous.archive) {
        const bytes = await readFile(previous.archive.archivePath);
        assert.equal(digest(bytes), previous.archive.sha256, 'Archived rendering evidence changed');
      }
      continue;
    }
    const request = { world: plan.world, domain: 'recipes', handlers: [target.handler], offset: target.offset, limit: target.limit??1, render: true, ...(full?{contentAudit:true}:{}),probes: plan.probes ?? [{ count: 1, channels: {} }] };
    const key = scopedJobKey(checkpoint, runner.runId, 'vis', request, fingerprint);
    request.key = key;
    if (!stage.active) {
      await runner.guardResources(plan, checkpoint, { stage: 'render-preflight', ...target });
      const started = await runner.client.request('POST', '/checks', request);
      assert.ok(started.id, 'Rendering check returned no job');
      stage.active = { id: started.id, key }; await runner.saveCheckpoint(checkpoint);
    }
    assert.equal(stage.active.key, key, 'An unrelated rendering check is active');
    let job;
    for (;;) {
      job = (await runner.client.request('GET', `/jobs/${stage.active.id}`)).job;
      if (job) assertJobScope(job, stage.active.id, request, checkpoint, runner.client.session);
      if (job && ['checked', 'failed', 'cancelled'].includes(job.state)) break;
      await new Promise(resolve => setTimeout(resolve, 500));
    }
    if (job.state !== 'checked') {
      const details = job.error?.details;
      if (diagnostic && job.state === 'failed' && typeof details?.type === 'string'
        && job.error.fatal === false && details.fatal === false && !fatal(job.error) && !fatal(details)) {
        stage.samples.push({ ...target, job: stage.active.id, status: 'failed', errors: [details], jobSnapshot: job });
        stage.active = null; await runner.saveCheckpoint(checkpoint); continue;
      }
      stage.status = 'stopped'; stage.error = job.error ?? { code: job.state };
      await runner.saveCheckpoint(checkpoint);
      throw new Error(`Rendering preflight stopped: ${stage.error.code}`);
    }
    assert.ok(job.report?.path && Number.isSafeInteger(job.report.bytes) && job.report.bytes > 0 && job.report.bytes <= 16 * 1024 * 1024, 'Rendering report metadata missing');
    assert.equal((await stat(job.report.path)).size, job.report.bytes, 'Rendering report file size changed');
    const bytes = await readFile(job.report.path);
    assert.equal(bytes.length, job.report.bytes, 'Rendering report byte count changed');
    assert.equal(digest(bytes), job.report.sha256, 'Rendering report digest changed');
    const report = await resolveReport(JSON.parse(bytes), path.dirname(job.report.path));
    assertReportScope(report, job, request, checkpoint, plan);
    assert.equal(report.job, stage.active.id, 'Rendering report belongs to another job');
    assert.equal(report.status, 'complete', 'Rendering report is incomplete');
    assert.equal(report.request?.key, key, 'Rendering request key differs');
    assert.equal(report.request?.world, plan.world, 'Rendering request world differs');
    assert.deepEqual(report.request?.handlers, request.handlers, 'Rendering request selection differs');
    assert.equal(report.request?.check?.render, true, 'A data-only check cannot satisfy rendering');
    assert.equal(report.request.check.offset, target.offset); assert.equal(report.request.check.limit, request.limit);
    if (full) assert.equal(report.request.check.contentAudit, true, 'Representative checks cannot certify a full content range');
    assert.ok(Array.isArray(report.rows) && report.rows.length === 1 && report.rows[0].handler === target.handler, 'Rendering report target differs');
    const row = report.rows[0], archive = await runner.archiveReport(job, bytes, [row]);
    const errors = [row.error, ...(row.failures ?? []).map(failure => failure.error)].filter(Boolean);
    const samples = row.visualSamples ?? [], expectedExcluded = target.excluded ?? [];
    const excludedIndices = new Set(expectedExcluded), byIndex = new Map(samples.map(sample => [sample.index, sample]));
    const validVisuals = full
      ? byIndex.size === samples.length
        && Array.from({ length: request.limit }, (_, index) => target.offset + index)
          .every(index => excludedIndices.has(index) || byIndex.has(index) && verifiedContent(byIndex.get(index)))
        && samples.every(sample => verifiedContent(sample) && (sample.index === -1
          || Number.isSafeInteger(sample.index) && sample.index >= target.offset && sample.index < target.offset + request.limit))
      : samples.some(sample => sample.index === target.offset && verifiedContent(sample));
    const passed = ['passed', 'partial'].includes(row.status) && row.totalRecipes === target.total && row.checkedRecipes === request.limit
      && row.offset === target.offset && row.end === target.offset + request.limit && !row.failedRecipes?.length && JSON.stringify([...(row.excludedRecipes??[])].sort((a,b)=>a-b))===JSON.stringify(expectedExcluded) && !errors.length && validVisuals;
    stage.samples.push({ ...target, job: stage.active.id, status: passed ? 'passed' : 'failed', archive, errors, visuals: row.visualSamples ?? [] });
    stage.active = null; await runner.saveCheckpoint(checkpoint);
    // Unknown isolation and native cleanup/environment faults cannot be continued.
    if (errors.some(fatal)) {
      stage.status = 'stopped'; await runner.saveCheckpoint(checkpoint);
      throw new Error('Rendering preflight found unsafe native state');
    }
  }
  assert.equal(await runner.planFingerprint(plan), checkpoint.fingerprint, 'Environment/session changed during rendering preflight');
  stage.status = !stage.samples.every(sample => sample.status === 'passed') ? 'failed' : blocked.length ? 'partial' : 'passed';
  await runner.saveCheckpoint(checkpoint); return stage;
}

export async function visualChecks(runner, plan, checkpoint, fatal, options = {}) {
  try { return await runChecks(runner, plan, checkpoint, fatal, options); }
  catch (error) {
    if (error.code === 'job_scope_mismatch') {
      const stage = checkpoint.stages?.[options.diagnostic ? 'diagnosticVisual' : 'preflight'];
      if (stage) {
        stage.status = 'stopped'; stage.error = { code: error.code, job: error.job, message: error.message };
        await runner.saveCheckpoint(checkpoint);
      }
    }
    throw error;
  }
}
