package com.ddakmatchum.car

data class Store(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    /** 가상 브랜드 이름 (실제 브랜드는 쓰지 않는다) */
    val brand: String = "",
) {
    /** 음성과 화면에 쓰는 이름. 짧은 이름("판교 딱맞춤DT점")은 그대로 쓰고, 길면 마지막 단어만 쓴다 */
    val shortName: String get() = if (name.length <= 12) name else (name.split(" ").lastOrNull() ?: name)
}

data class MenuItem(
    val id: String,
    val name: String,
    val price: Int,
    val prepSec: Int,
    val emoji: String,
    val tag: String? = null,
)

data class CartLine(val menuId: String, val qty: Int = 1, val who: String? = null)

data class OrderView(
    val id: String,
    val no: Int,
    val status: String, // waiting, making, ready, done, cancelled
    val summary: String,
    val amount: Int,
    val rank: Int?,
    val actionKind: String?,
    val actionLabel: String?,
    val late: Boolean,
    val inLane: Boolean,
    val bay: Int?,
    val arrivalInSec: Long?,
    val readyInSec: Long?,
    val prepSec: Int,
) {
    /** 도착했는데 아직 만드는 중 (서버가 bay 값을 준다). 다 되면 서버가 상태를 done으로 바꾼다 */
    val stillPreparing: Boolean get() = bay != null && status != "ready" && status != "done"

    /**
     * 접수 단계에서 보여 줄 한마디. 매장은 주문을 받자마자 만들지 않고 도착 시간에 맞춰 만들기 시작한다.
     * 그 기다리는 시간을 알려 준다 ("3분 뒤 조리 시작"). 알 수 없거나 이미 시작할 때면 null.
     */
    val startHint: String?
        get() {
            val startIn = (readyInSec ?: return null) - prepSec
            return when {
                startIn >= 60 -> "${(startIn + 30) / 60}분 뒤 조리 시작"
                startIn > 0 -> "곧 조리 시작"
                else -> null
            }
        }
}

/** 서버 없이도 화면이 뜨도록 같은 데모 메뉴를 앱에도 둔다 */
object Defaults {
    // 서버(server/data.js)와 같은 값. 서버에 연결되면 서버 값으로 덮어쓴다.
    val store = Store("dt-001", "판교 딱맞춤DT점", 37.4054, 127.1031, "경기 성남시 분당구")
    val menu = listOf(
        MenuItem("americano-ice", "아이스 아메리카노", 4000, 60, "🧊", "인기"),
        MenuItem("americano-hot", "따뜻한 아메리카노", 4000, 50, "☕"),
        MenuItem("latte", "카페 라떼", 4800, 90, "🥛"),
        MenuItem("vanilla-latte", "바닐라 라떼", 5300, 100, "🍦"),
        MenuItem("frappe", "초코 프라페", 5900, 150, "🍫", "오래 걸림"),
        MenuItem("ade", "레몬 에이드", 5500, 80, "🍋"),
        MenuItem("sandwich", "햄치즈 샌드위치", 6500, 120, "🥪"),
        MenuItem("cookie", "초코칩 쿠키", 2800, 15, "🍪"),
    )
}
