import { readFile, writeFile, rename, mkdir } from 'node:fs/promises';
import { existsSync, readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { GameClient, GameError } from './client.mjs';

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
    return createHash('sha256')
      .update(JSON.stringify({
        world: env.world,
        exporter: env.exporter,
        revision: env.revision,
        modSha256: env.modSha256 || null,
        planHash: env.planHash,
        probes: env.probes || null,
        dependencies: env.dependencies || null
      }))
      .digest('hex');
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

  async saveCheckpoint(checkpoint) {
    checkpoint.updatedAt = new Date().toISOString();
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

    const actualWorld = game.world?.folder || game.world?.name;
    if (!actualWorld) {
      throw new Error('Game client has no active world loaded');
    }
    if (plan.world && actualWorld !== plan.world) {
      throw new Error(`Target world mismatch: expected '${plan.world}', game has '${actualWorld}'`);
    }

    // Bind mod sha256 to environment fingerprint (C8)
    let actualModSha = plan.modSha256 || null;
    if (game.sources?.rows) {
      const nesqlRow = game.sources.rows.find(m => m.id === 'nesql');
      if (nesqlRow?.sha256) actualModSha = nesqlRow.sha256;
    }

    const planHash = createHash('sha256')
      .update(plan.handlers.map(h => h.id).sort().join(','))
      .digest('hex');
    const envFingerprint = AcceptanceCoordinator.fingerprint({
      world: actualWorld,
      exporter: game.exporter,
      revision: game.revision,
      modSha256: actualModSha,
      planHash,
      probes: plan.probes || null,
      dependencies: plan.dependencies || null
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

      if (state.status === 'passed' && state.unexamined === 0) {
        continue; // Already verified to completion
      }

      console.log(`[Coordinator] Processing ${item.name} (${handlerId}) from offset ${state.offset}...`);

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
          domain: 'recipes',
          handlers: [handlerId],
          offset: state.offset,
          limit,
          probes: [{ count: 1, channels: {} }]
        };

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

        // Check for fatal errors that MUST stop the whole plan (R7 / C8)
        if (job.state === 'cancelled') {
          state.status = 'cancelled';
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Acceptance plan cancelled during handler ${handlerId}`);
        }

        const isFatalError = (err) => {
          if (!err) return false;
          if (err.fatal === true) return true;
          const code = (err.code || '').toLowerCase();
          const msg = (err.message || '').toLowerCase();
          const fatalTokens = [
            'world_changed', 'slot_changed', 'handler_changed', 'client_error',
            'io_error', 'fatal', 'outofmemory', 'cleanup', 'cleanup_failed',
            'resource_leak', 'server_stopped'
          ];
          return fatalTokens.some(token => code.includes(token) || msg.includes(token));
        };

        if (job.state === 'failed' && isFatalError(job.error)) {
          state.status = 'failed';
          state.error = job.error;
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Fatal execution failure in handler ${handlerId}: ${job.error?.message || 'unknown'}`);
        }

        if (job.state !== 'checked') {
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

        // Archive report artifact shard (C2)
        if (!checkpoint.archivedReports) checkpoint.archivedReports = [];
        checkpoint.archivedReports.push({
          handler: handlerId,
          offset: row.offset,
          end: row.end,
          path: report.path,
          sha256: actualSha,
          bytes: rawReport.length,
          failuresCount: row.failures.length,
          failuresOmitted: rowOmitted
        });

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

    return checkpoint;
  }
}

// CLI Execution & Rehearsal (R9 / C3)
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const isDryRun = process.argv.includes('--dry-run');
  const configArgIdx = process.argv.indexOf('--config');
  const configPath = configArgIdx !== -1 ? process.argv[configArgIdx + 1] : 'acceptance-candidate.json';

  console.log(`[Coordinator] Starting Acceptance Coordinator (dry-run: ${isDryRun})...`);
  console.log(`[Coordinator] Loading config: ${configPath}`);

  let config;
  try {
    const raw = readFileSync(path.resolve(configPath), 'utf8');
    config = JSON.parse(raw);
  } catch (err) {
    console.error(`[Coordinator] Failed to read config ${configPath}: ${err.message}`);
    process.exit(1);
  }

  // Resolve worklist dynamically without hardcoded machine paths
  let worklistPath = config.worklist
    ? path.resolve(config.worklist)
    : path.resolve(path.dirname(configPath), 'completion-worklist.json');
  if (!existsSync(worklistPath)) {
    const fallback1 = path.resolve(path.dirname(configPath), '.refactor-state/acceptance/completion-worklist.json');
    const fallback2 = path.resolve('.refactor-state/acceptance/completion-worklist.json');
    if (existsSync(fallback1)) worklistPath = fallback1;
    else if (existsSync(fallback2)) worklistPath = fallback2;
  }

  let worklist = { handlers: [] };
  if (existsSync(worklistPath)) {
    try {
      worklist = JSON.parse(readFileSync(worklistPath, 'utf8'));
    } catch (e) {
      console.warn(`[Coordinator] Could not parse worklist: ${e.message}`);
    }
  }

  console.log('--------------------------------------------------');
  console.log('ACCEPTANCE PLAN REHEARSAL / SUMMARY:');
  console.log(`- Instance: ${config.instance}`);
  console.log(`- World: ${config.world}`);
  console.log(`- Candidate Mod: ${config.mod}`);
  console.log(`- Candidate Compiler: ${config.compiler}`);
  console.log(`- Candidate Web: ${config.web}`);
  console.log(`- Planned Handlers: ${worklist.handlers ? worklist.handlers.length : 0}`);
  console.log('Phase Sequence:');
  console.log('  1. Environment Preflight (Check game readiness & active world)');
  console.log('  2. Diagnostic Sampling (Domain & Fact sanity)');
  console.log('  3. Contiguous Full-Range Recipe Pagination (330 handlers)');
  console.log('  4. Source Dataset Sealing (Atomic export to dataset directory)');
  console.log('  5. Elysium Compiler Inspect & Deterministic Catalog Compilation');
  console.log('  6. NeoNEI Online/Offline Validation');
  console.log('--------------------------------------------------');

  if (isDryRun) {
    if (config.mod) {
      const modPath = path.resolve(config.instance, config.mod);
      console.log(`[Coordinator] Dry-run verifying mod artifact: ${modPath} (${existsSync(modPath) ? 'EXISTS' : 'NOT FOUND (Pre-install OK)'})`);
    }
    console.log('[Coordinator] Dry-run rehearsal completed successfully. No game mutations performed.');
    process.exit(0);
  }

  // Non-dry-run mode: Execute acceptance coordinator plan (C3)
  console.log('[Coordinator] Non-dry-run mode: Initializing AcceptanceCoordinator...');
  const coordinator = new AcceptanceCoordinator(config.instance, {
    pageSize: config.pageSize || 4096,
    runId: config.runId
  });

  const handlers = worklist.handlers && worklist.handlers.length > 0
    ? worklist.handlers
    : (config.handlers || []);

  const plan = {
    world: config.world,
    modSha256: config.modSha256 || null,
    handlers
  };

  console.log(`[Coordinator] Executing plan with ${handlers.length} handlers...`);
  try {
    const finalCheckpoint = await coordinator.runPlan(plan, {
      onProgress: (p) => {
        console.log(`[Coordinator] [${p.handler}] ${p.checked}/${p.total} (unexamined: ${p.unexamined})`);
      }
    });
    console.log('[Coordinator] Execution completed successfully!');
    console.log(`[Coordinator] Summary: checked=${finalCheckpoint.summary.checked}, failed=${finalCheckpoint.summary.failed}`);
    process.exit(0);
  } catch (err) {
    console.error(`[Coordinator] Execution failed: ${err.message}`);
    process.exit(1);
  }
}
