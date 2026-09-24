import { readFile, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { GameClient, GameError } from './client.mjs';

/**
 * Coordinated acceptance and multi-page check runner.
 * Automatically slices large recipe handlers beyond the 4096-limit into contiguous windows,
 * records checkpoints, aggregates errors by root cause, and handles reliable resume.
 */
export class AcceptanceCoordinator {
  constructor(instance, options = {}) {
    this.instance = instance;
    this.client = new GameClient(instance, options);
    this.checkpointPath = path.join(instance, 'nesql', 'acceptance-checkpoint.json');
    this.options = options;
  }

  async loadCheckpoint() {
    if (existsSync(this.checkpointPath)) {
      try {
        const text = await readFile(this.checkpointPath, 'utf8');
        return JSON.parse(text);
      } catch (err) {
        console.warn('Failed to parse existing checkpoint, starting fresh:', err.message);
      }
    }
    return {
      version: '0.15.0',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      handlers: {},
      summary: { total: 0, checked: 0, failed: 0, excluded: 0, unexamined: 0 },
      failures: []
    };
  }

  async saveCheckpoint(checkpoint) {
    checkpoint.updatedAt = new Date().toISOString();
    await writeFile(this.checkpointPath, JSON.stringify(checkpoint, null, 2), 'utf8');
  }

  /**
   * Run full verification for a list of handlers with automatic contiguous pagination.
   */
  async runPlan(plan, { onProgress = () => {} } = {}) {
    const checkpoint = await this.loadCheckpoint();
    const game = await this.client.request('GET', '/game');
    if (!game.ready || !game.singleplayer) {
      throw new Error('Game client is not in single-player or not ready');
    }
    const world = game.world?.folder || plan.world;
    if (!world) throw new Error('Missing target single-player save world folder');

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
        status: 'pending'
      };
      checkpoint.handlers[handlerId] = state;

      if (state.status === 'passed' && state.unexamined === 0) {
        continue; // Already verified to completion
      }

      console.log(`[Coordinator] Processing ${item.name} (${handlerId}) from offset ${state.offset}...`);

      while (true) {
        const key = `check-${handlerId}-${state.offset}`;
        const limit = this.options.pageSize || 4096;
        
        const checkRequest = {
          key,
          world,
          domain: 'recipes',
          handlers: [handlerId],
          offset: state.offset,
          limit,
          probes: [{ count: 1, channels: {} }]
        };

        const jobResult = await this.client.request('POST', '/checks', checkRequest);
        const jobId = jobResult.id;

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

        if (job.state !== 'checked') {
          state.status = 'failed';
          state.error = job.error || { code: 'job_' + job.state, message: 'Job stopped without check completion' };
          checkpoint.failures.push({ handler: handlerId, offset: state.offset, error: state.error });
          await this.saveCheckpoint(checkpoint);
          break;
        }

        const reportPath = job.report?.path;
        let reportData = null;
        if (reportPath && existsSync(reportPath)) {
          reportData = JSON.parse(await readFile(reportPath, 'utf8'));
        }

        const row = reportData?.rows?.[0] || {};
        const rowTotal = row.totalRecipes ?? job.report?.total ?? 0;
        const rowChecked = row.checkedRecipes ?? 0;
        const rowFailed = row.failedRecipes ?? [];
        const rowExcluded = row.excludedRecipes ?? [];
        const rowFailures = row.failures ?? [];

        state.total = rowTotal;
        state.checked += rowChecked;
        state.failed.push(...rowFailed);
        state.excluded.push(...rowExcluded);
        if (rowFailures.length > 0) {
          checkpoint.failures.push(...rowFailures.map(f => ({ handler: handlerId, ...f })));
        }

        state.offset += rowChecked;
        state.unexamined = Math.max(0, rowTotal - state.offset);

        onProgress({ handler: handlerId, checked: state.checked, total: rowTotal, unexamined: state.unexamined });

        if (state.offset >= rowTotal || rowChecked === 0) {
          state.status = state.failed.length > 0 ? 'failed' : 'passed';
          await this.saveCheckpoint(checkpoint);
          break;
        }

        await this.saveCheckpoint(checkpoint);
      }
    }

    return checkpoint;
  }
}
