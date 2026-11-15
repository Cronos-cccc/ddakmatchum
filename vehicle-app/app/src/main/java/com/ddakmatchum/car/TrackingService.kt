package com.ddakmatchum.car

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 주문이 진행되는 동안 앱을 살려 두는 포그라운드 서비스.
 *
 * 주문하면 내비 앱이 앞으로 나오고 우리 앱은 뒤로 간다. 그대로 두면 안드로이드가 뒤로 간 앱을
 * 동결(cached app freezer)시켜서 남은 시간 전송이 멈추고, 매장 화면에 "차량 신호 끊김"이 뜬다
 * (Pleos 에뮬레이터에서 실제로 겪음). 주행 중에는 내비 화면을 보는 것이 정상이므로,
 * 주문부터 수령까지는 이 서비스로 추적을 계속 돌린다. 실제 일은 DtViewModel이 한다.
 *
 * 내비 위의 작은 창: 주문 상태(접수, 조리 중, 조리 완료)와 남은 시간을 내비 화면 위에 보여 준다.
 * "다른 앱 위에 표시" 권한이 있을 때만 뜬다. 권한이 없으면 알림만 남는다.
 * 에뮬레이터에서는 adb shell appops set --user 10 com.ddakmatchum.car SYSTEM_ALERT_WINDOW allow 로 허용한다.
 */
class TrackingService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var box: View? = null
    private var headView: TextView? = null
    private var subView: TextView? = null
    private var leftView: TextView? = null
    private var badge: View? = null
    private var progress: ProgressView? = null

    private val refresh = object : Runnable {
        override fun run() {
            render()
            main.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "주문 추적", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("딱맞춤 주문 진행 중")
            .setContentText("도착 시간에 맞춰 준비하고 있어요")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        showPopup()
        main.removeCallbacks(refresh)
        main.post(refresh)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(refresh)
        val v = box
        if (v != null) runCatching { getSystemService(WindowManager::class.java).removeView(v) }
        box = null
        super.onDestroy()
    }

    private fun face(weight: Int): Typeface? = runCatching {
        Typeface.create(resources.getFont(R.font.asta_sans), weight, false)
    }.getOrNull()

    private fun text(px: Float, color: Int, weight: Int) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
        setTextColor(color)
        typeface = face(weight) ?: if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        includeFontPadding = false
    }

    private fun showPopup() {
        if (box != null || !Settings.canDrawOverlays(this)) return
        val headT = text(46f, ProgressView.INK, 800)
        val subT = text(34f, Color.parseColor("#666666"), 400).apply { setPadding(0, 10, 0, 0) }
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(headT)
            addView(subT)
        }
        // 남은 시간 표시
        val leftT = text(48f, ProgressView.INK, 800)
        val badgeV = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30, 18, 30, 20)
            background = GradientDrawable().apply {
                setColor(ProgressView.TINT)
                cornerRadius = 28f
            }
            addView(text(28f, ProgressView.BLUE_DARK, 600).apply { text = "도착까지" })
            addView(leftT)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(badgeV)
        }
        val bar = ProgressView(this).apply { labelPx = 30f }
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 36, 44, 30)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(2, Color.parseColor("#DADADA"))
                cornerRadius = 36f
            }
            addView(top)
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 270).apply { topMargin = 10 })
            // 누르면 우리 앱의 주문 화면으로 돌아온다
            setOnClickListener {
                runCatching {
                    startActivity(Intent(this@TrackingService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                }
            }
        }
        val lp = WindowManager.LayoutParams(
            780, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // 내비 화면의 오른쪽 위. 내비의 방향 안내 상자(왼쪽 위)와 2D 버튼(오른쪽 끝)을 가리지 않는 자리.
            // 위쪽은 내비의 방향 안내 상자, 2D 버튼과 같은 선에 맞춘다 (지도를 덜 가리게 위로 붙임)
            gravity = Gravity.TOP or Gravity.END
            x = 190
            y = 24
        }
        runCatching {
            getSystemService(WindowManager::class.java).addView(v, lp)
            box = v
            headView = headT
            subView = subT
            leftView = leftT
            badge = badgeV
            progress = bar
        }
    }

    private fun render() {
        val v = box ?: return
        // 우리 앱이 앞에 있을 때는 같은 내용이 화면에 있으므로 숨긴다
        v.visibility = if (appInFront || head.isBlank()) View.GONE else View.VISIBLE
        headView?.text = head
        subView?.text = sub
        leftView?.text = left
        badge?.visibility = if (left.isBlank()) View.GONE else View.VISIBLE
        progress?.step = step
    }

    companion object {
        private const val CHANNEL = "order-tracking"
        private const val NOTIFICATION_ID = 1

        /** 작은 창의 첫 줄: 주문이 접수됐어요, 조리 중이에요, 조리가 끝났어요 */
        @Volatile var head: String = ""
        /** 작은 창의 둘째 줄: 매장과 순서 */
        @Volatile var sub: String = ""
        /** 남은 시간 ("4분"). 비어 있으면 표시하지 않는다 */
        @Volatile var left: String = ""
        /** 진행 단계: 0 접수, 1 조리 중, 2 조리 완료 */
        @Volatile var step: Int = 0
        /** 우리 앱 화면이 앞에 있는지 (MainActivity가 알려 준다) */
        @Volatile var appInFront: Boolean = false

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, TrackingService::class.java)) }
        }

        fun stop(context: Context) {
            head = ""
            sub = ""
            left = ""
            step = 0
            runCatching { context.stopService(Intent(context, TrackingService::class.java)) }
        }
    }
}
