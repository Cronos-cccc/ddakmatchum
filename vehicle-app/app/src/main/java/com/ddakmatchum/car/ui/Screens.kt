package com.ddakmatchum.car.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ddakmatchum.car.CartLine
import com.ddakmatchum.car.DtViewModel
import com.ddakmatchum.car.MenuItem
import com.ddakmatchum.car.Phase
import com.ddakmatchum.car.UiState
import com.ddakmatchum.car.car.CarState
import com.ddakmatchum.car.navi.NaviState

val Bg = Color(0xFF0E1116)
val PanelC = Color(0xFF161B22)
val LineC = Color(0xFF262D37)
val TextC = Color(0xFFE8EDF3)
val Mute = Color(0xFF8B97A6)
val Accent = Color(0xFFFF8A1F)
val Blue = Color(0xFF3B8CFF)
val Green = Color(0xFF22C55E)
val Red = Color(0xFFF43F5E)
val Yellow = Color(0xFFEAB308)

// ---------------- 공통 ----------------
@Composable
fun T(text: String, size: TextUnit = 22.sp, color: Color = TextC, bold: Boolean = false, align: TextAlign? = null, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, fontSize = size, color = color, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal, textAlign = align, lineHeight = size * 1.25f)

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(
        modifier
            .background(PanelC, RoundedCornerShape(28.dp))
            .border(1.dp, LineC, RoundedCornerShape(28.dp))
            .padding(32.dp),
        content = content,
    )

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Accent, textColor: Color = Color(0xFF111111), enabled: Boolean = true, size: TextUnit = 26.sp) =
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = textColor, disabledContainerColor = LineC, disabledContentColor = Mute),
        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 22.dp),
    ) { Text(text, fontSize = size, fontWeight = FontWeight.Bold) }

@Composable
fun SmallButton(text: String, onClick: () -> Unit, color: Color = Color(0xFF1F2630)) =
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = TextC),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }

@Composable
fun Chip(text: String, color: Color = Mute) =
    Box(
        Modifier
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(99.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) { Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }

fun won(v: Int) = "%,d원".format(v)
fun mmss(sec: Long) = if (sec >= 60) "${sec / 60}분 ${sec % 60}초" else "${sec}초"
fun price(lines: List<CartLine>, menu: List<MenuItem>) = lines.sumOf { l -> (menu.firstOrNull { it.id == l.menuId }?.price ?: 0) * l.qty }
fun nameOf(id: String, menu: List<MenuItem>) = menu.firstOrNull { it.id == id }?.name ?: id

// ---------------- 앱 ----------------
@Composable
fun App(vm: DtViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val car by vm.carState.collectAsStateWithLifecycle()
    val navi by vm.navi.collectAsStateWithLifecycle()
    val voiceStatus by vm.voice.status.collectAsStateWithLifecycle()
    val listening by vm.voice.listening.collectAsStateWithLifecycle()
    val heard by vm.voice.heard.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().padding(28.dp)) {
            TopBar(ui, car, navi, voiceStatus, vm)
            Spacer(Modifier.height(24.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (ui.phase) {
                    Phase.HOME -> Home(ui, car, navi, vm)
                    Phase.SUGGEST -> Suggest(ui, listening, heard, vm)
                    Phase.MENU -> if (car.canBrowse) Menu(ui, vm) else Locked(vm)
                    Phase.PAY -> if (car.canBrowse) Pay(ui, vm) else Locked(vm)
                    Phase.TRACK -> Track(ui, navi)
                    Phase.ARRIVED -> Arrived(ui, vm)
                }
            }
        }
        PopupHost(ui.demoPanel) { DemoPanel(ui, vm) }
        ToastHost(ui.toast, top = 96.dp, fontSize = 20.sp)
    }
}

@Composable
fun TopBar(ui: UiState, car: CarState, navi: NaviState, voiceStatus: String, vm: DtViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            T("딱", 34.sp, Accent, bold = true)
            T("맞춤", 34.sp, bold = true)
        }
        T("도착하는 순간, 딱 나온다", 18.sp, Mute)
        Spacer(Modifier.weight(1f))
        Chip(if (car.parked) "P 정차" else "D 주행 ${car.speedKph.toInt()}km/h", if (car.parked) Green else Yellow)
        Chip(navi.source, if (navi.connected) Blue else Mute)
        Chip(voiceStatus)
        Chip(if (ui.serverOk) "매장 연결됨" else "매장 서버 끊김", if (ui.serverOk) Green else Red)
        SmallButton(if (ui.demoPanel) "데모 닫기" else "데모", vm::toggleDemoPanel)
    }
}

// ---------------- 홈 ----------------
@Composable
fun Home(ui: UiState, car: CarState, navi: NaviState, vm: DtViewModel) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Panel(Modifier.weight(1.5f).fillMaxHeight()) {
            T("경로 위 드라이브스루", 20.sp, Mute)
            Spacer(Modifier.height(8.dp))
            T(ui.store.name, 44.sp, bold = true)
            Spacer(Modifier.height(10.dp))
            val eta = navi.etaSec
            if (navi.tripActive && eta != null) T("도착 ${mmss(eta)}, ${navi.distanceM?.let { "%.1fkm".format(it / 1000.0) } ?: ""}", 26.sp, Accent, bold = true)
            else T("목적지를 정하면 경로 위 DT 매장을 찾아요", 24.sp, Mute)
            Spacer(Modifier.weight(1f))
            ui.suggestion?.let { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip(s.reason, Accent)
                    if (ui.routineDemo) Chip("데모 기록")
                }
                Spacer(Modifier.height(12.dp))
                T("🗣  \"${s.spoken}\"", 34.sp, bold = true)
                Spacer(Modifier.height(24.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BigButton("말로 주문하기", vm::startSuggestion)
                BigButton("메뉴 보기", vm::openMenu, color = Color(0xFF2B3542), textColor = TextC, enabled = car.canBrowse)
                if (!car.canBrowse) T("메뉴판은 정차(P) 중에만", 20.sp, Mute)
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            T("딱맞춤은 이렇게 달라요", 22.sp, Mute)
            Spacer(Modifier.height(20.dp))
            Step("1", "차가 먼저 제안해요", "앱을 열 필요 없이 \"응\" 한마디면 주문, 결제, 경유지 추가까지")
            Step("2", "도착 순서대로 만들어요", "내비의 남은 시간으로 매장이 주문순이 아닌 도착순으로 제조")
            Step("3", "도착하는 순간 딱 완성", "늦으면 옆 대기칸으로 안내해서 뒤차를 막지 않아요")
        }
    }
}

@Composable
fun Step(n: String, title: String, body: String) {
    Row(Modifier.padding(bottom = 22.dp)) {
        Box(Modifier.size(44.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) { T(n, 22.sp, Color(0xFF111111), bold = true) }
        Spacer(Modifier.width(16.dp))
        Column {
            T(title, 26.sp, bold = true)
            T(body, 19.sp, Mute)
        }
    }
}

// ---------------- 음성 제안 ----------------
@Composable
fun Suggest(ui: UiState, listening: Boolean, heard: String, vm: DtViewModel) {
    val pulse = rememberInfiniteTransition(label = "mic")
    val s by pulse.animateFloat(1f, 1.18f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "scale")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(150.dp).scale(if (listening) s else 1f).background(if (listening) Accent else LineC, CircleShape),
            contentAlignment = Alignment.Center,
        ) { T("🎙", 64.sp) }
        Spacer(Modifier.height(36.dp))
        T(ui.suggestText, 44.sp, bold = true, align = TextAlign.Center, modifier = Modifier.width(1200.dp))
        Spacer(Modifier.height(16.dp))
        T(
            when {
                ui.paying -> "등록된 CarPay 카드로 결제 중…"
                listening -> "듣고 있어요… ${if (heard.isNotBlank()) "\"$heard\"" else ""}"
                heard.isNotBlank() -> "\"$heard\""
                else -> "\"응\" 또는 \"아니\"라고 말하거나 버튼을 누르세요"
            },
            24.sp, Mute,
        )
        Spacer(Modifier.height(40.dp))
        if (ui.paying) {
            LinearSpinner(64.dp, Accent)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                BigButton("네, 주문", { vm.answer(true) }, Modifier.width(320.dp), size = 34.sp)
                BigButton("아니요", { vm.answer(false) }, Modifier.width(320.dp), color = Color(0xFF2B3542), textColor = TextC, size = 34.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        T("주행 중에는 말로만, 승인하면 등록된 CarPay 카드로 결제돼요 (데모)", 18.sp, Mute)
    }
}

// ---------------- 주행 중 잠금 ----------------
@Composable
fun Locked(vm: DtViewModel) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        T("🚗", 90.sp)
        Spacer(Modifier.height(16.dp))
        T("주행 중에는 메뉴판을 숨겨요", 44.sp, bold = true)
        Spacer(Modifier.height(12.dp))
        T("안전을 위해 정차(P) 중에만 고를 수 있어요. 평소 메뉴는 말로 주문하세요.", 24.sp, Mute)
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BigButton("말로 주문하기", vm::startSuggestion)
            BigButton("돌아가기", vm::back, color = Color(0xFF2B3542), textColor = TextC)
        }
    }
}

// ---------------- 메뉴 (정차 중) ----------------
@Composable
fun Menu(ui: UiState, vm: DtViewModel) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.7f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallButton("← 뒤로", vm::back)
                Spacer(Modifier.width(16.dp))
                T(ui.store.name, 28.sp, bold = true)
            }
            Spacer(Modifier.height(18.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(ui.menu, key = { it.id }) { m -> MenuCard(m, ui.cart.filter { it.menuId == m.id && it.who == null }.sumOf { it.qty }) { vm.add(m.id) } }
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            T("장바구니", 26.sp, bold = true)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                ui.cart.forEach { l ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("${nameOf(l.menuId, ui.menu)} ×${l.qty}", 22.sp, modifier = Modifier.weight(1f))
                        SmallButton("−", { vm.remove(l.menuId) })
                        Spacer(Modifier.width(8.dp))
                        SmallButton("+", { vm.add(l.menuId) })
                    }
                }
                ui.groupLines.forEach { l -> T("👤 ${l.who}, ${nameOf(l.menuId, ui.menu)}", 22.sp, Yellow, modifier = Modifier.padding(vertical = 6.dp)) }
                if (ui.cart.isEmpty() && ui.groupLines.isEmpty()) T("왼쪽에서 메뉴를 눌러 담아요", 20.sp, Mute)
            }
            ui.groupCode?.let { code ->
                val url = "${ui.phoneUrl.trimEnd('/')}/join/$code"
                val bmp = remember(url) { qrBitmap(url) }
                Row(Modifier.fillMaxWidth().background(Color(0xFF11161D), RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(bmp.asImageBitmap(), contentDescription = "동승자 주문 QR", modifier = Modifier.size(120.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        T("동승자도 같이 담기", 22.sp, bold = true)
                        T("폰으로 QR을 찍으면 각자 고른 메뉴가 여기 담겨요, 코드 $code", 17.sp, Mute)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            val total = price(ui.cart + ui.groupLines, ui.menu)
            BigButton("CarPay로 결제, ${won(total)}", vm::goPay, Modifier.fillMaxWidth(), enabled = total > 0)
        }
    }
}

@Composable
fun MenuCard(m: MenuItem, qty: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .background(PanelC, RoundedCornerShape(22.dp))
            .border(if (qty > 0) 2.dp else 1.dp, if (qty > 0) Accent else LineC, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(m.emoji, 44.sp)
            Spacer(Modifier.weight(1f))
            if (qty > 0) Box(Modifier.size(38.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) { T("$qty", 20.sp, Color(0xFF111111), bold = true) }
        }
        Spacer(Modifier.height(10.dp))
        T(m.name, 22.sp, bold = true)
        T(won(m.price), 19.sp, Mute)
        // 제조 예상 시간은 손님에게 보여 주지 않는다 (실제 메뉴판에 없는 정보). 매장 화면과 도착순 계산에서만 쓴다.
    }
}

// ---------------- CarPay 결제 (데모) ----------------
@Composable
fun Pay(ui: UiState, vm: DtViewModel) {
    val lines = ui.cart + ui.groupLines
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Panel(Modifier.width(820.dp)) {
            T("CarPay", 30.sp, Accent, bold = true)
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.fillMaxWidth().height(170.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF2A3342), Color(0xFF151A22))), RoundedCornerShape(22.dp))
                    .padding(26.dp),
            ) {
                Column {
                    T("등록된 카드", 18.sp, Mute)
                    T("데모카드  ••••  1234", 32.sp, bold = true)
                }
                T("차량 내 간편결제", 18.sp, Mute, modifier = Modifier.align(Alignment.BottomEnd))
            }
            Spacer(Modifier.height(20.dp))
            lines.groupBy { it.menuId }.forEach { (id, ls) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    T("${nameOf(id, ui.menu)} ×${ls.sumOf { it.qty }}", 22.sp, modifier = Modifier.weight(1f))
                    T(won(price(ls, ui.menu)), 22.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                T("합계", 28.sp, bold = true, modifier = Modifier.weight(1f))
                T(won(price(lines, ui.menu)), 32.sp, Accent, bold = true)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ui.paying) {
                    LinearSpinner(40.dp, Accent, stroke = 4.dp)
                    T("결제 중…", 24.sp)
                } else {
                    BigButton("결제하기", vm::pay, Modifier.weight(1f))
                    BigButton("뒤로", vm::back, color = Color(0xFF2B3542), textColor = TextC)
                }
            }
            Spacer(Modifier.height(14.dp))
            T("데모 결제, 실제로 청구되지 않아요. 실서비스는 현대차그룹 카페이 연동을 전제로 해요.", 16.sp, Mute)
        }
    }
}

// ---------------- 주행 중 추적 (큰 카드만) ----------------
@Composable
fun Track(ui: UiState, navi: NaviState) {
    val o = ui.order ?: return
    val step = when (o.status) {
        "waiting" -> 1
        "making" -> 2
        else -> 3
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Panel(Modifier.weight(1.4f).fillMaxHeight()) {
            T("주문 #${o.no}, ${ui.store.shortName}", 26.sp, Mute)
            Spacer(Modifier.height(18.dp))
            T("도착까지", 24.sp, Mute)
            T(navi.etaSec?.let { mmss(it) } ?: "-", 88.sp, bold = true)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("주문", "도착 맞춰 대기", "제조 중", "완성").forEachIndexed { i, label ->
                    val on = i <= step
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().height(12.dp).background(if (on) (if (i == 3) Green else Accent) else LineC, RoundedCornerShape(99.dp)))
                        Spacer(Modifier.height(10.dp))
                        T(label, 22.sp, if (i == step) TextC else Mute, bold = i == step)
                    }
                }
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            T("매장 상황", 22.sp, Mute)
            Spacer(Modifier.height(14.dp))
            if (o.inLane) T("차선 진입, 순서 확정", 30.sp, Yellow, bold = true)
            else o.rank?.let { T("제조 순서 ${it}번째", 40.sp, bold = true) }
            Spacer(Modifier.height(10.dp))
            o.actionLabel?.let { T(it, 26.sp, if (o.actionKind == "making" || o.actionKind == "start") Accent else TextC) }
            Spacer(Modifier.height(10.dp))
            val margin: Long? = o.readyInSec?.let { r -> (navi.etaSec ?: 0L) - r }
            if (o.late) T("⚠ 조금 늦을 수 있어요 → 옆 대기칸 안내", 22.sp, Red)
            else if (margin != null && o.status != "ready") T("도착 ${mmss(margin.coerceAtLeast(0L))} 전 완성 예정", 22.sp, Green)
            Spacer(Modifier.weight(1f))
            T("정체가 생기면 매장 순서가 자동으로 바뀌어요", 18.sp, Mute)
            T(o.summary, 20.sp)
        }
    }
}

// ---------------- 도착 ----------------
@Composable
fun Arrived(ui: UiState, vm: DtViewModel) {
    val o = ui.order
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (o?.bay == null) {
            T("✅", 110.sp)
            T("딱 맞춰 완성!", 64.sp, Green, bold = true)
            Spacer(Modifier.height(10.dp))
            T("창구에서 바로 받으세요, 대기 0초", 28.sp, Mute)
        } else {
            T("⏸", 110.sp)
            T("대기칸 ${o.bay}번으로 이동해 주세요", 56.sp, Yellow, bold = true)
            Spacer(Modifier.height(10.dp))
            T("뒤차가 기다리지 않게 옆으로 안내해요, 완성되면 가져다 드려요", 26.sp, Mute)
        }
        Spacer(Modifier.height(40.dp))
        BigButton("완료", vm::finish, Modifier.width(320.dp))
    }
}

// ---------------- 데모 패널 (시연 영상용) ----------------
@Composable
fun BoxScope.DemoPanel(ui: UiState, vm: DtViewModel) {
    var server by remember { mutableStateOf(ui.serverUrl) }
    var phone by remember { mutableStateOf(ui.phoneUrl) }
    Column(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(28.dp)
            .width(620.dp)
            .background(Color(0xF2161B22), RoundedCornerShape(24.dp))
            .border(1.dp, Accent, RoundedCornerShape(24.dp))
            .padding(22.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        T("데모 패널, 에뮬레이터 시연용", 20.sp, Accent, bold = true)
        T("화면 시안", 16.sp, Mute)
        DraftSwitch.short.withIndex().chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (i, n) ->
                    SmallButton(n, { DraftSwitch.current = i }, if (DraftSwitch.current == i) Accent.copy(alpha = 0.35f) else Color(0xFF1F2630))
                }
            }
        }
        T("차량", 16.sp, Mute)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("P 정차", vm::demoPark)
            SmallButton("D 주행", vm::demoDrive)
            SmallButton("실제 차량 신호", vm::demoRealCar)
        }
        T("주행 (데모 내비 ${if (ui.demoNavi) "켜짐" else "꺼짐"})", 16.sp, Mute)
        // release 빌드에서도 손으로 "목적지를 정한 상황"을 만들 수 있게 한다 (PC 명령은 debug에서만 된다)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("목적지 정함 (판교역)", vm::demoDestination, Accent.copy(alpha = 0.35f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("출발 (6분)", vm::demoStartTrip, Accent.copy(alpha = 0.35f))
            SmallButton("정체 +3분", vm::demoJam)
            SmallButton("매장 앞", vm::demoNear)
            SmallButton("도착", vm::demoArrive)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("차가 먼저 제안", vm::startSuggestion)
            SmallButton(if (ui.demoNavi) "실제 내비 쓰기" else "데모 내비 쓰기", { vm.setDemoNavi(!ui.demoNavi) })
            SmallButton("기록 초기화", vm::resetRoutine)
        }
        T("서버 주소 (에뮬레이터 → PC)", 16.sp, Mute)
        OutlinedTextField(server, { server = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        T("QR 주소 (폰 → PC, 같은 와이파이의 PC IP)", 16.sp, Mute)
        OutlinedTextField(phone, { phone = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SmallButton("저장, 연결 확인", { vm.setServer(server, phone) }, Accent.copy(alpha = 0.35f))
    }
}
