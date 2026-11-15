package com.ddakmatchum.car.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/*
 * 메뉴 그림. 사진 대신 직접 그린 단순한 그림이다 (실제 브랜드의 사진이나 로고를 쓰지 않는다).
 * 색은 그 음식의 실제 색을 쓴다 (커피는 갈색, 빵은 구운 색, 채소는 초록).
 * 앱의 파랑은 "지금 고른 것, 진행 중인 것"을 나타내는 색이라 음식에는 쓰지 않는다.
 */

/** 메뉴 그림의 바탕. 흰 컵과 흰 크림도, 노란 감자튀김도 다 보이는 옅은 크림색 */
val FoodBg = Color(0xFFF6E7C6)

private val Coffee = Color(0xFF4A2C1A)      // 아메리카노
private val CoffeeMilk = Color(0xFFC9996B)  // 라떼
private val Choco = Color(0xFF7A4A2B)       // 초코 프라페
private val Cola = Color(0xFF3B1D12)
private val Lemon = Color(0xFFF4C430)
private val LemonRind = Color(0xFFE2A91B)
private val Cream = Color(0xFFFFFFFF)
private val Ice = Color(0xE6FFFFFF)
private val Lid = Color(0xFF2E2A27)         // 뚜껑, 빨대, 김 (따뜻한 검정)
private val Steam = Color(0xFF9A8F85)
private val Bun = Color(0xFFD98E3B)         // 구운 빵
private val Bread = Color(0xFFEBC384)       // 식빵
private val Crust = Color(0xFFB9772F)
private val Patty = Color(0xFF5B3420)
private val Lettuce = Color(0xFF6BAF4B)
private val Cheese = Color(0xFFF6C435)
private val Ham = Color(0xFFE88F8F)
private val Tomato = Color(0xFFD9443A)      // 토마토, 감자튀김 상자, 콜라 빨대
private val Fry = Color(0xFFF0B429)
private val Nugget = Color(0xFFD9952F)
private val NuggetDark = Color(0xFFC07F22)
private val Cookie = Color(0xFFCB8D4E)
private val Mint = Color(0xFF3F9A6B)        // 에이드 빨대
private val Vanilla = Color(0xFFF1D78A)

private enum class Food { ICED, HOT, LATTE, VANILLA, FRAPPE, ADE, COLA, SANDWICH, COOKIE, BURGER, FRIES, NUGGET }

private fun foodOf(id: String): Food = when {
    id == "americano-ice" -> Food.ICED
    id == "americano-hot" -> Food.HOT
    id == "vanilla-latte" -> Food.VANILLA
    id.contains("latte") -> Food.LATTE
    id == "frappe" -> Food.FRAPPE
    id == "ade" -> Food.ADE
    id == "cola" -> Food.COLA
    id == "sandwich" -> Food.SANDWICH
    id == "cookie" -> Food.COOKIE
    id.startsWith("burger") -> Food.BURGER
    id == "fries" -> Food.FRIES
    id == "nuggets" -> Food.NUGGET
    else -> Food.ICED
}

@Composable
fun FoodArt(id: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        /** 뚜껑 있는 차가운 컵. fill은 안에 든 음료의 색 */
        fun cup(fill: Color, straw: Color? = Lid) {
            if (straw != null) drawLine(straw, Offset(w * 0.55f, h * 0.30f), Offset(w * 0.64f, h * 0.07f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
            val body = Path().apply {
                moveTo(w * 0.30f, h * 0.30f)
                lineTo(w * 0.70f, h * 0.30f)
                lineTo(w * 0.63f, h * 0.86f)
                lineTo(w * 0.37f, h * 0.86f)
                close()
            }
            drawPath(body, fill)
            drawRoundRect(Lid, Offset(w * 0.26f, h * 0.24f), Size(w * 0.48f, h * 0.07f), CornerRadius(h * 0.035f))
        }

        /** 흰 머그잔. liquid는 위에서 보이는 음료의 색 */
        fun mug(liquid: Color, body: Color = Cream) {
            listOf(0.36f, 0.50f).forEach { x ->
                drawLine(Steam, Offset(w * x, h * 0.31f), Offset(w * (x + 0.03f), h * 0.17f), strokeWidth = w * 0.03f, cap = StrokeCap.Round)
            }
            drawArc(body, -90f, 180f, false, Offset(w * 0.58f, h * 0.48f), Size(w * 0.22f, h * 0.24f), style = Stroke(w * 0.055f))
            drawRoundRect(body, Offset(w * 0.22f, h * 0.38f), Size(w * 0.48f, h * 0.44f), CornerRadius(w * 0.09f))
            drawRoundRect(liquid, Offset(w * 0.27f, h * 0.43f), Size(w * 0.38f, h * 0.10f), CornerRadius(h * 0.05f))
        }

        when (foodOf(id)) {
            Food.ICED -> {
                cup(Coffee)
                drawRoundRect(Ice, Offset(w * 0.39f, h * 0.40f), Size(w * 0.11f, w * 0.11f), CornerRadius(w * 0.02f))
                drawRoundRect(Ice, Offset(w * 0.50f, h * 0.55f), Size(w * 0.10f, w * 0.10f), CornerRadius(w * 0.02f))
                drawRoundRect(Ice, Offset(w * 0.41f, h * 0.68f), Size(w * 0.08f, w * 0.08f), CornerRadius(w * 0.02f))
            }
            Food.HOT -> mug(Coffee)
            Food.LATTE -> {
                mug(CoffeeMilk)
                // 우유 거품
                drawCircle(Cream, w * 0.035f, Offset(w * 0.46f, h * 0.48f))
            }
            Food.VANILLA -> {
                // 흰 잔에 바닐라색 띠를 둘러 카페 라떼와 구분한다
                mug(CoffeeMilk)
                drawCircle(Cream, w * 0.035f, Offset(w * 0.46f, h * 0.48f))
                drawRect(Vanilla, Offset(w * 0.22f, h * 0.61f), Size(w * 0.48f, h * 0.08f))
            }
            Food.FRAPPE -> {
                cup(Choco)
                // 휘핑크림과 초코 조각
                drawCircle(Cream, w * 0.11f, Offset(w * 0.42f, h * 0.23f))
                drawCircle(Cream, w * 0.11f, Offset(w * 0.56f, h * 0.23f))
                drawCircle(Cream, w * 0.10f, Offset(w * 0.49f, h * 0.14f))
                drawCircle(Choco, w * 0.02f, Offset(w * 0.44f, h * 0.18f))
                drawCircle(Choco, w * 0.02f, Offset(w * 0.54f, h * 0.15f))
            }
            Food.ADE -> {
                cup(Lemon, straw = Mint)
                drawRoundRect(Ice, Offset(w * 0.48f, h * 0.44f), Size(w * 0.10f, w * 0.10f), CornerRadius(w * 0.02f))
                drawRoundRect(Ice, Offset(w * 0.41f, h * 0.62f), Size(w * 0.09f, w * 0.09f), CornerRadius(w * 0.02f))
                // 컵에 꽂은 레몬 조각
                drawCircle(LemonRind, w * 0.11f, Offset(w * 0.32f, h * 0.30f))
                drawCircle(Color(0xFFFBE58A), w * 0.08f, Offset(w * 0.32f, h * 0.30f))
                drawCircle(LemonRind, w * 0.015f, Offset(w * 0.32f, h * 0.30f))
            }
            Food.COLA -> {
                cup(Cola, straw = Tomato)
                drawRoundRect(Ice, Offset(w * 0.40f, h * 0.42f), Size(w * 0.10f, w * 0.10f), CornerRadius(w * 0.02f))
                drawCircle(Ice, w * 0.02f, Offset(w * 0.55f, h * 0.60f))
                drawCircle(Ice, w * 0.015f, Offset(w * 0.47f, h * 0.70f))
            }
            Food.SANDWICH -> {
                // 세모로 자른 샌드위치: 빵 사이에 상추, 햄, 치즈
                val t = Path().apply {
                    moveTo(w * 0.18f, h * 0.78f)
                    lineTo(w * 0.82f, h * 0.78f)
                    lineTo(w * 0.50f, h * 0.22f)
                    close()
                }
                drawPath(t, Crust)
                val inner = Path().apply {
                    moveTo(w * 0.25f, h * 0.74f)
                    lineTo(w * 0.75f, h * 0.74f)
                    lineTo(w * 0.50f, h * 0.30f)
                    close()
                }
                drawPath(inner, Bread)
                drawLine(Lettuce, Offset(w * 0.33f, h * 0.66f), Offset(w * 0.67f, h * 0.66f), strokeWidth = w * 0.045f, cap = StrokeCap.Round)
                drawLine(Ham, Offset(w * 0.37f, h * 0.59f), Offset(w * 0.63f, h * 0.59f), strokeWidth = w * 0.045f, cap = StrokeCap.Round)
                drawLine(Cheese, Offset(w * 0.41f, h * 0.52f), Offset(w * 0.59f, h * 0.52f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
            }
            Food.COOKIE -> {
                drawCircle(Crust, w * 0.31f, Offset(w * 0.5f, h * 0.5f))
                drawCircle(Cookie, w * 0.28f, Offset(w * 0.5f, h * 0.5f))
                listOf(0.40f to 0.40f, 0.60f to 0.44f, 0.44f to 0.60f, 0.61f to 0.63f, 0.52f to 0.50f).forEach { (x, y) ->
                    drawCircle(Coffee, w * 0.035f, Offset(w * x, h * y))
                }
            }
            Food.BURGER -> {
                // 위에서부터: 깨 뿌린 빵, 상추, 치즈, 패티, 아래 빵
                drawArc(Bun, 180f, 180f, true, Offset(w * 0.20f, h * 0.20f), Size(w * 0.60f, h * 0.52f))
                listOf(0.38f to 0.32f, 0.50f to 0.27f, 0.62f to 0.33f).forEach { (x, y) ->
                    drawOval(Cream, Offset(w * x, h * y), Size(w * 0.045f, h * 0.025f))
                }
                drawRoundRect(Lettuce, Offset(w * 0.16f, h * 0.47f), Size(w * 0.68f, h * 0.07f), CornerRadius(h * 0.035f))
                drawRoundRect(Cheese, Offset(w * 0.19f, h * 0.54f), Size(w * 0.62f, h * 0.045f), CornerRadius(h * 0.02f))
                drawRoundRect(Patty, Offset(w * 0.20f, h * 0.59f), Size(w * 0.60f, h * 0.10f), CornerRadius(h * 0.04f))
                drawRoundRect(Bun, Offset(w * 0.20f, h * 0.71f), Size(w * 0.60f, h * 0.10f), CornerRadius(h * 0.05f))
            }
            Food.FRIES -> {
                listOf(0.33f, 0.42f, 0.51f, 0.60f).forEachIndexed { i, x ->
                    drawRoundRect(Fry, Offset(w * x, h * (0.15f + (i % 2) * 0.06f)), Size(w * 0.07f, h * 0.44f), CornerRadius(w * 0.02f))
                }
                val box = Path().apply {
                    moveTo(w * 0.27f, h * 0.47f)
                    lineTo(w * 0.73f, h * 0.47f)
                    lineTo(w * 0.66f, h * 0.86f)
                    lineTo(w * 0.34f, h * 0.86f)
                    close()
                }
                drawPath(box, Tomato)
                drawRoundRect(Cream, Offset(w * 0.44f, h * 0.60f), Size(w * 0.12f, h * 0.10f), CornerRadius(w * 0.03f))
            }
            Food.NUGGET -> {
                drawOval(NuggetDark, Offset(w * 0.36f, h * 0.22f), Size(w * 0.30f, h * 0.22f))
                drawOval(Nugget, Offset(w * 0.18f, h * 0.44f), Size(w * 0.32f, h * 0.24f))
                drawOval(Nugget, Offset(w * 0.46f, h * 0.52f), Size(w * 0.34f, h * 0.26f))
                // 찍어 먹는 소스
                drawCircle(Tomato, w * 0.07f, Offset(w * 0.76f, h * 0.30f))
            }
        }
    }
}
