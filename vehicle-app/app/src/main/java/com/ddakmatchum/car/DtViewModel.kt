package com.ddakmatchum.car

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ddakmatchum.car.car.CarSignals
import com.ddakmatchum.car.car.CarState
import com.ddakmatchum.car.navi.DemoNavi
import com.ddakmatchum.car.navi.NaviState
import com.ddakmatchum.car.navi.PleosNavi
import com.ddakmatchum.car.net.Api
import com.ddakmatchum.car.routine.Routine
import com.ddakmatchum.car.routine.Suggestion
import com.ddakmatchum.car.ui.DraftSwitch
import com.ddakmatchum.car.voice.Answer
import com.ddakmatchum.car.voice.Voice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Phase { HOME, SUGGEST, MENU, PAY, TRACK, ARRIVED }

data class UiState(
    val phase: Phase = Phase.HOME,
    val store: Store = Defaults.store,
    val menu: List<MenuItem> = Defaults.menu,
    val cart: List<CartLine> = emptyList(),
    val groupCode: String? = null,
    val groupLines: List<CartLine> = emptyList(),
    val suggestion: Suggestion? = null,
    val suggestText: String = "",
    val order: OrderView? = null,
    /** 주문 직후 몇 초 동안 "주문 완료" 카드를 보여 준다. 그 뒤에 내비로 넘긴다 */
    val justOrdered: Boolean = false,
    /** 서버가 아는 드라이브스루 매장 전체. 화면에서 현재 위치 기준으로 가까운 순으로 보여 준다 */
    val stores: List<Store> = listOf(Defaults.store),
    /** QR 결제(데모)를 기다리는 중이면 그 코드 */
    val payCode: String? = null,
    val paying: Boolean = false,
    val serverOk: Boolean = false,
    val serverUrl: String = DEFAULT_SERVER,
    val phoneUrl: String = DEFAULT_SERVER,
    val toast: String? = null,
    val demoPanel: Boolean = false,
    val demoNavi: Boolean = false,
    val routineDemo: Boolean = false,
)

const val DEFAULT_SERVER = "http://10.0.2.2:8787" // 안드로이드 에뮬레이터에서 본 PC의 localhost

/** 주문 완료 카드를 보여 주는 시간. 이 시간이 지나면 내비에 매장 경로를 넣는다 */
const val ORDER_CONFIRM_MS = 3000L

/** 목적지를 정했을 때, 이만큼(직선 거리 추정)보다 더 돌아가야 하는 매장은 "가는 길"로 권하지 않는다 */
const val ASK_MAX_DETOUR_M = 1000L

/** 내비에 목적지가 이 시간 넘게 없었으면 주행이 끝난 것으로 본다 */
const val TRIP_GAP_MS = 30_000L

/** 근처 매장 한 곳: 현재 위치에서의 직선 거리와, 목적지가 있을 때 돌아가는 거리 */
data class NearStore(val store: Store, val distanceM: Long?, val detourM: Long?)

/**
 * 드라이브스루 매장을 가까운 순으로 정렬한다. 내비 SDK에 장소 검색이 없어서 서버의 매장 목록을 쓴다.
 * 목적지가 있으면 "얼마나 돌아가는지"(직선 거리 기준 추정)가 작은 순, 없으면 가까운 순.
 */
fun nearbyStores(stores: List<Store>, n: NaviState): List<NearStore> {
    val lat = n.curLat
    val lon = n.curLon
    if (lat == null || lon == null) return stores.map { NearStore(it, null, null) }
    val dLat = n.destLat
    val dLon = n.destLon
    return stores.map { s ->
        val d = com.ddakmatchum.car.navi.distanceM(lat, lon, s.latitude, s.longitude)
        val detour = if (dLat != null && dLon != null) {
            (d + com.ddakmatchum.car.navi.distanceM(s.latitude, s.longitude, dLat, dLon) -
                com.ddakmatchum.car.navi.distanceM(lat, lon, dLat, dLon)).coerceAtLeast(0)
        } else null
        NearStore(s, d, detour)
    }.sortedWith(compareBy({ it.detourM ?: it.distanceM ?: Long.MAX_VALUE }, { it.distanceM ?: Long.MAX_VALUE }))
}

class DtViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val car = CarSignals(app)
    private val pleosNavi = PleosNavi(app)
    private val demoNavi = DemoNavi()
    val voice = Voice(app)
    private val routine = Routine(app)
    private val api = Api(prefs.getString("server", DEFAULT_SERVER) ?: DEFAULT_SERVER)

    private val _ui = MutableStateFlow(
        UiState(
            serverUrl = api.baseUrl,
            phoneUrl = prefs.getString("phone", api.baseUrl) ?: api.baseUrl,
        ),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val carState: StateFlow<CarState> = car.state

    /** 실제 내비가 연결되면 실제 값, 아니면(또는 데모 주행을 켜면) 데모 내비 */
    val navi: StateFlow<NaviState> = combine(pleosNavi.state, demoNavi.state, _ui) { p, d, u ->
        if (u.demoNavi || !p.connected) d else p
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NaviState())

    private var trackJob: Job? = null
    private var routeJob: Job? = null
    private var payJob: Job? = null
    private var groupJob: Job? = null
    /**
     * 이번 주행에서 이미 물어본(또는 묻지 않기로 한) 목적지 이름. 같은 목적지로 두 번 묻지 않는다.
     * 하나만 기억하면, 주문할 때 매장 이름으로 덮어써져서 수령을 마친 직후 원래 목적지로 또 묻게 된다.
     */
    private val askedDestinations = mutableSetOf<String>()
    /** 내비에 목적지가 없어진 시각. 오래 비어 있었으면 주행이 끝난 것으로 보고 위 기록을 비운다 */
    private var noDestinationSince: Long? = null
    /** 목적지를 정해서 "주문할까요?"를 묻는 중 */
    private var asking = false
    private var announced = mutableSetOf<String>()
    private var suggestedThisTrip = false

    init {
        car.start()
        pleosNavi.start()
        pleosNavi.watch(Defaults.store)
        // 주문 기록을 앱이 스스로 채우지 않는다. 기록이 없으면 제안도 "평소처럼"도 나오지 않는다.
        // 시연용 기록은 데모 패널의 "기록 초기화"(resetRoutine)로만 넣는다.
        _ui.update { it.copy(routineDemo = routine.isDemo, suggestion = routine.suggest(it.menu)) }
        refreshServer()
        // 실제 내비가 4초 안에 안 붙으면 데모 주행 모드로
        viewModelScope.launch {
            delay(4000)
            if (!pleosNavi.state.value.connected) {
                _ui.update { it.copy(demoNavi = true) }
                toast("Pleos 내비 미연결 → 데모 주행 모드")
            }
        }
        // 주행이 시작되고 경로 위에 매장이 있으면, 차가 먼저 말을 건다 (한 번만)
        viewModelScope.launch {
            combine(car.state, navi) { c, n -> c to n }.collect { (c, n) ->
                val u = _ui.value
                // 예전 시안(기존, A, B, C)에서만 쓰는 동작. 기본 화면은 아래의 목적지 감지를 쓴다.
                val legacy = DraftSwitch.current != DraftSwitch.MIXED
                if (legacy && !suggestedThisTrip && u.phase == Phase.HOME && !c.parked && n.tripActive && (n.etaSec ?: 0) > 60 && u.suggestion != null) {
                    suggestedThisTrip = true
                    startSuggestion()
                }
            }
        }
        // 기본 화면: 내비에 목적지가 정해지면 주문할지 묻는다 (목적지가 DT 매장이면 그 매장 메뉴를 연다)
        viewModelScope.launch {
            navi.collect { n -> if (DraftSwitch.current == DraftSwitch.MIXED) onDestination(n) }
        }
    }

    private fun onDestination(n: NaviState) {
        val name = n.destinationName?.takeIf { it.isNotBlank() }
        val t = System.currentTimeMillis()
        if (name == null) {
            if (noDestinationSince == null) noDestinationSince = t
            return
        }
        // 목적지가 한동안 없다가 다시 생겼으면 새 주행이다. 내비가 경로를 다시 잡는 동안 잠깐 비는 것과는 구분한다
        noDestinationSince?.let { if (t - it > TRIP_GAP_MS) askedDestinations.clear() }
        noDestinationSince = null
        val u = _ui.value
        if (u.phase != Phase.HOME || u.order != null || name in askedDestinations) return
        val dtStore = u.stores.firstOrNull { it.name == name }
        if (dtStore != null) {
            // 목적지가 드라이브스루 매장이면 그 매장 메뉴를 바로 연다
            askedDestinations += name
            selectStore(dtStore, openMenu = true)
            return
        }
        // 가는 길에서 가장 덜 돌아가는 매장을 고른다
        val near = nearbyStores(u.stores, n).firstOrNull() ?: return
        // 현재 위치나 목적지 좌표를 아직 모르면 판단을 미룬다 (다음 내비 값에서 다시 본다)
        val detour = near.detourM ?: return
        askedDestinations += name
        // 많이 돌아가야 하는 매장을 "가는 길에 있다"고 권하지 않는다 (근처 목록에는 그대로 보인다)
        if (detour > ASK_MAX_DETOUR_M) return
        val best = near.store
        selectStore(best, openMenu = false)
        asking = true
        _ui.update { it.copy(phase = Phase.SUGGEST, suggestText = "가는 길에 ${best.name}이 있어요.\n주문할까요?") }
        voice.speak("가는 길에 ${best.name}이 있어요. 주문할까요?") { listenForAnswer() }
    }

    /** 매장을 고른다. 그 매장의 메뉴를 서버에서 받아 온다 */
    fun selectStore(store: Store, openMenu: Boolean = false) {
        _ui.update { it.copy(store = store, cart = emptyList()) }
        pleosNavi.watch(store)
        viewModelScope.launch {
            runCatching { api.menu(store.id) }.onSuccess { (s, menu) ->
                _ui.update { it.copy(store = s, menu = menu, suggestion = suggestFor(menu)) }
            }
            if (openMenu) _ui.update { it.copy(phase = Phase.MENU) }
        }
    }

    /** 주문 기록에서 뽑은 제안. 이 매장 메뉴에 없는 것이 섞여 있으면 쓰지 않는다 */
    private fun suggestFor(menu: List<MenuItem>): Suggestion? =
        routine.suggest(menu)?.takeIf { s -> s.lines.all { l -> menu.any { it.id == l.menuId } } }

    /** "지난 주문 그대로": 제안된 메뉴를 담고 결제 화면으로 간다 */
    fun reorder() {
        val s = _ui.value.suggestion ?: return
        _ui.update { it.copy(cart = s.lines, phase = Phase.PAY) }
    }

    fun showNavi() = pleosNavi.showNavi()

    fun onPermissionsResult() {
        car.refresh()
        voice.initStt()
    }

    // ---------------- 서버 ----------------
    fun refreshServer() = viewModelScope.launch {
        val ok = api.health()
        _ui.update { it.copy(serverOk = ok) }
        if (ok) runCatching { api.menu() }.onSuccess { (store, menu) ->
            _ui.update { it.copy(store = store, menu = menu, suggestion = routine.suggest(menu)) }
            pleosNavi.watch(store)
        }
        if (ok) runCatching { api.stores() }.onSuccess { list -> if (list.isNotEmpty()) _ui.update { it.copy(stores = list) } }
    }

    fun setServer(url: String, phoneUrl: String) {
        api.baseUrl = url.trim()
        prefs.edit().putString("server", api.baseUrl).putString("phone", phoneUrl.trim()).apply()
        _ui.update { it.copy(serverUrl = api.baseUrl, phoneUrl = phoneUrl.trim()) }
        refreshServer()
    }

    // ---------------- 1) 차가 먼저 제안 → 말 한마디로 주문 ----------------
    fun startSuggestion() {
        val u = _ui.value
        val s = routine.suggest(u.menu) ?: run {
            toast("주문 기록이 없어요. 메뉴에서 골라주세요")
            return
        }
        val etaMin = ((navi.value.etaSec ?: 360) / 60).coerceAtLeast(1)
        val text = "${s.spoken}, ${etaMin}분 뒤 지나는 ${u.store.shortName}에서 받을까요?"
        _ui.update { it.copy(phase = Phase.SUGGEST, suggestion = s, suggestText = text) }
        voice.speak(text) { listenForAnswer() }
    }

    private fun listenForAnswer() {
        if (_ui.value.phase != Phase.SUGGEST) return
        voice.listen(6000) { heard ->
            when (Answer.parse(heard)) {
                true -> answer(true)
                false -> answer(false)
                null -> if (heard != null) voice.speak("네 또는 아니요로 말씀해 주세요.") { listenForAnswer() }
            }
        }
    }

    fun answer(yes: Boolean) {
        voice.cancelListen()
        if (_ui.value.phase != Phase.SUGGEST) return
        if (asking) {
            // 기본 화면: "예"면 주문 화면으로 들어간다. 여기서 바로 결제하지 않는다.
            asking = false
            if (yes) openMenu() else _ui.update { it.copy(phase = Phase.HOME) }
            return
        }
        if (!yes) {
            _ui.update { it.copy(phase = Phase.HOME) }
            voice.speak("알겠어요. 필요하면 다시 불러주세요.")
            return
        }
        val s = _ui.value.suggestion ?: return
        // 주행 중이라 화면 결제 대신 음성 확인 = 결제 승인 (등록된 CarPay 카드)
        placeOrder(s.lines, spokenConfirm = true)
    }

    // ---------------- 2) 정차 중 메뉴 주문 + 동승자 QR ----------------
    fun openMenu() {
        _ui.update { it.copy(phase = Phase.MENU) }
        // 동승자와 같이 담기(QR)는 예전 시안에만 있다. 기본 화면에서는 쓰지 않으므로 서버에 묻지 않는다
        if (DraftSwitch.current != DraftSwitch.MIXED && _ui.value.groupCode == null) startGroup()
    }

    fun add(menuId: String) = _ui.update { u ->
        val i = u.cart.indexOfFirst { it.menuId == menuId && it.who == null }
        val cart = if (i >= 0) u.cart.toMutableList().also { it[i] = it[i].copy(qty = it[i].qty + 1) } else u.cart + CartLine(menuId)
        u.copy(cart = cart)
    }

    fun remove(menuId: String) = _ui.update { u ->
        val i = u.cart.indexOfFirst { it.menuId == menuId && it.who == null }
        if (i < 0) return@update u
        val line = u.cart[i]
        val cart = if (line.qty > 1) u.cart.toMutableList().also { it[i] = line.copy(qty = line.qty - 1) } else u.cart.filterIndexed { k, _ -> k != i }
        u.copy(cart = cart)
    }

    private fun startGroup() {
        groupJob?.cancel()
        groupJob = viewModelScope.launch {
            val code = runCatching { api.createGroup() }.getOrNull() ?: return@launch
            _ui.update { it.copy(groupCode = code) }
            while (isActive) {
                runCatching { api.group(code) }.onSuccess { lines -> _ui.update { it.copy(groupLines = lines) } }
                delay(2000)
            }
        }
    }

    fun goPay() {
        val u = _ui.value
        if (u.cart.isEmpty() && u.groupLines.isEmpty()) return toast("메뉴를 담아주세요")
        _ui.update { it.copy(phase = Phase.PAY) }
    }

    fun pay() {
        val u = _ui.value
        placeOrder(u.cart + u.groupLines, spokenConfirm = false)
    }

    fun back() {
        voice.cancelListen()
        _ui.update { it.copy(phase = if (it.phase == Phase.PAY) Phase.MENU else Phase.HOME) }
    }

    // ---------------- 주문 → 내비 경유지 → 도착순 추적 ----------------
    /** QR 결제(데모): 코드를 만들어 화면에 QR로 보여 주고, 폰에서 결제가 끝나면 주문을 넣는다 */
    fun payByQr() {
        val u = _ui.value
        val lines = u.cart
        if (lines.isEmpty()) return toast("메뉴를 담아주세요")
        payJob?.cancel()
        payJob = viewModelScope.launch {
            val amount = lines.sumOf { l -> (u.menu.firstOrNull { it.id == l.menuId }?.price ?: 0) * l.qty }
            val code = runCatching { api.payCreate(amount, u.store.name) }.getOrNull()
            if (code == null) {
                toast("서버에 연결할 수 없어요 (${u.serverUrl})")
                return@launch
            }
            _ui.update { it.copy(payCode = code) }
            while (isActive) {
                delay(1500)
                if (runCatching { api.payPaid(code) }.getOrDefault(false)) {
                    _ui.update { it.copy(payCode = null) }
                    placeOrder(lines, spokenConfirm = false, payment = "QR(데모)")
                    break
                }
            }
        }
    }

    fun cancelQr() {
        payJob?.cancel()
        _ui.update { it.copy(payCode = null) }
    }

    /** 내비 위에 뜨는 작은 창에 보낼 문구 (TrackingService가 그린다) */
    private fun publishStatus(o: OrderView) {
        val store = _ui.value.store.shortName
        val step = when (o.status) {
            "ready", "done" -> 2
            "making" -> 1
            else -> 0
        }
        TrackingService.step = step
        TrackingService.head = when (step) {
            2 -> "조리가 끝났어요"
            1 -> "조리 중이에요"
            else -> "주문이 접수됐어요"
        }
        // 접수 단계에서는 "도착에 맞춰 언제 만들기 시작하는지"를 같이 보여 준다 (이 앱의 핵심)
        TrackingService.sub = if (step == 0) listOfNotNull(store, o.startHint).joinToString(", ") else store
        val eta = navi.value.etaSec
        TrackingService.left = when {
            eta == null -> ""
            eta >= 60 -> "${(eta + 30) / 60}분"
            else -> "곧 도착"
        }
    }

    private fun placeOrder(lines: List<CartLine>, spokenConfirm: Boolean, payment: String = "CarPay(데모)") {
        if (lines.isEmpty()) return
        val u = _ui.value
        _ui.update { it.copy(paying = true) }
        viewModelScope.launch {
            delay(if (spokenConfirm) 300 else 1200) // CarPay 결제 처리 연출 (데모: 실제 청구 없음)
            if (_ui.value.demoNavi) demoNavi.ensureTrip(u.store.name)
            val n = navi.value
            val result = runCatching {
                api.createOrder(lines, carLabel = "내 차, Pleos", etaSec = n.etaSec, distanceM = n.distanceM, storeId = u.store.id, payment = payment)
            }
            _ui.update { it.copy(paying = false) }
            result.onSuccess { order ->
                routine.record(lines)
                groupJob?.cancel()
                announced.clear()
                _ui.update { it.copy(phase = Phase.TRACK, order = order, justOrdered = true, cart = emptyList(), groupLines = emptyList(), groupCode = null) }
                // 경로를 넣으면 내비가 화면 앞으로 나와 우리 앱을 가린다.
                // 그래서 "주문 완료" 카드를 먼저 보여 주고 나서 경로를 넣는다.
                // 우리가 내비에 넣은 매장을 "새 목적지"로 착각해 다시 묻지 않게 한다
                askedDestinations += u.store.name
                publishStatus(order)
                // 내비 위에 작은 창을 띄울 수 있으면 바로 내비로 넘어가고, 없으면 주문 완료 화면을 먼저 보여 준다
                val popup = android.provider.Settings.canDrawOverlays(getApplication())
                routeJob?.cancel()
                routeJob = viewModelScope.launch {
                    delay(if (popup) 500 else ORDER_CONFIRM_MS)
                    // 목적지가 이미 있으면 매장을 경유지로 넣고, 없으면 매장을 목적지로 안내한다
                    pleosNavi.routeVia(u.store)
                    delay(700)
                    pleosNavi.showNavi()
                    _ui.update { it.copy(justOrdered = false) }
                }
                voice.speak("주문이 접수됐어요. ${u.store.shortName}으로 안내할게요.")
                // 내비 화면 뒤로 가도 추적이 멈추지 않게 한다 (TrackingService 설명 참고)
                TrackingService.start(getApplication())
                startTracking(order.id)
            }.onFailure {
                toast("서버에 연결할 수 없어요 (${u.serverUrl})")
                if (_ui.value.phase == Phase.SUGGEST) _ui.update { s -> s.copy(phase = Phase.HOME) }
            }
        }
    }

    private fun startTracking(orderId: String) {
        trackJob?.cancel()
        trackJob = viewModelScope.launch {
            while (isActive) {
                val n = navi.value
                val c = car.state.value
                runCatching { api.telemetry(orderId, n.etaSec, n.distanceM, c.speedKph, n.offRoute) }
                    .onSuccess { o -> onOrderUpdate(o) }
                val arrivedAtWindow = n.arrived || ((n.distanceM ?: Long.MAX_VALUE) <= 30 && c.speedKph < 3f)
                // 도착 알림이 서버에 안 닿았으면 여기서 멈추지 않고 다음 바퀴에 다시 보낸다
                val atWindow = if (arrivedAtWindow) runCatching { api.arrived(orderId) }.getOrNull() else null
                if (atWindow != null) {
                    onArrived(atWindow)
                    // 도착했는데 아직 만드는 중이면, 다 될 때까지 지켜보다가 화면을 "준비됐어요"로 바꾼다.
                    // 전에는 여기서 추적을 끝내서 화면이 "거의 다 됐어요"에 머물렀다.
                    var cur: OrderView = atWindow
                    while (isActive && cur.stillPreparing) {
                        delay(2000)
                        cur = runCatching { api.order(orderId) }.getOrNull() ?: continue
                        if (cur.stillPreparing) _ui.update { it.copy(order = cur) } else onArrived(cur, afterWait = true)
                    }
                    break
                }
                delay(3000)
            }
        }
    }

    private fun onOrderUpdate(o: OrderView) {
        _ui.update { it.copy(order = o) }
        publishStatus(o)
        fun once(key: String, text: String) {
            if (announced.add(key)) voice.speak(text)
        }
        when {
            o.status == "making" -> once("making", "도착 시간에 맞춰 지금 만들기 시작했어요.")
            o.status == "ready" -> once("ready", "주문하신 메뉴가 준비됐어요. 도착하면 바로 받으세요.")
            o.late -> once("late", "주문이 몰려 조금 늦을 수 있어요.")
        }
    }

    /** afterWait: 도착해서 잠깐 기다린 끝에 준비가 된 경우 */
    private fun onArrived(o: OrderView, afterWait: Boolean = false) {
        _ui.update { it.copy(phase = Phase.ARRIVED, order = o) }
        val preparing = o.stillPreparing
        TrackingService.head = if (preparing) "거의 다 됐어요" else if (afterWait) "준비됐어요" else "도착했어요"
        TrackingService.sub = if (preparing) "주문 ${o.no}번, 잠시만 기다려 주세요" else "주문 ${o.no}번, 창구에서 받아 주세요"
        TrackingService.left = ""
        TrackingService.step = if (preparing) 1 else 2
        when {
            preparing -> voice.speak("거의 다 됐어요. 잠시만 기다려 주세요.")
            afterWait -> voice.speak("준비됐어요. 창구에서 받아 주세요.")
            else -> voice.speak("딱 맞춰 완성됐어요. 바로 받으세요!")
        }
    }

    fun finish() {
        trackJob?.cancel()
        routeJob?.cancel()
        payJob?.cancel()
        asking = false
        TrackingService.stop(getApplication())
        // 이번 주문에서 내비에 넣은 매장 경로를 정리한다 (다음 주문 때 같은 매장이 겹쳐 쌓이지 않게)
        pleosNavi.clearRoute()
        suggestedThisTrip = false
        demoNavi.reset()
        _ui.update { it.copy(phase = Phase.HOME, order = null, justOrdered = false, payCode = null, cart = emptyList(), suggestion = suggestFor(it.menu), routineDemo = routine.isDemo) }
    }

    // ---------------- 데모 패널 (에뮬레이터 시연용) ----------------
    fun toggleDemoPanel() = _ui.update { it.copy(demoPanel = !it.demoPanel) }
    fun setDemoNavi(on: Boolean) = _ui.update { it.copy(demoNavi = on) }
    fun demoStartTrip() {
        _ui.update { it.copy(demoNavi = true) }
        suggestedThisTrip = false
        demoNavi.startTrip(360, _ui.value.store.name)
        car.demoDrive(42f)
    }
    fun demoJam() = demoNavi.jam(180).also { toast("정체 발생: 도착 +3분 → 매장 순서 재계산") }
    fun demoNear() { demoNavi.nearStore(); car.demoDrive(8f) }
    /** 시연 녹화용: 데모 주행의 남은 시간을 맞춘다 (6분을 다 기다리지 않고 도착 직전 장면을 찍을 때) */
    fun demoEta(sec: Int) = demoNavi.setRemaining(sec)
    fun demoArrive() { demoNavi.arriveNow(); car.demoDrive(0f) }
    fun demoPark() = car.demoPark()
    fun demoDrive() = car.demoDrive(42f)
    fun demoRealCar() = car.setDemo(false)

    /** 시연용: 운전자가 내비에 (DT 매장이 아닌) 목적지를 정한 상황 */
    fun demoDestination() {
        _ui.update { it.copy(demoNavi = true) }
        askedDestinations.clear()
        // 같은 목적지를 다시 정해도 묻도록 한 번 비웠다가 넣는다.
        // 값이 그대로면 내비 상태가 바뀌지 않아, "아니요"를 누른 뒤 다시 눌렀을 때 아무 일도 일어나지 않았다.
        viewModelScope.launch {
            demoNavi.clearDestination()
            delay(200)
            demoNavi.setDestination("판교역", 37.3948, 127.1112)
        }
    }

    /** 시험용: 실제 순정 내비에 목적지를 정한다 (데모 주행이 아니라 실제 내비 값으로 흐름을 돌려 볼 때) */
    fun demoRealDestination() {
        _ui.update { it.copy(demoNavi = false) }
        askedDestinations.clear()
        pleosNavi.routeTo("판교역", 37.3948, 127.1112)
    }

    /** 시연용: 근처 목록의 i번째 매장을 고르고 메뉴를 연다 */
    fun demoSelectStore(i: Int) {
        val list = nearbyStores(_ui.value.stores, navi.value)
        list.getOrNull(i)?.let { selectStore(it.store, openMenu = true) }
    }

    /** 시연용: 지금 메뉴의 첫 번째 것을 담는다 */
    fun demoAddFirst() {
        _ui.value.menu.firstOrNull()?.let { add(it.id) }
    }
    fun resetRoutine() {
        routine.clear()
        routine.seedDemoIfEmpty()
        _ui.update { it.copy(suggestion = routine.suggest(it.menu), routineDemo = routine.isDemo) }
        toast("주문 기록을 데모 데이터로 초기화했어요")
    }

    fun toast(text: String) {
        _ui.update { it.copy(toast = text) }
        viewModelScope.launch {
            delay(2500)
            _ui.update { if (it.toast == text) it.copy(toast = null) else it }
        }
    }

    override fun onCleared() {
        TrackingService.stop(getApplication())
        voice.release()
        pleosNavi.stop()
        car.stop()
    }
}
