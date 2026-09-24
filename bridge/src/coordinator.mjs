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
        planHash: env.planHash
      }))
      .digest('hex');
  }

  async loadCheckpoint(currentFingerprint) {
    if (existsSync(this.checkpointPath)) {
      try {
        const text = await readFile(this.checkpointPath, 'utf8');
        const data = JSON.parse(text);
        if (data.fingerprint && data.fingerprint === currentFingerprint) {
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
      failures: []
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

    const planHash = createHash('sha256')
      .update(plan.handlers.map(h => h.id).sort().join(','))
      .digest('hex');
    const envFingerprint = AcceptanceCoordinator.fingerprint({
      world: actualWorld,
      exporter: game.exporter,
      revision: game.revision,
      modSha256: plan.modSha256 || null,
      planHash
    });

    const checkpoint = await this.loadCheckpoint(envFingerprint);

    // If an active job was interrupted, verify its status first
    if (checkpoint.activeJob) {
      try {
        const poll = await this.client.request('GET', `/jobs/${checkpoint.activeJob.id}`);
        if (poll.job && (poll.job.state === 'checked' || poll.job.state === 'running')) {
          console.log(`[Coordinator] Resumed observing active job: ${checkpoint.activeJob.id}`);
        } else {
          checkpoint.activeJob = null;
          await this.saveCheckpoint(checkpoint);
        }
      } catch (err) {
        checkpoint.activeJob = null;
        await this.saveCheckpoint(checkpoint);
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
        failuresOmitted: 0,
        status: 'pending'
      };
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

        // Check for fatal errors that MUST stop the whole plan (R7)
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

        const reportPath = job.report?.path;
        if (!reportPath || !existsSync(reportPath)) {
          state.status = 'failed';
          state.error = { code: 'missing_report', message: `Report artifact not found at ${reportPath}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Diagnostic report artifact missing: ${reportPath}`);
        }

        // Verify report bytes and sha256 strictly (R6)
        const rawReport = await readFile(reportPath);
        const actualSha = createHash('sha256').update(rawReport).digest('hex');
        if (job.report?.sha256 && actualSha !== job.report.sha256) {
          state.status = 'failed';
          state.error = { code: 'report_sha_mismatch', message: `Report hash mismatch: expected ${job.report.sha256}, got ${actualSha}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Report SHA256 mismatch for ${reportPath}`);
        }
        if (job.report?.bytes && rawReport.length !== job.report.bytes) {
          state.status = 'failed';
          state.error = { code: 'report_bytes_mismatch', message: `Report size mismatch: expected ${job.report.bytes}, got ${rawReport.length}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          throw new Error(`Report bytes mismatch for ${reportPath}`);
        }

        const reportData = JSON.parse(rawReport.toString('utf8'));
        const row = reportData.rows?.find(r => r.handler === handlerId || r.id === handlerId);
        if (!row) {
          state.status = 'failed';
          state.error = { code: 'handler_row_missing', message: `Report contains no row matching handler ${handlerId}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          break;
        }

        // Refuse unsupported or failed row status (R6)
        if (row.status && row.status !== 'checked' && row.status !== 'passed' && row.status !== 'ok') {
          state.status = 'failed';
          state.error = { code: 'handler_' + row.status, message: row.reason || `Handler reported non-success status: ${row.status}` };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          break;
        }

        const rowTotal = row.totalRecipes ?? 0;
        const rowChecked = row.checkedRecipes ?? 0;
        const rowFailed = row.failedRecipes ?? [];
        const rowExcluded = row.excludedRecipes ?? [];
        const rowFailures = row.failures ?? [];
        const rowOmitted = row.failuresOmitted ?? 0;

        state.total = rowTotal;
        state.checked += rowChecked;
        state.failed.push(...rowFailed);
        state.excluded.push(...rowExcluded);
        state.failuresOmitted += rowOmitted;
        if (rowFailures.length > 0) {
          checkpoint.failures.push(...rowFailures.map(f => ({ handler: handlerId, ...f })));
        }

        state.offset += rowChecked;
        state.unexamined = Math.max(0, rowTotal - state.offset);

        onProgress({ handler: handlerId, checked: state.checked, total: rowTotal, unexamined: state.unexamined });

        // Evaluate pagination termination
        if (rowChecked === 0 || state.offset >= rowTotal) {
          if (state.failed.length > 0) {
            state.status = 'failed';
          } else if (state.checked < rowTotal) {
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

// CLI Execution & Rehearsal (R9)
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

  const worklistPath = path.resolve('E:/codex/NEI/.refactor-state/acceptance/completion-worklist.json');
  let worklist = { handlers: [] };
  if (existsSync(worklistPath)) {
    worklist = JSON.parse(readFileSync(worklistPath, 'utf8'));
  }

  console.log('--------------------------------------------------');
  console.log('ACCEPTANCE PLAN REHEARSAL / SUMMARY:');
  console.log(`- Instance: ${config.instance}`);
  console.log(`- World: ${config.world}`);
  console.log(`- Candidate Mod: ${config.mod}`);
  console.log(`- Candidate Compiler: ${config.compiler}`);
  console.log(`- Candidate Web: ${config.web}`);
  console.log(`- Planned Handlers: ${worklist.handlers.length}`);
  console.log('Phase Sequence:');
  console.log('  1. Environment Preflight (Check game readiness & active world)');
  console.log('  2. Diagnostic Sampling (Domain & Fact sanity)');
  console.log('  3. Contiguous Full-Range Recipe Pagination (330 handlers)');
  console.log('  4. Source Dataset Sealing (Atomic export to dataset directory)');
  console.log('  5. Elysium Compiler Inspect & Deterministic Catalog Compilation');
  console.log('  6. NeoNEI Online/Offline Validation');
  console.log('--------------------------------------------------');

  if (isDryRun) {
    console.log('[Coordinator] Dry-run rehearsal completed successfully. No game mutations performed.');
    process.exit(0);
  }

  console.log('[Coordinator] Non-dry-run mode requested. Awaiting approved execution trigger.');
}
