import { readFile, writeFile, rename, mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { GameClient, GameError } from './client.mjs';

/**
 * Coordinated acceptance runner strictly fulfilling R5, R6, R7:
 * - Validates true game environment (world folder/name, ready) without artificial singleplayer flags.
 * - Enforces bounded job keys strictly <= 80 characters.
 * - Automatically advances contiguous pagination across arbitrary ranges without stopping at 4096.
 * - Refuses unverified reports; verifies real row status and preserves all failure locations.
 * - Stores environment-isolated checkpoints atomically; halts entirely on fatal environment errors.
 */
export class AcceptanceCoordinator {
  constructor(instance, options = {}) {
    this.instance = instance;
    this.client = new GameClient(instance, options);
    this.checkpointPath = path.join(instance, 'nesql', 'acceptance-checkpoint.json');
    this.options = options;
  }

  static fingerprint(env) {
    return createHash('sha256')
      .update(JSON.stringify({
        world: env.world,
        exporter: env.exporter,
        revision: env.revision,
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
        // Enforce key <= 80 characters strictly
        const shortHash = createHash('sha256').update(handlerId).digest('hex').slice(0, 16);
        const key = `chk-${shortHash}-${state.offset}`;
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
          const msg = (err.message || '') + (err.code || '');
          return msg.includes('world_changed') || msg.includes('slot_changed') || msg.includes('fatal')
            || msg.includes('OutOfMemory') || msg.includes('cleanup');
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

        const reportData = JSON.parse(await readFile(reportPath, 'utf8'));
        const row = reportData.rows?.find(r => r.id === handlerId) || reportData.rows?.[0];
        if (!row) {
          state.status = 'failed';
          state.error = { code: 'empty_report', message: 'Report contains no diagnostic rows' };
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
          // Finished all recipes of this handler
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
