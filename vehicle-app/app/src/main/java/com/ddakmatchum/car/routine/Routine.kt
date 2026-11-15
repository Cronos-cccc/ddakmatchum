package com.ddakmatchum.car.routine

import android.content.Context
import com.ddakmatchum.car.CartLine
import com.ddakmatchum.car.MenuItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Suggestion(
    val lines: List<CartLine>,
    /** "평소처럼 아이스 아메리카노" 처럼 말로 할 문장 */
    val spoken: String,
    /** 화면에 보여줄 근거: "평일 이 시간대 3번 주문" */
    val reason: String,
)

/**
 * 주문 습관 기억: 요일 유형(평일/주말), 시간대가 비슷할 때 자주 시킨 메뉴를 먼저 제안한다.
 * 앱을 열 필요 없이 차가 먼저 말을 거는 근거 (GM Marketplace와 다른 점).
 */
class Routine(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("routine", Context.MODE_PRIVATE)

    private data class Entry(val key: String, val weekend: Boolean, val hour: Int, val at: Long)

    private fun load(): List<Entry> {
        val arr = runCatching { JSONArray(prefs.getString("entries", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(o.getString("key"), o.getBoolean("weekend"), o.getInt("hour"), o.getLong("at"))
        }
    }

    private fun save(list: List<Entry>) {
        val arr = JSONArray()
        list.takeLast(50).forEach {
            arr.put(JSONObject().put("key", it.key).put("weekend", it.weekend).put("hour", it.hour).put("at", it.at))
        }
        prefs.edit().putString("entries", arr.toString()).apply()
    }

    private fun keyOf(lines: List<CartLine>): String =
        lines.groupBy { it.menuId }.map { (id, ls) -> "$id*${ls.sumOf { it.qty }}" }.sorted().joinToString(",")

    private fun linesOf(key: String): List<CartLine> = key.split(",").filter { it.isNotBlank() }.map {
        val (id, q) = it.split("*")
        CartLine(id, q.toIntOrNull() ?: 1)
    }

    fun record(lines: List<CartLine>, at: Long = System.currentTimeMillis()) {
        val c = Calendar.getInstance().apply { timeInMillis = at }
        val weekend = c.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        save(load() + Entry(keyOf(lines), weekend, c.get(Calendar.HOUR_OF_DAY), at))
    }

    val count: Int get() = load().size

    /**
     * 시연용 주문 기록 3건을 넣는다 (화면에 데모 기록이라고 표시).
     * 앱이 스스로 넣지 않는다. 데모 패널의 "기록 초기화"나 PC 명령 reset 으로만 들어간다.
     * 기록이 없으면 차는 "평소처럼"이라고 말하지 않는다.
     * 오늘과 같은 요일 유형(평일/주말)인 최근 3일의 지금 시각으로 넣어, 어느 요일에 시연해도 같은 결과가 나오게 한다.
     */
    fun seedDemoIfEmpty() {
        if (load().isNotEmpty()) return
        val day = 24 * 3600 * 1000L
        val now = System.currentTimeMillis()
        fun weekendAt(t: Long) = Calendar.getInstance().apply { timeInMillis = t }
            .get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        val today = weekendAt(now)
        var back = 1
        var added = 0
        while (added < 3 && back <= 14) {
            val t = now - back * day
            if (weekendAt(t) == today) {
                record(listOf(CartLine("americano-ice", 1)), t)
                added++
            }
            back++
        }
        prefs.edit().putBoolean("demo", true).apply()
    }

    val isDemo: Boolean get() = prefs.getBoolean("demo", false)

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun suggest(menu: List<MenuItem>, at: Long = System.currentTimeMillis()): Suggestion? {
        val all = load()
        if (all.isEmpty()) return null
        val c = Calendar.getInstance().apply { timeInMillis = at }
        val weekend = c.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val similar = all.filter { it.weekend == weekend && kotlin.math.abs(it.hour - hour) <= 1 }
        val best = similar.groupBy { it.key }.maxByOrNull { it.value.size }
        val dayType = if (weekend) "주말" else "평일"
        // "평소처럼"은 같은 요일 유형, 같은 시간대에 3번 이상 주문한 기록이 있을 때만 쓴다
        return if (best != null && best.value.size >= 3) {
            val lines = linesOf(best.key)
            Suggestion(lines, "평소처럼 ${describe(lines, menu)}", "$dayType 이 시간대 ${best.value.size}번 주문")
        } else {
            val last = all.maxBy { it.at }
            val lines = linesOf(last.key)
            Suggestion(lines, "지난번처럼 ${describe(lines, menu)}", "최근 주문")
        }
    }

    companion object {
        fun describe(lines: List<CartLine>, menu: List<MenuItem>): String =
            lines.groupBy { it.menuId }.map { (id, ls) ->
                val name = menu.firstOrNull { it.id == id }?.name ?: id
                val q = ls.sumOf { it.qty }
                if (q > 1) "$name ${q}잔" else name
            }.joinToString(", ")
    }
}
