package com.ddakmatchum.car.net

import com.ddakmatchum.car.CartLine
import com.ddakmatchum.car.MenuItem
import com.ddakmatchum.car.OrderView
import com.ddakmatchum.car.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 딱맞춤 서버와 통신 (의존성 없이 HttpURLConnection + org.json) */
class Api(var baseUrl: String) {

    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 3000
            conn.readTimeout = 5000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
            if (code !in 200..299) throw IllegalStateException("서버 오류 $code: $text")
            JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun health(): Boolean = runCatching { call("GET", "/api/health").optBoolean("ok") }.getOrDefault(false)

    private fun parseStore(s: JSONObject) =
        Store(s.getString("id"), s.getString("name"), s.getDouble("latitude"), s.getDouble("longitude"), s.optString("address"), s.optString("brand"))

    /** 드라이브스루 매장 목록. 가까운 순 정렬은 앱이 현재 위치로 한다 (내비 SDK에 장소 검색이 없다) */
    suspend fun stores(): List<Store> {
        val arr = call("GET", "/api/stores").getJSONArray("stores")
        return (0 until arr.length()).map { parseStore(arr.getJSONObject(it)) }
    }

    /** QR 결제(데모) 코드를 만든다 */
    suspend fun payCreate(amount: Int, storeName: String): String =
        call("POST", "/api/pay", JSONObject().put("amount", amount).put("storeName", storeName)).getString("code")

    suspend fun payPaid(code: String): Boolean = call("GET", "/api/pay/$code").optBoolean("paid", false)

    suspend fun menu(storeId: String? = null): Pair<Store, List<MenuItem>> {
        val j = call("GET", "/api/menu" + (storeId?.let { "?store=$it" } ?: ""))
        val store = parseStore(j.getJSONObject("store"))
        val arr = j.getJSONArray("menu")
        val menu = (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            MenuItem(
                id = m.getString("id"),
                name = m.getString("name"),
                price = m.getInt("price"),
                prepSec = m.getInt("prepSec"),
                emoji = m.optString("emoji", "☕"),
                tag = m.optString("tag").ifBlank { null },
            )
        }
        return store to menu
    }

    suspend fun createOrder(
        lines: List<CartLine>, carLabel: String, etaSec: Long?, distanceM: Long?,
        storeId: String? = null, payment: String = "CarPay(데모)",
    ): OrderView {
        val items = JSONArray()
        lines.groupBy { it.menuId }.forEach { (id, ls) ->
            items.put(JSONObject().put("menuId", id).put("qty", ls.sumOf { it.qty }))
        }
        val body = JSONObject()
            .put("items", items)
            .put("carLabel", carLabel)
            .put("source", "car")
            .put("payment", payment)
        if (storeId != null) body.put("storeId", storeId)
        if (etaSec != null) body.put("eta", JSONObject().put("sec", etaSec).put("distanceM", distanceM ?: JSONObject.NULL))
        return parseOrder(call("POST", "/api/orders", body))
    }

    suspend fun telemetry(id: String, etaSec: Long?, distanceM: Long?, speedKph: Float, offRoute: Boolean): OrderView {
        val body = JSONObject()
            .put("etaSec", etaSec ?: JSONObject.NULL)
            .put("distanceM", distanceM ?: JSONObject.NULL)
            .put("speedKph", speedKph.toDouble())
            .put("offRoute", offRoute)
        return parseOrder(call("POST", "/api/orders/$id/telemetry", body))
    }

    suspend fun arrived(id: String): OrderView = parseOrder(call("POST", "/api/orders/$id/arrived", JSONObject()))

    /** 주문의 지금 상태 (도착한 뒤 "아직 준비 중"이 끝났는지 볼 때 쓴다) */
    suspend fun order(id: String): OrderView = parseOrder(call("GET", "/api/orders/$id"))

    suspend fun createGroup(): String = call("POST", "/api/groups", JSONObject()).getString("code")

    suspend fun group(code: String): List<CartLine> {
        val arr = call("GET", "/api/groups/$code").getJSONArray("items")
        return (0 until arr.length()).map { i ->
            val it = arr.getJSONObject(i)
            CartLine(it.getString("menuId"), it.optInt("qty", 1), it.optString("who", "동승자"))
        }
    }

    private fun parseOrder(j: JSONObject): OrderView {
        val now = j.optLong("now", System.currentTimeMillis())
        val action = j.optJSONObject("action")
        val readyAt = if (j.has("readyAt") && !j.isNull("readyAt")) j.optLong("readyAt") else null
        return OrderView(
            id = j.getString("id"),
            no = j.getInt("no"),
            status = j.optString("status", "waiting"),
            summary = j.optString("summary"),
            amount = j.optInt("amount"),
            rank = if (j.has("rank")) j.optInt("rank") else null,
            actionKind = action?.optString("kind"),
            actionLabel = action?.optString("label"),
            late = j.optBoolean("late", false),
            inLane = j.optBoolean("inLane", false),
            bay = if (j.has("bay") && !j.isNull("bay")) j.optInt("bay") else null,
            arrivalInSec = if (j.has("arrivalInSec")) j.optLong("arrivalInSec") else null,
            readyInSec = readyAt?.let { (it - now) / 1000 },
            prepSec = j.optInt("prepSec"),
        )
    }
}
