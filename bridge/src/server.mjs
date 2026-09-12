import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { GameError } from './client.mjs';

const identifier = z.string().regex(/^[a-zA-Z0-9_-]{1,80}$/);
const probe = z.object({ count: z.number().int().min(1).max(64),
  channels: z.record(z.string().regex(/^[a-z0-9_-]{1,64}$/), z.number().int().min(1).max(65535)).optional(),
}).strict().refine(value => Object.keys(value.channels ?? {}).length <= 32, 'At most 32 channels per probe');
const readOnly = { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false };

export function createServer(client) {
  const server = new McpServer({ name: 'nesql', version: '0.1.0' });
  const call = (method, endpoint, body) => async (args, extra) => {
    try {
      const result = await client.request(method, endpoint(args), body?.(args), extra.signal);
      return { content: [{ type: 'text', text: JSON.stringify(result) }], structuredContent: result };
    } catch (error) {
      if (!(error instanceof GameError)) throw error;
      const result = { error: { code: error.code, message: error.message } };
      return { isError: true, content: [{ type: 'text', text: JSON.stringify(result) }], structuredContent: result };
    }
  };

  server.registerTool('inspect_game', {
    description: 'Inspect the local Minecraft client, single-player readiness and available export profiles.',
    inputSchema: {}, annotations: readOnly,
  }, call('GET', () => '/game'));

  server.registerTool('start_export', {
    description: 'Start one export job in a loaded local single-player world. Returns immediately. Reuse the same key when retrying an uncertain request. Bulk files remain on disk; poll read_job for completion.',
    inputSchema: {
      key: identifier.describe('Idempotency key chosen for this operation. A retry must use identical arguments.'),
      name: identifier.describe('Name of the local dataset.'),
      world: z.string().min(1).max(128).regex(/^[^/\\\x00-\x1f\x7f]+$/).refine(value => value !== '.' && value !== '..').optional()
        .describe('Expected single-player save folder from inspect_game.world.folder. A different world fails before capture. Included in retry identity.'),
      profile: z.enum(['full', 'data', 'images']).describe('The requested collection scope; only a complete dataset can become a full catalog.'),
      handlers: z.array(z.string().regex(/^category_[a-f0-9]{64}$/)).max(512).optional()
        .describe('Explicit handler selection from inspect_game. Omit for all handlers. Unsupported handlers fail the job; selecting a subset publishes a selection snapshot.'),
      probes: z.array(probe).min(1).max(16).refine(values => new Set(values.map(value =>
        JSON.stringify([value.count, Object.entries(value.channels ?? {}).sort(([a], [b]) => a < b ? -1 : Number(a > b))]))).size === values.length,
      'Structure probes must be unique').optional().describe('Distinct construction parameter sets applied to each controller. Channel names are lowercase. Omit for count 1 and default channels. Order does not affect retries; parameters are recorded in the snapshot.'),
    },
    annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  }, call('POST', () => '/jobs', args => args));

  server.registerTool('read_job', {
    description: 'Read job state, progress, bounded events, errors and the published result. Omit id to resume observing the active or latest job. Returns {job}, with null when no job exists. Cancelling a tool request does not cancel the export.',
    inputSchema: { id: identifier.optional() }, annotations: readOnly,
  }, call('GET', args => args.id === undefined ? '/jobs' : `/jobs/${args.id}`));

  server.registerTool('cancel_export', {
    description: 'Request cooperative cancellation. The job reaches cancelled only after game work and file cleanup have stopped. Poll read_job while it is cancelling.',
    inputSchema: { id: identifier },
    annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  }, call('POST', args => `/jobs/${args.id}/cancel`, () => ({})));

  server.registerTool('list_exports', {
    description: 'List completed source datasets in content-id order. Returns rows and next; pass next as after to continue. New exports with smaller ids appear on a fresh listing. Incomplete jobs are excluded.',
    inputSchema: {
      after: z.string().regex(/^[a-f0-9]{64}$/).optional(),
      limit: z.number().int().min(1).max(100).default(20),
    }, annotations: readOnly,
  }, call('GET', args => {
    const query = new URLSearchParams({ limit: String(args.limit) });
    if (args.after !== undefined) query.set('after', args.after);
    return `/exports?${query}`;
  }));

  server.registerTool('read_export', {
    description: 'Read the manifest summary, counts and disk location of one completed source dataset. Large manifests, textures and records remain on disk.',
    inputSchema: { id: z.string().regex(/^[a-f0-9]{64}$/) }, annotations: readOnly,
  }, call('GET', args => `/exports/${args.id}`));

  return server;
}
