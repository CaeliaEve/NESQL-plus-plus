#!/usr/bin/env node
import { parseArgs } from 'node:util';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { GameClient } from './client.mjs';
import { createServer } from './server.mjs';

try {
  const { values } = parseArgs({ options: { instance: { type: 'string' } }, strict: true, allowPositionals: false });
  if (!values.instance) throw new Error('Usage: nesql-mcp --instance <absolute game instance directory>');
  const server = createServer(new GameClient(values.instance));
  await server.connect(new StdioServerTransport());
  const close = async () => { await server.close(); process.exitCode = 0; };
  process.once('SIGINT', close);
  process.once('SIGTERM', close);
} catch (error) {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
}
