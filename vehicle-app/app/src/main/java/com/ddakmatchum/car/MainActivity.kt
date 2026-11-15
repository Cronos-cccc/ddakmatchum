package com.ddakmatchum.car

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.ddakmatchum.car.ui.Accent
import com.ddakmatchum.car.ui.App
import com.ddakmatchum.car.ui.DraftApp
import com.ddakmatchum.car.ui.DraftSwitch
import com.ddakmatchum.car.ui.MainApp

class MainActivity : ComponentActivity() {
    private val vm: DtViewModel by viewModels()

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionsResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 마이크(음성 주문), 속도(차선 진입 판단)
        permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, "android.car.permission.CAR_SPEED"))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) {
                // 0 = 처음 만든 화면(ui/Screens.kt), 그 외 = 새 화면(ui/Drafts.kt). 기본은 DraftSwitch.MIXED
                when (DraftSwitch.current) {
                    DraftSwitch.MIXED -> MainApp(vm) // 기본 화면 (ui/Main.kt)
                    0 -> App(vm)
                    else -> DraftApp(vm)
                }
            }
        }
        handleDemoIntent(intent)
    }

    // 우리 화면이 앞에 있을 때는 내비 위의 작은 창을 숨긴다
    override fun onResume() {
        super.onResume()
        TrackingService.appInFront = true
    }

    override fun onPause() {
        TrackingService.appInFront = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDemoIntent(intent)
    }

    /**
     * 시연 녹화용. 화면을 누르지 않고 PC에서 단계를 넘긴다. debug 빌드에서만 동작한다
     * (release에서는 다른 앱이 같은 명령으로 주문을 넘길 수 없게 막는다).
     * adb shell am start --user 10 -f 0x20000000 -n com.ddakmatchum.car/.MainActivity --ei design 4 --es demo start
     * design: 0 기존, 1 A, 2 B, 3 C, 4 기본
     * demo: start, jam, near, arrive, park, drive, suggest, yes, no, menu, finish, panel, reset
     */
    private fun handleDemoIntent(i: Intent?) {
        if (i == null) return
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
        if (i.hasExtra("design")) DraftSwitch.current = i.getIntExtra("design", DraftSwitch.MIXED).coerceIn(0, DraftSwitch.names.size - 1)
        when (i.getStringExtra("demo")) {
            "start" -> vm.demoStartTrip()
            "jam" -> vm.demoJam()
            "near" -> vm.demoNear()
            "eta" -> vm.demoEta(i.getIntExtra("sec", 90)) // 남은 시간을 sec초로 맞춘다 (--ei sec 90)
            "arrive" -> vm.demoArrive()
            "park" -> vm.demoPark()
            "drive" -> vm.demoDrive()
            "suggest" -> vm.startSuggestion()
            "yes" -> vm.answer(true)
            "no" -> vm.answer(false)
            "menu" -> vm.openMenu()
            "finish" -> vm.finish()
            "panel" -> vm.toggleDemoPanel()
            "reset" -> vm.resetRoutine()
            // 기본 화면용: dest(목적지를 정한 상황), store(--ei index n번째 매장 열기), add(첫 메뉴 담기),
            // gopay(결제 화면), paycar(차량 간편결제), payqr(QR 결제), back, navi(내비 보기)
            "dest" -> vm.demoDestination()
            "realdest" -> vm.demoRealDestination() // 실제 순정 내비에 목적지를 정한다
            "store" -> vm.demoSelectStore(i.getIntExtra("index", 0))
            "add" -> vm.demoAddFirst()
            "gopay" -> vm.goPay()
            "paycar" -> vm.pay()
            "payqr" -> vm.payByQr()
            "back" -> vm.back()
            "navi" -> vm.showNavi()
        }
        i.removeExtra("demo")
    }
}
