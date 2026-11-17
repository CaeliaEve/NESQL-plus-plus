import { readFile, writeFile } from 'node:fs/promises';
import { digest } from '../../src/plan.mjs';

// Older row/archiving fixtures omit transport metadata. Complete those fixtures
// at the transport boundary, preserving explicitly supplied fields and deliberate
// byte/hash corruption. Scope-rejection tests use full HTTP fixtures separately.
export function withNativeEvidence(runner) {
  if (runner.client.request.currentJobFixture) return runner;
  const send = runner.client.request.bind(runner.client), issued = new Map();
  const request = async (method, url, body) => {
    const response = await send(method, url, body);
    if (method === 'POST' && ['/checks', '/jobs'].includes(url) && response.id) {
      const check = url === '/checks';
      issued.set(response.id, { created: new Date().toISOString(), request: check ? {
        key: body.key, name: 'check', profile: 'data', world: body.world,
        handlers: body.handlers ?? [], probes: body.probes ?? [{ count: 1, channels: {} }],
        check: { domain: body.domain, controllers: body.controllers ?? [], resources: body.resources ?? [],
          offset: body.offset ?? 0, limit: body.limit ?? 128, render: body.render ?? false, contentAudit: body.contentAudit ?? false }
      } : body });
    }
    if (!response.job) return response;
    const id = response.job.id ?? url.slice(6), model = issued.get(id);
    if (!model) return response;
    const provenance = { revision: 1, environment: '1'.repeat(64), runtime: '2'.repeat(64), session: '3'.repeat(64), selection: '4'.repeat(64) };
    const job = { id, ...model, provenance, ...response.job };
    if (job.report?.path) {
      const raw = await readFile(job.report.path), data = JSON.parse(raw);
      const value = { format: 'nesql.check', job: id, created: model.created, status: 'complete', provenance,
        environment: { mods: [{ id: 'nesql-exporter', version: '0.15.0', sha256: digest(Buffer.from('NESQL protocol fixture')) }] }, ...data,
        request: { ...model.request, ...data.request,
          check: { ...model.request.check, ...data.request?.check } } };
      const bytes = Buffer.from(JSON.stringify(value)); await writeFile(job.report.path, bytes);
      job.report = { ...job.report,
        bytes: job.report.bytes === raw.length ? bytes.length : job.report.bytes,
        sha256: job.report.sha256 === digest(raw) ? digest(bytes) : job.report.sha256 };
    }
    return { ...response, job };
  };
  request.currentJobFixture = true; runner.client.request = request;
  return runner;
}
