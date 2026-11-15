# 윈도우 설치, 실행 가이드 (RAM 16GB 기준)

## 빠른 길: 더블클릭 4번

프로젝트 폴더(`ddakmatchum`)에 있는 파일을 번호 순서대로 더블클릭하면 됩니다. 처음 한 번만 30분~1시간 걸려요 (대부분 다운로드).

> 폴더는 `C:\ddakmatchum` 처럼 **한글이 없는 경로**에 두세요. 바탕화면이나 한글 이름 폴더 아래에 두면 안드로이드 빌드가 실패할 수 있어요.

| 순서 | 파일 | 하는 일 |
|---|---|---|
| 1 | `1-setup.bat` | Node.js, Android Studio(JDK), Android SDK, Pleos Connect 에뮬레이터 이미지, 에뮬레이터 기기를 `C:\Android` 아래에 한 번에 설치. 관리자 권한은 필요 없음. C 드라이브 여유 공간 35GB 이상 필요 |
| 2 | `2-start-emulator.bat` | Pleos 에뮬레이터 실행 (부팅 1~2분). **화면이 뜬 뒤 3분은 건드리지 말고 기다릴 것** (바로 조작하면 화면이 검게 변하는데, 그러면 창을 닫고 다시 실행). 기본은 소프트웨어 그래픽. 그래픽 카드를 쓰려면 명령 창에서 `2-start-emulator.bat host` (화면이 멈추거나 앱 설치가 안 되면 기본값으로 돌아갈 것) |
| 3 | `3-build-install.bat` | 차량 앱 빌드 → 에뮬레이터에 설치 → 실행. 에뮬레이터 홈 화면이 뜬 다음에 실행 |
| 4 | `4-start-server.bat` | 매장 서버 실행, 브라우저로 매장 화면 열기 |

- `1-setup.bat`은 끝난 단계를 건너뛰니까, 중간에 끊기면 그냥 다시 실행하면 됩니다.
- 실행하면 Android SDK 라이선스와 Pleos Connect SDK 라이선스에 동의하는 것으로 처리돼요.
- "Windows의 PC 보호" 파란 창이 뜨면 **추가 정보 → 실행**.
- 앱이 뜨면 아래 **시연 순서**로 넘어가세요.

아래 0~4번은 같은 일을 손으로 하는 방법입니다. 빠른 길이 막혔을 때만 보세요.

---

순서대로 하면 됩니다. 처음 한 번만 1~2시간 걸려요 (대부분 다운로드).

## 0. 미리 확인 (5분)

- **가상화 켜져 있는지**: `Ctrl+Shift+Esc` → 성능 → CPU → 오른쪽 아래 **가상화: 사용**. "사용 안 함"이면 BIOS에서 Intel VT-x / AMD SVM을 켜야 해요.
- **Windows 기능**: 시작 → "Windows 기능 켜기/끄기" → **Windows 하이퍼바이저 플랫폼** 체크 → 재부팅.
- **16GB 팁**: 에뮬레이터에 4GB를 주니까, 실행 중엔 크롬 탭, 게임 등은 닫아 두세요. 그래픽 드라이버도 최신으로.

## 1. Node.js 설치 → 매장 서버 실행 (10분)

1. https://nodejs.org 에서 **LTS** 설치 (설치 화면 기본값 그대로)
2. `ddakmatchum\server` 폴더에서 주소창에 `cmd` 입력 → 엔터
3. 실행:
   ```
   node server.js
   ```
4. 브라우저에서 **http://localhost:8787/store** → 매장 화면이 뜨면 성공
5. **▶ 정체 시나리오** 버튼 → 가상 차 5대가 주문하고, 한 대가 정체에 걸리면 순서가 바뀌는 걸 볼 수 있어요
6. 방화벽 허용 창이 뜨면 **허용** (폰 QR 주문 때 필요)

> 서버 창은 데모 내내 켜 두세요. 끄면 차 앱이 "매장 서버 끊김"으로 바뀌어요.

## 2. Android Studio 설치 (30분)

1. https://developer.android.com/studio 에서 최신 버전 설치, 처음 실행 시 **Standard** 설치

## 3. Pleos 에뮬레이터 설치 (30분, 공식 문서 순서)

1. Android Studio → **Tools > SDK Manager**
2. **SDK Update Sites** 탭 → **+**
   - Name: `Pleos Connect System Image`
   - URL: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
3. **SDK Platforms** 탭 → 아래 **Show Package Details** 체크 → **Android API 34** 아래 `Pleos Connect system Image` 체크 → OK → 설치 → Android Studio 재시작
4. **Tools > Device Manager** → **Create device** → 왼쪽 **Automotive** → `Pleos Connect v2.1.0` → Next
5. **Advanced Settings**: Default boot = **Cold**, RAM = **4 GB**, (구버전이면 Graphics = Hardware)
6. 시스템 이미지 **API 34, Pleos Connect v2.1.0** 선택 → Finish
7. ▶ 눌러 실행. 첫 부팅은 몇 분 걸려요.

공식 문서: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator

## 4. 차량 앱 실행 (10분)

1. Android Studio → **Open** → `ddakmatchum\vehicle-app` 폴더 선택
2. 오른쪽 아래 Gradle Sync가 끝날 때까지 대기 (Pleos SDK를 nexus-playground.pleos.ai에서 받아요)
3. 위쪽 기기 선택에서 Pleos 에뮬레이터 → **▶ Run**
4. 권한 창 (마이크, 차량 속도) → **허용**
5. 내비 위 주문 카드를 보려면 "다른 앱 위에 표시" 권한을 켜요 (한 번만):
   `adb shell appops set --user 10 com.ddakmatchum.car SYSTEM_ALERT_WINDOW allow`
6. 앱 첫 화면에 **근처 드라이브스루** 3곳이 뜨면 끝 (3곳이 아니면 매장 서버가 켜져 있는지 확인)

### 시연 순서

1. 매장 화면(브라우저)과 에뮬레이터를 나란히 띄우기
2. 차 앱 오른쪽 위 **데모** → **목적지 정함 (판교역)** → "가는 길에 판교 딱맞춤DT점이 있어요. 주문할까요?"
3. **예** (또는 마이크에 "응") → 메뉴를 눌러 담기 → **결제** → **차량 간편결제**
4. 순정 내비가 앞으로 나오고 그 위에 주문 카드가 뜸. 매장 화면에 주문 등장
5. 매장 화면 **▶ 정체 시나리오** → 다른 차들 등장
6. 주문 카드를 눌러 앱으로 돌아온 뒤 데모 패널 **정체 +3분** → 매장 화면에서 내 차의 조리 시작이 뒤로 밀리고 다른 차가 앞으로
7. **매장 앞** → **도착** → 주문 번호 화면 → **완료**

## 폰으로 QR 결제하기

1. PC와 폰을 **같은 와이파이**에
2. 서버 실행 창에 찍힌 `같은 와이파이 폰: http://192.168.x.x:8787` 주소 확인
3. 차 앱 **데모** → "QR 주소"에 그 주소 입력 → **저장, 연결 확인**
4. 결제 화면에서 **QR 결제** → QR을 폰 카메라로 찍기 (모의 결제, 실제로 청구되지 않음)

## 막히면

| 증상 | 해결 |
|---|---|
| Gradle Sync에서 `ai.pleos.playground` 못 찾음 | 인터넷 확인 → File > Sync Project with Gradle Files 재시도 |
| 에뮬레이터 검은 화면, 튕김 | Device Manager → 편집 → Graphics를 Hardware ↔ Software 바꿔보기, Cold boot |
| 근처 매장이 3곳으로 안 나옴 | 서버 창이 켜져 있는지, 데모 패널 서버 주소가 `http://10.0.2.2:8787`인지 |
| 음성이 안 나옴 | 에뮬레이터 볼륨 확인. 버튼으로도 전부 진행 가능 |
| 내비 위에 주문 카드가 안 뜸 | 위 4번의 `appops` 명령을 실행했는지 확인 |
| 너무 느림 | 다른 프로그램 닫기, 에뮬레이터 RAM은 4GB 유지 |

## 제출용 APK 만들기

Android Studio → **Build > Build App Bundle(s) / APK(s) > Build APK(s)** → `vehicle-app\app\build\outputs\apk\` 의 APK를 `apk\딱맞춤_release.apk` 로 복사해서 GitHub에 올리면 돼요. (공모전 규정: 저장소 `apk/` 폴더, 파일명 `팀명_앱이름_release.apk`)
