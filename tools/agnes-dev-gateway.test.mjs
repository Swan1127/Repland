import assert from 'node:assert/strict';
import test from 'node:test';
import { createGateway } from './agnes-dev-gateway.mjs';

test('gateway keeps the key upstream and binds the response to the request', async () => {
  let sent;
  const server = createGateway({
    apiKey: 'test-only-key',
    fetchImpl: async (_url, init) => {
      sent = init;
      return { ok: true, json: async () => ({ choices: [{ message: { content: '{"summary":"可先整理章节","dependencyNote":null,"requestId":"model-forged-id"}' } }] }) };
    },
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const address = server.address();
    const response = await fetch(`http://127.0.0.1:${address.port}/advice`, {
      method: 'POST',
      body: JSON.stringify({ requestId: 'r1', contractVersion: 'repland-ai-advisor/v1', kind: 'task_understanding', task: { title: '复习' } }),
    });
    const payload = await response.json();
    assert.equal(response.status, 200);
    assert.equal(payload.requestId, 'r1');
    assert.equal(payload.summary, '可先整理章节');
    assert.equal(sent.headers.Authorization, 'Bearer test-only-key');
    assert.equal(JSON.stringify(payload).includes('test-only-key'), false);
  } finally {
    server.close();
  }
});
