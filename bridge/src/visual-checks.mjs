import assert from 'node:assert/strict';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { canonical, digest } from './plan.mjs';
import { resolveReport } from './reports.mjs';

/** Bounded representative native rendering. Never substitutes for complete data diagnostics. */
export async function visualChecks(runner, plan, checkpoint, fatal) {
  const option = plan.stages.source.preflight;
  assert.ok(option === true || option && typeof option === 'object' && !Array.isArray(option), 'Invalid rendering preflight options');
  const extra = option === true ? {} : option.samples ?? {};
  const selected = plan.stages.source.handlers, targets = [], empty = [];
  for (const id of Object.keys(extra)) assert.ok(selected.includes(id), 'Rendering samples widen the selected scope');
  for (const id of selected) {
    const state = checkpoint.handlers[id];
    assert.ok(state?.status === 'passed' && state.checked === state.total && state.unexamined === 0, 'Rendering requires complete data diagnostics');
    const excluded = new Set(state.excluded), total = state.total;
    let first = 0, last = total - 1;
    while (first < total && excluded.has(first)) first++;
    while (last >= first && excluded.has(last)) last--;
    const indices = extra[id] ?? [];
    assert.ok(Array.isArray(indices) && indices.length <= 14, 'At most 14 additional risk indices per Handler');
    for (const index of indices) assert.ok(Number.isInteger(index) && index >= 0 && index < total && !excluded.has(index), 'Invalid or excluded rendering risk index');
    if (first === total) { empty.push({ handler: id, status: 'not_applicable', reason: 'No non-excluded recipes', total }); continue; }
    for (const offset of [...new Set([first, last, ...indices])].sort((a, b) => a - b)) targets.push({ handler: id, offset, total });
  }
  const fingerprint = digest(JSON.stringify(canonical({ environment: checkpoint.fingerprint, targets, key: option.key ?? null })));
  checkpoint.stages ??= {};
  let stage = checkpoint.stages.preflight;
  if (!stage || stage.fingerprint !== fingerprint) {
    if (stage) { checkpoint.preflightAttempts ??= []; checkpoint.preflightAttempts.push(stage); }
    stage = checkpoint.stages.preflight = { fingerprint, status: 'running', samples: [], empty };
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
    const key = `vis-${fingerprint.slice(0, 12)}-${digest(target.handler).slice(0, 12)}-${target.offset}`;
    const request = { key, world: plan.world, domain: 'recipes', handlers: [target.handler], offset: target.offset, limit: 1, render: true, probes: plan.probes ?? [{ count: 1, channels: {} }] };
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
      if (job && ['checked', 'failed', 'cancelled'].includes(job.state)) break;
      await new Promise(resolve => setTimeout(resolve, 500));
    }
    if (job.state !== 'checked') {
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
    assert.equal(report.job, stage.active.id, 'Rendering report belongs to another job');
    assert.equal(report.status, 'complete', 'Rendering report is incomplete');
    assert.equal(report.request?.key, key, 'Rendering request key differs');
    assert.equal(report.request?.world, plan.world, 'Rendering request world differs');
    assert.deepEqual(report.request?.handlers, request.handlers, 'Rendering request selection differs');
    assert.equal(report.request?.check?.render, true, 'A data-only check cannot satisfy rendering');
    assert.equal(report.request.check.offset, target.offset); assert.equal(report.request.check.limit, 1);
    assert.ok(Array.isArray(report.rows) && report.rows.length === 1 && report.rows[0].handler === target.handler, 'Rendering report target differs');
    const row = report.rows[0], archive = await runner.archiveReport(job, bytes, [row]);
    const errors = [row.error, ...(row.failures ?? []).map(failure => failure.error)].filter(Boolean);
    const passed = ['passed', 'partial'].includes(row.status) && row.totalRecipes === target.total && row.checkedRecipes === 1
      && row.offset === target.offset && row.end === target.offset + 1 && !row.failedRecipes?.length && !row.excludedRecipes?.length && !errors.length
      && row.visualSamples?.some(sample => sample.index === target.offset && sample.scenes > 0 && /^\d+$/.test(sample.pixels) && BigInt(sample.pixels) > 0n);
    stage.samples.push({ ...target, job: stage.active.id, status: passed ? 'passed' : 'failed', archive, errors, visuals: row.visualSamples ?? [] });
    stage.active = null; await runner.saveCheckpoint(checkpoint);
    // Unknown isolation and native cleanup/environment faults cannot be continued.
    if (errors.some(fatal)) {
      stage.status = 'stopped'; await runner.saveCheckpoint(checkpoint);
      throw new Error('Rendering preflight found unsafe native state');
    }
  }
  assert.equal(await runner.planFingerprint(plan), checkpoint.fingerprint, 'Environment/session changed during rendering preflight');
  stage.status = stage.samples.every(sample => sample.status === 'passed') ? 'passed' : 'failed';
  await runner.saveCheckpoint(checkpoint); return stage;
}
