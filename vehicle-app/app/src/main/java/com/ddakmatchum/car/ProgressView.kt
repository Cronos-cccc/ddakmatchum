package com.ddakmatchum.car

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * 주문 진행 줄: 접수, 조리 중, 조리 완료 세 점과 그 위를 따라가는 작은 차.
 * 내비 위 작은 창(TrackingService)과 앱의 주문 상태 화면(ui/Main.kt)이 같은 그림을 쓴다.
 * 차 그림은 이 앱을 위해 직접 그린 것이다. 다른 회사의 캐릭터를 본뜨지 않았다.
 */
class ProgressView(context: Context) : View(context) {
    var step: Int = 0
        set(v) {
            field = v.coerceIn(0, 2)
            invalidate()
        }
    var labels: List<String> = listOf("접수", "조리 중", "조리 완료")
    var labelPx: Float = 32f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val regular: Typeface? = runCatching { Typeface.create(resources.getFont(R.font.asta_sans), 400, false) }.getOrNull()
    private val bold: Typeface? = runCatching { Typeface.create(resources.getFont(R.font.asta_sans), 700, false) }.getOrNull()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val car = h * 0.52f
        val y = car + h * 0.12f
        val pad = car * 0.80f
        val x0 = pad
        val x1 = w - pad
        val xs = floatArrayOf(x0, (x0 + x1) / 2, x1)
        val lineH = h * 0.05f
        p.style = Paint.Style.FILL
        p.color = TRACK
        c.drawRoundRect(RectF(x0, y - lineH / 2, x1, y + lineH / 2), lineH, lineH, p)
        p.color = BLUE
        c.drawRoundRect(RectF(x0, y - lineH / 2, xs[step], y + lineH / 2), lineH, lineH, p)
        for (i in 0..2) {
            p.color = if (i <= step) BLUE else TRACK
            c.drawCircle(xs[i], y, lineH * 1.15f, p)
        }
        p.textSize = labelPx
        for (i in 0..2) {
            p.color = if (i == step) INK else if (i < step) BLUE_DARK else MUTE
            p.typeface = if (i == step) bold else regular
            p.textAlign = when (i) {
                0 -> Paint.Align.LEFT
                1 -> Paint.Align.CENTER
                else -> Paint.Align.RIGHT
            }
            val tx = when (i) {
                0 -> x0 - lineH * 1.2f
                1 -> xs[1]
                else -> x1 + lineH * 1.2f
            }
            c.drawText(labels[i], tx, y + lineH + labelPx * 1.3f, p)
        }
        drawCar(c, xs[step], y - lineH * 1.5f, car)
    }

    /**
     * 르르르(현대차 캐릭터)를 단순하게 그린 것: 크림색 몸, 더듬이 두 개, 파란 가방. 발 아래 가운데가 (cx, bottom).
     * 공식 그림이 아니라 사용자 요청으로 직접 그린 단순한 모양이다. 현대차 로고(H)는 그리지 않는다.
     * 제출물에 써도 되는지는 사용자가 주최 측에 확인하기로 했다. 안 되면 이 함수만 다른 그림으로 바꾼다.
     */
    private fun drawCar(c: Canvas, cx: Float, bottom: Float, size: Float) {
        // 르르르가 작은 오픈카를 운전하는 모습. 오른쪽이 앞이다.
        val s = size * 0.84f // 더듬이까지 그림 영역 안에 들어오게 줄인다
        val wheelR = s * 0.15f
        val bw = s * 1.55f
        val bh = s * 0.40f
        val left = cx - bw / 2
        val bodyBottom = bottom - wheelR * 0.9f
        val bodyTop = bodyBottom - bh
        p.typeface = regular
        p.strokeCap = Paint.Cap.ROUND

        // 르르르 (운전석, 차 가운데보다 조금 뒤)
        val hx = cx - bw * 0.07f
        val hw = s * 0.52f
        val headTop = bodyTop - s * 0.44f
        p.color = BODY
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.055f
        c.drawLine(hx - hw * 0.20f, headTop + s * 0.10f, hx - hw * 0.34f, headTop - s * 0.14f, p)
        c.drawLine(hx + hw * 0.20f, headTop + s * 0.10f, hx + hw * 0.30f, headTop - s * 0.14f, p)
        p.style = Paint.Style.FILL
        c.drawCircle(hx - hw * 0.34f, headTop - s * 0.16f, s * 0.065f, p)
        c.drawCircle(hx + hw * 0.30f, headTop - s * 0.16f, s * 0.065f, p)
        c.drawRoundRect(RectF(hx - hw / 2, headTop, hx + hw / 2, bodyTop + bh * 0.6f), hw * 0.5f, hw * 0.5f, p)
        // 얼굴 (앞쪽을 본다)
        p.color = INK
        c.drawCircle(hx - hw * 0.04f, headTop + s * 0.19f, s * 0.036f, p)
        c.drawCircle(hx + hw * 0.28f, headTop + s * 0.19f, s * 0.036f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.028f
        c.drawArc(RectF(hx + hw * 0.03f, headTop + s * 0.19f, hx + hw * 0.21f, headTop + s * 0.30f), 20f, 140f, false, p)
        // 가방 끈
        p.color = BLUE
        p.strokeWidth = s * 0.055f
        c.drawLine(hx - hw * 0.42f, headTop + s * 0.30f, hx + hw * 0.20f, bodyTop + bh * 0.1f, p)
        // 앞 유리와 운전대
        p.color = INK
        p.strokeWidth = s * 0.045f
        c.drawLine(hx + hw * 0.80f, bodyTop + bh * 0.1f, hx + hw * 1.02f, bodyTop - s * 0.26f, p)
        c.drawLine(hx + hw * 0.50f, bodyTop + bh * 0.05f, hx + hw * 0.66f, bodyTop - s * 0.10f, p)
        p.style = Paint.Style.FILL

        // 차
        p.color = BLUE
        c.drawRoundRect(RectF(left, bodyTop, left + bw, bodyBottom), bh * 0.5f, bh * 0.5f, p)
        p.color = Color.WHITE
        c.drawRoundRect(RectF(left + bw * 0.12f, bodyTop + bh * 0.42f, left + bw * 0.80f, bodyTop + bh * 0.54f), bh * 0.1f, bh * 0.1f, p)
        p.color = TINT
        c.drawCircle(left + bw * 0.93f, bodyTop + bh * 0.46f, s * 0.055f, p)
        p.color = INK
        c.drawCircle(left + bw * 0.22f, bodyBottom, wheelR, p)
        c.drawCircle(left + bw * 0.78f, bodyBottom, wheelR, p)
        p.color = Color.WHITE
        c.drawCircle(left + bw * 0.22f, bodyBottom, wheelR * 0.42f, p)
        c.drawCircle(left + bw * 0.78f, bodyBottom, wheelR * 0.42f, p)
    }

    companion object {
        // 색은 현대차 캐릭터 르르르의 공식 행사 사진에서 뽑았다: 가방의 파랑, 몸의 크림색. 글자는 검정과 회색.
        val BLUE = Color.parseColor("#3F9DD1")
        /** 르르르 몸 색 (행사 사진에서 뽑은 크림색) */
        val BODY = Color.parseColor("#F2D29C")
        /** 지난 단계 이름과 작은 글자. 파랑 글자는 흰 바탕에서 명암비가 모자라 짙은 회색을 쓴다 */
        val BLUE_DARK = Color.parseColor("#444444")
        val TINT = Color.parseColor("#F7E3B5")
        val CORAL = Color.parseColor("#E0A840")
        val INK = Color.parseColor("#111111")
        val MUTE = Color.parseColor("#8A8A8A")
        val TRACK = Color.parseColor("#E3E3E3")
    }
}
