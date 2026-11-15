// 데모용 가상 매장, 메뉴 (실제 브랜드 이름, 로고, 메뉴명 사용 금지: 공모전 제3자 권리 규정)
// 드라이브스루(DT) 매장만 다룬다.
//
// Pleos 에뮬레이터의 내비는 현재 위치가 성남 판교(위도 37.4127, 경도 127.0947)로 고정이다.
// 실제 내비에서 "몇 분 뒤 도착"이 나오도록 그 근처의 가상 좌표를 쓴다 (실제 매장 아님).
const STORES = [
  { id: 'dt-001', brand: '딱맞춤커피', name: '판교 딱맞춤DT점', latitude: 37.4054, longitude: 127.1031, address: '경기 성남시 분당구', menu: 'cafe' },
  { id: 'dt-002', brand: '버거포트', name: '버거포트 판교DT점', latitude: 37.3985, longitude: 127.1102, address: '경기 성남시 분당구', menu: 'burger' },
  { id: 'dt-003', brand: '그린빈커피', name: '그린빈커피 분당DT점', latitude: 37.3838, longitude: 127.1215, address: '경기 성남시 분당구', menu: 'cafe' },
];

// 예전 코드와 맞추기 위한 기본 매장
const STORE = STORES[0];

// prepSec: 메뉴별 제조 시간(초). 이걸 역산해서 "지금 만들기 시작" 시점을 정한다.
const MENUS = {
  cafe: [
    { id: 'americano-ice', name: '아이스 아메리카노', price: 4000, prepSec: 60, emoji: '🧊', tag: '인기' },
    { id: 'americano-hot', name: '따뜻한 아메리카노', price: 4000, prepSec: 50, emoji: '☕' },
    { id: 'latte', name: '카페 라떼', price: 4800, prepSec: 90, emoji: '🥛' },
    { id: 'vanilla-latte', name: '바닐라 라떼', price: 5300, prepSec: 100, emoji: '🍦' },
    { id: 'frappe', name: '초코 프라페', price: 5900, prepSec: 150, emoji: '🍫', tag: '오래 걸림' },
    { id: 'ade', name: '레몬 에이드', price: 5500, prepSec: 80, emoji: '🍋' },
    { id: 'sandwich', name: '햄치즈 샌드위치', price: 6500, prepSec: 120, emoji: '🥪' },
    { id: 'cookie', name: '초코칩 쿠키', price: 2800, prepSec: 15, emoji: '🍪' },
  ],
  burger: [
    { id: 'burger-classic', name: '클래식 버거 세트', price: 7900, prepSec: 150, emoji: '🍔' },
    { id: 'burger-cheese', name: '치즈 버거 세트', price: 8500, prepSec: 160, emoji: '🍔' },
    { id: 'burger-chicken', name: '치킨 버거 세트', price: 8200, prepSec: 170, emoji: '🍔' },
    { id: 'nuggets', name: '치킨 너겟 6조각', price: 3900, prepSec: 120, emoji: '🍗' },
    { id: 'fries', name: '감자튀김', price: 2500, prepSec: 90, emoji: '🍟' },
    { id: 'cola', name: '콜라', price: 2000, prepSec: 20, emoji: '🥤' },
  ],
};

// 가격, 제조 시간을 찾을 때 쓰는 전체 메뉴
const MENU = [...MENUS.cafe, ...MENUS.burger];

function menuOf(storeId) {
  const s = STORES.find((x) => x.id === storeId) || STORE;
  return { store: s, menu: MENUS[s.menu] || MENUS.cafe };
}

module.exports = { STORE, STORES, MENU, MENUS, menuOf };
