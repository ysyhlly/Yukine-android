param(
    [switch]$SkipBuild,
    [switch]$Connected,
    [switch]$ReleaseSmoke,
    [switch]$CreateSmokeKeystore,
    [switch]$AllowPreAndroid13Smoke,
    [string]$DeviceSerial = "",
    [string]$SmokeKeystore = "app/build/tmp/echo-smoke-release-local.jks",
    [string]$SmokeStorePassword = "echo-smoke-store-local",
    [string]$SmokeKeyAlias = "echo-smoke",
    [string]$SmokeKeyPassword = "echo-smoke-store-local"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$Gradle = Join-Path $ProjectRoot "gradlew.bat"
$LocalAdb = Join-Path $ProjectRoot "android-sdk/platform-tools/adb.exe"

function Invoke-Checked {
    param(
        [string]$Title,
        [scriptblock]$Command
    )
    Write-Host ""
    Write-Host "==> $Title"
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Title failed with exit code $LASTEXITCODE"
    }
}

function Resolve-Adb {
    if (Test-Path $LocalAdb) {
        return $LocalAdb
    }
    $candidate = (Get-Command adb.exe -ErrorAction SilentlyContinue)
    if ($candidate) {
        return $candidate.Source
    }
    throw "adb.exe was not found. Expected project-local adb at $LocalAdb or adb.exe on PATH."
}

function Invoke-Adb {
    param(
        [string]$Adb,
        [string[]]$Arguments
    )
    if ($DeviceSerial.Trim().Length -gt 0) {
        & $Adb -s $DeviceSerial @Arguments
    } else {
        & $Adb @Arguments
    }
}

function Invoke-AdbAllowFailure {
    param(
        [string]$Adb,
        [string[]]$Arguments
    )
    if ($DeviceSerial.Trim().Length -gt 0) {
        & $Adb -s $DeviceSerial @Arguments
    } else {
        & $Adb @Arguments
    }
}

function Invoke-AdbCapture {
    param(
        [string]$Adb,
        [string[]]$Arguments
    )
    if ($DeviceSerial.Trim().Length -gt 0) {
        return & $Adb -s $DeviceSerial @Arguments
    }
    return & $Adb @Arguments
}

function Assert-ReleaseSmokeTarget {
    param(
        [string]$Adb
    )
    $sdkText = (Invoke-AdbCapture $Adb @("shell", "getprop", "ro.build.version.sdk") | Select-Object -First 1).Trim()
    $sdk = 0
    if (![int]::TryParse($sdkText, [ref]$sdk)) {
        throw "Unable to determine attached device Android SDK level. Use -DeviceSerial when multiple devices are attached."
    }
    if ($sdk -lt 33 -and !$AllowPreAndroid13Smoke) {
        throw "Release smoke targets Android 13+/API 33+. Attached device is API $sdk. Use -AllowPreAndroid13Smoke only for non-release historical confidence checks."
    }
    Write-Host "Release smoke target SDK: API $sdk"
    return $sdk
}

function Grant-ReleaseSmokePermissions {
    param(
        [string]$Adb,
        [int]$Sdk
    )
    if ($Sdk -ge 33) {
        Invoke-Checked "grant READ_MEDIA_AUDIO" { Invoke-Adb $Adb @("shell", "pm", "grant", "app.echo.next", "android.permission.READ_MEDIA_AUDIO") }
        Invoke-Checked "grant POST_NOTIFICATIONS" { Invoke-Adb $Adb @("shell", "pm", "grant", "app.echo.next", "android.permission.POST_NOTIFICATIONS") }
    } else {
        Invoke-Checked "grant READ_EXTERNAL_STORAGE" { Invoke-Adb $Adb @("shell", "pm", "grant", "app.echo.next", "android.permission.READ_EXTERNAL_STORAGE") }
    }
}

function Grant-AudioPermission {
    param(
        [string]$Adb
    )
    $sdkText = (Invoke-AdbCapture $Adb @("shell", "getprop", "ro.build.version.sdk") | Select-Object -First 1).Trim()
    $sdk = 0
    if (![int]::TryParse($sdkText, [ref]$sdk)) {
        throw "Unable to determine attached device Android SDK level."
    }
    if ($sdk -ge 33) {
        Invoke-Checked "grant READ_MEDIA_AUDIO" { Invoke-Adb $Adb @("shell", "pm", "grant", "app.echo.next", "android.permission.READ_MEDIA_AUDIO") }
    } else {
        Invoke-Checked "grant READ_EXTERNAL_STORAGE" { Invoke-Adb $Adb @("shell", "pm", "grant", "app.echo.next", "android.permission.READ_EXTERNAL_STORAGE") }
    }
}

function Clear-InstalledPackages {
    param(
        [string]$Adb
    )
    Write-Host ""
    Write-Host "==> clear existing app installs"
    Invoke-AdbAllowFailure $Adb @("shell", "am", "force-stop", "app.echo.next")
    Invoke-AdbAllowFailure $Adb @("uninstall", "app.echo.next.test")
    Invoke-AdbAllowFailure $Adb @("uninstall", "app.echo.next")
    $global:LASTEXITCODE = 0
}

Push-Location $ProjectRoot
try {
    if (!(Test-Path $Gradle)) {
        throw "Gradle wrapper not found at $Gradle"
    }

    if (!$SkipBuild) {
        Invoke-Checked "assembleDebugAndroidTest" { & $Gradle assembleDebugAndroidTest }
        Invoke-Checked "assembleDebug" { & $Gradle assembleDebug }
        Invoke-Checked "lintDebug" { & $Gradle lintDebug }
        Invoke-Checked "assembleRelease" { & $Gradle assembleRelease }
        Invoke-Checked "bundleRelease" { & $Gradle bundleRelease }
        Invoke-Checked "lintRelease" { & $Gradle lintRelease }
    }

    $Adb = $null
    if ($Connected -or $ReleaseSmoke) {
        $Adb = Resolve-Adb
        Invoke-Checked "adb devices" { Invoke-Adb $Adb @("devices", "-l") }
    }

    if ($Connected) {
        if ($DeviceSerial.Trim().Length -gt 0) {
            $debugApk = Join-Path $ProjectRoot "app/build/outputs/apk/debug/app-debug.apk"
            $testApk = Join-Path $ProjectRoot "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
            if (!(Test-Path $debugApk)) {
                throw "Debug APK not found at $debugApk. Run without -SkipBuild first or build assembleDebug."
            }
            if (!(Test-Path $testApk)) {
                throw "Android test APK not found at $testApk. Run without -SkipBuild first or build assembleDebugAndroidTest."
            }
            Clear-InstalledPackages $Adb
            Invoke-Checked "install debug APK on selected device" { Invoke-Adb $Adb @("install", "-r", $debugApk) }
            Invoke-Checked "install androidTest APK on selected device" { Invoke-Adb $Adb @("install", "-r", $testApk) }
            Grant-AudioPermission $Adb
            Invoke-Checked "run instrumentation on selected device" { Invoke-Adb $Adb @("shell", "am", "instrument", "-w", "app.echo.next.test/androidx.test.runner.AndroidJUnitRunner") }
        } else {
            Clear-InstalledPackages $Adb
            Invoke-Checked "connectedDebugAndroidTest" { & $Gradle connectedDebugAndroidTest }
        }
    }

    if ($ReleaseSmoke) {
        $releaseSmokeSdk = Assert-ReleaseSmokeTarget $Adb
        $previousStoreFile = $env:ECHO_RELEASE_STORE_FILE
        $previousStorePassword = $env:ECHO_RELEASE_STORE_PASSWORD
        $previousKeyAlias = $env:ECHO_RELEASE_KEY_ALIAS
        $previousKeyPassword = $env:ECHO_RELEASE_KEY_PASSWORD
        try {
            if ($CreateSmokeKeystore) {
                $keystorePath = Join-Path $ProjectRoot $SmokeKeystore
                $keystoreDir = Split-Path $keystorePath -Parent
                New-Item -ItemType Directory -Force -Path $keystoreDir | Out-Null
                if (!(Test-Path $keystorePath)) {
                    Invoke-Checked "create temporary smoke keystore" {
                        & keytool -genkeypair -v `
                            -keystore $keystorePath `
                            -storepass $SmokeStorePassword `
                            -alias $SmokeKeyAlias `
                            -keypass $SmokeKeyPassword `
                            -keyalg RSA `
                            -keysize 2048 `
                            -validity 3650 `
                            -dname "CN=ECHO NEXT Smoke,O=Local,C=CN"
                    }
                }
                $env:ECHO_RELEASE_STORE_FILE = (Resolve-Path $keystorePath).Path
                $env:ECHO_RELEASE_STORE_PASSWORD = $SmokeStorePassword
                $env:ECHO_RELEASE_KEY_ALIAS = $SmokeKeyAlias
                $env:ECHO_RELEASE_KEY_PASSWORD = $SmokeKeyPassword
            } elseif (!$env:ECHO_RELEASE_STORE_FILE -or !$env:ECHO_RELEASE_STORE_PASSWORD -or !$env:ECHO_RELEASE_KEY_ALIAS -or !$env:ECHO_RELEASE_KEY_PASSWORD) {
                throw "Release smoke requires signing values. Provide ECHO_RELEASE_* env vars or pass -CreateSmokeKeystore."
            }

            Invoke-Checked "assemble signed release APK" { & $Gradle assembleRelease }
            Invoke-Checked "bundle signed release AAB" { & $Gradle bundleRelease }

            $apk = Join-Path $ProjectRoot "app/build/outputs/apk/release/app-release.apk"
            if (!(Test-Path $apk)) {
                throw "Signed release APK not found at $apk"
            }

            Clear-InstalledPackages $Adb
            Invoke-Checked "install signed release APK" { Invoke-Adb $Adb @("install", "-r", $apk) }
            Grant-ReleaseSmokePermissions $Adb $releaseSmokeSdk
            Invoke-Checked "clear logcat" { Invoke-Adb $Adb @("logcat", "-c") }
            Invoke-Checked "launch release MainActivity" { Invoke-Adb $Adb @("shell", "am", "start", "-W", "-n", "app.echo.next/.MainActivity") }
            Start-Sleep -Seconds 5

            Invoke-Checked "verify release process is alive" { Invoke-Adb $Adb @("shell", "pidof", "app.echo.next") }
            Invoke-Checked "verify MainActivity is resumed" {
                $activityState = if ($DeviceSerial.Trim().Length -gt 0) {
                    & $Adb -s $DeviceSerial shell dumpsys activity activities
                } else {
                    & $Adb shell dumpsys activity activities
                }
                $resumed = $activityState | Select-String -Pattern "ResumedActivity:.*app.echo.next/.MainActivity|topResumedActivity=.*app.echo.next/.MainActivity"
                if (!$resumed) {
                    throw "app.echo.next/.MainActivity is not the resumed foreground activity."
                }
            }
            Invoke-Checked "check logcat for fatal app crash" {
                $logcat = if ($DeviceSerial.Trim().Length -gt 0) {
                    & $Adb -s $DeviceSerial logcat -d -t 300
                } else {
                    & $Adb logcat -d -t 300
                }
                $fatal = $logcat | Select-String -Pattern "FATAL EXCEPTION|AndroidRuntime.*app.echo.next|Process: app.echo.next"
                if ($fatal) {
                    $fatal | Select-Object -First 20 | ForEach-Object { Write-Host $_.Line }
                    throw "Fatal app crash found in sampled logcat."
                }
            }
            Invoke-Checked "capture release smoke screenshot" {
                $screenshot = Join-Path $ProjectRoot "app/build/tmp/echo-smoke-release-latest.png"
                if ($DeviceSerial.Trim().Length -gt 0) {
                    & $Adb -s $DeviceSerial exec-out screencap -p > $screenshot
                } else {
                    & $Adb exec-out screencap -p > $screenshot
                }
            }
            Write-Host "Release smoke screenshot: app/build/tmp/echo-smoke-release-latest.png"
        } finally {
            $env:ECHO_RELEASE_STORE_FILE = $previousStoreFile
            $env:ECHO_RELEASE_STORE_PASSWORD = $previousStorePassword
            $env:ECHO_RELEASE_KEY_ALIAS = $previousKeyAlias
            $env:ECHO_RELEASE_KEY_PASSWORD = $previousKeyPassword
        }
    }
} finally {
    Pop-Location
}
