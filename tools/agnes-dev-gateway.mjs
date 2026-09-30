import http from 'node:http';

const PORT = 8787;
const MODEL = process.env.REPLAND_AGNES_MODEL || 'agnes-3.0-flash';
const API_BASE = process.env.REPLAND_AGNES_BASE_URL || 'https://apihub.agnes-ai.com/v1';
const API_KEY = process.env.REPLAND_AGNES_API_KEY;
const MAX_REQUEST_BYTES = 60_000;

const instructionByKind = {
  task_understanding: 'Return JSON: {"summary":string,"dependencyNote":string|null}. Explain the task and uncertain dependencies; do not pretend work is complete.',
  difficulty_duration: 'Return JSON: {"difficulty":integer 1..5,"suggestedDurationMinutes":integer|null,"explanation":string}. Duration is an estimate, not observed time.',
  task_breakdown: 'Return JSON: {"steps":string[],"explanation":string}. Give specific, doable steps.',
  sorting_explanation: 'Return JSON: {"explanation":string}. Explain the supplied local order without overriding it.',
  replan: 'Return JSON: {"orderedTaskReferences":string[],"proposedSegments":[{"taskReference":string,"date":"YYYY-MM-DD","startMinute":integer,"endMinute":integer}],"explanation":string}. Use only supplied task references; never overlap hard constraints, locked segments, or other proposed segments. Every time boundary must be a 30-minute multiple. If you cannot establish a safe change, return empty proposedSegments and say why.',
  daily_summary: 'Return JSON: {"summary":string,"suggestedNextStep":string|null}. Only summarize confirmed feedback; distinguish missing evidence from failure.',
};

function reply(response, status, body) {
  response.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
  response.end(JSON.stringify(body));
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    let text = '';
    let bytes = 0;
    request.on('data', chunk => {
      bytes += chunk.length;
      text += chunk;
      if (bytes > MAX_REQUEST_BYTES) {
        reject(new Error('request too large'));
        request.destroy();
      }
    });
    request.on('end', () => resolve(text));
    request.on('error', reject);
  });
}

function parseModelJson(content) {
  const trimmed = content.trim().replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '');
  return JSON.parse(trimmed);
}

export function createGateway({ apiKey = API_KEY, apiBase = API_BASE, model = MODEL, fetchImpl = fetch } = {}) {
  return http.createServer(async (request, response) => {
    if (request.method === 'GET' && request.url === '/health') {
      reply(response, 200, { ready: Boolean(apiKey), model });
      return;
    }
    if (request.method !== 'POST' || request.url !== '/advice') {
      reply(response, 404, { error: 'not found' });
      return;
    }
    if (!apiKey) {
      reply(response, 503, { error: 'Agnes API key is not configured in the host process' });
      return;
    }
    try {
      const input = JSON.parse(await readBody(request));
      if (typeof input.requestId !== 'string' || input.contractVersion !== 'repland-ai-advisor/v1'
          || !Object.hasOwn(instructionByKind, input.kind)) {
        reply(response, 400, { error: 'unsupported request' });
        return;
      }
      const upstream = await fetchImpl(`${apiBase}/chat/completions`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model,
          temperature: 0.2,
          messages: [
            { role: 'system', content: `你是 Repland 的中文学习规划助理。仅返回一个 JSON 对象，不要 Markdown。${instructionByKind[input.kind]} 所有内容只是建议，不得宣称已修改本地任务、日程或用户画像。` },
            { role: 'user', content: JSON.stringify(input) },
          ],
        }),
        signal: AbortSignal.timeout(30_000),
      });
      if (!upstream.ok) {
        reply(response, 502, { error: 'Agnes upstream rejected the request', upstreamStatus: upstream.status });
        return;
      }
      const envelope = await upstream.json();
      const content = envelope?.choices?.[0]?.message?.content;
      if (typeof content !== 'string') throw new Error('missing model content');
      const advice = parseModelJson(content);
      if (!advice || typeof advice !== 'object' || Array.isArray(advice)) throw new Error('invalid advice');
      reply(response, 200, { ...advice, contractVersion: input.contractVersion, requestId: input.requestId });
    } catch (error) {
      // Do not log prompts, model output, or credentials.
      reply(response, 502, { error: error instanceof SyntaxError ? 'invalid JSON' : 'gateway request failed' });
    }
  });
}

if (process.argv[1] && import.meta.url === new URL(`file:///${process.argv[1].replaceAll('\\', '/')}`).href) {
  createGateway().listen(PORT, '127.0.0.1', () => {
    process.stdout.write(`Repland Agnes dev gateway on 127.0.0.1:${PORT}; model=${MODEL}; keyConfigured=${Boolean(API_KEY)}\n`);
  });
}
