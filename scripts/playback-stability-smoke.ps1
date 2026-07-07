param(
    [switch]$SkipBuild,
    [switch]$SkipManualCheckpoint,
    [string]$DeviceSerial = "",
    [string]$EvidenceDir = "",
    [int]$LaunchWaitSeconds = 5,
    [int]$RelaunchWaitSeconds = 5
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$Gradle = Join-Path $ProjectRoot "gradlew.bat"
$LocalAdb = Join-Path $ProjectRoot "android-sdk/platform-tools/adb.exe"
$UserAdb = Join-Path $env:LOCALAPPDATA "Android/Sdk/platform-tools/adb.exe"
$PackageName = "app.yukine"
$MainActivity = "app.yukine/.MainActivity"

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
    if (Test-Path $UserAdb) {
        return $UserAdb
    }
    $candidate = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($candidate) {
        return $candidate.Source
    }
    throw "adb.exe was not found. Expected project-local adb at $LocalAdb, user SDK adb at $UserAdb, or adb.exe on PATH."
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
    $global:LASTEXITCODE = 0
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

function Resolve-DeviceSerial {
    param(
        [string]$Adb
    )
    if ($DeviceSerial.Trim().Length -gt 0) {
        return $DeviceSerial
    }
    $deviceLines = @(Invoke-AdbCapture $Adb @("devices") |
        Where-Object { $_ -match "^\S+\s+device$" })
    if ($deviceLines.Count -ne 1) {
        Invoke-Adb $Adb @("devices", "-l")
        throw "Expected exactly one connected device. Pass -DeviceSerial when multiple devices are attached."
    }
    return ($deviceLines[0] -split "\s+")[0]
}

function Grant-MediaPermissions {
    param(
        [string]$Adb
    )
    $sdkText = (Invoke-AdbCapture $Adb @("shell", "getprop", "ro.build.version.sdk") | Select-Object -First 1).Trim()
    $sdk = 0
    if (![int]::TryParse($sdkText, [ref]$sdk)) {
        throw "Unable to determine attached device Android SDK level."
    }
    if ($sdk -ge 33) {
        Invoke-AdbAllowFailure $Adb @("shell", "pm", "grant", $PackageName, "android.permission.READ_MEDIA_AUDIO")
        Invoke-AdbAllowFailure $Adb @("shell", "pm", "grant", $PackageName, "android.permission.POST_NOTIFICATIONS")
    } else {
        Invoke-AdbAllowFailure $Adb @("shell", "pm", "grant", $PackageName, "android.permission.READ_EXTERNAL_STORAGE")
    }
}

function Save-AdbText {
    param(
        [string]$Adb,
        [string[]]$Arguments,
        [string]$Path
    )
    Invoke-AdbCapture $Adb $Arguments | Out-File -FilePath $Path -Encoding utf8
}

function Save-Screenshot {
    param(
        [string]$Adb,
        [string]$Path
    )
    $remotePath = "/sdcard/yukine-playback-stability-smoke.png"
    Invoke-Checked "capture screenshot to device" { Invoke-Adb $Adb @("shell", "screencap", "-p", $remotePath) }
    Invoke-Checked "pull screenshot" { Invoke-Adb $Adb @("pull", $remotePath, $Path) }
    Invoke-AdbAllowFailure $Adb @("shell", "rm", $remotePath)
}

function Save-LogcatAndAssertNoFatal {
    param(
        [string]$Adb,
        [string]$Path,
        [string]$Title,
        [string]$Tail = "1000"
    )
    $logcat = @(Invoke-AdbCapture $Adb @("logcat", "-d", "-t", $Tail))
    $logcat | Out-File -FilePath $Path -Encoding utf8
    $fatal = $logcat | Select-String -Pattern "FATAL EXCEPTION|AndroidRuntime.*app.yukine|Process: app.yukine"
    if ($fatal) {
        $fatal | Select-Object -First 20 | ForEach-Object { Write-Host $_.Line }
        throw "$Title found a fatal app crash. See $Path"
    }
}

function Assert-AppProcessAlive {
    param(
        [string]$Adb,
        [string]$Title
    )
    Invoke-Checked $Title { Invoke-Adb $Adb @("shell", "pidof", $PackageName) }
}

function Write-MatrixResultsTemplate {
    param(
        [string]$Path,
        [string]$ResolvedSerial,
        [string]$DebugApk,
        [bool]$ManualSkipped
    )
    $rows = @(
        "| Local song play | Not run |  | Confirm playback starts; NowBar, player screen, and notification agree; progress advances. |",
        "| Pause and resume | Not run |  | Exercise player, notification, and lockscreen controls. |",
        "| Skip previous and next | Not run |  | Confirm title, queue index, and notification stay in sync. |",
        "| Seek progress | Not run |  | Seek middle, near end, and beginning; verify playback position and lyrics/waveform sync. |",
        "| Background playback | Not run |  | Press Home, wait 2 minutes, return to app; playback and UI should stay coherent. |",
        "| Lockscreen controls | Not run |  | Lock device and use pause/play/next controls. |",
        "| Notification controls | Not run |  | Use pause/play/previous/next from notification shade. |",
        "| Cold-start queue restore | Not run |  | Stop process after mid-song playback, relaunch, verify queue/current/position restore. |",
        "| Force-stop restore | Not run | 03-after-force-stop-relaunch.png; 03-activity.txt; 03-media-session.txt; 03-logcat-tail.txt | Script captures relaunch evidence; human still verifies restored state. |",
        "| Headset disconnect | Not run |  | Disconnect wired or Bluetooth headset; verify ACTION_AUDIO_BECOMING_NOISY behavior and no unexpected speaker playback. |",
        "| Bluetooth switch | Not run |  | Switch speaker -> Bluetooth -> disconnect -> reconnect; verify controls and no crash. |",
        "| Call interruption | Not run |  | Simulate or receive call during playback; verify system audio-focus behavior. |"
    )
    $content = @(
        "# Playback Service Stability Matrix Results",
        "",
        "Date: $(Get-Date -Format o)",
        "Device serial: $ResolvedSerial",
        "APK: $DebugApk",
        "Evidence directory: $(Split-Path $Path -Parent)",
        "Manual checkpoint skipped: $ManualSkipped",
        "",
        "This file is generated by scripts/playback-stability-smoke.ps1. Fill the Result and Evidence columns after running the manual matrix. Use Pass, Fail, Blocked, or Not run.",
        "",
        "## Script Evidence Files",
        "",
        "- 01-launch.png",
        "- 01-activity.txt",
        "- 01-media-session.txt",
        "- 01-notification.txt",
        "- 01-audio.txt",
        "- 01-logcat-tail.txt",
        "- 02-post-manual.png / 02-* files when the manual checkpoint is not skipped",
        "- 03-after-force-stop-relaunch.png",
        "- 03-activity.txt",
        "- 03-media-session.txt",
        "- 03-logcat-tail.txt",
        "",
        "## P0 Required Scenarios",
        "",
        "| Scenario | Result | Evidence | Notes |",
        "| --- | --- | --- | --- |"
    ) + $rows + @(
        "",
        "## Blocking Summary",
        "",
        "| Severity | Count | Notes |",
        "| --- | --- | --- |",
        "| P0 |  |  |",
        "| P1 |  |  |",
        "| P2 |  |  |",
        "",
        "Release allowed: Yes / No"
    )
    $content | Out-File -FilePath $Path -Encoding utf8
}

Push-Location $ProjectRoot
try {
    if (!(Test-Path $Gradle)) {
        throw "Gradle wrapper not found at $Gradle"
    }

    if (!$SkipBuild) {
        Invoke-Checked "assembleDebug" { & $Gradle assembleDebug --console=plain }
    }

    $Adb = Resolve-Adb
    Invoke-Checked "adb devices" { Invoke-Adb $Adb @("devices", "-l") }
    $ResolvedSerial = Resolve-DeviceSerial $Adb

    if ($EvidenceDir.Trim().Length -eq 0) {
        $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
        $EvidenceDir = Join-Path $ProjectRoot "app/build/playback-stability/$timestamp-$ResolvedSerial"
    } elseif (![System.IO.Path]::IsPathRooted($EvidenceDir)) {
        $EvidenceDir = Join-Path $ProjectRoot $EvidenceDir
    }
    New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

    $debugApk = Join-Path $ProjectRoot "app/build/outputs/apk/debug/app-debug.apk"
    if (!(Test-Path $debugApk)) {
        throw "Debug APK not found at $debugApk. Run without -SkipBuild first or build assembleDebug."
    }

    Invoke-Checked "install debug APK" { Invoke-Adb $Adb @("install", "-r", $debugApk) }
    Grant-MediaPermissions $Adb
    Invoke-Checked "clear logcat" { Invoke-Adb $Adb @("logcat", "-c") }
    Invoke-Checked "launch MainActivity" { Invoke-Adb $Adb @("shell", "am", "start", "-W", "-n", $MainActivity) }
    Start-Sleep -Seconds $LaunchWaitSeconds
    Assert-AppProcessAlive $Adb "verify app process after launch"

    Save-Screenshot $Adb (Join-Path $EvidenceDir "01-launch.png")
    Save-AdbText $Adb @("shell", "dumpsys", "activity", "activities") (Join-Path $EvidenceDir "01-activity.txt")
    Save-AdbText $Adb @("shell", "dumpsys", "media_session") (Join-Path $EvidenceDir "01-media-session.txt")
    Save-AdbText $Adb @("shell", "dumpsys", "notification", "--noredact") (Join-Path $EvidenceDir "01-notification.txt")
    Save-AdbText $Adb @("shell", "dumpsys", "audio") (Join-Path $EvidenceDir "01-audio.txt")
    Save-LogcatAndAssertNoFatal $Adb (Join-Path $EvidenceDir "01-logcat-tail.txt") "launch smoke"

    if ($SkipManualCheckpoint) {
        Write-Host ""
        Write-Host "Skipping manual matrix checkpoint. This run only proves install, launch, force-stop relaunch, and fatal-crash sampling."
    } else {
        Write-Host ""
        Write-Host "Manual matrix checkpoint:"
        Write-Host "  1. Start local playback and capture your screen recording externally."
        Write-Host "  2. Exercise pause/resume, seek, next/previous, background, lockscreen, notification, headset/Bluetooth, and call interruption."
        Write-Host "  3. When done, press Enter here to collect post-manual dumpsys/logcat evidence."
        Read-Host | Out-Null

        Save-Screenshot $Adb (Join-Path $EvidenceDir "02-post-manual.png")
        Save-AdbText $Adb @("shell", "dumpsys", "media_session") (Join-Path $EvidenceDir "02-media-session.txt")
        Save-AdbText $Adb @("shell", "dumpsys", "notification", "--noredact") (Join-Path $EvidenceDir "02-notification.txt")
        Save-AdbText $Adb @("shell", "dumpsys", "audio") (Join-Path $EvidenceDir "02-audio.txt")
        Save-LogcatAndAssertNoFatal $Adb (Join-Path $EvidenceDir "02-logcat-tail.txt") "post-manual playback smoke"
    }

    Invoke-Checked "force-stop app for restore sample" { Invoke-Adb $Adb @("shell", "am", "force-stop", $PackageName) }
    Invoke-Checked "relaunch MainActivity after force-stop" { Invoke-Adb $Adb @("shell", "am", "start", "-W", "-n", $MainActivity) }
    Start-Sleep -Seconds $RelaunchWaitSeconds
    Assert-AppProcessAlive $Adb "verify app process after force-stop relaunch"
    Save-Screenshot $Adb (Join-Path $EvidenceDir "03-after-force-stop-relaunch.png")
    Save-AdbText $Adb @("shell", "dumpsys", "activity", "activities") (Join-Path $EvidenceDir "03-activity.txt")
    Save-AdbText $Adb @("shell", "dumpsys", "media_session") (Join-Path $EvidenceDir "03-media-session.txt")
    Save-LogcatAndAssertNoFatal $Adb (Join-Path $EvidenceDir "03-logcat-tail.txt") "force-stop relaunch smoke"

    $summary = @(
        "# Playback Stability Smoke Evidence",
        "",
        "Date: $(Get-Date -Format o)",
        "Device serial: $ResolvedSerial",
        "APK: $debugApk",
        "Evidence directory: $EvidenceDir",
        "Manual checkpoint skipped: $($SkipManualCheckpoint.IsPresent)",
        "",
        "This script collects device evidence for PLAYBACK_SERVICE_STABILITY_MATRIX.md.",
        "It does not mark manual playback, lockscreen, Bluetooth, headset, or call-interruption rows as passed by itself."
    )
    $summary | Out-File -FilePath (Join-Path $EvidenceDir "README.md") -Encoding utf8
    Write-MatrixResultsTemplate (Join-Path $EvidenceDir "matrix-results.md") $ResolvedSerial $debugApk $SkipManualCheckpoint.IsPresent

    Write-Host ""
    Write-Host "Playback stability evidence written to:"
    Write-Host $EvidenceDir
} finally {
    Pop-Location
}
