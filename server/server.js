// 딱맞춤 데모 서버 (의존성 없음, Node 18+)
// 실행: node server.js   →  매장 화면 http://localhost:8787/store
const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const { CONFIG, plan, fifoSchedule, laneEntered, arrivalAt } = require('./queue');
const { MENU, STORE, STORES, menuOf } = require('./data');

const PORT = Number(process.env.PORT || 8787);
const PUBLIC = path.join(__dirname, 'public');

// ---------------- 상태 ----------------
let orders = [];
// 매장마다 줄이 따로다. 다른 매장의 주문이 이 매장의 순서나 만드는 사람 수를 차지하면 안 된다.
let prevIdsByStore = new Map();
let nextNo = 1;
let log = [];
let groups = new Map(); // QR 동승자 주문 장바구니
let pays = new Map(); // QR 결제 (데모): 코드 → { amount, paid }
const clients = new Set();
let sim = { running: false, speed: 1, cars: [], t: 0, autoBarista: true };

// 가상 시계: 시뮬 배속(1~10x)을 써도 모든 시간 계산이 일관되게
let clock = Date.now();
function now() { return clock; }
function uid() { return Math.random().toString(36).slice(2, 10); }
// storeId가 있으면 그 매장 화면에만 보이는 기록, 없으면 모든 매장 화면에 보이는 기록
function pushLog(text, kind = 'info', storeId = null) {
  log.unshift({ at: now(), text, kind, storeId });
  log = log.slice(0, 80);
}
function storeOf(id) { return STORES.find((s) => s.id === id) || STORE; }

function itemsToPrep(items) {
  // 가장 오래 걸리는 메뉴 + 추가 잔당 15초
  let max = 0, count = 0;
  for (const it of items) {
    const m = MENU.find((x) => x.id === it.menuId);
    if (!m) continue;
    max = Math.max(max, m.prepSec);
    count += it.qty || 1;
  }
  return max + Math.max(0, count - 1) * 15;
}

function describe(items) {
  return items
    .map((it) => {
      const m = MENU.find((x) => x.id === it.menuId);
      return m ? `${m.name}${(it.qty || 1) > 1 ? ' ×' + it.qty : ''}` : '?';
    })
    .join(', ');
}

function total(items) {
  return items.reduce((s, it) => s + (MENU.find((x) => x.id === it.menuId)?.price || 0) * (it.qty || 1), 0);
}

function createOrder({ items, carLabel, source = 'car', eta, payment, storeId }) {
  const store = STORES.find((s) => s.id === storeId) || STORE;
  const o = {
    id: uid(),
    no: nextNo++,
    storeId: store.id,
    storeName: store.name,
    carLabel: carLabel || (source === 'walkin' ? '현장 주문' : source === 'phone' ? '폰 주문' : '차량'),
    source,
    items,
    summary: describe(items),
    amount: total(items),
    payment: payment || (source === 'car' ? 'CarPay(데모)' : '앱/현장'),
    prepSec: itemsToPrep(items),
    status: 'waiting',
    createdAt: now(),
    eta: eta && eta.sec != null ? { sec: eta.sec, distanceM: eta.distanceM ?? null, at: now() } : null,
    inLane: source === 'walkin',
    laneAt: source === 'walkin' ? now() : null,
    offRoute: false,
    bay: null,
  };
  orders.push(o);
  pushLog(`#${o.no} 주문 접수 (${o.carLabel}), ${o.summary}`, 'new', o.storeId);
  replan();
  return o;
}

let lastRanks = new Map();
let lastPlan = [];
function replan() {
  const t = now();
  const all = [];
  // 매장별로 따로 순서를 세운다 (순번, 흔들림 방지, 만드는 사람 수 모두 매장 단위)
  for (const s of STORES) {
    const mine = orders.filter((o) => storeOf(o.storeId).id === s.id);
    const { ranked, ids } = plan(mine, prevIdsByStore.get(s.id) || [], t);
    prevIdsByStore.set(s.id, ids);
    // 순서가 바뀐 걸 로그로 남김 (발표 때 보여줄 핵심 장면)
    ranked.forEach((v) => {
      const before = lastRanks.get(v.id);
      if (before && v.rank < before && v.status === 'waiting') {
        const passed = ranked.filter((x) => x.rank > v.rank && lastRanks.get(x.id) < before && x.status === 'waiting');
        if (passed.length) {
          const p = passed[0];
          const why = p.hold ? p.hold : p.inLane ? '' : `도착 ${Math.max(0, Math.round((p.arrivalAt - v.arrivalAt) / 60000))}분 늦음`;
          pushLog(`#${v.no} ↑ ${before}→${v.rank}번째, #${p.no}보다 먼저 도착 예정${why ? ' (' + why + ')' : ''}`, 'swap', s.id);
        }
      }
    });
    all.push(...ranked);
  }
  lastRanks = new Map(all.map((v) => [v.id, v.rank]));
  lastPlan = all;
  return all;
}

function viewOf(o) {
  const v = lastPlan.find((x) => x.id === o.id);
  return { ...o, ...(v || {}), now: now() };
}

function compare(storeId) {
  // 같은 주문들을 기존(주문순, 즉시 제조) 방식으로 했을 때와 비교 (이 매장의 주문만)
  const t = now();
  const relevant = orders.filter((o) => o.status !== 'cancelled' && storeOf(o.storeId).id === storeId);
  const fifo = new Map(fifoSchedule(relevant, t).map((x) => [x.id, x]));
  let fifoWait = 0, fifoIdle = 0, ourWait = 0, ourIdle = 0, n = 0;
  for (const o of relevant) {
    const arrival = o.arrivedAt || arrivalAt(o, t);
    const ours = o.readyAt || lastPlan.find((x) => x.id === o.id)?.readyAt;
    const f = fifo.get(o.id);
    if (!ours || !f) continue;
    n++;
    fifoWait += Math.max(0, f.readyAt - arrival);
    fifoIdle += Math.max(0, arrival - f.readyAt);
    ourWait += Math.max(0, ours - arrival);
    ourIdle += Math.max(0, arrival - ours);
  }
  const avg = (x) => (n ? Math.round(x / n / 1000) : 0);
  return { n, fifo: { waitSec: avg(fifoWait), idleSec: avg(fifoIdle) }, ours: { waitSec: avg(ourWait), idleSec: avg(ourIdle) } };
}

// 매장 화면 한 대가 보는 내용: 자기 매장의 주문만
function snapshot(storeId) {
  const store = storeOf(storeId);
  const mine = (o) => storeOf(o.storeId).id === store.id;
  return {
    now: now(),
    store,
    stores: STORES.map((s) => ({ id: s.id, name: s.name })),
    config: CONFIG,
    queue: lastPlan.filter(mine),
    done: orders.filter((o) => o.status === 'done' && mine(o)).slice(-6).reverse(),
    log: log.filter((l) => !l.storeId || l.storeId === store.id).slice(0, 40),
    sim: { running: sim.running, speed: sim.speed, autoBarista: sim.autoBarista },
    compare: compare(store.id),
  };
}

function broadcast() {
  for (const res of clients) res.write(`data: ${JSON.stringify(snapshot(res.storeId))}\n\n`);
}

// ---------------- 시뮬레이션 (가상 차량) ----------------
// 에뮬레이터는 차 1대뿐이라, 여러 대가 동시에 오는 상황은 서버가 가상 차량으로 만든다.
const SCENARIOS = {
  // 발표용 핵심 장면: #1이 먼저 주문했지만 정체에 걸리고, 뒤에 주문한 차가 먼저 도착
  traffic: [
    { label: '12가 3456, 그랜저', items: [{ menuId: 'americano-ice', qty: 2 }], etaSec: 240, jamAt: 25, jamAdd: 300 },
    { label: '34나 7890, 아이오닉5', items: [{ menuId: 'latte', qty: 1 }], etaSec: 300, orderDelay: 4 },
    { label: '56다 1122, 싼타페', items: [{ menuId: 'frappe', qty: 1 }, { menuId: 'americano-hot', qty: 1 }], etaSec: 420, orderDelay: 8 },
    { label: '현장 주문', walkin: true, items: [{ menuId: 'americano-hot', qty: 1 }], orderDelay: 15 },
    { label: '78라 3344, 쏘렌토', items: [{ menuId: 'ade', qty: 2 }], etaSec: 200, orderDelay: 30 },
  ],
};

function simReset() {
  orders = orders.filter((o) => o.source === 'car' && !o.sim); // 실제 차량 앱 주문은 유지
  prevIdsByStore = new Map();
  lastRanks = new Map();
  sim = { running: false, speed: sim.speed, cars: [], t: 0, autoBarista: sim.autoBarista, storeId: sim.storeId };
  pushLog('시뮬레이션 초기화', 'info');
  replan();
}

// 가상 차량은 시나리오를 누른 매장 화면의 매장으로 주문한다
function simStart(name = 'traffic', storeId) {
  simReset();
  const sc = SCENARIOS[name] || SCENARIOS.traffic;
  sim.storeId = storeOf(storeId).id;
  sim.cars = sc.map((c) => ({ ...c, orderId: null, remaining: c.etaSec, jammed: false }));
  sim.running = true;
  pushLog(`시뮬레이션 시작: 가상 차량 ${sc.length}대`, 'info');
}

function simTick(dtSec) {
  if (!sim.running) return;
  sim.t += dtSec;
  for (const c of sim.cars) {
    if (!c.orderId && sim.t >= (c.orderDelay || 0)) {
      const o = createOrder({
        items: c.items,
        carLabel: c.label,
        source: c.walkin ? 'walkin' : 'car',
        eta: c.walkin ? null : { sec: c.remaining, distanceM: c.remaining * 11 },
        storeId: sim.storeId,
      });
      o.sim = true;
      c.orderId = o.id;
      continue;
    }
    if (!c.orderId || c.walkin) continue;
    const o = orders.find((x) => x.id === c.orderId);
    if (!o || o.status === 'done') continue;
    if (c.jamAt && !c.jammed && sim.t >= c.jamAt) {
      c.jammed = true;
      c.remaining += c.jamAdd;
      pushLog(`🚧 ${c.label}: 사고 정체로 도착 ${Math.round(c.jamAdd / 60)}분 늦어짐 (내비 실시간 반영)`, 'warn', o.storeId);
    }
    c.remaining = Math.max(0, c.remaining - dtSec);
    const distanceM = Math.round(c.remaining * 11);
    const speedKph = c.remaining < 20 ? 8 : 40;
    o.eta = { sec: c.remaining, distanceM, at: now() };
    if (!o.inLane && laneEntered(distanceM, speedKph)) {
      o.inLane = true;
      o.laneAt = now();
      pushLog(`🚗 #${o.no} DT 차선 진입 → 순서 확정`, 'lane', o.storeId);
    }
    // 픽업창 도착 처리
    if (o.inLane && now() - o.laneAt > CONFIG.LANE_TO_WINDOW_SEC * 1000 && !o.arrivedAt) arrive(o);
  }
}

// 가상 바리스타: '지금 시작'이 된 주문을 자동으로 시작, 완성 (혼자 시연할 때). 매장 화면에서 끄면 버튼으로 직접.
function autoBarista() {
  if (!sim.autoBarista) return;
  for (const v of lastPlan) {
    const o = orders.find((x) => x.id === v.id);
    if (!o) continue;
    if (o.status === 'waiting' && v.action.kind === 'start') setStatus(o, 'making');
    else if (o.status === 'making' && now() >= o.startedAt + o.prepSec * 1000) setStatus(o, 'ready');
  }
}

function arrive(o) {
  o.arrivedAt = now();
  // 창구에 온 차는 줄 순서가 정해진 차다. 차선 진입 신호를 못 받았더라도 여기서 확정한다
  if (!o.inLane) {
    o.inLane = true;
    o.laneAt = now();
  }
  if (o.status === 'ready') {
    pushLog(`✅ #${o.no} 도착 즉시 수령 (대기 0초)`, 'good', o.storeId);
    setStatus(o, 'done');
  } else {
    o.bay = 1 + (orders.filter((x) => x.bay).length % 3);
    // bay 값은 "도착했지만 아직 준비 중"이라는 표시로만 쓴다 (대기칸 안내는 뺐다)
    pushLog(`⏸ #${o.no} 도착, 아직 준비 중`, 'warn', o.storeId);
  }
}

function setStatus(o, status) {
  o.status = status;
  if (status === 'making') o.startedAt = now();
  if (status === 'ready') {
    o.readyAt = now();
    if (o.bay) {
      pushLog(`☕ #${o.no} 완성 → 창구에서 전달`, 'good', o.storeId);
      o.status = 'done';
    }
  }
  if (status === 'done') o.doneAt = now();
  replan();
}

// ---------------- HTTP ----------------
function send(res, code, body, type = 'application/json; charset=utf-8') {
  res.writeHead(code, {
    'Content-Type': type,
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
  });
  res.end(typeof body === 'string' || Buffer.isBuffer(body) ? body : JSON.stringify(body));
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (c) => (data += c));
    req.on('end', () => {
      try { resolve(data ? JSON.parse(data) : {}); } catch { resolve({}); }
    });
  });
}

const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png' };

function serveFile(res, file) {
  const p = path.join(PUBLIC, file);
  if (!p.startsWith(PUBLIC) || !fs.existsSync(p)) return send(res, 404, { error: 'not found' });
  send(res, 200, fs.readFileSync(p), MIME[path.extname(p)] || 'application/octet-stream');
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const p = url.pathname;
  const m = (re) => p.match(re);
  if (req.method === 'OPTIONS') return send(res, 204, '');

  // 화면
  if (p === '/' || p === '/store') return serveFile(res, 'store.html');
  if (m(/^\/join\/[A-Z0-9]+$/)) return serveFile(res, 'join.html');
  if (m(/^\/pay\/[A-Z0-9]+$/)) return serveFile(res, 'pay.html');
  if (p.startsWith('/static/')) return serveFile(res, p.replace('/static/', ''));

  // 실시간 스트림 (매장 화면)
  if (p === '/api/stream') {
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive', 'Access-Control-Allow-Origin': '*' });
    res.storeId = url.searchParams.get('store'); // 이 화면이 보는 매장 (없으면 기본 매장)
    res.write(`data: ${JSON.stringify(snapshot(res.storeId))}\n\n`);
    clients.add(res);
    req.on('close', () => clients.delete(res));
    return;
  }

  if (p === '/api/health') return send(res, 200, { ok: true, store: STORE.name });
  // 매장 목록 (DT 매장만). 차량 앱이 현재 위치 기준으로 가까운 순으로 정렬한다.
  if (p === '/api/stores') return send(res, 200, { stores: STORES });
  // 매장별 메뉴: /api/menu?store=dt-002 (store가 없으면 기본 매장)
  if (p === '/api/menu') return send(res, 200, menuOf(url.searchParams.get('store')));

  // QR 결제 (데모): 차량 앱이 코드를 만들고, 폰에서 /pay/코드 를 열어 결제를 누르면 paid가 된다
  if (p === '/api/pay' && req.method === 'POST') {
    const b = await readBody(req);
    const code = Math.random().toString(36).slice(2, 8).toUpperCase();
    pays.set(code, { code, amount: Number(b.amount) || 0, storeName: String(b.storeName || ''), paid: false, createdAt: now() });
    return send(res, 200, pays.get(code));
  }
  // 시연용: 진행 중인 QR 결제 목록 (PC에서 폰 결제를 대신 눌러 줄 때 코드를 찾는다)
  if (p === '/api/pay' && req.method === 'GET') return send(res, 200, { pays: [...pays.values()] });
  let pm;
  if ((pm = m(/^\/api\/pay\/([A-Z0-9]+)$/)) && req.method === 'GET') {
    const x = pays.get(pm[1]);
    return x ? send(res, 200, x) : send(res, 404, { error: 'no pay' });
  }
  if ((pm = m(/^\/api\/pay\/([A-Z0-9]+)\/done$/)) && req.method === 'POST') {
    const x = pays.get(pm[1]);
    if (!x) return send(res, 404, { error: 'no pay' });
    x.paid = true;
    return send(res, 200, x);
  }
  if (p === '/api/state') return send(res, 200, snapshot(url.searchParams.get('store')));

  // 주문 생성 (차량 앱)
  if (p === '/api/orders' && req.method === 'POST') {
    const b = await readBody(req);
    if (!Array.isArray(b.items) || !b.items.length) return send(res, 400, { error: 'items 필요' });
    const o = createOrder(b);
    broadcast();
    return send(res, 200, viewOf(o));
  }

  let mm;
  // 차량 텔레메트리: 남은 시간, 거리, 속도 → 순서 재계산
  if ((mm = m(/^\/api\/orders\/(\w+)\/telemetry$/)) && req.method === 'POST') {
    const o = orders.find((x) => x.id === mm[1]);
    if (!o) return send(res, 404, { error: 'no order' });
    const b = await readBody(req);
    if (b.etaSec != null) o.eta = { sec: Number(b.etaSec), distanceM: b.distanceM ?? null, at: now() };
    o.offRoute = !!b.offRoute;
    if (!o.inLane && laneEntered(b.distanceM, b.speedKph)) {
      o.inLane = true;
      o.laneAt = now();
      pushLog(`🚗 #${o.no} DT 차선 진입 → 순서 확정`, 'lane', o.storeId);
    }
    replan();
    broadcast();
    return send(res, 200, viewOf(o));
  }
  if ((mm = m(/^\/api\/orders\/(\w+)\/arrived$/)) && req.method === 'POST') {
    const o = orders.find((x) => x.id === mm[1]);
    if (!o) return send(res, 404, { error: 'no order' });
    if (!o.arrivedAt) arrive(o);
    replan();
    broadcast();
    return send(res, 200, viewOf(o));
  }
  if ((mm = m(/^\/api\/orders\/(\w+)\/status$/)) && req.method === 'POST') {
    const o = orders.find((x) => x.id === mm[1]);
    if (!o) return send(res, 404, { error: 'no order' });
    const b = await readBody(req);
    if (!['waiting', 'making', 'ready', 'done', 'cancelled'].includes(b.status)) return send(res, 400, { error: 'bad status' });
    setStatus(o, b.status);
    broadcast();
    return send(res, 200, viewOf(o));
  }
  if ((mm = m(/^\/api\/orders\/(\w+)$/)) && req.method === 'GET') {
    const o = orders.find((x) => x.id === mm[1]);
    if (!o) return send(res, 404, { error: 'no order' });
    return send(res, 200, viewOf(o));
  }

  // 동승자 QR 주문 (그룹 장바구니)
  if (p === '/api/groups' && req.method === 'POST') {
    const code = Math.random().toString(36).slice(2, 6).toUpperCase();
    groups.set(code, { code, items: [], createdAt: now() });
    return send(res, 200, groups.get(code));
  }
  if ((mm = m(/^\/api\/groups\/([A-Z0-9]+)$/)) && req.method === 'GET') {
    const g = groups.get(mm[1]);
    return g ? send(res, 200, { ...g, store: STORE, menu: MENU }) : send(res, 404, { error: 'no group' });
  }
  if ((mm = m(/^\/api\/groups\/([A-Z0-9]+)\/items$/)) && req.method === 'POST') {
    const g = groups.get(mm[1]);
    if (!g) return send(res, 404, { error: 'no group' });
    const b = await readBody(req);
    if (!MENU.find((x) => x.id === b.menuId)) return send(res, 400, { error: 'bad menu' });
    g.items.push({ who: String(b.who || '동승자').slice(0, 10), menuId: b.menuId, qty: 1 });
    return send(res, 200, g);
  }

  // 시뮬레이션 제어 (매장 화면 버튼)
  if (p === '/api/sim/start' && req.method === 'POST') { const b = await readBody(req); simStart(b.scenario, b.store); broadcast(); return send(res, 200, { ok: true }); }
  if (p === '/api/sim/barista' && req.method === 'POST') { const b = await readBody(req); sim.autoBarista = !!b.on; broadcast(); return send(res, 200, { ok: true }); }
  if (p === '/api/sim/reset' && req.method === 'POST') { simReset(); broadcast(); return send(res, 200, { ok: true }); }
  if (p === '/api/sim/speed' && req.method === 'POST') { const b = await readBody(req); sim.speed = Math.min(10, Math.max(1, Number(b.speed) || 1)); broadcast(); return send(res, 200, { ok: true }); }

  send(res, 404, { error: 'not found' });
});

// 1초마다: 시뮬 진행 + 순서 재계산 + 화면 갱신
setInterval(() => {
  for (let i = 0; i < sim.speed; i++) {
    clock += 1000;
    simTick(1);
    autoBarista();
  }
  replan();
  broadcast();
}, 1000);

function lanIps() {
  return Object.values(os.networkInterfaces()).flat().filter((x) => x && x.family === 'IPv4' && !x.internal).map((x) => x.address);
}

if (require.main === module) {
  server.listen(PORT, '0.0.0.0', () => {
    console.log(`\n딱맞춤 서버 실행 중`);
    console.log(`  매장 화면:      http://localhost:${PORT}/store`);
    console.log(`  에뮬레이터에서: http://10.0.2.2:${PORT}  (차량 앱 기본값)`);
    for (const ip of lanIps()) console.log(`  같은 와이파이 폰: http://${ip}:${PORT}`);
    console.log('');
  });
}

module.exports = { server, createOrder, setStatus, simStart, simTick, replan, snapshot };
