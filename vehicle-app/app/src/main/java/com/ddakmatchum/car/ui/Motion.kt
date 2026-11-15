package com.ddakmatchum.car.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/*
 * 화면 움직임 모음.
 * 차 화면이라 원칙을 둔다: 짧게(0.15~0.45초), 한 번만, 튀지 않게. 운전자의 눈을 끄는 반복 움직임이나
 * 통통 튀는 스프링은 쓰지 않는다 (로딩 표시만 예외로 일정한 속도로 돈다).
 *  - 들어올 때는 ease-out(빨리 나와서 부드럽게 멈춤), 나갈 때는 ease-in(천천히 시작해 빨리 사라짐)
 *  - 토스트: 제자리에서 페이드
 *  - 팝업(데모 패널): 살짝 커지면서 나타남
 *  - 체크 표시: 선이 그려짐 (ease-in-out)
 *  - 로딩: 일정한 속도로 회전 (linear)
 */

private val EaseOut = LinearOutSlowInEasing
private val EaseIn = FastOutLinearInEasing
private val EaseInOut = FastOutSlowInEasing

/** 토스트: 제자리에서 페이드로 나타났다가 사라진다 */
@Composable
fun BoxScope.ToastHost(text: String?, top: Dp, fontSize: TextUnit) {
    // 사라지는 동안에도 글자가 남아 있도록 마지막 문구를 기억한다
    val last = remember { arrayOf("") }
    if (text != null) last[0] = text
    AnimatedVisibility(
        visible = text != null,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = top),
        enter = fadeIn(tween(180, easing = EaseOut)),
        exit = fadeOut(tween(220, easing = EaseIn)),
    ) {
        Box(Modifier.background(Accent, RoundedCornerShape(99.dp)).padding(horizontal = 28.dp, vertical = 14.dp)) {
            Text(last[0], color = Color(0xFF111111), fontSize = fontSize, fontWeight = FontWeight.Bold)
        }
    }
}

/** 팝업: 오른쪽 아래에서 살짝 커지면서 나타난다 (scale-in, ease-out). 나갈 때는 짧게 */
@Composable
fun BoxScope.PopupHost(visible: Boolean, content: @Composable BoxScope.() -> Unit) {
    val origin = TransformOrigin(1f, 1f)
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomEnd),
        enter = scaleIn(tween(200, easing = EaseOut), initialScale = 0.94f, transformOrigin = origin) + fadeIn(tween(160, easing = EaseOut)),
        exit = scaleOut(tween(140, easing = EaseIn), targetScale = 0.97f, transformOrigin = origin) + fadeOut(tween(140, easing = EaseIn)),
    ) {
        Box(content = content)
    }
}

/** 체크 표시: 선이 쓱 그려진다 (ease-in-out, 한 번만) */
@Composable
fun AnimatedCheck(size: Dp, color: Color) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(450, delayMillis = 120, easing = EaseInOut)) }
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val full = Path().apply { moveTo(w * 0.18f, h * 0.52f); lineTo(w * 0.42f, h * 0.76f); lineTo(w * 0.84f, h * 0.28f) }
        val measure = PathMeasure().apply { setPath(full, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * progress.value, drawn, true)
        drawPath(drawn, color, style = Stroke(w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 로딩: 일정한 속도로 돈다 (linear) */
@Composable
fun LinearSpinner(size: Dp, color: Color, stroke: Dp = 6.dp) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    Canvas(Modifier.size(size)) {
        val s = stroke.toPx()
        val topLeft = Offset(s / 2, s / 2)
        val arcSize = Size(this.size.width - s, this.size.height - s)
        drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, topLeft = topLeft, size = arcSize, style = Stroke(s))
        drawArc(color, angle - 90f, 100f, false, topLeft = topLeft, size = arcSize, style = Stroke(s, cap = StrokeCap.Round))
    }
}
