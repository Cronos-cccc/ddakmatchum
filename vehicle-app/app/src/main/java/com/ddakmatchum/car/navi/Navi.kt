package com.ddakmatchum.car.navi

import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ddakmatchum.car.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class NaviState(
    val connected: Boolean = false,
    val source: String = "연결 전",
    val destinationName: String? = null,
    /** 매장(경유지 또는 목적지)까지 남은 시간 */
    val etaSec: Long? = null,
    /** 매장까지 직선 거리 */
    val distanceM: Long? = null,
    val arrived: Boolean = false,
    val offRoute: Boolean = false,
    val tripActive: Boolean = false,
    /** 내비에 정해진 목적지 좌표 (없으면 null) */
    val destLat: Double? = null,
    val destLon: Double? = null,
    /** 현재 위치. 근처 매장을 가까운 순으로 정렬할 때 쓴다 */
    val curLat: Double? = null,
    val curLon: Double? = null,
)

/**
 * Pleos 순정 내비 연동 (NaviHelper SDK 3.0.5)
 * - 매장을 경유지로 추가 (목적지가 없으면 목적지로)
 * - 매장까지 남은 시간(onDrivingInfo)과 현재 위치(onCurrentLocationInfo)를 받는다
 */
class PleosNavi(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var helper: NaviHelper? = null
    private val _state = MutableStateFlow(NaviState(source = "Pleos NaviHelper"))
    val state: StateFlow<NaviState> = _state.asStateFlow()

    private var target: Store? = null
    private var hasDestination = false
    private var polling = false
    private var drivingSeen = false
    private var lastLoggedDestination: String? = null
    private var locationLogged = false

    // 내비가 마지막으로 알려 준 목적지와 경유지 이름. 같은 매장을 경로에 두 번 넣지 않으려고 쓴다.
    private var naviDestinationName: String? = null
    private var naviWaypointNames: List<String> = emptyList()
    // 이번 주문에서 우리가 내비에 넣은 것. 수령이 끝나면 이것만 정리한다.
    private var startedRoute = false
    private var addedWaypoint = false

    private val poll = object : Runnable {
        override fun run() {
            if (!polling) return
            helper?.let { h ->
                runCatching {
                    h.getCurrentLocationInfo()
                    h.getDestinationInfo()
                    if (target != null) h.getWaypointInfo()
                }
            }
            main.postDelayed(this, POLL_MS)
        }
    }

    private val listener = object : NaviHelperEventListener {
        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            val t = target
            val wp = if (t != null) drivingInfo.waypoints.firstOrNull { it.name == t.name } else null
            val leg = wp ?: drivingInfo.destination
            val eta = num(leg.duration)
            Log.d(TAG, "onDrivingInfo dest=${drivingInfo.destination.name} duration=${drivingInfo.destination.duration} distance=${drivingInfo.destination.distance} waypoints=${drivingInfo.waypoints.map { "${it.name}:${it.duration}:${it.distance}" }}")
            // 에뮬레이터에서 확인: duration은 초, distance는 미터.
            // 경로를 잡는 순간 이름이 비고 duration이 0인 값이 한 번 온다. 그대로 쓰면 매장이 "지금 도착"으로 보고
            // 제조를 시작해 버리므로 무시한다. 실제 도착은 onDestinationArrived, onWayPointArrived로 처리한다.
            if (eta == null || eta <= 0 || leg.name.isBlank()) return
            drivingSeen = true
            _state.update { it.copy(etaSec = eta, tripActive = true, offRoute = false) }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            hasDestination = destinationInfo.latitude != 0.0
            naviDestinationName = destinationInfo.name.takeIf { it.isNotBlank() }
            if (destinationInfo.name != lastLoggedDestination) {
                lastLoggedDestination = destinationInfo.name
                Log.d(TAG, "onDestinationInfo name=${destinationInfo.name} remainTime=${destinationInfo.remainTime} remainDistance=${destinationInfo.remainDistance}")
            }
            // 주행 정보(onDrivingInfo)는 차가 달릴 때만 온다. 그 전에는 목적지가 매장이면 목적지의 남은 시간을 쓴다.
            val t = target
            val fallback = if (!drivingSeen && t != null && destinationInfo.name == t.name) num(destinationInfo.remainTime)?.takeIf { it > 0 } else null
            _state.update {
                it.copy(
                    destinationName = destinationInfo.name,
                    destLat = destinationInfo.latitude.takeIf { hasDestination },
                    destLon = destinationInfo.longitude.takeIf { hasDestination },
                    etaSec = fallback ?: it.etaSec,
                    tripActive = it.tripActive || fallback != null,
                )
            }
        }

        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
            // 매장이 경유지로 들어간 경우: 경유지의 남은 시간을 쓴다 (주행 정보가 오기 전까지)
            naviWaypointNames = waypointInfoList.map { it.name }
            val t = target ?: return
            if (drivingSeen) return
            val wp = waypointInfoList.firstOrNull { it.name == t.name } ?: return
            Log.d(TAG, "onWayPointInfo name=${wp.name} remainTime=${wp.remainTime} remainDistance=${wp.remainDistance}")
            val eta = num(wp.remainTime)?.takeIf { it > 0 } ?: return
            _state.update { it.copy(etaSec = eta, tripActive = true) }
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            val lat = (currentLocationInfo.latitude as? Number)?.toDouble() ?: return
            val lon = (currentLocationInfo.longitude as? Number)?.toDouble() ?: return
            val t = target
            val d = t?.let { distanceM(lat, lon, it.latitude, it.longitude) }
            if (!locationLogged) {
                locationLogged = true
                Log.d(TAG, "onCurrentLocationInfo lat=$lat lon=$lon straightDistanceToStore=${d}m")
            }
            _state.update { it.copy(curLat = lat, curLon = lon, distanceM = d ?: it.distanceM) }
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            _state.update { it.copy(tripActive = true, arrived = false) }
        }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = Unit

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            _state.update { it.copy(arrived = true, etaSec = 0) }
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            _state.update { it.copy(arrived = true, etaSec = 0) }
        }

        override fun onRouteCancelled() {
            forgetRoute()
            _state.update { it.copy(offRoute = true) }
        }

        override fun onRouteEnded() {
            forgetRoute()
        }
    }

    /**
     * 내비의 안내가 끝났거나 취소됐다. 내비에서 읽어 둔 목적지와 경유지 이름만 비운다 (3초마다 다시 읽는다).
     * startedRoute, addedWaypoint는 여기서 지우지 않는다. 내비는 경로를 잡는 도중에도 onRouteEnded를 한 번 보내는데,
     * 그때 지우면 수령 완료 때 우리가 넣은 경로를 정리하지 못한다 (에뮬레이터에서 실제로 겪음).
     */
    private fun forgetRoute() {
        naviDestinationName = null
        naviWaypointNames = emptyList()
        hasDestination = false
    }

    fun start() {
        if (helper != null) return
        val h = runCatching { NaviHelper(app) }.getOrNull()
        if (h == null) {
            _state.update { it.copy(source = "NaviHelper 없음") }
            return
        }
        helper = h
        runCatching {
            h.initialize(
                onConnected = {
                    main.post {
                        h.addListener(listener)
                        _state.update { it.copy(connected = true, source = "Pleos NaviHelper") }
                        polling = true
                        main.post(poll)
                    }
                },
                onFailed = { _ ->
                    main.post { _state.update { it.copy(connected = false, source = "NaviHelper 연결 실패") } }
                },
            )
        }.onFailure { _state.update { s -> s.copy(source = "NaviHelper 연결 실패") } }
    }

    /** 매장을 경유지로 넣는다. 목적지가 없으면 매장을 목적지로. */
    fun routeVia(store: Store) {
        target = store
        drivingSeen = false
        _state.update { it.copy(arrived = false, offRoute = false) }
        val h = helper ?: return

        // 같은 매장이 이미 경로에 있으면 다시 넣지 않는다.
        // 전에는 두 번째 주문부터 같은 매장을 경유지로 계속 추가해서, 내비가 "경유지는 최대 2개까지"라고 막았다.
        val alreadyDestination = naviDestinationName == store.name
        val alreadyWaypoint = naviWaypointNames.any { it == store.name }
        if (alreadyDestination || alreadyWaypoint || startedRoute || addedWaypoint) {
            Log.d(TAG, "routeVia: 매장이 이미 경로에 있어 다시 넣지 않음 (목적지=$alreadyDestination 경유지=$alreadyWaypoint 이번주문에넣음=${startedRoute || addedWaypoint})")
            return
        }

        runCatching {
            if (hasDestination) {
                addedWaypoint = true
                Log.d(TAG, "routeVia: 기존 목적지(${naviDestinationName}) 앞에 매장을 경유지로 추가")
                h.addWaypoint(
                    RequestWaypointInfo(
                        latitude = store.latitude,
                        longitude = store.longitude,
                        poiName = store.name,
                        waypointIndex = WaypointIndex.FIRST,
                        poiId = "",
                        address = store.address,
                        poiSubId = "0",
                        poiProvider = "ddakmatchum",
                    ),
                )
            } else {
                startedRoute = true
                Log.d(TAG, "routeVia: 목적지가 없어 매장을 목적지로 경로 요청")
                h.requestRoute(
                    RouteInfo(
                        latitude = store.latitude,
                        longitude = store.longitude,
                        displayLatitude = null,
                        displayLongitude = null,
                        poiName = store.name,
                        poiId = "",
                        address = store.address,
                        poiSubId = "0",
                    ),
                )
            }
        }
    }

    /**
     * 수령이 끝났을 때 부른다. 이번 주문에서 우리가 내비에 넣은 것만 정리한다.
     * 매장을 경유지로 넣었으면 그 경유지를 빼고(원래 목적지 안내는 계속), 매장이 목적지였으면 안내를 끝낸다.
     * 사용자가 직접 정한 목적지나 경유지는 건드리지 않는다.
     */
    fun clearRoute() {
        val h = helper
        val t = target
        Log.d(TAG, "clearRoute: 우리가넣은목적지=$startedRoute 우리가넣은경유지=$addedWaypoint 내비목적지=$naviDestinationName 내비경유지=$naviWaypointNames")
        if (h != null && t != null) {
            runCatching {
                if (addedWaypoint && naviWaypointNames.firstOrNull() == t.name) {
                    Log.d(TAG, "clearRoute: 우리가 넣은 경유지(매장) 제거")
                    h.removeWaypoint(WaypointIndex.FIRST)
                } else if (startedRoute && naviDestinationName == t.name) {
                    // 내비의 목적지가 아직 매장일 때만 끝낸다. 사용자가 이미 안내를 끄거나 목적지를 바꿨으면 건드리지 않는다.
                    Log.d(TAG, "clearRoute: 우리가 시작한 매장 안내 종료")
                    h.cancelRoute()
                }
            }
        }
        startedRoute = false
        addedWaypoint = false
    }

    fun watch(store: Store) {
        target = store
    }

    /** 시험용: 순정 내비에 (매장이 아닌) 목적지를 정한다. 운전자가 내비에서 목적지를 고른 것과 같은 상태가 된다 */
    fun routeTo(name: String, lat: Double, lon: Double) {
        runCatching {
            helper?.requestRoute(
                RouteInfo(latitude = lat, longitude = lon, displayLatitude = null, displayLongitude = null, poiName = name, poiId = "", address = "", poiSubId = "0"),
            )
            Log.d(TAG, "routeTo: 시험용 목적지 $name 요청")
        }.onFailure { Log.d(TAG, "routeTo 실패: ${it.message}") }
    }

    /** 순정 내비 화면을 앞으로 띄운다. 지도는 내비에 맡긴다 */
    fun showNavi() {
        runCatching { helper?.requestNavigationScreen(NavigationScreenRequest.Foreground) }
            .onFailure { Log.d(TAG, "showNavi 실패: ${it.message}") }
    }

    fun stop() {
        polling = false
        main.removeCallbacksAndMessages(null)
        helper?.let {
            runCatching { it.removeListener(listener) }
            runCatching { it.release() }
        }
        helper = null
    }

    private fun num(x: Any?): Long? = (x as? Number)?.toLong()

    private companion object {
        const val POLL_MS = 3_000L
        const val TAG = "DdakNavi"
    }
}

/**
 * 데모 내비: 에뮬레이터에서 실제로 달릴 수 없으니 남은 시간을 흉내 낸다.
 * 정체(+3분), 매장 근처 진입, 도착을 버튼으로 재현한다.
 */
class DemoNavi {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(NaviState(connected = true, source = "데모 주행", curLat = 37.4127212, curLon = 127.094681))
    val state: StateFlow<NaviState> = _state.asStateFlow()
    private var running = false
    private var remaining = 0.0

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            remaining = (remaining - 1).coerceAtLeast(0.0)
            val dist = if (remaining < 20) (remaining * 3).toLong() else (remaining * 11).toLong()
            _state.update { it.copy(etaSec = remaining.toLong(), distanceM = dist, arrived = remaining <= 0.0) }
            if (remaining <= 0.0) running = false else main.postDelayed(this, 1000)
        }
    }

    fun startTrip(etaSec: Int = 360, destination: String) {
        remaining = etaSec.toDouble()
        _state.update { it.copy(tripActive = true, arrived = false, destinationName = destination, etaSec = etaSec.toLong(), distanceM = etaSec * 11L) }
        if (!running) {
            running = true
            main.postDelayed(tick, 1000)
        }
    }

    fun ensureTrip(destination: String) {
        if (!_state.value.tripActive) startTrip(360, destination)
    }

    /** 시연용: 운전자가 내비에 목적지를 정한 상황을 흉내 낸다 (판교 근처의 가상 좌표) */
    fun setDestination(name: String, lat: Double, lon: Double) {
        _state.update { it.copy(destinationName = name, destLat = lat, destLon = lon, curLat = 37.4127212, curLon = 127.094681) }
    }

    /** 시연용: 정해 둔 목적지를 지운다 (같은 목적지를 다시 정했을 때도 감지되게 하려고 쓴다) */
    fun clearDestination() {
        _state.update { it.copy(destinationName = null, destLat = null, destLon = null) }
    }

    fun jam(addSec: Int = 180) {
        remaining += addSec
    }

    /** 매장 150m 앞, DT 차선 진입 직전 */
    fun nearStore() {
        remaining = 40.0
    }

    /** 시연 녹화용: 남은 시간을 원하는 값(초)으로 맞춘다 */
    fun setRemaining(sec: Int) {
        remaining = sec.coerceAtLeast(1).toDouble()
    }

    fun arriveNow() {
        remaining = 0.0
        _state.update { it.copy(etaSec = 0, distanceM = 0, arrived = true) }
    }

    fun reset() {
        running = false
        main.removeCallbacksAndMessages(null)
        _state.value = NaviState(connected = true, source = "데모 주행", curLat = 37.4127212, curLon = 127.094681)
    }
}

fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Long {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return (r * 2 * atan2(sqrt(a), sqrt(1 - a))).toLong()
}
