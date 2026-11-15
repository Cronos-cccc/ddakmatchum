package com.ddakmatchum.car.ui

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ddakmatchum.car.CartLine
import com.ddakmatchum.car.DtViewModel
import com.ddakmatchum.car.MenuItem
import com.ddakmatchum.car.OrderView
import com.ddakmatchum.car.Phase
import com.ddakmatchum.car.UiState
import com.ddakmatchum.car.car.CarState
import com.ddakmatchum.car.navi.NaviState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/*
 * 화면 시안 (2026-11-12). 기존 화면(Screens.kt)은 그대로 두고, 같은 상태(DtViewModel)를 다르게 보여 주는 시안 3개.
 *  A 한 장 카드: 상태마다 숫자 하나와 문장 하나. 운전 중 한눈에 보는 것만 남김.
 *  B 맞춤 타임라인: 내 차 도착과 음료 완성을 같은 시간축에 놓아 "딱 맞춤"을 그림으로 보여 줌.
 *  C 도착 순서: 매장 제조 순서(도착 예정순)와 그 안의 내 자리를 보여 줌.
 * 데모 패널의 "화면 시안" 줄이나 adb(--ei design 0~4, debug 빌드에서만)로 바꾼다. 메뉴판과 결제(정차 중 화면)는 기존 화면을 그대로 쓴다.
 *
 * 지금 앱의 기본 화면은 이 시안을 섞은 것이다: 홈, 제안, 도착은 A, 주문 추적은 B (DraftSwitch.MIXED).
 * 처음 만든 화면(Screens.kt의 App)은 데모 패널에서 "기존"을 누르면 볼 수 있다.
 */
object DraftSwitch {
    /** 기본 화면: 홈, 제안, 도착은 A(한 장 카드), 주문 추적은 B(맞춤 타임라인) */
    const val MIXED = 4

    /** 0 = 처음 만든 화면(Screens.kt), 1~3 = 비교용 시안, 4 = 기본. 앱을 켜면 기본으로 시작한다 */
    var current by mutableIntStateOf(MIXED)
    val names = listOf("기존 화면", "A 한 장 카드", "B 맞춤 타임라인", "C 도착 순서", "기본")
    val short = listOf("기존", "A 카드", "B 타임라인", "C 순서", "기본")
}

private val DBg = Color(0xFF0A0D12)
private val DSurf = Color(0xFF131820)
private val DSurf2 = Color(0xFF1C232D)
private val DLine = Color(0xFF2A333F)
private val DGreen = Color(0xFF2BD46E)
private val DAmber = Color(0xFFFFC233)
private val Ink = Color(0xFF111111)
private val Card = RoundedCornerShape(32.dp)

// ---------------- 공통 ----------------

/** 운전 중 한눈에: 1분 이상은 분만, 1분 미만은 초 */
private fun glance(sec: Long): Pair<String, String> =
    if (sec >= 60) "${(sec + 30) / 60}" to "분" else "${sec.coerceAtLeast(0)}" to "초"

private fun clockText(sec: Long) = glance(sec).let { it.first + it.second }

internal fun itemsText(lines: List<CartLine>, menu: List<MenuItem>) =
    lines.groupBy { it.menuId }.entries.joinToString(", ") { (id, ls) ->
        val q = ls.sumOf { it.qty }
        nameOf(id, menu) + if (q > 1) " ${q}개" else ""
    }

private fun estPrep(lines: List<CartLine>, menu: List<MenuItem>): Int =
    lines.sumOf { l -> (menu.firstOrNull { it.id == l.menuId }?.prepSec ?: 60) * l.qty }.coerceIn(15, 600)

@Composable
private fun Glyph(w: Dp, h: Dp = w, draw: DrawScope.() -> Unit) = Canvas(Modifier.size(w, h)) { draw() }

@Composable
private fun Pill(text: String, color: Color, big: Boolean = false) =
    Box(
        Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(99.dp))
            .padding(horizontal = if (big) 18.dp else 14.dp, vertical = if (big) 8.dp else 6.dp),
    ) { Text(text, color = color, fontSize = if (big) 21.sp else 17.sp, fontWeight = FontWeight.Bold) }

@Composable
private fun DButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    size: TextUnit = 32.sp,
    icon: (DrawScope.(Color) -> Unit)? = null,
) {
    val bg = if (!enabled) DSurf2 else if (primary) Accent else DSurf2
    val fg = if (!enabled) Mute else if (primary) Ink else TextC
    Row(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Canvas(Modifier.size(38.dp)) { icon(fg) }
            Spacer(Modifier.width(14.dp))
        }
        Text(text, color = fg, fontSize = size, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BigClock(sec: Long?, size: TextUnit, color: Color) {
    if (sec == null) {
        Text("-", color = Mute, fontSize = size, fontWeight = FontWeight.ExtraBold, lineHeight = size)
        return
    }
    val (n, u) = glance(sec)
    Text(
        buildAnnotatedString {
            append(n)
            withStyle(SpanStyle(fontSize = size * 0.38f, color = Mute)) { append(" $u") }
        },
        color = color, fontSize = size, fontWeight = FontWeight.ExtraBold, lineHeight = size,
    )
}

@Composable
private fun ListenDot(listening: Boolean, d: Dp = 44.dp) {
    val t = rememberInfiniteTransition(label = "listen")
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Canvas(Modifier.size(d)) {
        if (listening) drawCircle(Accent.copy(alpha = 0.3f * a), radius = size.minDimension / 2)
        drawCircle(if (listening) Accent else DLine, radius = size.minDimension * 0.28f)
    }
}

internal fun voiceLine(ui: UiState, listening: Boolean, heard: String) = when {
    ui.paying -> "등록된 카드로 결제하고 있어요"
    listening -> if (heard.isNotBlank()) "듣고 있어요, \"$heard\"" else "듣고 있어요"
    heard.isNotBlank() -> "\"$heard\""
    else -> "\"응\" 또는 \"아니\"라고 답해 주세요"
}

@Composable
private fun QuestionText(ui: UiState, size: TextUnit, modifier: Modifier = Modifier) {
    val q = ui.suggestText
    val head = ui.suggestion?.spoken
    Text(
        buildAnnotatedString {
            // 쉼표 뒤에서 줄을 바꿔 "판교DT점에 / 서"처럼 단어 중간에서 끊기지 않게 한다
            if (head != null && q.startsWith(head)) {
                withStyle(SpanStyle(color = Accent)) { append(head) }
                append(q.removePrefix(head).replaceFirst(", ", ",\n"))
            } else append(q.replaceFirst(", ", ",\n"))
        },
        modifier, color = TextC, fontSize = size, fontWeight = FontWeight.ExtraBold, lineHeight = size * 1.22f,
    )
}

@Composable
private fun AnswerButtons(ui: UiState, vm: DtViewModel, height: Dp, size: TextUnit) {
    if (ui.paying) {
        Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
            LinearSpinner(56.dp, Accent)
            Spacer(Modifier.width(20.dp))
            Text("등록된 카드로 결제하고 있어요", color = TextC, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            DButton("네, 주문", { vm.answer(true) }, Modifier.weight(1f).fillMaxHeight(), size = size)
            DButton("아니요", { vm.answer(false) }, Modifier.weight(1f).fillMaxHeight(), primary = false, size = size)
        }
    }
}

/** 서버가 준 완성까지 남은 시간을 화면에서 1초 단위로 줄인다 (서버 갱신은 3초마다) */
@Composable
internal fun liveReadyIn(o: OrderView): Long? {
    val got = remember(o) { SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            now = SystemClock.elapsedRealtime()
        }
    }
    return o.readyInSec?.let { it - (now - got) / 1000 }
}

private data class TrackSt(val headline: String, val sub: String, val color: Color)

private fun trackState(o: OrderView, eta: Long?, readyIn: Long?): TrackSt {
    val margin = if (eta != null && readyIn != null) eta - readyIn else null
    val startIn = readyIn?.let { it - o.prepSec }
    return when {
        o.status == "ready" -> TrackSt("완성됐어요", "도착하면 창구에서 바로 받아요", DGreen)
        o.late -> TrackSt("조금 늦을 수 있어요", "주문이 몰려 도착보다 늦게 준비돼요", DAmber)
        o.status == "making" -> TrackSt(
            "만들고 있어요",
            margin?.let { "도착 ${clockText(it.coerceAtLeast(0))} 전에 완성돼요" } ?: "도착에 맞춰 만드는 중이에요",
            Accent,
        )
        else -> TrackSt(
            margin?.let { "도착 ${clockText(it.coerceAtLeast(0))} 전 완성" } ?: "도착에 맞춰 만들어요",
            startIn?.let { if (it > 0) "${clockText(it)} 뒤 만들기 시작해요" else "곧 만들기 시작해요" } ?: "도착 시간에 맞춰 순서를 잡았어요",
            DGreen,
        )
    }
}

@Composable
private fun StepBar(o: OrderView, size: TextUnit) {
    val step = when (o.status) {
        "waiting" -> 1
        "making" -> 2
        else -> 3
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("주문", "도착 맞춰 대기", "제조 중", "완성").forEachIndexed { i, label ->
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(14.dp).background(if (i <= step) (if (i == 3) DGreen else Accent) else DLine, RoundedCornerShape(99.dp)))
                Spacer(Modifier.height(12.dp))
                Text(label, color = if (i == step) TextC else Mute, fontSize = size, fontWeight = if (i == step) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

// ---------------- 아이콘 (이모지 대신 직접 그림) ----------------

internal fun DrawScope.micGlyph(c: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(c, Offset(w * 0.34f, h * 0.04f), Size(w * 0.32f, h * 0.54f), CornerRadius(w * 0.16f))
    drawArc(c, 0f, 180f, false, Offset(w * 0.18f, h * 0.2f), Size(w * 0.64f, h * 0.54f), style = Stroke(w * 0.08f, cap = StrokeCap.Round))
    drawLine(c, Offset(w * 0.5f, h * 0.76f), Offset(w * 0.5f, h * 0.94f), w * 0.08f, StrokeCap.Round)
}

internal fun DrawScope.cupGlyph(c: Color) {
    val w = size.width
    val h = size.height
    val body = Path().apply {
        moveTo(w * 0.22f, h * 0.32f); lineTo(w * 0.78f, h * 0.32f); lineTo(w * 0.68f, h * 0.96f); lineTo(w * 0.32f, h * 0.96f); close()
    }
    drawPath(body, c)
    drawRoundRect(c, Offset(w * 0.15f, h * 0.2f), Size(w * 0.7f, h * 0.1f), CornerRadius(h * 0.05f))
    drawLine(c, Offset(w * 0.56f, h * 0.2f), Offset(w * 0.68f, h * 0.02f), w * 0.07f, StrokeCap.Round)
}

private fun DrawScope.carGlyph(c: Color, bg: Color) {
    val w = size.width
    val h = size.height
    val cabin = Path().apply {
        moveTo(w * 0.24f, h * 0.48f); lineTo(w * 0.36f, h * 0.2f); lineTo(w * 0.68f, h * 0.2f); lineTo(w * 0.82f, h * 0.48f); close()
    }
    drawPath(cabin, c)
    drawRoundRect(c, Offset(w * 0.03f, h * 0.44f), Size(w * 0.94f, h * 0.32f), CornerRadius(h * 0.12f))
    listOf(0.25f, 0.75f).forEach { x ->
        drawCircle(bg, h * 0.17f, Offset(w * x, h * 0.78f))
        drawCircle(c, h * 0.12f, Offset(w * x, h * 0.78f))
    }
}

/** 위에서 본 차 (C 도착 화면) */
private fun DrawScope.carTop(c: Color, center: Offset, len: Float) {
    val wid = len * 0.5f
    drawRoundRect(c, Offset(center.x - len / 2, center.y - wid / 2), Size(len, wid), CornerRadius(wid * 0.35f))
    drawRoundRect(Ink.copy(alpha = 0.55f), Offset(center.x + len * 0.08f, center.y - wid * 0.36f), Size(len * 0.2f, wid * 0.72f), CornerRadius(wid * 0.12f))
}

private fun DrawScope.pinGlyph(c: Color, inner: Color) {
    val w = size.width
    val h = size.height
    val p = Path().apply { moveTo(w * 0.5f, h * 0.98f); lineTo(w * 0.2f, h * 0.5f); lineTo(w * 0.8f, h * 0.5f); close() }
    drawPath(p, c)
    drawCircle(c, w * 0.32f, Offset(w * 0.5f, h * 0.38f))
    drawCircle(inner, w * 0.13f, Offset(w * 0.5f, h * 0.38f))
}

internal fun DrawScope.checkGlyph(c: Color) {
    val w = size.width
    val h = size.height
    val p = Path().apply { moveTo(w * 0.18f, h * 0.52f); lineTo(w * 0.42f, h * 0.76f); lineTo(w * 0.84f, h * 0.28f) }
    drawPath(p, c, style = Stroke(w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

internal fun DrawScope.label(m: TextMeasurer, text: String, x: Float, y: Float, color: Color, size: TextUnit, bold: Boolean = false, center: Boolean = true) {
    val r = m.measure(text, TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    drawText(r, topLeft = Offset(if (center) x - r.size.width / 2f else x, y - r.size.height / 2f))
}

// ---------------- 시안 공통 틀 ----------------

@Composable
fun DraftApp(vm: DtViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val car by vm.carState.collectAsStateWithLifecycle()
    val navi by vm.navi.collectAsStateWithLifecycle()
    val listening by vm.voice.listening.collectAsStateWithLifecycle()
    val heard by vm.voice.heard.collectAsStateWithLifecycle()
    val d = DraftSwitch.current
    val queue = rememberQueue(ui.serverUrl, d == 3 && ui.serverOk)

    Box(Modifier.fillMaxSize().background(DBg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 22.dp)) {
            DHeader(ui, car, vm)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (ui.phase) {
                    Phase.HOME -> when (d) {
                        DraftSwitch.MIXED -> MHome(ui, car, navi, vm)
                        1 -> AHome(ui, car, navi, vm)
                        2 -> BHome(ui, car, navi, vm)
                        else -> CHome(ui, car, navi, vm, queue)
                    }
                    Phase.SUGGEST -> when (d) {
                        DraftSwitch.MIXED -> MSuggest(ui, listening, heard, vm)
                        1 -> ASuggest(ui, listening, heard, vm)
                        2 -> BSuggest(ui, navi, listening, heard, vm)
                        else -> CSuggest(ui, navi, listening, heard, vm, queue)
                    }
                    Phase.MENU -> if (car.canBrowse) Menu(ui, vm) else Locked(vm)
                    Phase.PAY -> if (car.canBrowse) Pay(ui, vm) else Locked(vm)
                    Phase.TRACK -> when (d) {
                        DraftSwitch.MIXED -> MTrack(ui, navi)
                        1 -> ATrack(ui, navi)
                        2 -> BTrack(ui, navi)
                        else -> CTrack(ui, navi, queue)
                    }
                    Phase.ARRIVED -> when (d) {
                        DraftSwitch.MIXED -> MArrived(ui, vm)
                        1 -> AArrived(ui, vm)
                        2 -> BArrived(ui, vm)
                        else -> CArrived(ui, vm)
                    }
                }
            }
        }
        PopupHost(ui.demoPanel) { DemoPanel(ui, vm) }
        ToastHost(ui.toast, top = 90.dp, fontSize = 22.sp)
    }
}

/** 운전자에게 필요한 상태만 위에 둔다. TTS, 내비 연결 같은 개발용 표시는 데모 패널로 */
@Composable
private fun DHeader(ui: UiState, car: CarState, vm: DtViewModel) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Accent)) { append("딱") }
                append("맞춤")
            },
            color = TextC, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.weight(1f))
        if (DraftSwitch.current == DraftSwitch.MIXED) {
            // 기본 화면: 기어 상태는 차량 화면에 이미 보이므로 적지 않는다
            if (!ui.serverOk) Text("매장 연결 끊김", color = Red, fontSize = 24.sp)
        } else {
            if (!ui.serverOk) Pill("매장 연결 끊김", Red, big = true)
            Pill(if (car.parked) "P 정차 중" else "주행 중", if (car.parked) DGreen else DAmber, big = true)
        }
        // 비교용 시안을 보고 있을 때만 이름표를 단다. 기본 화면에서는 숨긴다.
        if (DraftSwitch.current != DraftSwitch.MIXED) Pill("시안 ${DraftSwitch.names[DraftSwitch.current]}", Mute)
        SmallButton(if (ui.demoPanel) "데모 닫기" else "데모", vm::toggleDemoPanel)
    }
}

// ================= 기본 화면 =================
// 화면에는 운전자가 지금 알아야 할 것만 둔다: 무엇을, 어디서, 언제.
// 서비스가 어떻게 동작하는지 설명하는 문구, 알약 모양 표시, 카드 상자는 쓰지 않는다.
// 크기는 세 가지(큰 제목, 본문, 보조)만 쓰고 강조색은 주 버튼 하나에만 쓴다.

private val MBtn = RoundedCornerShape(14.dp)
private val MPad = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)

@Composable
private fun MButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true) {
    val bg = if (!enabled) DSurf else if (primary) Accent else DSurf2
    val fg = if (!enabled) Mute else if (primary) Ink else TextC
    Box(
        modifier.height(116.dp).clip(MBtn).background(bg).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 36.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = 34.sp, fontWeight = FontWeight.SemiBold) }
}

/** 접수, 준비 중, 완료 세 단계 */
@Composable
private fun MSteps(o: OrderView) {
    val step = when (o.status) {
        "waiting" -> 0
        "making" -> 1
        else -> 2
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("접수", "준비 중", "완료").forEachIndexed { i, label ->
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(6.dp).background(if (i <= step) TextC else DLine))
                Spacer(Modifier.height(12.dp))
                Text(label, color = if (i == step) TextC else Mute, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun MHome(ui: UiState, car: CarState, navi: NaviState, vm: DtViewModel) {
    val s = ui.suggestion
    val eta = navi.etaSec.takeIf { navi.tripActive }
    Column(Modifier.fillMaxSize().then(MPad)) {
        Text(ui.store.shortName + (eta?.let { ", ${clockText(it)}" } ?: ""), color = Mute, fontSize = 30.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            s?.let { itemsText(it.lines, ui.menu) } ?: "주문 기록이 없어요",
            color = TextC, fontSize = 92.sp, fontWeight = FontWeight.Bold, lineHeight = 104.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                s == null -> "메뉴에서 골라 주문할 수 있어요"
                ui.routineDemo -> "${s.reason} (데모 기록)"
                else -> s.reason
            },
            color = Mute, fontSize = 28.sp,
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (s != null) MButton("주문하기", vm::startSuggestion, Modifier.weight(1f))
            MButton(
                if (car.canBrowse) "메뉴 보기" else "메뉴는 정차 중에", vm::openMenu, Modifier.weight(1f),
                primary = s == null, enabled = car.canBrowse,
            )
        }
    }
}

@Composable
private fun MSuggest(ui: UiState, listening: Boolean, heard: String, vm: DtViewModel) {
    Column(Modifier.fillMaxSize().then(MPad)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ListenDot(listening, 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(voiceLine(ui, listening, heard), color = Mute, fontSize = 26.sp)
        }
        Spacer(Modifier.weight(1f))
        // 쉼표 뒤에서 줄을 바꿔 단어 중간에서 끊기지 않게 한다
        Text(ui.suggestText.replaceFirst(", ", ",\n"), color = TextC, fontSize = 68.sp, fontWeight = FontWeight.Bold, lineHeight = 86.sp)
        Spacer(Modifier.weight(1f))
        if (ui.paying) {
            Row(Modifier.fillMaxWidth().height(116.dp), verticalAlignment = Alignment.CenterVertically) {
                LinearSpinner(48.dp, Accent)
                Spacer(Modifier.width(20.dp))
                Text("결제하고 있어요", color = TextC, fontSize = 32.sp)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MButton("네, 주문", { vm.answer(true) }, Modifier.weight(1f))
                MButton("아니요", { vm.answer(false) }, Modifier.weight(1f), primary = false)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("등록된 카드로 결제돼요 (데모 결제)", color = Mute, fontSize = 22.sp)
    }
}

@Composable
private fun MTrack(ui: UiState, navi: NaviState) {
    val o = ui.order ?: return
    val (head, sub) = when {
        ui.justOrdered -> "주문 완료" to "${ui.store.shortName}에서 준비할게요"
        o.status == "ready" -> "준비 완료" to "도착하면 창구에서 받아 주세요"
        o.late -> "조금 늦어져요" to "주문이 몰려 도착보다 늦게 준비돼요"
        o.status == "making" -> "준비 중" to "${ui.store.shortName}에서 만들고 있어요"
        else -> "주문 접수" to "도착 시간에 맞춰 준비해요"
    }
    Row(Modifier.fillMaxSize().then(MPad)) {
        Column(Modifier.weight(1.5f).fillMaxHeight()) {
            if (ui.justOrdered) {
                AnimatedCheck(96.dp, DGreen)
                Spacer(Modifier.height(20.dp))
            }
            Text(head, color = TextC, fontSize = 104.sp, fontWeight = FontWeight.Bold, lineHeight = 116.sp)
            Spacer(Modifier.height(14.dp))
            Text(sub, color = Mute, fontSize = 32.sp)
            Spacer(Modifier.weight(1f))
            MSteps(o)
            Spacer(Modifier.height(24.dp))
            Text("주문 ${o.no}번, ${o.summary}", color = Mute, fontSize = 26.sp)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.End) {
            Text("도착까지", color = Mute, fontSize = 28.sp)
            BigClock(navi.etaSec, 200.sp, TextC)
        }
    }
}

@Composable
private fun MArrived(ui: UiState, vm: DtViewModel) {
    val o = ui.order
    // 서버는 도착했는데 아직 준비 중인 주문에 bay 값을 준다. 여기서는 "아직 준비 중" 표시로만 쓴다
    val waiting = o?.bay != null
    Column(Modifier.fillMaxSize().then(MPad)) {
        Spacer(Modifier.weight(1f))
        if (!waiting) {
            AnimatedCheck(120.dp, DGreen)
            Spacer(Modifier.height(24.dp))
        }
        Text(if (waiting) "거의 다 됐어요" else "준비됐어요", color = TextC, fontSize = 112.sp, fontWeight = FontWeight.Bold, lineHeight = 124.sp)
        Spacer(Modifier.height(14.dp))
        Text(if (waiting) "잠시만 기다려 주세요" else "창구에서 받아 주세요", color = Mute, fontSize = 36.sp)
        Spacer(Modifier.weight(1f))
        if (o != null) Text("주문 ${o.no}번, ${o.summary}", color = Mute, fontSize = 26.sp)
        Spacer(Modifier.height(20.dp))
        MButton("완료", vm::finish, Modifier.width(360.dp))
    }
}

// ================= A 한 장 카드 =================

@Composable
private fun AHome(ui: UiState, car: CarState, navi: NaviState, vm: DtViewModel) {
    val s = ui.suggestion
    val eta = navi.etaSec.takeIf { navi.tripActive }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.55f).fillMaxHeight().background(DSurf, Card).padding(48.dp)) {
            Text(if (s != null) "평소 이 시간에 드신 것" else "주문 기록이 아직 없어요", color = Mute, fontSize = 26.sp)
            Spacer(Modifier.height(12.dp))
            Text(s?.let { itemsText(it.lines, ui.menu) } ?: "메뉴에서 골라 주세요", color = TextC, fontSize = 84.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 96.sp)
            Spacer(Modifier.height(18.dp))
            if (s != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(s.reason, Accent)
                if (ui.routineDemo) Pill("데모 기록", Mute)
            }
            Spacer(Modifier.height(56.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(44.dp, 52.dp) { pinGlyph(Accent, DSurf) }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(ui.store.shortName, color = TextC, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(if (eta != null) "경로 위, ${clockText(eta)} 뒤 도착" else "목적지를 정하면 경로 위 매장을 찾아요", color = Mute, fontSize = 24.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                DButton("말로 주문", vm::startSuggestion, Modifier.weight(1f), icon = { micGlyph(it) })
                DButton(
                    if (car.canBrowse) "메뉴 보기" else "메뉴는 정차 중에만", vm::openMenu, Modifier.weight(1f),
                    primary = false, enabled = car.canBrowse, size = if (car.canBrowse) 32.sp else 26.sp,
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().background(DSurf, Card).padding(48.dp), verticalArrangement = Arrangement.Center) {
            if (eta != null) {
                Text("${ui.store.shortName}까지", color = Mute, fontSize = 28.sp)
                BigClock(eta, 190.sp, TextC)
                Text("주행 중에는 차가 먼저 물어봐요", color = Mute, fontSize = 24.sp)
            } else {
                Glyph(80.dp) { micGlyph(Accent) }
                Spacer(Modifier.height(28.dp))
                Text("출발하면\n차가 먼저 물어봐요", color = TextC, fontSize = 50.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 62.sp)
                Spacer(Modifier.height(16.dp))
                Text("\"응\" 한마디면 주문, 결제, 경유지까지", color = Mute, fontSize = 26.sp)
            }
        }
    }
}

@Composable
private fun ASuggest(ui: UiState, listening: Boolean, heard: String, vm: DtViewModel) {
    Column(Modifier.fillMaxSize().background(DSurf, Card).padding(horizontal = 56.dp, vertical = 44.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ListenDot(listening)
            Spacer(Modifier.width(14.dp))
            Text(voiceLine(ui, listening, heard), color = if (listening) Accent else Mute, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        QuestionText(ui, 66.sp)
        Spacer(Modifier.weight(1f))
        AnswerButtons(ui, vm, 190.dp, 54.sp)
        Spacer(Modifier.height(18.dp))
        Text("말로 답해도 돼요. 승인하면 등록된 카드로 결제돼요 (데모)", color = Mute, fontSize = 21.sp)
    }
}

@Composable
private fun ATrack(ui: UiState, navi: NaviState) {
    val o = ui.order ?: return
    val readyIn = liveReadyIn(o)
    val st = trackState(o, navi.etaSec, readyIn)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.2f).fillMaxHeight().background(DSurf, Card).padding(48.dp)) {
            Text("${ui.store.shortName}까지", color = Mute, fontSize = 30.sp)
            BigClock(navi.etaSec, 230.sp, TextC)
            Text(navi.distanceM?.let { "%.1fkm, 경유지로 안내 중".format(it / 1000.0) } ?: "경유지로 안내 중", color = Mute, fontSize = 26.sp)
            Spacer(Modifier.weight(1f))
            StepBar(o, 26.sp)
        }
        Column(
            Modifier.weight(1f).fillMaxHeight()
                .background(st.color.copy(alpha = 0.10f), Card)
                .border(2.dp, st.color.copy(alpha = 0.45f), Card)
                .padding(48.dp),
        ) {
            Glyph(64.dp, 72.dp) { cupGlyph(st.color) }
            Spacer(Modifier.height(28.dp))
            Text(st.headline, color = st.color, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 66.sp)
            Spacer(Modifier.height(14.dp))
            Text(st.sub, color = TextC, fontSize = 30.sp, lineHeight = 40.sp)
            Spacer(Modifier.weight(1f))
            Text("주문 #${o.no}, ${o.summary}", color = Mute, fontSize = 22.sp)
        }
    }
}

@Composable
private fun AArrived(ui: UiState, vm: DtViewModel) {
    val bay = ui.order?.bay
    val c = if (bay == null) DGreen else DAmber
    Row(
        Modifier.fillMaxSize()
            .background(c.copy(alpha = 0.11f), Card)
            .border(2.dp, c.copy(alpha = 0.45f), Card)
            .padding(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(380.dp).background(c, RoundedCornerShape(56.dp)), contentAlignment = Alignment.Center) {
            if (bay == null) AnimatedCheck(230.dp, Ink)
            else Text("$bay", color = Ink, fontSize = 280.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 280.sp)
        }
        Spacer(Modifier.width(64.dp))
        Column(Modifier.weight(1f)) {
            Text(if (bay == null) "바로 받으세요" else "대기칸 ${bay}번으로", color = c, fontSize = 92.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 104.sp)
            Spacer(Modifier.height(20.dp))
            Text(
                if (bay == null) "도착에 맞춰 완성됐어요.\n창구에서 기다리지 않아요." else "뒤차가 기다리지 않게 옆 칸에서 받아요.\n완성되면 가져다 드려요.",
                color = TextC, fontSize = 34.sp, lineHeight = 46.sp,
            )
            Spacer(Modifier.height(48.dp))
            DButton("완료", vm::finish, Modifier.width(320.dp))
        }
    }
}

// ================= B 맞춤 타임라인 =================

/**
 * 같은 시간축(지금 → 미래)에 위는 내 차, 아래는 내 음료.
 * 매장 핀(도착)과 컵(완성)이 거의 같은 x에 오면 "딱 맞춤". 정체로 도착이 밀리면 핀과 제조 막대가 함께 오른쪽으로 움직인다.
 */
@Composable
private fun SyncTimeline(etaSec: Long?, readyInSec: Long?, prepSec: Int, status: String, modifier: Modifier = Modifier, axis: Boolean = true) {
    val m = rememberTextMeasurer()
    val etaA by animateFloatAsState((etaSec ?: 0L).toFloat(), tween(900), label = "eta")
    val readyA by animateFloatAsState((readyInSec ?: etaSec ?: 0L).toFloat(), tween(900), label = "ready")
    Canvas(modifier) {
        val padL = 150.dp.toPx()
        val padR = 70.dp.toPx()
        val w = size.width - padL - padR
        // 축은 10분 고정 (넘으면 늘림). 그래야 정체로 도착이 밀릴 때 핀과 제조 막대가 실제로 오른쪽으로 움직인다.
        // 도착 2분 30초 전부터는 3분 축으로 확대해 마지막 순간(제조 중, 완성)이 왼쪽에 몰리지 않게 한다
        val far = maxOf(etaA, readyA)
        val span = if (far <= 150f) 180f else maxOf(600f, far * 1.1f)
        val step = if (span <= 180f) 30f else 60f
        fun x(t: Float) = padL + (t.coerceIn(0f, span) / span) * w
        val y1 = size.height * (if (axis) 0.30f else 0.32f)
        val y2 = size.height * (if (axis) 0.64f else 0.70f)
        val laneLabel = 26.sp

        // 지금 기준선과 분 눈금
        drawLine(DLine, Offset(x(0f), y1 - 70.dp.toPx()), Offset(x(0f), y2 + 30.dp.toPx()), 2.dp.toPx())
        if (axis) {
            val ay = size.height - 30.dp.toPx()
            drawLine(DLine, Offset(x(0f), ay), Offset(x(span), ay), 2.dp.toPx())
            var t = 0f
            while (t <= span) {
                val major = (t / (step * 2)).let { it == it.toInt().toFloat() }
                drawLine(if (major) Mute else DLine, Offset(x(t), ay - (if (major) 10 else 6).dp.toPx()), Offset(x(t), ay), 2.dp.toPx())
                if (major) label(m, if (t == 0f) "지금" else "${(t / 60).toInt()}분", x(t), ay + 18.dp.toPx(), Mute, 20.sp)
                t += step
            }
        } else label(m, "지금", x(0f), y2 + 50.dp.toPx(), Mute, 20.sp)

        // 1) 내 차
        label(m, "내 차", 0f, y1, TextC, laneLabel, bold = true, center = false)
        drawLine(DLine, Offset(x(0f), y1), Offset(x(etaA), y1), 12.dp.toPx(), StrokeCap.Round)
        drawLine(Accent.copy(alpha = 0.55f), Offset(x(0f), y1), Offset(x(etaA), y1), 4.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(18f, 14f)))
        val car = 70.dp.toPx()
        translateBox(Offset(x(0f) - car * 0.1f, y1 - car * 0.62f), car, car * 0.7f) { carGlyph(TextC, DSurf) }
        val pin = 46.dp.toPx()
        translateBox(Offset(x(etaA) - pin / 2, y1 - pin * 1.15f), pin, pin * 1.15f) { pinGlyph(Accent, DSurf) }
        val e = etaA.toLong().coerceAtLeast(0)
        label(m, "도착 " + (if (e >= 60) "${e / 60}분 ${e % 60}초" else "${e}초"), x(etaA), y1 - pin * 1.15f - 24.dp.toPx(), Accent, 26.sp, bold = true)

        // 도착 시각 세로선 (음료 줄까지 내려 맞춤 여부를 비교)
        drawLine(Accent.copy(alpha = 0.5f), Offset(x(etaA), y1 + 10.dp.toPx()), Offset(x(etaA), y2 + 40.dp.toPx()), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))

        // 2) 내 음료
        label(m, "내 음료", 0f, y2, TextC, laneLabel, bold = true, center = false)
        val bar = 24.dp.toPx()
        val cup = 40.dp.toPx()
        if (status == "ready") {
            translateBox(Offset(x(0f) - cup / 2, y2 - cup * 1.2f), cup, cup * 1.1f) { cupGlyph(DGreen) }
            label(m, "완성됨, 도착하면 바로", x(0f) + cup, y2, DGreen, 24.sp, bold = true, center = false)
        } else {
            val startA = if (status == "making") 0f else (readyA - prepSec).coerceAtLeast(0f)
            if (startA > 0f) {
                drawLine(Mute.copy(alpha = 0.6f), Offset(x(0f), y2), Offset(x(startA), y2), 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f)))
                label(m, "도착 맞춰 대기", (x(0f) + x(startA)) / 2, y2 - 26.dp.toPx(), Mute, 21.sp)
            }
            drawRoundRect(Accent, Offset(x(startA), y2 - bar / 2), Size((x(readyA) - x(startA)).coerceAtLeast(bar), bar), CornerRadius(bar / 2))
            label(m, if (status == "making") "제조 중" else "제조 ${clockText(prepSec.toLong())}", (x(startA) + x(readyA)) / 2, y2 + 40.dp.toPx(), Accent, 21.sp)
            translateBox(Offset(x(readyA) - cup / 2, y2 - bar / 2 - cup * 1.15f), cup, cup * 1.1f) { cupGlyph(TextC) }
            // 완성과 도착의 차이
            val gap = etaA - readyA
            if (gap >= 0f) {
                drawLine(DGreen, Offset(x(readyA), y2 + 56.dp.toPx()), Offset(x(etaA), y2 + 56.dp.toPx()), 6.dp.toPx(), StrokeCap.Round)
                label(m, "${clockText(gap.toLong())} 여유", maxOf((x(readyA) + x(etaA)) / 2, x(readyA) + 60.dp.toPx()), y2 + 84.dp.toPx(), DGreen, 22.sp, bold = true)
            } else {
                drawLine(Red, Offset(x(etaA), y2 + 56.dp.toPx()), Offset(x(readyA), y2 + 56.dp.toPx()), 6.dp.toPx(), StrokeCap.Round)
                label(m, "${clockText((-gap).toLong())} 늦음", (x(readyA) + x(etaA)) / 2, y2 + 84.dp.toPx(), Red, 22.sp, bold = true)
            }
        }
    }
}

/** 아이콘을 원하는 위치와 크기로 그린다 */
internal fun DrawScope.translateBox(topLeft: Offset, w: Float, h: Float, block: DrawScope.() -> Unit) {
    drawContext.canvas.save()
    drawContext.canvas.translate(topLeft.x, topLeft.y)
    val old = drawContext.size
    drawContext.size = Size(w, h)
    block()
    drawContext.size = old
    drawContext.canvas.restore()
}

/** 지금 위치 → 경로 위 매장 → 목적지 */
@Composable
private fun RouteStrip(navi: NaviState, storeName: String, storeFull: String, modifier: Modifier = Modifier) {
    val m = rememberTextMeasurer()
    val trip = navi.tripActive
    val dest = navi.destinationName?.takeIf { it != storeFull && !it.contains(storeName) }
    Canvas(modifier) {
        val y = size.height * 0.52f
        val x0 = 40.dp.toPx()
        val xd = size.width - 40.dp.toPx()
        val xs = if (dest != null) size.width * 0.45f else xd
        drawLine(DLine, Offset(x0, y), Offset(xd, y), 14.dp.toPx(), StrokeCap.Round)
        if (trip) drawLine(Accent, Offset(x0, y), Offset(xs, y), 14.dp.toPx(), StrokeCap.Round)
        else drawLine(Mute, Offset(x0, y), Offset(xd, y), 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 16f)))
        drawCircle(Blue.copy(alpha = 0.25f), 26.dp.toPx(), Offset(x0, y))
        drawCircle(Blue, 14.dp.toPx(), Offset(x0, y))
        label(m, "지금", x0, y + 52.dp.toPx(), TextC, 24.sp, bold = true)
        val pin = 60.dp.toPx()
        translateBox(Offset(xs - pin / 2, y - pin * 1.25f), pin, pin * 1.15f) { pinGlyph(Accent, DSurf) }
        label(m, storeName, xs, y + 52.dp.toPx(), Accent, 28.sp, bold = true)
        label(
            m, if (trip && navi.etaSec != null) "${clockText(navi.etaSec)} 뒤 지나요" else "경로 위 드라이브스루",
            xs, y + 88.dp.toPx(), Mute, 22.sp,
        )
        if (dest != null) {
            drawCircle(TextC, 12.dp.toPx(), Offset(xd, y))
            label(m, dest, xd - 20.dp.toPx(), y + 52.dp.toPx(), TextC, 24.sp, bold = true)
        }
        if (!trip) label(m, "목적지를 정하면 경로 위 매장을 찾아요", size.width / 2, y - 90.dp.toPx(), Mute, 26.sp)
    }
}

@Composable
private fun BHome(ui: UiState, car: CarState, navi: NaviState, vm: DtViewModel) {
    val s = ui.suggestion
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.fillMaxWidth().weight(1f).background(DSurf, Card).padding(40.dp)) {
            Text(if (navi.tripActive) "지금 경로" else "경로 위 드라이브스루", color = Mute, fontSize = 26.sp)
            RouteStrip(navi, ui.store.shortName, ui.store.name, Modifier.fillMaxWidth().weight(1f))
        }
        Row(Modifier.fillMaxWidth().background(DSurf, Card).padding(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("평소 이 시간", color = Mute, fontSize = 24.sp)
                Text(s?.let { itemsText(it.lines, ui.menu) } ?: "주문 기록이 아직 없어요", color = TextC, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                if (s != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill(s.reason, Accent)
                    if (ui.routineDemo) Pill("데모 기록", Mute)
                }
            }
            DButton("말로 주문", vm::startSuggestion, icon = { micGlyph(it) })
            Spacer(Modifier.width(20.dp))
            DButton(
                if (car.canBrowse) "메뉴 보기" else "메뉴는 정차 중에만", vm::openMenu,
                primary = false, enabled = car.canBrowse, size = if (car.canBrowse) 32.sp else 26.sp,
            )
        }
    }
}

@Composable
private fun BSuggest(ui: UiState, navi: NaviState, listening: Boolean, heard: String, vm: DtViewModel) {
    val eta = navi.etaSec ?: 360L
    val prep = ui.suggestion?.let { estPrep(it.lines, ui.menu) } ?: 60
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.fillMaxWidth().weight(1f).background(DSurf, Card).padding(horizontal = 48.dp, vertical = 36.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ListenDot(listening)
                Spacer(Modifier.width(14.dp))
                Text(voiceLine(ui, listening, heard), color = if (listening) Accent else Mute, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(18.dp))
            QuestionText(ui, 54.sp)
            Spacer(Modifier.weight(1f))
            Text("\"응\" 하면 이렇게 맞춰요 (예상)", color = Mute, fontSize = 24.sp)
            // 서버와 같은 규칙: 완성 = 도착 20초 전
            SyncTimeline(eta, eta - 20, prep, "waiting", Modifier.fillMaxWidth().height(320.dp), axis = false)
        }
        AnswerButtons(ui, vm, 150.dp, 46.sp)
    }
}

@Composable
private fun BStat(label: String, sec: Long?, color: Color, modifier: Modifier, done: String? = null) {
    Column(modifier.background(DSurf, Card).padding(horizontal = 36.dp, vertical = 28.dp)) {
        Text(label, color = Mute, fontSize = 26.sp)
        if (done != null) Text(done, color = color, fontSize = 84.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 100.sp)
        else PreciseClock(sec, 84.sp, color)
    }
}

/** B는 "몇 초 차이"가 핵심이라 분과 초를 다 보여 준다: 5분 40초 */
@Composable
private fun PreciseClock(sec: Long?, size: TextUnit, color: Color) {
    if (sec == null) {
        Text("-", color = Mute, fontSize = size, fontWeight = FontWeight.ExtraBold, lineHeight = size * 1.2f)
        return
    }
    val s = sec.coerceAtLeast(0)
    val small = SpanStyle(fontSize = size * 0.4f, color = Mute)
    Text(
        buildAnnotatedString {
            if (s >= 60) {
                append("${s / 60}")
                withStyle(small) { append("분 ") }
            }
            append("${s % 60}")
            withStyle(small) { append("초") }
        },
        color = color, fontSize = size, fontWeight = FontWeight.ExtraBold, lineHeight = size * 1.2f,
    )
}

@Composable
private fun BTrack(ui: UiState, navi: NaviState) {
    val o = ui.order ?: return
    val readyIn = liveReadyIn(o)
    val st = trackState(o, navi.etaSec, readyIn)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BStat("도착까지", navi.etaSec, TextC, Modifier.weight(1f))
            BStat("완성까지", readyIn?.coerceAtLeast(0), if (o.status == "ready") DGreen else Accent, Modifier.weight(1f), done = if (o.status == "ready") "완성" else null)
            Column(
                Modifier.weight(1.3f).background(st.color.copy(alpha = 0.10f), Card).border(2.dp, st.color.copy(alpha = 0.45f), Card)
                    .padding(horizontal = 36.dp, vertical = 28.dp),
            ) {
                Text(st.headline, color = st.color, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 54.sp)
                Spacer(Modifier.height(10.dp))
                Text(st.sub, color = TextC, fontSize = 26.sp, lineHeight = 34.sp)
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(DSurf, Card).padding(horizontal = 40.dp, vertical = 28.dp)) {
            SyncTimeline(navi.etaSec, readyIn, o.prepSec, o.status, Modifier.fillMaxSize())
        }
        Text("정체로 도착이 늦어지면 제조 시작도 함께 뒤로 밀려요. 주문 #${o.no}, ${o.summary}", color = Mute, fontSize = 22.sp)
    }
}

@Composable
private fun BArrived(ui: UiState, vm: DtViewModel) {
    val bay = ui.order?.bay
    val c = if (bay == null) DGreen else DAmber
    Column(Modifier.fillMaxSize().background(DSurf, Card).padding(56.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(220.dp, 154.dp) { carGlyph(TextC, DSurf) }
            Canvas(Modifier.width(120.dp).height(20.dp)) {
                drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 6.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(16f, 14f)))
            }
            Box(Modifier.size(180.dp).background(c, CircleShape), contentAlignment = Alignment.Center) {
                if (bay == null) AnimatedCheck(110.dp, Ink) else Text("$bay", color = Ink, fontSize = 110.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 110.sp)
            }
            Canvas(Modifier.width(120.dp).height(20.dp)) {
                drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 6.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(16f, 14f)))
            }
            Glyph(110.dp, 130.dp) { cupGlyph(if (bay == null) TextC else Mute) }
        }
        Spacer(Modifier.height(44.dp))
        Text(if (bay == null) "도착과 완성이 딱 맞았어요" else "대기칸 ${bay}번으로 가 주세요", color = c, fontSize = 80.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        Text(
            if (bay == null) "창구에서 바로 받으세요" else "뒤차가 기다리지 않게 옆 칸에서 받아요. 완성되면 가져다 드려요",
            color = TextC, fontSize = 32.sp,
        )
        Spacer(Modifier.height(44.dp))
        DButton("완료", vm::finish, Modifier.width(320.dp))
    }
}

// ================= C 도착 순서 =================

private data class QItem(
    val id: String,
    val no: Int,
    val source: String,
    val status: String,
    val rank: Int,
    val arrivalIn: Long,
    val inLane: Boolean,
    val late: Boolean,
)

/** 매장 서버의 현재 제조 계획(도착 예정순). 차량 앱은 다른 차의 차종이나 이름 없이 주문 번호만 쓴다 */
@Composable
private fun rememberQueue(serverUrl: String, enabled: Boolean): List<QItem> {
    var q by remember { mutableStateOf(emptyList<QItem>()) }
    LaunchedEffect(serverUrl, enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            runCatching { fetchQueue(serverUrl) }.onSuccess { q = it }
            delay(1500)
        }
    }
    return q
}

private suspend fun fetchQueue(base: String): List<QItem> = withContext(Dispatchers.IO) {
    val conn = URL(base.trimEnd('/') + "/api/state").openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 2000
        conn.readTimeout = 3000
        val j = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        val now = j.optLong("now")
        val arr = j.optJSONArray("queue") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            QItem(
                id = o.getString("id"),
                no = o.optInt("no"),
                source = o.optString("source"),
                status = o.optString("status"),
                rank = o.optInt("rank"),
                arrivalIn = if (o.has("arrivalInSec")) o.optLong("arrivalInSec") else (o.optLong("arrivalAt") - now) / 1000,
                inLane = o.optBoolean("inLane"),
                late = o.optBoolean("late"),
            )
        }.filter { it.status != "done" && it.status != "cancelled" }.sortedBy { it.rank }
    } finally {
        conn.disconnect()
    }
}

@Composable
private fun QueueRow(q: QItem, mine: Boolean, rank: Int, modifier: Modifier = Modifier, preview: Boolean = false) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth()
            .background(if (mine) Accent.copy(alpha = 0.14f) else DSurf2, shape)
            .border(if (mine) 2.dp else 0.dp, if (mine) Accent else DSurf2, shape)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(if (mine) Accent else DLine, CircleShape), contentAlignment = Alignment.Center) {
            Text("$rank", color = if (mine) Ink else TextC, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (mine) (if (preview) "내 차 (주문하면)" else "내 차")
                else when (q.source) {
                    "car" -> "차량 주문 #${q.no}"
                    "phone" -> "폰 주문 #${q.no}"
                    else -> "현장 주문 #${q.no}"
                },
                color = if (mine) Accent else TextC, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    q.inLane -> "줄에 있음"
                    q.arrivalIn <= 0 -> "곧 도착"
                    else -> "${clockText(q.arrivalIn)} 뒤 도착"
                },
                color = Mute, fontSize = 21.sp,
            )
        }
        val (t, c) = when {
            q.status == "ready" -> "완성" to DGreen
            q.status == "making" -> "제조 중" to Accent
            q.late -> "지연" to DAmber
            else -> "대기" to Mute
        }
        Pill(t, c)
    }
}

/** 지금 주문하면 몇 번째로 만들지: 나보다 먼저 도착할 차 + 1 */
private fun previewRank(queue: List<QItem>, eta: Long) = queue.count { it.inLane || it.arrivalIn < eta } + 1

@Composable
private fun PreviewQueue(queue: List<QItem>, eta: Long, modifier: Modifier = Modifier) {
    val k = previewRank(queue, eta)
    val me = QItem("me", 0, "car", "waiting", k, eta, false, false)
    val list = (queue.take(5).toMutableList().apply { add((k - 1).coerceIn(0, size), me) }).take(5)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        list.forEachIndexed { i, q -> QueueRow(q, mine = q.id == "me", rank = i + 1, preview = true) }
    }
}

@Composable
private fun CHome(ui: UiState, car: CarState, navi: NaviState, vm: DtViewModel, queue: List<QItem>) {
    val s = ui.suggestion
    val eta = navi.etaSec.takeIf { navi.tripActive }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().background(DSurf, Card).padding(44.dp)) {
            Text("${ui.store.shortName} 지금", color = Mute, fontSize = 26.sp)
            Text(
                buildAnnotatedString {
                    append("${queue.size}")
                    withStyle(SpanStyle(fontSize = 44.sp, color = Mute)) { append(" 건 준비 중") }
                },
                color = TextC, fontSize = 130.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 140.sp,
            )
            Text(
                if (eta != null) "지금 주문하면 ${previewRank(queue, eta)}번째로 만들어요" else "주문순이 아니라 도착이 빠른 차부터 만들어요",
                color = Accent, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                queue.take(4).forEach { QueueRow(it, mine = false, rank = it.rank) }
                if (queue.isEmpty()) Text(if (ui.serverOk) "지금은 기다리는 주문이 없어요" else "매장 서버에 연결되지 않았어요", color = Mute, fontSize = 24.sp)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().background(DSurf, Card).padding(44.dp)) {
            Text("평소 이 시간", color = Mute, fontSize = 26.sp)
            Spacer(Modifier.height(8.dp))
            Text(s?.let { itemsText(it.lines, ui.menu) } ?: "주문 기록이 아직 없어요", color = TextC, fontSize = 58.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 70.sp)
            Spacer(Modifier.height(14.dp))
            if (s != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(s.reason, Accent)
                if (ui.routineDemo) Pill("데모 기록", Mute)
            }
            Spacer(Modifier.weight(1f))
            DButton("말로 주문", vm::startSuggestion, Modifier.fillMaxWidth(), icon = { micGlyph(it) })
            Spacer(Modifier.height(16.dp))
            DButton(
                if (car.canBrowse) "메뉴 보기" else "메뉴는 정차 중에만", vm::openMenu, Modifier.fillMaxWidth(),
                primary = false, enabled = car.canBrowse, size = if (car.canBrowse) 32.sp else 26.sp,
            )
        }
    }
}

@Composable
private fun CSuggest(ui: UiState, navi: NaviState, listening: Boolean, heard: String, vm: DtViewModel, queue: List<QItem>) {
    val eta = navi.etaSec ?: 360L
    val k = previewRank(queue, eta)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.35f).fillMaxHeight().background(DSurf, Card).padding(horizontal = 52.dp, vertical = 44.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ListenDot(listening)
                Spacer(Modifier.width(14.dp))
                Text(voiceLine(ui, listening, heard), color = if (listening) Accent else Mute, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            QuestionText(ui, 58.sp)
            Spacer(Modifier.weight(1f))
            AnswerButtons(ui, vm, 170.dp, 48.sp)
        }
        Column(Modifier.weight(1f).fillMaxHeight().background(DSurf, Card).padding(40.dp)) {
            Text("주문하면", color = Mute, fontSize = 26.sp)
            Text("${k}번째로 만들어요", color = Accent, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (k > 1) "나보다 먼저 도착할 주문 ${k - 1}건" else "가장 먼저 도착해요", color = Mute, fontSize = 24.sp)
            Spacer(Modifier.height(28.dp))
            PreviewQueue(queue, eta)
        }
    }
}

@Composable
private fun CTrack(ui: UiState, navi: NaviState, queue: List<QItem>) {
    val o = ui.order ?: return
    val readyIn = liveReadyIn(o)
    val st = trackState(o, navi.etaSec, readyIn)
    var prevRank by remember(o.id) { mutableStateOf<Int?>(null) }
    var event by remember(o.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(o.id, o.rank) {
        val p = prevRank
        val r = o.rank
        if (p != null && r != null && r != p) {
            event = if (r < p) "앞 순서 차의 도착이 늦어져 ${p}번째에서 ${r}번째로 당겨졌어요" else "도착 예정이 바뀌어 ${p}번째에서 ${r}번째로 조정됐어요"
        }
        prevRank = r
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().background(DSurf, Card).padding(36.dp)) {
            Text("매장 제조 순서", color = Mute, fontSize = 24.sp)
            Text("도착이 빠른 차부터 만들어요", color = TextC, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(22.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(queue.take(6), key = { it.id }) { q -> QueueRow(q, mine = q.id == o.id, rank = q.rank, modifier = Modifier.animateItem()) }
            }
            if (queue.isEmpty()) Text("매장 순서를 불러오는 중이에요", color = Mute, fontSize = 22.sp)
        }
        Column(Modifier.weight(1f).fillMaxHeight().background(DSurf, Card).padding(48.dp)) {
            Text("내 순서", color = Mute, fontSize = 28.sp)
            Text(
                buildAnnotatedString {
                    append("${o.rank ?: "-"}")
                    withStyle(SpanStyle(fontSize = 50.sp, color = Mute)) { append(" 번째") }
                },
                color = Accent, fontSize = 190.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 196.sp,
            )
            Text(st.headline, color = st.color, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text(st.sub, color = TextC, fontSize = 27.sp)
            Spacer(Modifier.weight(1f))
            event?.let {
                Box(Modifier.fillMaxWidth().background(Blue.copy(alpha = 0.14f), RoundedCornerShape(20.dp)).padding(22.dp)) {
                    Text(it, color = Blue, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
                }
                Spacer(Modifier.height(16.dp))
            }
            Text("${ui.store.shortName}까지 ${navi.etaSec?.let { clockText(it) } ?: "-"}, 주문 #${o.no}", color = Mute, fontSize = 23.sp)
        }
    }
}

/** 위에서 본 드라이브스루: 매장, 픽업 창구, 차선, 대기칸. 갈 곳을 색으로 칠한다 */
@Composable
private fun LaneMap(bay: Int?, modifier: Modifier = Modifier) {
    val m = rememberTextMeasurer()
    val t = rememberInfiniteTransition(label = "lane")
    val pulse by t.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "p")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val target = if (bay == null) DGreen else DAmber
        // 매장 건물
        drawRoundRect(DSurf2, Offset(w * 0.22f, h * 0.06f), Size(w * 0.56f, h * 0.30f), CornerRadius(24f))
        label(m, "매장", w * 0.5f, h * 0.17f, Mute, 26.sp, bold = true)
        // 픽업 창구
        val winC = if (bay == null) target else DLine
        drawRoundRect(winC, Offset(w * 0.43f, h * 0.30f), Size(w * 0.14f, h * 0.07f), CornerRadius(10f))
        label(m, "픽업 창구", w * 0.5f, h * 0.26f, if (bay == null) target else Mute, 22.sp, bold = true)
        // 차선
        val laneTop = h * 0.40f
        val laneH = h * 0.17f
        drawRoundRect(DLine, Offset(w * 0.02f, laneTop), Size(w * 0.96f, laneH), CornerRadius(laneH / 2))
        for (i in 0 until 6) {
            val cx = w * (0.1f + i * 0.16f)
            val cy = laneTop + laneH / 2
            val p = Path().apply { moveTo(cx - 10f, cy - 14f); lineTo(cx + 6f, cy); lineTo(cx - 10f, cy + 14f) }
            drawPath(p, Mute.copy(alpha = 0.5f), style = Stroke(4f, cap = StrokeCap.Round))
        }
        // 대기칸
        val bayTop = h * 0.64f
        val bayH = h * 0.30f
        listOf(1 to 0.62f, 2 to 0.80f).forEach { (n, bx) ->
            val on = bay == n
            val o = Offset(w * bx, bayTop)
            val s = Size(w * 0.15f, bayH)
            if (on) drawRoundRect(target.copy(alpha = 0.25f * pulse + 0.1f), o, s, CornerRadius(16f))
            drawRoundRect(if (on) target else Mute.copy(alpha = 0.6f), o, s, CornerRadius(16f), style = Stroke(4f, pathEffect = if (on) null else PathEffect.dashPathEffect(floatArrayOf(12f, 10f))))
            label(m, "대기칸 $n", o.x + s.width / 2, bayTop + bayH + 26.dp.toPx(), if (on) target else Mute, 22.sp, bold = on)
        }
        // 뒤차 두 대
        val carLen = w * 0.11f
        listOf(0.12f, 0.27f).forEach { carTop(Color(0xFF4A5563), Offset(w * it, laneTop + laneH / 2), carLen) }
        // 내 차
        if (bay == null) {
            drawCircle(target.copy(alpha = 0.25f * pulse), carLen * 0.75f, Offset(w * 0.5f, laneTop + laneH / 2))
            carTop(target, Offset(w * 0.5f, laneTop + laneH / 2), carLen)
        } else {
            val bx = if (bay == 1) 0.62f else 0.80f
            val dest = Offset(w * bx + w * 0.075f, bayTop + bayH / 2)
            val from = Offset(w * 0.46f, laneTop + laneH / 2)
            drawLine(target, from, Offset(dest.x, laneTop + laneH / 2), 6f, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
            drawLine(target, Offset(dest.x, laneTop + laneH / 2), dest, 6f, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
            carTop(Mute, from, carLen)
            drawCircle(target, 14f, dest)
        }
    }
}

@Composable
private fun CArrived(ui: UiState, vm: DtViewModel) {
    val bay = ui.order?.bay
    val c = if (bay == null) DGreen else DAmber
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(Modifier.weight(1.25f).fillMaxHeight().background(DSurf, Card).padding(32.dp)) {
            LaneMap(bay, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).fillMaxHeight().background(c.copy(alpha = 0.10f), Card).border(2.dp, c.copy(alpha = 0.45f), Card).padding(48.dp)) {
            Text(if (bay == null) "도착" else "거의 다 됐어요", color = Mute, fontSize = 28.sp)
            Text(if (bay == null) "창구에서\n바로 받으세요" else "대기칸\n${bay}번으로", color = c, fontSize = 84.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 96.sp)
            Spacer(Modifier.height(20.dp))
            Text(
                if (bay == null) "도착 순서에 맞춰 완성했어요. 기다리지 않아요." else "뒤차가 기다리지 않게 옆 칸에서 받아요. 완성되면 가져다 드려요.",
                color = TextC, fontSize = 30.sp, lineHeight = 40.sp,
            )
            Spacer(Modifier.weight(1f))
            DButton("완료", vm::finish, Modifier.fillMaxWidth())
        }
    }
}
