// 실행: node queue.test.js
const assert = require('assert');
const { plan, CONFIG } = require('./queue');

const T = 1_000_000;
const mk = (id, no, etaSec, extra = {}) => ({
  id, no, source: 'car', items: [], prepSec: 60, status: 'waiting', createdAt: T + no,
  eta: { sec: etaSec, at: T }, inLane: false, laneAt: null, ...extra,
});

let passed = 0;
function test(name, fn) { fn(); passed++; console.log('✓', name); }

test('먼저 주문했어도 늦게 도착하면 뒤로 간다', () => {
  const a = mk('a', 7, 600); // 7번: 10분 뒤
  const b = mk('b', 8, 180); // 8번: 3분 뒤
  const { ranked } = plan([a, b], [], T);
  assert.deepStrictEqual(ranked.map((x) => x.no), [8, 7]);
});

test('차이가 45초 미만이면 순서를 안 바꾼다 (흔들림 방지)', () => {
  const a = mk('a', 1, 330);
  const b = mk('b', 2, 300);
  const { ranked } = plan([a, b], ['a', 'b'], T);
  assert.deepStrictEqual(ranked.map((x) => x.no), [1, 2]);
});

test('차선에 들어온 차는 아무리 늦게 주문했어도 앞', () => {
  const a = mk('a', 1, 60);
  const b = mk('b', 2, 900, { inLane: true, laneAt: T });
  const { ranked } = plan([a, b], [], T);
  assert.strictEqual(ranked[0].no, 2);
});

test('제조 중인 주문은 순서가 고정된다', () => {
  const a = mk('a', 1, 900, { status: 'making', startedAt: T });
  const b = mk('b', 2, 30);
  const { ranked } = plan([a, b], [], T);
  assert.deepStrictEqual(ranked.map((x) => x.no), [1, 2]);
});

test('ETA 신호가 끊기면 보류되고 뒤로 간다', () => {
  const now = T + (CONFIG.STALE_SEC + 5) * 1000;
  const a = mk('a', 1, 120, { eta: { sec: 120, at: T } });
  const b = mk('b', 2, 200, { eta: { sec: 200, at: now } });
  const { ranked } = plan([a, b], ['a', 'b'], now);
  assert.strictEqual(ranked[0].no, 2);
  assert.ok(ranked[1].hold);
});

test('너무 일찍 만들지 않는다: 도착 - 제조시간 - 여유 에 시작', () => {
  const a = mk('a', 1, 600);
  const { ranked } = plan([a], [], T);
  assert.strictEqual(ranked[0].startAt, T + (600 - 60 - CONFIG.BUFFER_SEC) * 1000);
  assert.strictEqual(ranked[0].action.kind, 'later');
});

test('바리스타가 모자라면 지연 예상을 표시한다', () => {
  const list = [1, 2, 3].map((n) => ({ ...mk('o' + n, n, 30), prepSec: 120 }));
  const { ranked } = plan(list, [], T);
  assert.ok(ranked.some((x) => x.late));
});

test('이미 도착한 주문은 신호가 끊겨도 보류하지 않고 바로 만든다', () => {
  const now = T + (CONFIG.STALE_SEC + 30) * 1000;
  const a = mk('a', 1, 20, { eta: { sec: 20, at: T }, arrivedAt: T + 20_000, inLane: true, laneAt: T + 20_000, prepSec: 15 });
  const { ranked } = plan([a], [], now);
  assert.strictEqual(ranked[0].hold, null);
  assert.strictEqual(ranked[0].action.kind, 'start');
});

test('매장마다 따로 줄을 세우면 다른 매장 주문이 순서와 제조 인원을 차지하지 않는다', () => {
  const busy = [1, 2, 3].map((n) => ({ ...mk('x' + n, n, 30), prepSec: 120, storeId: 's1' }));
  const mine = { ...mk('m', 4, 200), storeId: 's2' };
  const all = [...busy, mine];
  const alone = plan(all.filter((o) => o.storeId === 's2'), [], T).ranked[0];
  assert.strictEqual(alone.rank, 1);
  assert.strictEqual(alone.late, false);
  assert.strictEqual(alone.startAt, T + (200 - 60 - CONFIG.BUFFER_SEC) * 1000);
});

console.log(`\n${passed}개 통과`);
