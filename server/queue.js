// 딱맞춤 핵심 알고리즘: 주문순이 아니라 "도착 예정순"으로 제조 순서와 시작 시각을 정한다.
// 순수 함수만 둔다(서버, 테스트에서 같이 씀).

const CONFIG = {
  SWAP_GAP_SEC: 45,       // 두 차의 도착 예정 차이가 이보다 작으면 순서를 바꾸지 않음 (6분↔7분 흔들림 방지)
  BUFFER_SEC: 20,         // 도착 몇 초 전에 완성되게 할지
  STALE_SEC: 60,          // 이 시간 동안 차에서 ETA가 안 오면 '보류'
  LANE_DIST_M: 150,       // 매장 이 거리 안 + 저속이면 DT 차선 진입으로 판단
  LANE_SPEED_KPH: 15,
  LANE_TO_WINDOW_SEC: 60, // 차선 진입 후 픽업창까지 걸리는 시간 가정
  WALKIN_TO_WINDOW_SEC: 120,
  BARISTAS: 2,            // 동시에 만들 수 있는 사람 수
  PHONE_DEFAULT_SEC: 600, // 폰 주문은 ETA를 모르니 10분 뒤 도착으로 가정
};

const ACTIVE = new Set(['waiting', 'making', 'ready']);

function arrivalAt(o, now, cfg = CONFIG) {
  if (o.arrivedAt) return o.arrivedAt; // 이미 창구에 와 있다: 더 미루지 않고 바로 만든다
  if (o.inLane) return o.laneAt + (o.source === 'walkin' ? cfg.WALKIN_TO_WINDOW_SEC : cfg.LANE_TO_WINDOW_SEC) * 1000;
  if (o.eta) return o.eta.at + o.eta.sec * 1000;
  if (o.source === 'phone') return o.createdAt + cfg.PHONE_DEFAULT_SEC * 1000;
  return o.createdAt + cfg.PHONE_DEFAULT_SEC * 1000;
}

function holdReason(o, now, cfg = CONFIG) {
  // 도착한 차는 남은 시간을 더 보내지 않는다. 그것을 "신호 끊김"으로 보고 보류하면 영영 만들지 않게 된다
  if (o.status !== 'waiting' || o.inLane || o.arrivedAt) return null;
  if (o.offRoute) return '경로 이탈';
  if (o.source === 'car' && o.eta && now - o.eta.at > cfg.STALE_SEC * 1000) return '차량 신호 끊김';
  return null;
}

// a가 b 앞에 있을 때 둘을 바꿔야 하나?
function shouldSwap(a, b, now, cfg) {
  if (a.inLane && b.inLane) return b.laneAt < a.laneAt;      // 차선 안에서는 물리적 순서가 절대 기준
  if (a.inLane) return false;
  if (b.inLane) return true;
  const ha = !!holdReason(a, now, cfg), hb = !!holdReason(b, now, cfg);
  if (ha !== hb) return ha;                                   // 보류된 주문은 뒤로
  return arrivalAt(a, now, cfg) - arrivalAt(b, now, cfg) > cfg.SWAP_GAP_SEC * 1000;
}

/**
 * @param orders 전체 주문
 * @param prevIds 직전 순서(id 배열) - 흔들림 방지용
 * @returns {ranked: view[], ids: string[]}
 */
function plan(orders, prevIds, now, cfg = CONFIG) {
  const active = orders.filter((o) => ACTIVE.has(o.status));
  const pinned = active
    .filter((o) => o.status !== 'waiting')
    .sort((a, b) => (a.startedAt || 0) - (b.startedAt || 0));

  const prevIndex = new Map((prevIds || []).map((id, i) => [id, i]));
  const rest = active
    .filter((o) => o.status === 'waiting')
    .sort((a, b) => {
      const pa = prevIndex.has(a.id) ? prevIndex.get(a.id) : Infinity;
      const pb = prevIndex.has(b.id) ? prevIndex.get(b.id) : Infinity;
      if (pa !== pb) return pa - pb;
      return a.createdAt - b.createdAt;
    });

  // 임계값이 있는 버블 정렬: 차이가 충분히 클 때만 자리를 바꾼다
  let swapped = true;
  while (swapped) {
    swapped = false;
    for (let i = 0; i < rest.length - 1; i++) {
      if (shouldSwap(rest[i], rest[i + 1], now, cfg)) {
        [rest[i], rest[i + 1]] = [rest[i + 1], rest[i]];
        swapped = true;
      }
    }
  }

  const ranked = [...pinned, ...rest];
  const views = schedule(ranked, now, cfg);
  return { ranked: views, ids: rest.map((o) => o.id) };
}

// 바리스타 수(capacity)를 고려해 각 주문의 시작, 완성 시각을 계산
function schedule(ranked, now, cfg = CONFIG) {
  const free = Array(cfg.BARISTAS).fill(now);
  return ranked.map((o, i) => {
    const arrival = arrivalAt(o, now, cfg);
    const hold = holdReason(o, now, cfg);
    let startAt, readyAt;
    if (o.status === 'making') {
      startAt = o.startedAt;
      readyAt = o.startedAt + o.prepSec * 1000;
      const k = free.indexOf(Math.min(...free));
      free[k] = Math.max(free[k], readyAt);
    } else if (o.status === 'ready') {
      startAt = o.startedAt;
      readyAt = o.readyAt;
    } else {
      const k = free.indexOf(Math.min(...free));
      const latest = arrival - (o.prepSec + cfg.BUFFER_SEC) * 1000; // 이보다 일찍 만들면 식거나 얼음이 녹음
      startAt = Math.max(free[k], latest);
      readyAt = startAt + o.prepSec * 1000;
      if (!hold) free[k] = readyAt;
    }
    const late = o.status !== 'ready' && readyAt > arrival + 5000;
    let action;
    if (o.status === 'making') action = { kind: 'making', label: '제조 중' };
    else if (o.status === 'ready') action = { kind: 'ready', label: '완성, 픽업 대기' };
    else if (hold) action = { kind: 'hold', label: `보류, ${hold}` };
    else if (startAt <= now) action = { kind: 'start', label: '지금 시작' };
    else action = { kind: 'later', label: `${fmt(startAt - now)} 후 시작` };
    return {
      ...o,
      rank: i + 1,
      arrivalAt: arrival,
      arrivalInSec: Math.round((arrival - now) / 1000),
      startAt,
      readyAt,
      late,
      hold,
      action,
    };
  });
}

// 비교용: 기존 방식(주문 들어온 순서대로 바로 제조)
function fifoSchedule(orders, now, cfg = CONFIG) {
  const list = orders.filter((o) => o.status !== 'cancelled').sort((a, b) => a.createdAt - b.createdAt);
  const free = Array(cfg.BARISTAS).fill(-Infinity);
  return list.map((o) => {
    const k = free.indexOf(Math.min(...free));
    const startAt = Math.max(free[k], o.createdAt);
    const readyAt = startAt + o.prepSec * 1000;
    free[k] = readyAt;
    return { id: o.id, startAt, readyAt };
  });
}

function fmt(ms) {
  const s = Math.max(0, Math.round(ms / 1000));
  const m = Math.floor(s / 60);
  return m > 0 ? `${m}분 ${s % 60}초` : `${s}초`;
}

function laneEntered(distanceM, speedKph, cfg = CONFIG) {
  return distanceM != null && distanceM <= cfg.LANE_DIST_M && speedKph != null && speedKph <= cfg.LANE_SPEED_KPH;
}

module.exports = { CONFIG, plan, schedule, fifoSchedule, arrivalAt, holdReason, laneEntered, fmt };
