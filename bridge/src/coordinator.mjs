import { readFile, writeFile, rename, mkdir, stat } from 'node:fs/promises';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { parseArgs } from 'node:util';
import assert from 'node:assert/strict';
import { prepare, canonical, sha, artifact, digest } from './plan.mjs';
import { GameClient, GameError } from './client.mjs';

/** Only the Java producer can classify exception inheritance and cleanup failures.
 * Missing severity is unknown isolation, so it must stop further game work. */
export function isFatalError(error) {
  if (!error || error.fatal !== false) return true;
  const code = typeof error.code === 'string' ? error.code : '';
  return Boolean(code === 'environment_changed' || code === 'world_unavailable'
    || code === 'check_cleanup' || code === 'preview_cleanup'
    || code.startsWith('client_') || code.endsWith('_changed')
    || (Array.isArray(error.suppressed) && error.suppressed.length > 0)
    || (Number.isInteger(error.suppressedOmitted) && error.suppressedOmitted > 0)
    || (error.cause && isFatalError(error.cause)));
}

export function assertFailureDetails(error) {
  if (!error || typeof error !== 'object') throw new Error('Job failure has no structured Java details');
  const details = error.details;
  assert.ok(details && typeof details === 'object' && typeof details.type === 'string', 'Job failure details are missing');
  assert.ok(typeof details.fatal === 'boolean' || typeof error.fatal === 'boolean', 'Job failure severity is missing');
  return details;
}

/**
 * Coordinated acceptance runner strictly fulfilling R5, R6, R7, R9:
 * - Validates true game environment (world folder/name, ready) without artificial singleplayer flags.
 * - Enforces bounded job keys strictly <= 80 characters with explicit runId.
 * - Automatically advances contiguous pagination across arbitrary ranges without stopping at 4096.
 * - Refuses unverified reports; verifies real row.status, verifies report bytes/sha256, preserves all failure locations.
 * - Stores environment-isolated checkpoints atomically; halts entirely on fatal environment errors.
 * - Provides CLI entry point with dry-run rehearsal capability.
 */
export class AcceptanceCoordinator {
  constructor(instance, options = {}) {
    this.instance = instance;
    this.client = new GameClient(instance, options);
    this.checkpointPath = path.join(instance, 'nesql', 'acceptance-checkpoint.json');
    this.options = options;
    this.runId = options.runId || ('run-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 6));
  }

  static fingerprint(env) {
    return digest(JSON.stringify(canonical(env)));
  }

  async loadCheckpoint(currentFingerprint) {
    if (existsSync(this.checkpointPath)) {
      try {
        const text = await readFile(this.checkpointPath, 'utf8');
        const data = JSON.parse(text);
        if (data.fingerprint && data.fingerprint === currentFingerprint) {
          // Restore persisted runId to ensure idempotence across restarts (C8)
          this.runId = data.runId || this.runId;
          return data;
        }
        console.warn(`[Coordinator] Checkpoint environment fingerprint mismatch (${data.fingerprint} vs ${currentFingerprint}); starting fresh.`);
      } catch (err) {
        console.warn('[Coordinator] Corrupted checkpoint, starting fresh:', err.message);
      }
    }
    return {
      version: '0.15.0',
      runId: this.runId,
      fingerprint: currentFingerprint,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      activeJob: null,
      handlers: {},
      summary: { total: 0, checked: 0, failed: 0, excluded: 0, unexamined: 0 },
      failures: [],
      archivedReports: []
    };
  }

  updateSummary(checkpoint) {
    const handlers = Object.values(checkpoint.handlers || {});
    const summary = {
      total: handlers.length,
      checked: 0,
      failed: 0,
      excluded: 0,
      unexamined: 0
    };
    for (const h of handlers) {
      if (h.status === 'passed') summary.checked++;
      else if (h.status === 'failed' || h.status === 'unsupported') summary.failed++;
      else if (h.status === 'excluded') summary.excluded++;
      else summary.unexamined++;
    }
    checkpoint.summary = summary;
    return summary;
  }

  async saveCheckpoint(checkpoint) {
    checkpoint.updatedAt = new Date().toISOString();
    this.updateSummary(checkpoint);
    const tempPath = this.checkpointPath + '.tmp';
    await mkdir(path.dirname(this.checkpointPath), { recursive: true });
    await writeFile(tempPath, JSON.stringify(checkpoint, null, 2), 'utf8');
    await rename(tempPath, this.checkpointPath);
  }

  /**
   * Run full verification for a list of handlers with automatic contiguous pagination.
   */
  async runPlan(plan, { onProgress = () => {} } = {}) {
    const game = await this.client.request('GET', '/game');
    if (!game.ready) {
      throw new Error(`Game server is not ready: ${game.reason || 'unknown'}`);
    }

    const expectedExporter = plan.expectedExporter || '0.15.0';
    const expectedRevision = plan.expectedRevision !== undefined ? plan.expectedRevision : 14;
    if (game.exporter !== expectedExporter || game.revision !== expectedRevision) {
      throw new Error(`Game exporter protocol mismatch: expected ${expectedExporter} rev ${expectedRevision}, got ${game.exporter} rev ${game.revision}`);
    }

    const actualWorld = game.world?.folder || game.world?.name;
    if (!actualWorld) {
      throw new Error('Game client has no active world loaded');
    }
    if (plan.world && actualWorld !== plan.world) {
      throw new Error(`Target world mismatch: expected '${plan.world}', game has '${actualWorld}'`);
    }

    assert.ok(game.sources?.valid === true && Array.isArray(game.sources.rows), 'Game source provenance is missing or invalid');
    const foundModRow = game.sources.rows.find(row => row.id === 'nesql-exporter' || row.id === 'nesql');
    assert.ok(foundModRow?.valid === true && typeof foundModRow.path === 'string' && path.isAbsolute(foundModRow.path), 'Loaded mod source is missing or invalid');
    assert.ok((await stat(foundModRow.path)).isFile(), 'Loaded mod source is not a file');
    const actualModSha = digest(await readFile(foundModRow.path));
    assert.ok(sha(plan.modSha256), 'Expected mod SHA256 is required');
    assert.equal(actualModSha, plan.modSha256, 'Loaded mod SHA256 mismatch');
    if (foundModRow.sha256 !== undefined) assert.equal(foundModRow.sha256, actualModSha, 'Reported mod source digest differs from its file');
    assert.ok(Array.isArray(plan.handlers) && plan.handlers.length, 'A nonempty plan is required');
    assert.equal(new Set(plan.handlers.map(item => item.id)).size, plan.handlers.length, 'Duplicate plan target');
    const planHash = digest(JSON.stringify(canonical({ handlers: plan.handlers, probes: plan.probes ?? [], dependencies: plan.dependencies ?? {}, pageSize: this.options.pageSize ?? 4096 })));
    const envFingerprint = AcceptanceCoordinator.fingerprint({
      world: actualWorld, exporter: game.exporter, revision: game.revision,
      modSha256: actualModSha, planHash, session: this.client.session,
      sources: game.sources, handlers: game.handlers, structures: game.structures,
      environment: game.environment, dependencies: plan.dependencies ?? {},
    });

    const checkpoint = await this.loadCheckpoint(envFingerprint);

    // If an active job was interrupted, verify its status first (C8)
    if (checkpoint.activeJob) {
      try {
        const poll = await this.client.request('GET', `/jobs/${checkpoint.activeJob.id}`);
        if (poll.job && (poll.job.state === 'checked' || poll.job.state === 'running' || poll.job.state === 'queued')) {
          console.log(`[Coordinator] Resumed observing active job: ${checkpoint.activeJob.id} (${poll.job.state})`);
        } else if (poll.job && (poll.job.state === 'failed' || poll.job.state === 'cancelled')) {
          console.log(`[Coordinator] Interrupted job already completed with terminal state: ${checkpoint.activeJob.id} (${poll.job.state})`);
        } else {
          checkpoint.activeJob = null;
          await this.saveCheckpoint(checkpoint);
        }
      } catch (err) {
        if (err.code === 'not_found' || err.status === 404) {
          checkpoint.activeJob = null;
          await this.saveCheckpoint(checkpoint);
        } else {
          throw new Error(`Could not reconnect to active job ${checkpoint.activeJob.id}: ${err.message}`);
        }
      }
    }

    for (const item of plan.handlers) {
      const handlerId = item.id;
      const state = checkpoint.handlers[handlerId] || {
        id: handlerId,
        name: item.name,
        offset: 0,
        total: null,
        checked: 0,
        unexamined: null,
        failed: [],
        excluded: [],
        failures: [],
        failuresOmitted: 0,
        hasFailures: false,
        status: 'pending'
      };
      if (!Array.isArray(state.failures)) state.failures = [];
      if (state.hasFailures === undefined) state.hasFailures = false;
      checkpoint.handlers[handlerId] = state;

      // Handle tooling / justified exclusions (U2)
      if (item.classification === 'tooling' && item.implementationStatus === 'excluded_justified') {
        state.status = 'excluded';
        state.reason = item.reason || 'Approved non-gameplay tooling exclusion';
        await this.saveCheckpoint(checkpoint);
        continue;
      }

      if (state.status === 'passed' && state.unexamined === 0) {
        continue; // Already verified to completion
      }

      console.log(`[Coordinator] Processing ${item.name} (${handlerId}) from offset ${state.offset}...`);

      const domain = item.domain === 'structures' || item.route === 'domain:structures' || item.route?.startsWith('structure:') ? 'structures' : 'recipes';
      if ((item.classification === 'domain' || item.classification === 'interactive') && domain !== 'structures') {
        // Domain and interactive work is validated by the export pipeline, not
        // by recipe pagination. Require a declared export dataset and record
        // it as a pending route until the domain report is consumed. Never
        // silently downgrade it to unsupported.
        assert.ok(item.route && item.route.startsWith(`${item.classification}:`), `Invalid ${item.classification} route`);
        state.status = 'pending';
        state.route = item.route;
        state.error = { code: 'domain_export_required', message: 'A complete source export and domain report are required for ' + item.route };
        checkpoint.failures.push({ handler: handlerId, error: state.error });
        await this.saveCheckpoint(checkpoint);
        throw new Error(state.error.message);
      }

      while (true) {
        // Enforce key <= 80 characters strictly, including runId slice
        const shortHash = createHash('sha256').update(handlerId).digest('hex').slice(0, 16);
        const shortRun = this.runId.slice(-6);
        const key = `chk-${shortRun}-${shortHash}-${state.offset}`;
        if (key.length > 80) throw new Error(`Generated job key exceeds 80 chars: ${key}`);

        const limit = this.options.pageSize || 4096;
        const checkRequest = {
          key,
          world: actualWorld,
          domain,
          probes: plan.probes ?? [{ count: 1, channels: {} }]
        };
        if (domain === 'structures') {
          checkRequest.controllers = item.controllers ?? game.structures?.map(row => row.controller);
          assert.ok(Array.isArray(checkRequest.controllers) && checkRequest.controllers.length, 'An explicit structure inventory is required');
        } else {
          checkRequest.handlers = [handlerId];
          checkRequest.offset = state.offset;
          checkRequest.limit = limit;
        }

        let jobId;
        if (checkpoint.activeJob && checkpoint.activeJob.key === key) {
          jobId = checkpoint.activeJob.id;
        } else {
          const jobResult = await this.client.request('POST', '/checks', checkRequest);
          jobId = jobResult.id;
          checkpoint.activeJob = { id: jobId, key, handlerId, offset: state.offset };
          await this.saveCheckpoint(checkpoint);
        }

        // Poll for completion
        let job;
        while (true) {
          const poll = await this.client.request('GET', `/jobs/${jobId}`);
          job = poll.job;
          if (job && (job.state === 'checked' || job.state === 'failed' || job.state === 'cancelled')) {
            break;
          }
          await new Promise(resolve => setTimeout(resolve, 500));
        }

        checkpoint.activeJob = null;
        await this.saveCheckpoint(checkpoint);

        // Check for fatal errors that MUST stop the whole plan (R7 / C8 / U8)
        if (job.state === 'cancelled') {
          state.status = 'cancelled';
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Acceptance plan cancelled during handler ${handlerId}`);
        }

        if (job.state === 'failed' && isFatalError(job.error)) {
          assertFailureDetails(job.error);
          state.status = 'failed';
          state.error = job.error;
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Fatal execution failure in handler ${handlerId}: ${job.error?.message || 'unknown'}`);
        }

        if (job.state !== 'checked') {
          if (job.error) assertFailureDetails(job.error);
          state.status = 'failed';
          state.error = job.error || { code: 'job_' + job.state, message: 'Job stopped without check completion' };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          break; // Next handler
        }

        // Strict report validation (C2)
        const report = job.report;
        if (!report || typeof report.path !== 'string' || !report.path) {
          state.status = 'failed';
          state.error = { code: 'missing_report_metadata', message: 'Job report metadata or path is missing' };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Diagnostic report metadata missing for job ${jobId}`);
        }
        if (!existsSync(report.path)) {
          state.status = 'failed';
          state.error = { code: 'missing_report', message: `Report artifact not found at ${report.path}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Diagnostic report artifact missing: ${report.path}`);
        }
        if (typeof report.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(report.sha256)) {
          state.status = 'failed';
          state.error = { code: 'invalid_report_sha', message: `Report sha256 is missing or invalid format: ${report.sha256}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Report SHA256 invalid or missing for ${report.path}`);
        }
        if (typeof report.bytes !== 'number' || report.bytes <= 0) {
          state.status = 'failed';
          state.error = { code: 'invalid_report_bytes', message: `Report bytes is missing or non-positive: ${report.bytes}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Report bytes invalid or non-positive for ${report.path}`);
        }

        const rawReport = await readFile(report.path);
        if (rawReport.length !== report.bytes) {
          state.status = 'failed';
          state.error = { code: 'report_bytes_mismatch', message: `Report size mismatch: declared ${report.bytes}, actual ${rawReport.length}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Report bytes mismatch for ${report.path}: declared ${report.bytes}, actual ${rawReport.length}`);
        }
        const actualSha = createHash('sha256').update(rawReport).digest('hex');
        if (actualSha !== report.sha256) {
          state.status = 'failed';
          state.error = { code: 'report_sha_mismatch', message: `Report hash mismatch: declared ${report.sha256}, actual ${actualSha}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Report SHA256 mismatch for ${report.path}: declared ${report.sha256}, actual ${actualSha}`);
        }

        // Parse row and enforce strict protocol checks (C2)
        const reportData = JSON.parse(rawReport.toString('utf8'));
        if (!Array.isArray(reportData.rows)) {
          state.status = 'failed';
          state.error = { code: 'invalid_report_format', message: 'Report data has no rows array' };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Report rows array missing for ${report.path}`);
        }
        if (domain === 'structures') {
          const expected = new Set(checkRequest.controllers);
          assert.equal(reportData.rows.length, expected.size, 'Structure report has a different target count');
          for (const row of reportData.rows) {
            assert.ok(expected.delete(row.controller), 'Unexpected or duplicate structure report row');
            assert.ok(['passed', 'failed', 'unsupported'].includes(row.status), 'Structure report is incomplete');
          }
          const archive = await this.archiveReport(job, rawReport, reportData.rows);
          checkpoint.archivedReports.push(archive);
          state.total = state.checked = reportData.rows.length; state.unexamined = 0;
          state.failures = reportData.rows.filter(row => row.status !== 'passed');
          state.status = state.failures.length ? 'failed' : 'passed';
          checkpoint.failures.push(...state.failures.map(row => ({ handler: handlerId, ...row })));
          await this.saveCheckpoint(checkpoint);
          break;
        }
        const row = reportData.rows.find(r => r.handler === handlerId || r.id === handlerId);
        if (!row) {
          state.status = 'failed';
          state.error = { code: 'handler_row_missing', message: `Report contains no row matching handler ${handlerId}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          break;
        }

        const validStatuses = ['passed', 'partial', 'failed', 'unsupported'];
        if (!row.status || typeof row.status !== 'string' || !validStatuses.includes(row.status)) {
          state.status = 'failed';
          state.error = { code: 'invalid_row_status', message: `Row status missing or invalid: ${row.status}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          break;
        }

        if (row.status === 'unsupported') {
          state.status = 'failed';
          state.error = { code: 'handler_unsupported', message: row.reason || 'Handler is unsupported' };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          break;
        }

        if (typeof row.totalRecipes !== 'number' || row.totalRecipes < 0) {
          state.status = 'failed';
          state.error = { code: 'invalid_row_total', message: `totalRecipes missing or invalid: ${row.totalRecipes}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          break;
        }
        if (state.total !== null && row.totalRecipes !== state.total) {
          state.status = 'failed';
          state.error = { code: 'total_stability_violation', message: `Total recipe count unstable: was ${state.total}, got ${row.totalRecipes}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Total recipe stability violation in handler ${handlerId}`);
        }
        state.total = row.totalRecipes;

        if (typeof row.offset !== 'number' || row.offset !== state.offset) {
          state.status = 'failed';
          state.error = { code: 'interval_mismatch', message: `Offset mismatch: expected ${state.offset}, got ${row.offset}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Interval offset mismatch in handler ${handlerId}: expected ${state.offset}, got ${row.offset}`);
        }

        if (typeof row.checkedRecipes !== 'number') {
          state.status = 'failed';
          state.error = { code: 'invalid_checked_count', message: `checkedRecipes missing or invalid: ${row.checkedRecipes}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          break;
        }

        if (row.checkedRecipes <= 0 && state.offset < state.total) {
          state.status = 'failed';
          state.error = { code: 'zero_progress', message: `Zero progress made: offset ${state.offset} of ${state.total}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Zero progress made during pagination for handler ${handlerId}`);
        }

        const expectedEnd = row.offset + row.checkedRecipes;
        if (typeof row.end !== 'number' || row.end !== expectedEnd) {
          state.status = 'failed';
          state.error = { code: 'interval_mismatch', message: `End mismatch: expected ${expectedEnd}, got ${row.end}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Interval end mismatch in handler ${handlerId}: expected ${expectedEnd}, got ${row.end}`);
        }

        if (!Array.isArray(row.failedRecipes) || !Array.isArray(row.failures) || !Array.isArray(row.excludedRecipes)) {
          state.status = 'failed';
          state.error = { code: 'invalid_report_arrays', message: 'failedRecipes, failures, or excludedRecipes is not an array' };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          break;
        }

        const rowOmitted = typeof row.failuresOmitted === 'number' ? row.failuresOmitted : 0;

        const archive = await this.archiveReport(job, rawReport, [row]);
        checkpoint.archivedReports.push(archive);

        // Accumulate contiguous page data (C2)
        state.checked += row.checkedRecipes;
        state.failed.push(...row.failedRecipes);
        state.excluded.push(...row.excludedRecipes);
        if (row.failures.length > 0) {
          state.hasFailures = true;
          state.failures.push(...row.failures.map(f => ({ handler: handlerId, offset: state.offset, ...f })));
          checkpoint.failures.push(...row.failures.map(f => ({ handler: handlerId, ...f })));
        }
        state.failuresOmitted += rowOmitted;
        if (row.status === 'failed') {
          state.hasFailures = true;
        }

        state.offset = row.end;
        state.unexamined = Math.max(0, state.total - state.offset);

        onProgress({ handler: handlerId, checked: state.checked, total: state.total, unexamined: state.unexamined });

        // Evaluate pagination termination
        if (state.offset >= state.total || state.total === 0) {
          if (state.hasFailures || state.failed.length > 0) {
            state.status = 'failed';
            state.error = { code: 'recipe_check_failures', message: `${state.failed.length} recipe(s) failed in handler ${handlerId}` };
          } else if (state.checked < state.total) {
            state.status = 'partial';
          } else {
            state.status = 'passed';
          }
          await this.saveCheckpoint(checkpoint);
          break;
        }

        await this.saveCheckpoint(checkpoint);
      }
    }

    this.updateSummary(checkpoint);
    return checkpoint;
  }

  async archiveReport(job, rawReport, rows) {
    const directory = path.join(this.instance, 'nesql', 'archived-reports', job.id);
    assert.match(job.id, /^[a-zA-Z0-9_-]{1,80}$/);
    await mkdir(directory, { recursive: true });
    const files = [];
    for (const row of rows) {
      const isStructure = Number.isInteger(row.controller);
      const failures = isStructure
        ? (row.status === 'passed' ? [] : [
          ...(Array.isArray(row.failedStructures) ? row.failedStructures : row.status === 'failed' ? [row.controller] : []),
          ...(Array.isArray(row.unsupportedStructures) ? row.unsupportedStructures : row.status === 'unsupported' ? [row.controller] : [])
        ])
        : (row.failedRecipes ?? []);
      const details = row.failureFiles ?? [];
      assert.equal(details.length, failures.length, 'Failure detail files are incomplete');
      const pending = isStructure ? null : new Set(failures);
      for (const entry of details) {
        if (!isStructure) assert.ok(pending.delete(entry.index), 'Unexpected or duplicate failure detail');
        const reportDirectory = path.dirname(job.report.path);
        const checked = await artifact(reportDirectory, entry, 16 * 1024 * 1024);
        const detail = JSON.parse(checked.bytes.toString('utf8'));
        assert.equal(detail.format, 'nesql.failure'); assert.equal(detail.job, job.id);
        if (isStructure) assert.equal(detail.target.controller, row.controller);
        else assert.equal(detail.target.handler, row.handler);
        assert.equal(detail.index, entry.index);
        const archivePath = path.join(directory, path.basename(entry.path));
        await this.storeArtifact(archivePath, checked.bytes);
        files.push({ ...entry, archivePath });
      }
      if (pending) assert.equal(pending.size, 0, 'Failure detail files do not cover every failed target');
    }
    const archivePath = path.join(directory, 'report.json');
    await this.storeArtifact(archivePath, rawReport);
    return { job: job.id, sourcePath: job.report.path, archivePath, sha256: digest(rawReport), bytes: rawReport.length, files };
  }

  async storeArtifact(file, bytes) {
    if (existsSync(file)) {
      assert.equal(digest(await readFile(file)), digest(bytes), 'An existing archive has different bytes');
      return;
    }
    const temporary = file + '.tmp';
    await writeFile(temporary, bytes, { flag: 'wx' });
    await rename(temporary, file);
  }
}

// Rehearsal and execution validate the same immutable candidate inputs.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const { values } = parseArgs({ options: { config: { type: 'string' }, 'dry-run': { type: 'boolean' } }, strict: true, allowPositionals: false });
    assert.ok(values.config, 'Usage: coordinator.mjs --config <plan.json> [--dry-run]');
    const { config, plan, packages } = await prepare(values.config);
    console.log(JSON.stringify({ world: plan.world, targets: plan.handlers.length, packages: plan.dependencies.packages, dryRun: !!values['dry-run'] }));
    if (!values['dry-run']) {
      const coordinator = new AcceptanceCoordinator(config.instance, { pageSize: config.pageSize ?? 4096, runId: config.runId });
      const result = await coordinator.runPlan(plan);
      console.log(JSON.stringify(result.summary));
      process.exitCode = result.summary.failed || result.summary.unexamined || result.failures.length ? 1 : 0;
    }
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
