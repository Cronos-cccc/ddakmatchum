# 딱맞춤 개발 환경 설치 (Windows)
# 실행: 프로젝트 폴더의 1-setup.bat 을 더블클릭
# 하는 일: Node.js, Android Studio(JDK 포함), Android SDK, Pleos Connect 에뮬레이터 이미지, 에뮬레이터 기기(AVD) 설치
# 이미 설치된 것은 건너뛰므로 중간에 끊겨도 다시 실행하면 이어서 진행된다.
# 이 스크립트를 실행하면 Android SDK 라이선스와 Pleos Connect SDK 라이선스에 동의하는 것으로 처리된다.

param(
    # 에뮬레이터 이미지(4GB 다운로드, 설치 후 약 20GB)를 건너뛰고 빌드 도구만 설치
    [switch]$SkipEmulatorImage,
    # 설치 없이 함수만 불러오기 (점검용)
    [switch]$FunctionsOnly
)

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

$PleosSourceUrl = 'https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml'
$PleosSourceName = 'Pleos Connect System Image'
$PleosImagePackage = 'system-images;android-34;pleos_car_emulator;x86_64'
$PleosDeviceId = 'Pleos_Connect_v2_0'
$AvdName = 'Pleos_Connect'
$CmdlineToolsUrl = 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip'
# 관리자 권한 창 없이 설치하려고 설치 프로그램(exe, msi) 대신 zip을 받아 사용자 폴더에 푼다
$StudioZipUrl = 'https://edgedl.me.gvt1.com/android/studio/ide-zips/2026.2.2.8/android-studio-rabbit2-windows.zip'
$Jdk21ZipUrl = 'https://aka.ms/download-jdk/microsoft-jdk-21-windows-x64.zip'
$StudioJbrSystem =Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'

function Get-File([string]$url, [string]$dest) {
    $curl = Join-Path $env:SystemRoot 'System32\curl.exe'
    if (Test-Path $curl) {
        & $curl -L --fail --retry 5 --retry-delay 5 -sS -o $dest $url
        if ($LASTEXITCODE -ne 0) { throw "다운로드에 실패했습니다: $url" }
    } else {
        Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing
    }
}

function Expand-Zip([string]$zip, [string]$destDir) {
    if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Force -Path $destDir | Out-Null }
    $tar = Join-Path $env:SystemRoot 'System32\tar.exe'
    if (Test-Path $tar) {
        & $tar -xf $zip -C $destDir
        if ($LASTEXITCODE -ne 0) { throw "압축을 푸는 데 실패했습니다: $zip" }
    } else {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [IO.Compression.ZipFile]::ExtractToDirectory($zip, $destDir)
    }
}

function Write-Step($text) { Write-Host ''; Write-Host "==> $text" -ForegroundColor Cyan }
function Write-Ok($text) { Write-Host "    확인: $text" -ForegroundColor Green }
function Write-Note($text) { Write-Host "    $text" -ForegroundColor Yellow }

function Test-NonAscii([string]$text) {
    foreach ($ch in $text.ToCharArray()) { if ([int]$ch -gt 127) { return $true } }
    return $false
}

# BOM 없는 UTF-8로 저장 (설정 파일은 BOM이 있으면 첫 줄이 깨진다)
function Write-PlainFile([string]$path, [string[]]$lines) {
    $dir = Split-Path -Parent $path
    if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    [IO.File]::WriteAllLines($path, $lines, (New-Object Text.UTF8Encoding($false)))
}

# key=value 형식 파일에서 값을 바꾸거나 없으면 추가
function Set-IniValue([string]$path, [string]$key, [string]$value) {
    $lines = @()
    if (Test-Path $path) { $lines = @(Get-Content -LiteralPath $path) }
    $found = $false
    $out = foreach ($line in $lines) {
        if ($line -match ('^\s*' + [regex]::Escape($key) + '\s*=')) { $found = $true; "$key=$value" } else { $line }
    }
    $out = @($out)
    if (-not $found) { $out += "$key=$value" }
    Write-PlainFile $path $out
}

# sdkmanager가 Pleos 이미지 목록을 보도록 사용자 저장소 등록 (Android Studio의 SDK Update Sites와 같은 파일)
function Add-PleosSource([string]$cfgPath) {
    $lines = @()
    if (Test-Path $cfgPath) { $lines = @(Get-Content -LiteralPath $cfgPath) }
    if ($lines | Where-Object { $_ -like '*nexus-playground.pleos.ai*' }) { return $false }
    $count = 0
    foreach ($line in $lines) { if ($line -match '^count=(\d+)') { $count = [int]$Matches[1] } }
    $idx = '{0:D2}' -f $count
    $kept = @($lines | Where-Object { $_ -notmatch '^count=' })
    if ($kept.Count -eq 0) { $kept = @('### User Sources for Android Repository') }
    $kept += "count=$($count + 1)"
    $kept += "enabled$idx=true"
    $kept += "disp$idx=$PleosSourceName"
    $kept += ('src' + $idx + '=' + $PleosSourceUrl.Replace(':', '\:'))
    Write-PlainFile $cfgPath $kept
    return $true
}

# local.properties 용 경로 (백슬래시는 두 번, 한글 등은 \uXXXX)
function ConvertTo-PropertiesPath([string]$path) {
    $sb = New-Object Text.StringBuilder
    foreach ($ch in $path.ToCharArray()) {
        if ($ch -eq '\') { [void]$sb.Append('\\') }
        elseif ($ch -eq ':') { [void]$sb.Append('\:') }
        elseif ([int]$ch -gt 127) { [void]$sb.Append(('\u{0:x4}' -f [int]$ch)) }
        else { [void]$sb.Append($ch) }
    }
    return $sb.ToString()
}

function Update-SessionPath {
    $machine = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $user = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = "$machine;$user"
}

function Add-UserPath([string]$dir) {
    $user = [Environment]::GetEnvironmentVariable('Path', 'User')
    if (-not $user) { $user = '' }
    $parts = $user.Split(';') | Where-Object { $_ }
    if ($parts -notcontains $dir) {
        [Environment]::SetEnvironmentVariable('Path', (($parts + $dir) -join ';'), 'User')
    }
}

if ($FunctionsOnly) { return }

# ---------------------------------------------------------------- 시작
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Write-Host '딱맞춤 개발 환경 설치를 시작합니다.' -ForegroundColor White
Write-Host '관리자 권한은 필요 없습니다. 모든 것은 C:\Android 아래에 설치됩니다.'
Write-Host '전체 30분에서 1시간 정도 걸립니다 (대부분 다운로드). 창을 닫지 마세요.'

try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

    # 한글 경로 점검: 안드로이드 빌드 도구와 에뮬레이터는 한글이 들어간 경로에서 자주 실패한다
    $projectPathBad = Test-NonAscii $ProjectRoot
    # 전부 C:\Android 아래에 설치한다.
    # 이유 1: 사용자 폴더 이름에 한글이 있어도 안전하다.
    # 이유 2: Claude 데스크톱 앱(스토어 패키지) 안에서 실행하면 %LOCALAPPDATA% 아래에 새로 만드는 폴더가
    #         앱 전용 폴더(Packages\Claude_...\LocalCache)로 돌려져서 앱 밖(더블클릭한 bat)에서는 보이지 않는다.
    $ToolsRoot = 'C:\Android'
    $SdkRoot = Join-Path $ToolsRoot 'Sdk'
    $AvdHome = Join-Path $ToolsRoot 'avd'
    Write-Host "    설치 위치: $ToolsRoot (Node.js, Android Studio, SDK, 에뮬레이터 기기)"
    $NodeDir = Join-Path $ToolsRoot 'nodejs'
    $StudioJbrUser = Join-Path $ToolsRoot 'android-studio\jbr'
    $AndroidPrefs = Join-Path $env:USERPROFILE '.android'

    # 디스크 여유 공간
    $drive = (Split-Path -Qualifier $SdkRoot).TrimEnd(':')
    $freeGb = [math]::Round((Get-PSDrive $drive).Free / 1GB)
    $needGb = 35
    if ($SkipEmulatorImage) { $needGb = 10 }
    if ($freeGb -lt $needGb) {
        Write-Note "${drive}: 드라이브 여유 공간이 ${freeGb}GB 입니다. ${needGb}GB 이상 필요합니다."
        throw '디스크 공간을 비운 뒤 다시 실행해 주세요.'
    }
    Write-Ok "${drive}: 드라이브 여유 공간 ${freeGb}GB"

    # ------------------------------------------------------------ 1. Node.js
    Write-Step '1/7 Node.js'
    $nodeExe = Join-Path $NodeDir 'node.exe'
    $nodeOnPath = Get-Command node -ErrorAction SilentlyContinue
    if ($nodeOnPath) {
        $NodeDir = Split-Path -Parent $nodeOnPath.Source
        Write-Ok ("이미 설치됨 " + (& node -v))
    } elseif (Test-Path $nodeExe) {
        Write-Ok ("이미 설치됨 " + (& $nodeExe -v))
    } else {
        # 공식 사이트의 최신 LTS zip을 사용자 폴더에 푼다 (관리자 권한 불필요)
        # Windows PowerShell 5.1은 JSON 배열을 한 덩어리로 넘기므로 변수에 먼저 담아야 한 개씩 걸러진다
        $nodeIndex = Invoke-RestMethod 'https://nodejs.org/dist/index.json'
        $lts = @($nodeIndex) | Where-Object { $_.lts } | Select-Object -First 1
        if (-not $lts -or $lts.version -notmatch '^v\d+\.\d+\.\d+$') { throw 'Node.js 버전 목록을 받지 못했습니다. 인터넷 연결을 확인해 주세요.' }
        $nodeName = "node-$($lts.version)-win-x64"
        $tmp = Join-Path $env:TEMP 'ddak-node'
        if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
        New-Item -ItemType Directory -Force -Path $tmp | Out-Null
        $zip = Join-Path $tmp "$nodeName.zip"
        Write-Host "    다운로드 중 ($($lts.version), 약 35MB)..."
        Get-File "https://nodejs.org/dist/$($lts.version)/$nodeName.zip" $zip
        $sums = (Invoke-WebRequest -Uri "https://nodejs.org/dist/$($lts.version)/SHASUMS256.txt" -UseBasicParsing).Content
        $want = [regex]::Match($sums, '([0-9a-f]{64})\s+' + [regex]::Escape("$nodeName.zip")).Groups[1].Value
        $got = (Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash.ToLower()
        if (-not $want -or $want -ne $got) { throw 'Node.js 다운로드 파일의 검증값이 맞지 않습니다. 다시 실행해 주세요.' }
        Expand-Zip $zip $tmp
        if (-not (Test-Path $ToolsRoot)) { New-Item -ItemType Directory -Force -Path $ToolsRoot | Out-Null }
        if (Test-Path $NodeDir) { Remove-Item -Recurse -Force $NodeDir }
        Move-Item -LiteralPath (Join-Path $tmp $nodeName) -Destination $NodeDir
        Remove-Item -Recurse -Force $tmp
        if (-not (Test-Path $nodeExe)) { throw 'Node.js 설치에 실패했습니다.' }
        Write-Ok ("설치 완료 " + (& $nodeExe -v))
    }
    if (-not $nodeOnPath) {
        Add-UserPath $NodeDir
        $env:Path = "$NodeDir;$env:Path"
    }

    # ------------------------------------------------------------ 2. Android Studio (JDK 포함)
    Write-Step '2/7 Android Studio (JDK 포함, 약 1.5GB 다운로드)'
    if (Test-Path (Join-Path $StudioJbrSystem 'bin\java.exe')) {
        $StudioJbr = $StudioJbrSystem
        Write-Ok '이미 설치됨'
    } elseif (Test-Path (Join-Path $StudioJbrUser 'bin\java.exe')) {
        $StudioJbr = $StudioJbrUser
        Write-Ok '이미 설치됨'
    } else {
        # zip 배포본을 사용자 폴더에 푼다 (관리자 권한 불필요). 실행 파일은 android-studio\bin\studio64.exe
        $tmp = Join-Path $env:TEMP 'ddak-studio'
        if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
        New-Item -ItemType Directory -Force -Path $tmp | Out-Null
        $zip = Join-Path $tmp 'android-studio.zip'
        Write-Host '    다운로드 중입니다. 진행률이 안 보여도 창을 닫지 마세요.'
        Get-File $StudioZipUrl $zip
        Write-Host '    압축을 푸는 중...'
        $studioDir = Split-Path -Parent $StudioJbrUser
        if (Test-Path $studioDir) { Remove-Item -Recurse -Force $studioDir }
        Expand-Zip $zip $ToolsRoot
        Remove-Item -Recurse -Force $tmp
        $StudioJbr = $StudioJbrUser
        if (-not (Test-Path (Join-Path $StudioJbr 'bin\java.exe'))) { throw "Android Studio를 푼 뒤에도 JDK를 찾지 못했습니다: $StudioJbr" }
        Write-Ok "설치 완료 ($studioDir)"
    }

    # 빌드용 JDK 21. 최신 Android Studio에 든 JDK는 25인데 이 프로젝트의 Gradle 8.10.2는 JDK 25에서
    # 빌드가 실패한다 (오류 내용이 "25.0.4" 한 줄뿐). 클라우드에서 검증한 것과 같은 JDK 21을 따로 받아 쓴다.
    $JdkDir = Join-Path $ToolsRoot 'jdk-21'
    if (Test-Path (Join-Path $JdkDir 'bin\java.exe')) {
        Write-Ok '빌드용 JDK 21 이미 설치됨'
    } else {
        $tmp = Join-Path $env:TEMP 'ddak-jdk'
        if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
        New-Item -ItemType Directory -Force -Path $tmp | Out-Null
        $zip = Join-Path $tmp 'jdk21.zip'
        Write-Host '    빌드용 JDK 21 다운로드 중 (약 200MB)...'
        Get-File $Jdk21ZipUrl $zip
        $sumText = (Invoke-WebRequest -Uri ($Jdk21ZipUrl + '.sha256sum.txt') -UseBasicParsing).Content
        if ($sumText -is [byte[]]) { $sumText = [Text.Encoding]::ASCII.GetString($sumText) }
        $want = [regex]::Match([string]$sumText, '[0-9a-fA-F]{64}').Value.ToLower()
        $got = (Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash.ToLower()
        if (-not $want -or $want -ne $got) { throw 'JDK 다운로드 파일의 검증값이 맞지 않습니다. 다시 실행해 주세요.' }
        Expand-Zip $zip $tmp
        $inner = Get-ChildItem -LiteralPath $tmp -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
        if (-not $inner) { throw 'JDK 압축을 푼 뒤 java.exe 를 찾지 못했습니다.' }
        if (Test-Path $JdkDir) { Remove-Item -Recurse -Force $JdkDir }
        Move-Item -LiteralPath $inner.FullName -Destination $JdkDir
        Remove-Item -Recurse -Force $tmp
        Write-Ok "빌드용 JDK 21 설치 완료 ($JdkDir)"
    }
    $StudioJbr = $JdkDir
    $env:JAVA_HOME = $JdkDir

    # ------------------------------------------------------------ 3. Android SDK 명령줄 도구
    Write-Step '3/7 Android SDK 명령줄 도구'
    $toolsBin = Join-Path $SdkRoot 'cmdline-tools\latest\bin'
    $sdkmanager = Join-Path $toolsBin 'sdkmanager.bat'
    $avdmanager = Join-Path $toolsBin 'avdmanager.bat'
    if (Test-Path $sdkmanager) {
        Write-Ok '이미 설치됨'
    } else {
        $tmp = Join-Path $env:TEMP 'ddak-cmdline-tools'
        if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
        New-Item -ItemType Directory -Force -Path $tmp | Out-Null
        $zip = Join-Path $tmp 'clt.zip'
        Write-Host '    다운로드 중 (약 150MB)...'
        Invoke-WebRequest -Uri $CmdlineToolsUrl -OutFile $zip -UseBasicParsing
        Expand-Archive -LiteralPath $zip -DestinationPath $tmp -Force
        New-Item -ItemType Directory -Force -Path (Join-Path $SdkRoot 'cmdline-tools') | Out-Null
        Move-Item -LiteralPath (Join-Path $tmp 'cmdline-tools') -Destination (Join-Path $SdkRoot 'cmdline-tools\latest')
        Remove-Item -Recurse -Force $tmp
        if (-not (Test-Path $sdkmanager)) { throw 'SDK 명령줄 도구를 푸는 데 실패했습니다.' }
        Write-Ok '설치 완료'
    }
    $env:ANDROID_HOME = $SdkRoot
    $env:ANDROID_SDK_ROOT = $SdkRoot
    $env:ANDROID_AVD_HOME = $AvdHome

    # ------------------------------------------------------------ 4. SDK 구성 요소
    Write-Step '4/7 Android SDK 구성 요소 (platform-tools, emulator, android-35, build-tools)'
    if (Add-PleosSource (Join-Path $AndroidPrefs 'repositories.cfg')) { Write-Ok 'Pleos 이미지 저장소 등록' }
    # 라이선스 동의 (Android SDK, Pleos Connect SDK)
    # PowerShell 파이프로 y를 넣으면 첫 줄 앞에 BOM이 붙어 sdkmanager가 "y"로 인식하지 못한다 (실제로 겪음).
    # 그래서 ASCII 파일을 만들어 cmd의 입력 리다이렉트(<)로 넣는다.
    $yesFile = Join-Path $env:TEMP 'ddak-yes.txt'
    [IO.File]::WriteAllText($yesFile, ("y`r`n" * 40), [Text.Encoding]::ASCII)
    & cmd.exe /c "`"$sdkmanager`" --licenses < `"$yesFile`"" | Out-Null
    Remove-Item -Force $yesFile -ErrorAction SilentlyContinue
    if (-not (Test-Path (Join-Path $SdkRoot 'licenses\android-sdk-license'))) { throw 'SDK 라이선스 동의 처리에 실패했습니다.' }
    Write-Host '    다운로드 중입니다. 진행률이 한동안 안 보여도 창을 닫지 마세요.'
    & $sdkmanager 'platform-tools' 'emulator' 'platforms;android-35' 'build-tools;35.0.0'
    $code = $LASTEXITCODE
    $adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
    $emulator = Join-Path $SdkRoot 'emulator\emulator.exe'
    if ($code -ne 0 -or -not (Test-Path $adb) -or -not (Test-Path $emulator)) { throw 'SDK 구성 요소 설치에 실패했습니다. 인터넷 연결을 확인하고 다시 실행해 주세요.' }
    Write-Ok '설치 완료'

    # ------------------------------------------------------------ 5. Pleos Connect 에뮬레이터 이미지
    Write-Step '5/7 Pleos Connect 에뮬레이터 이미지 (4GB 다운로드, 오래 걸립니다)'
    $imageDir = Join-Path $SdkRoot 'system-images\android-34\pleos_car_emulator\x86_64'
    if ($SkipEmulatorImage) {
        Write-Note '건너뜀 (-SkipEmulatorImage)'
    } elseif (Test-Path (Join-Path $imageDir 'system.img')) {
        Write-Ok '이미 설치됨'
    } else {
        Write-Host '    다운로드 중입니다. 진행률이 한동안 안 보여도 창을 닫지 마세요 (인터넷 속도에 따라 10분에서 40분).'
        & $sdkmanager $PleosImagePackage
        $code = $LASTEXITCODE
        if ($code -ne 0 -or -not (Test-Path (Join-Path $imageDir 'system.img'))) { throw 'Pleos 이미지 설치에 실패했습니다. 다시 실행하면 이어서 시도합니다.' }
        Write-Ok '설치 완료'
    }

    # ------------------------------------------------------------ 6. 에뮬레이터 기기(AVD)
    Write-Step '6/7 에뮬레이터 기기 만들기'
    $avdIni = Join-Path $AvdHome "$AvdName.ini"
    $avdConfig = Join-Path $AvdHome "$AvdName.avd\config.ini"
    if ($SkipEmulatorImage) {
        Write-Note '건너뜀'
    } else {
        if (Test-Path $avdIni) {
            Write-Ok "이미 있음 ($AvdName)"
        } else {
            # ANDROID_AVD_HOME 폴더가 없으면 avdmanager가 조용히 무시하고 %USERPROFILE%\.android\avd 에 만든다 (실제로 겪음)
            if (-not (Test-Path $AvdHome)) { New-Item -ItemType Directory -Force -Path $AvdHome | Out-Null }
            # 질문이 나오면 "no" (PowerShell 파이프는 BOM이 붙으므로 cmd의 echo로 넣는다)
            & cmd.exe /c "echo no| `"$avdmanager`" create avd -n $AvdName -k `"$PleosImagePackage`" -d $PleosDeviceId --force"
            if (-not (Test-Path $avdConfig)) { throw '에뮬레이터 기기를 만들지 못했습니다.' }
            Write-Ok "생성 완료 ($AvdName)"
        }
        # 공식 가이드 값: RAM 4GB, Cold boot, 그래픽 하드웨어
        Set-IniValue $avdConfig 'hw.ramSize' '4096'
        # 4개로 둔다. 8개로 올려 봤을 때 시스템 화면(systemui)과 차량 서비스가 반복해서 죽었다
        # (원인이 코어 수인지 강제 종료로 상한 데이터인지는 가리지 못함). 4개 + 깨끗한 데이터가 확인된 조합이다.
        Set-IniValue $avdConfig 'hw.cpu.ncore' '4'
        Set-IniValue $avdConfig 'vm.heapSize' '256M'
        Set-IniValue $avdConfig 'disk.dataPartition.size' '6G'
        Set-IniValue $avdConfig 'fastboot.forceColdBoot' 'yes'
        Set-IniValue $avdConfig 'fastboot.forceFastBoot' 'no'
        Set-IniValue $avdConfig 'hw.gpu.enabled' 'yes'
        Set-IniValue $avdConfig 'hw.gpu.mode' 'host'
        Set-IniValue $avdConfig 'hw.initialOrientation' 'landscape'
        Set-IniValue $avdConfig 'hw.keyboard' 'yes'
        Set-IniValue $avdConfig 'showDeviceFrame' 'no'
        Write-Ok 'RAM 4GB, Cold boot, 그래픽 host 로 설정'
    }

    # ------------------------------------------------------------ 7. 환경 변수와 프로젝트 설정
    Write-Step '7/7 환경 변수와 프로젝트 설정'
    [Environment]::SetEnvironmentVariable('ANDROID_HOME', $SdkRoot, 'User')
    [Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $SdkRoot, 'User')
    [Environment]::SetEnvironmentVariable('ANDROID_AVD_HOME', $AvdHome, 'User')
    if (-not [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User') -and -not [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')) {
        [Environment]::SetEnvironmentVariable('JAVA_HOME', $StudioJbr, 'User')
    }
    Add-UserPath (Join-Path $SdkRoot 'platform-tools')
    Add-UserPath (Join-Path $SdkRoot 'emulator')
    Add-UserPath $toolsBin

    Write-PlainFile (Join-Path $ProjectRoot 'vehicle-app\local.properties') @('sdk.dir=' + (ConvertTo-PropertiesPath $SdkRoot))
    # 2, 3번 bat 파일이 읽는 경로 모음
    $envLines = @(
        '@echo off',
        "set ""JAVA_HOME=$StudioJbr""",
        "set ""ANDROID_HOME=$SdkRoot""",
        "set ""ANDROID_SDK_ROOT=$SdkRoot""",
        "set ""ANDROID_AVD_HOME=$AvdHome""",
        "set ""DDAK_AVD=$AvdName""",
        "set ""PATH=$NodeDir;%PATH%"""
    )
    [IO.File]::WriteAllLines((Join-Path $PSScriptRoot 'env.bat'), $envLines, [Text.Encoding]::ASCII)
    Write-Ok 'local.properties, scripts\env.bat 기록'

    Write-Host ''
    Write-Host '설치가 끝났습니다.' -ForegroundColor Green
    Write-Host "  SDK 위치: $SdkRoot"
    if (-not $SkipEmulatorImage) { Write-Host "  에뮬레이터 기기: $AvdName" }
    if ($projectPathBad) {
        Write-Host ''
        Write-Note "주의: 프로젝트 폴더 경로에 한글이 있습니다: $ProjectRoot"
        Write-Note '안드로이드 빌드가 실패할 수 있으니 이 폴더를 통째로 C:\ddakmatchum 으로 옮긴 뒤 1-setup.bat 을 한 번 더 실행해 주세요 (금방 끝납니다).'
    }
    Write-Host ''
    Write-Host '다음 순서: 2-start-emulator.bat (에뮬레이터 켜기) -> 3-build-install.bat (앱 빌드, 설치) -> 4-start-server.bat (매장 서버)'
    exit 0
} catch {
    Write-Host ''
    Write-Host "설치가 중단되었습니다: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host '이 창의 내용을 그대로 복사해서 알려 주시면 원인을 찾을 수 있습니다. 다시 실행하면 끝난 단계는 건너뜁니다.'
    exit 1
}
