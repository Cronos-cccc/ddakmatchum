@file:OptIn(ExperimentalTextApi::class)

package com.ddakmatchum.car.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ddakmatchum.car.DtViewModel
import com.ddakmatchum.car.ProgressView
import com.ddakmatchum.car.NearStore
import com.ddakmatchum.car.OrderView
import com.ddakmatchum.car.Phase
import com.ddakmatchum.car.R
import com.ddakmatchum.car.UiState
import com.ddakmatchum.car.navi.NaviState
import com.ddakmatchum.car.nearbyStores

/*
 * 기본 화면 (2026-11-14).
 *
 * 흐름
 *  - 앱을 열면 근처 드라이브스루 매장이 가까운 순으로 나온다. 매장을 골라 메뉴로 들어가 주문한다.
 *  - 내비에 목적지를 정하면 가는 길의 매장을 주문할지 묻는다. "예"면 주문 화면으로 들어간다.
 *  - 목적지가 드라이브스루 매장이면 그 매장 메뉴가 바로 열린다.
 *  - 결제(차량 간편결제 또는 QR, 둘 다 데모)가 끝나면 매장을 내비 경로에 넣고 내비 화면으로 넘어간다.
 *    지도는 내비가 맡고, 주문 상태는 내비 위의 작은 창(TrackingService)과 이 앱의 주문 화면에서 본다.
 *
 * 생김새 (Pleos Connect UI 디자인 가이드 기준)
 *  - 글꼴: 시스템 글꼴인 Asta Sans
 *  - 글자 높이: 일반 3mm 이상, 핵심 정보 4mm 이상. 12.8인치 2560x1440 화면에서 36sp, 52sp 이상에 해당한다
 *  - 색: 푸른 기 없는 검정, 회색, 흰색. 강조색은 하나만, "지금 진행 중인 것"에만 쓴다
 *  - 화면에는 지금 필요한 것만 둔다. 서비스 설명 문구는 넣지 않는다
 */

private val Ground = Color(0xFFF3F3F3)
private val Panel = Color(0xFFFFFFFF)
private val InkC = Color(0xFF111111)
private val SubC = Color(0xFF666666)
private val RuleC = Color(0xFFDADADA)
// 색은 현대차 캐릭터 르르르의 공식 행사 사진(현대자동차그룹 뉴스룸, 2026-05)에서 뽑았다.
// 가방의 파랑(#3696CA~#47A4D7)과 몸의 크림색(#F2D29C 부근). 여기에 검정과 흰색만 더한다.
private val AccentC = Color(0xFF3F9DD1)
/** 주 버튼 색. 흰 글자는 명암비가 모자라서(약 3:1) 이 위에는 검정 글자를 올린다 (약 7:1) */
private val PrimaryC = Color(0xFF3F9DD1)
private val TintC = Color(0xFFF7E3B5)
private val Edge = RoundedCornerShape(20.dp)

private val Asta = FontFamily(
    Font(R.font.asta_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.asta_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.asta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.asta_sans, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

@Composable
private fun Tx(
    text: String,
    size: TextUnit,
    color: Color = InkC,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = size * 1.3f,
    maxLines: Int = Int.MAX_VALUE,
) = Text(
    text, modifier, color = color, fontSize = size, fontWeight = weight, fontFamily = Asta,
    lineHeight = lineHeight, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
)

@Composable
private fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    height: Dp = 132.dp,
) {
    val bg = if (!enabled) RuleC else if (primary) PrimaryC else Panel
    val fg = if (!enabled) SubC else InkC
    Box(
        modifier.height(height).clip(Edge).background(bg)
            .then(if (!primary && enabled) Modifier.border(2.dp, RuleC, Edge) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center,
    ) { Tx(text, 44.sp, fg, if (primary) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1) }
}

@Composable
fun MainApp(vm: DtViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val navi by vm.navi.collectAsStateWithLifecycle()
    val listening by vm.voice.listening.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Ground)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(ui, vm)
            Box(Modifier.weight(1f).fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 36.dp, bottom = 44.dp)) {
                when (ui.phase) {
                    Phase.HOME -> Home(ui, navi, vm)
                    Phase.SUGGEST -> Ask(ui, listening, vm)
                    Phase.MENU -> MenuScreen(ui, vm)
                    Phase.PAY -> PayScreen(ui, vm)
                    Phase.TRACK -> TrackScreen(ui, navi, vm)
                    Phase.ARRIVED -> ArrivedScreen(ui, vm)
                }
            }
        }
        PopupHost(ui.demoPanel) { DemoPanel(ui, vm) }
        ToastHost(ui.toast, top = 124.dp, fontSize = 30.sp)
    }
}

@Composable
private fun TopBar(ui: UiState, vm: DtViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(102.dp).padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Tx("딱맞춤", 40.sp, weight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            if (!ui.serverOk) {
                Tx("매장 연결 끊김", 36.sp, SubC)
                Spacer(Modifier.width(32.dp))
            }
            // 시연용 조작판. 실제 사용자 화면에는 없는 버튼이다
            Tx("데모", 36.sp, SubC, modifier = Modifier.clickable(onClick = vm::toggleDemoPanel).padding(horizontal = 12.dp, vertical = 16.dp))
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(RuleC))
    }
}

// ---------------- 근처 드라이브스루 ----------------

private fun km(m: Long) = "%.1f".format(m / 1000.0)

/** 목적지가 있을 때: 가는 길인지, 얼마나 돌아가는지 (직선 거리 기준 추정) */
private fun storeNote(n: NearStore, hasDestination: Boolean): String {
    val brand = n.store.brand
    val detour = n.detourM
    if (!hasDestination || detour == null) return brand
    val way = if (detour < 400) "가는 길에 있음" else "약 ${km(detour)}km 돌아감"
    return if (brand.isBlank()) way else "$brand, $way"
}

@Composable
private fun Home(ui: UiState, navi: NaviState, vm: DtViewModel) {
    val near = remember(ui.stores, navi.curLat, navi.curLon, navi.destLat, navi.destLon) { nearbyStores(ui.stores, navi).take(3) }
    val hasDestination = navi.destLat != null
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Tx("근처 드라이브스루", 52.sp, weight = FontWeight.ExtraBold)
            Spacer(Modifier.height(24.dp))
            near.forEach { n ->
                val selected = n.store.id == ui.store.id
                Row(
                    Modifier.fillMaxWidth().height(190.dp).background(if (selected) Panel else Color.Transparent)
                        .clickable { vm.selectStore(n.store) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(8.dp).fillMaxHeight().background(if (selected) AccentC else Color.Transparent))
                    Spacer(Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Tx(n.store.name, 46.sp, weight = FontWeight.Bold, maxLines = 1)
                        val note = storeNote(n, hasDestination)
                        if (note.isNotBlank()) Tx(note, 36.sp, SubC, maxLines = 1)
                    }
                    n.distanceM?.let { d ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            Tx(km(d), 64.sp, weight = FontWeight.ExtraBold, lineHeight = 64.sp)
                            Tx("km", 36.sp, SubC, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
                        }
                    }
                    Spacer(Modifier.width(32.dp))
                }
                if (!selected) Box(Modifier.fillMaxWidth().height(2.dp).background(RuleC))
            }
        }
        Column(Modifier.width(540.dp).fillMaxHeight().background(Panel).padding(40.dp)) {
            val s = ui.suggestion
            Tx(ui.store.name, 44.sp, weight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(36.dp))
            if (s != null) {
                Tx(if (ui.routineDemo) "${s.reason} (데모 기록)" else s.reason, 36.sp, SubC)
                Spacer(Modifier.height(8.dp))
                Tx(itemsText(s.lines, ui.menu), 48.sp, weight = FontWeight.ExtraBold, maxLines = 3)
                Spacer(Modifier.height(8.dp))
                Tx(won(s.lines.sumOf { l -> (ui.menu.firstOrNull { it.id == l.menuId }?.price ?: 0) * l.qty }), 40.sp)
            }
            Spacer(Modifier.weight(1f))
            if (s != null) {
                Btn("이대로 주문", vm::reorder, Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
            }
            Btn("메뉴 보기", vm::openMenu, Modifier.fillMaxWidth(), primary = s == null)
        }
    }
}

// ---------------- 목적지를 정했을 때 묻기 ----------------

@Composable
private fun Ask(ui: UiState, listening: Boolean, vm: DtViewModel) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(48.dp))
        Tx(ui.suggestText, 76.sp, weight = FontWeight.ExtraBold, lineHeight = 100.sp)
        Spacer(Modifier.height(28.dp))
        Tx(if (listening) "듣고 있어요" else "\"응\" 또는 \"아니\"라고 말해도 됩니다", 36.sp, SubC)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Btn("예", { vm.answer(true) }, Modifier.weight(1.4f))
            Btn("아니요", { vm.answer(false) }, Modifier.weight(1f), primary = false)
        }
    }
}

// ---------------- 메뉴 ----------------

@Composable
private fun MenuScreen(ui: UiState, vm: DtViewModel) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Btn("뒤로", vm::back, Modifier.width(180.dp), primary = false, height = 88.dp)
                Spacer(Modifier.width(28.dp))
                Tx(ui.store.name, 52.sp, weight = FontWeight.ExtraBold, maxLines = 1)
            }
            Spacer(Modifier.height(28.dp))
            // 두 줄로 놓는다. 세 줄이면 "아이스 아메리카노"가 낱말 중간에서 끊긴다
            ui.menu.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { m ->
                        val qty = ui.cart.filter { it.menuId == m.id }.sumOf { it.qty }
                        Row(
                            Modifier.weight(1f).height(172.dp).clip(Edge).background(Panel)
                                .then(if (qty > 0) Modifier.border(4.dp, PrimaryC, Edge) else Modifier)
                                .clickable { vm.add(m.id) }.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 메뉴 그림 (ui/Art.kt에서 직접 그린 것)
                            FoodArt(m.id, Modifier.size(132.dp).clip(RoundedCornerShape(24.dp)).background(FoodBg))
                            Spacer(Modifier.width(22.dp))
                            Column(Modifier.weight(1f)) {
                                Tx(m.name, 40.sp, weight = FontWeight.SemiBold, maxLines = 2, lineHeight = 50.sp)
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Tx(won(m.price), 36.sp, SubC)
                                    Spacer(Modifier.weight(1f))
                                    if (qty > 0) Tx("${qty}개", 36.sp, InkC, FontWeight.Bold)
                                }
                            }
                        }
                    }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        Column(Modifier.width(500.dp).fillMaxHeight().background(Panel).padding(36.dp)) {
            Tx("담은 메뉴", 36.sp, SubC)
            Spacer(Modifier.height(16.dp))
            if (ui.cart.isEmpty()) Tx("메뉴를 눌러 담아 주세요", 40.sp, SubC)
            ui.cart.take(4).forEach { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tx(nameOf(line.menuId, ui.menu) + if (line.qty > 1) " ${line.qty}개" else "", 40.sp, modifier = Modifier.weight(1f), maxLines = 2, lineHeight = 48.sp)
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.size(76.dp).clip(Edge).border(2.dp, RuleC, Edge).clickable { vm.remove(line.menuId) },
                        contentAlignment = Alignment.Center,
                    ) { Tx("빼기", 28.sp, SubC) }
                }
            }
            Spacer(Modifier.weight(1f))
            val total = ui.cart.sumOf { l -> (ui.menu.firstOrNull { it.id == l.menuId }?.price ?: 0) * l.qty }
            Tx("합계", 36.sp, SubC)
            Tx(won(total), 60.sp, weight = FontWeight.ExtraBold)
            Spacer(Modifier.height(24.dp))
            Btn("결제", vm::goPay, Modifier.fillMaxWidth(), enabled = ui.cart.isNotEmpty())
        }
    }
}

// ---------------- 결제: 차량 간편결제와 QR 결제를 항상 같이 보여 준다 (둘 다 데모) ----------------

@Composable
private fun PayScreen(ui: UiState, vm: DtViewModel) {
    val total = ui.cart.sumOf { l -> (ui.menu.firstOrNull { it.id == l.menuId }?.price ?: 0) * l.qty }
    val code = ui.payCode
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Tx(ui.store.name, 44.sp, SubC, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            Tx(itemsText(ui.cart, ui.menu), 52.sp, weight = FontWeight.ExtraBold, maxLines = 3, lineHeight = 66.sp)
            Spacer(Modifier.height(36.dp))
            Tx("결제 금액", 36.sp, SubC)
            Tx(won(total), 104.sp, weight = FontWeight.ExtraBold, lineHeight = 116.sp)
            Spacer(Modifier.weight(1f))
            when {
                ui.paying -> Row(Modifier.height(132.dp), verticalAlignment = Alignment.CenterVertically) {
                    LinearSpinner(52.dp, InkC)
                    Spacer(Modifier.width(24.dp))
                    Tx("결제하고 있어요", 44.sp)
                }
                code == null -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Btn("차량 간편결제", vm::pay, Modifier.weight(1f))
                        Btn("QR 결제", vm::payByQr, Modifier.weight(1f), primary = false)
                        Btn("뒤로", vm::back, Modifier.width(200.dp), primary = false)
                    }
                    Spacer(Modifier.height(16.dp))
                    Tx("데모 결제입니다. 실제로 청구되지 않습니다", 36.sp, SubC)
                }
                else -> Btn("QR 결제 취소", vm::cancelQr, Modifier.width(420.dp), primary = false)
            }
        }
        if (code != null) {
            Column(Modifier.width(540.dp).fillMaxHeight().background(Panel).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val url = ui.phoneUrl.trimEnd('/') + "/pay/" + code
                val bitmap = remember(url) { qrBitmap(url, 480).asImageBitmap() }
                Image(bitmap, contentDescription = "결제 QR", modifier = Modifier.size(440.dp))
                Spacer(Modifier.height(28.dp))
                Tx("폰으로 찍어 결제해 주세요", 40.sp, weight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Tx("결제가 끝나면 자동으로 넘어갑니다", 36.sp, SubC)
            }
        }
    }
}

// ---------------- 주문 상태 ----------------

/** 주문 상태: 문장 하나, 남은 시간, 세 점과 그 위를 따라가는 작은 차 (내비 위 작은 창과 같은 그림) */
@Composable
private fun TrackScreen(ui: UiState, navi: NaviState, vm: DtViewModel) {
    val o = ui.order ?: return
    val step = when (o.status) {
        "waiting" -> 0
        "making" -> 1
        else -> 2
    }
    val head = when (step) {
        0 -> "주문이 접수됐어요"
        1 -> "조리 중이에요"
        else -> "조리가 끝났어요"
    }
    val sub = when {
        step == 0 && o.startHint != null -> "${ui.store.name}, ${o.startHint}"
        o.late && step < 2 -> "${ui.store.name}, 조금 늦어질 수 있어요"
        else -> ui.store.name
    }
    val eta = navi.etaSec
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Tx(head, 92.sp, weight = FontWeight.ExtraBold, lineHeight = 108.sp)
                Spacer(Modifier.height(8.dp))
                Tx(sub, 44.sp, SubC, maxLines = 2)
            }
            if (eta != null) {
                Column(
                    Modifier.clip(RoundedCornerShape(32.dp)).background(TintC).padding(horizontal = 44.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Tx("도착까지", 36.sp, InkC, FontWeight.SemiBold)
                    Tx(if (eta >= 60) "${(eta + 30) / 60}분" else "곧 도착", 88.sp, weight = FontWeight.ExtraBold, lineHeight = 100.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        AndroidView(
            factory = { ProgressView(it).apply { labelPx = 38f } },
            update = { it.step = step },
            modifier = Modifier.fillMaxWidth().height(380.dp),
        )
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Tx("주문 ${o.no}번", 40.sp, SubC)
            Spacer(Modifier.width(24.dp))
            Tx(o.summary, 40.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Btn("내비 보기", vm::showNavi, Modifier.width(360.dp), primary = false, height = 112.dp)
        }
    }
}

@Composable
private fun Steps(o: OrderView) {
    val step = when (o.status) {
        "waiting" -> 0
        "making" -> 1
        else -> 2
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("접수 완료", "조리 중", "조리 완료").forEachIndexed { i, label ->
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(10.dp).background(if (i < step) InkC else if (i == step) AccentC else RuleC))
                Spacer(Modifier.height(16.dp))
                Tx(label, 36.sp, if (i == step) InkC else SubC, if (i == step) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun Track(ui: UiState, navi: NaviState, vm: DtViewModel) {
    val o = ui.order ?: return
    val head = when (o.status) {
        "waiting" -> "접수 완료"
        "making" -> "조리 중"
        else -> "조리 완료"
    }
    val sub = when {
        o.status == "waiting" && o.startHint != null -> "${ui.store.name}, ${o.startHint}"
        o.late && o.status != "ready" -> "${ui.store.name}, 조금 늦어질 수 있어요"
        else -> ui.store.name
    }
    val eta = navi.etaSec
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Tx(head, 108.sp, weight = FontWeight.ExtraBold, lineHeight = 120.sp)
                Spacer(Modifier.height(8.dp))
                Tx(sub, 44.sp, SubC, maxLines = 2)
            }
            Column(horizontalAlignment = Alignment.End) {
                Tx("도착까지", 36.sp, SubC)
                Tx(
                    when {
                        eta == null -> "-"
                        eta >= 60 -> "${(eta + 30) / 60}분"
                        else -> "곧 도착"
                    },
                    108.sp, weight = FontWeight.ExtraBold, lineHeight = 120.sp,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Steps(o)
        Spacer(Modifier.height(36.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Tx("주문 ${o.no}번", 40.sp, SubC)
            Spacer(Modifier.width(24.dp))
            Tx(o.summary, 40.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Btn("내비 보기", vm::showNavi, Modifier.width(360.dp), primary = false, height = 112.dp)
        }
    }
}

@Composable
private fun ArrivedScreen(ui: UiState, vm: DtViewModel) {
    val o = ui.order
    // 서버는 도착했는데 아직 준비 중인 주문에 bay 값을 준다. 여기서는 "아직 준비 중" 표시로만 쓴다
    val waiting = o?.stillPreparing == true
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(72.dp)) {
            Column(Modifier.size(400.dp).clip(RoundedCornerShape(40.dp)).background(PrimaryC), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Tx("주문 번호", 36.sp, InkC)
                Tx("${o?.no ?: ""}", 220.sp, InkC, FontWeight.ExtraBold, lineHeight = 230.sp)
            }
            Column {
                Tx(if (waiting) "거의 다 됐어요" else "준비됐어요", 108.sp, weight = FontWeight.ExtraBold, lineHeight = 120.sp)
                Spacer(Modifier.height(12.dp))
                Tx(if (waiting) "잠시만 기다려 주세요" else "창구에서 받아 주세요", 48.sp)
                Spacer(Modifier.height(8.dp))
                Tx(o?.summary ?: "", 40.sp, SubC, maxLines = 2)
            }
        }
        Spacer(Modifier.weight(1f))
        Btn("완료", vm::finish, Modifier.width(420.dp))
    }
}
