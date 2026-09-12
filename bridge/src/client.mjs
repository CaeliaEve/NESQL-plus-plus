import { open } from 'node:fs/promises';
import path from 'node:path';

const MAX_RESPONSE_BYTES = 1024 * 1024;

export class GameError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'GameError';
    this.code = code;
  }
}

/** Each request rediscovers the game so restarting Minecraft does not restart MCP. */
export class GameClient {
  constructor(instance, { timeout = 10_000 } = {}) {
    if (!path.isAbsolute(instance)) throw new Error('The game instance path must be absolute.');
    this.connectionFile = path.join(instance, 'nesql', 'connection.json');
    this.timeout = timeout;
  }

  async request(method, endpoint, body, signal) {
    let connection;
    try {
      const file = await open(this.connectionFile, 'r');
      try {
        const stat = await file.stat();
        if (!stat.isFile() || stat.size > 4096) throw new Error('Invalid connection file.');
        const buffer = Buffer.alloc(4097);
        let size = 0;
        for (;;) {
          const { bytesRead } = await file.read(buffer, size, buffer.length - size, size);
          if (bytesRead === 0) break;
          size += bytesRead;
          if (size > 4096) throw new Error('Connection file exceeds the size limit.');
        }
        connection = JSON.parse(buffer.subarray(0, size).toString('utf8'));
      } finally { await file.close(); }
    } catch (error) {
      if (error.code === 'ENOENT') {
        throw new GameError('game_offline', 'Start the GTNH client with the NESQL mod installed.');
      }
      throw new GameError('invalid_connection', 'Cannot read the game connection file.');
    }
    const { protocol, port, token, session } = connection ?? {};
    if (protocol !== 1 || !Number.isInteger(port) || port < 1 || port > 65535
        || typeof token !== 'string' || !/^[a-f0-9]{64}$/.test(token)
        || typeof session !== 'string' || !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(session)) {
      throw new GameError('invalid_connection', 'The game connection file has an unsupported shape.');
    }
    // The discovery file cannot redirect requests or the bearer token off this machine.
    const [pathname, query] = endpoint.split('?');
    if (!/^\/(game|jobs|exports)(\/[a-zA-Z0-9_-]+){0,2}$/.test(pathname)
        || endpoint.split('?').length > 2 || (query !== undefined && (pathname !== '/exports'
          || !/^(?:limit=[1-9][0-9]{0,2}|after=[a-f0-9]{64})(?:&(?:limit=[1-9][0-9]{0,2}|after=[a-f0-9]{64}))?$/.test(query)
          || new Set(new URLSearchParams(query).keys()).size !== [...new URLSearchParams(query)].length))) {
      throw new Error('Invalid game endpoint.');
    }
    const timeout = AbortSignal.timeout(this.timeout);
    const requestSignal = signal ? AbortSignal.any([signal, timeout]) : timeout;
    let response;
    try {
      response = await fetch(`http://127.0.0.1:${port}${endpoint}`, {
        method,
        headers: {
          Authorization: `Bearer ${token}`,
          'X-NESQL-Session': session,
          Accept: 'application/json',
          ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        },
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: requestSignal,
        redirect: 'error',
      });
    } catch (error) {
      if (requestSignal.aborted) throw new GameError('request_cancelled', 'The game request was cancelled or timed out.');
      throw new GameError('game_offline', 'The game connection is unavailable.');
    }
    if (response.headers.get('X-NESQL-Session') !== session) {
      await response.body?.cancel();
      throw new GameError('game_changed', 'The endpoint does not match the discovered game session.');
    }
    if (Number(response.headers.get('content-length')) > MAX_RESPONSE_BYTES) {
      await response.body?.cancel();
      throw new GameError('response_limit', 'The game response exceeds the size limit.');
    }
    const chunks = [];
    let size = 0;
    try {
      for await (const chunk of response.body) {
        size += chunk.length;
        if (size > MAX_RESPONSE_BYTES) throw new GameError('response_limit', 'The game response exceeds the size limit.');
        chunks.push(chunk);
      }
      const result = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      if (!response.ok) {
        throw new GameError(result.error?.code ?? 'game_error', result.error?.message ?? `Game request failed (${response.status}).`);
      }
      if (!result || typeof result !== 'object' || Array.isArray(result)) throw new Error('Expected an object.');
      return result;
    } catch (error) {
      if (error instanceof GameError) throw error;
      if (requestSignal.aborted) throw new GameError('request_cancelled', 'The game request was cancelled or timed out.');
      throw new GameError('invalid_response', 'The game returned an invalid response.');
    }
  }
}
