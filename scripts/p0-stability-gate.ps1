param(
    [switch]$SkipDeviceProbe,
    [switch]$IncludeAssemble,
    [string]$ReportPath = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$Gradle = Join-Path $ProjectRoot "gradlew.bat"
$LocalAdb = Join-Path $ProjectRoot "android-sdk/platform-tools/adb.exe"
$UserAdb = Join-Path $env:LOCALAPPDATA "Android/Sdk/platform-tools/adb.exe"
$ReportLines = New-Object System.Collections.Generic.List[string]

function Invoke-Checked {
    param(
        [string]$Title,
        [scriptblock]$Command,
        [switch]$Native
    )
    Write-Host ""
    Write-Host "==> $Title"
    & $Command
    if ($Native -and $LASTEXITCODE -ne 0) {
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
    return $null
}

function Add-ReportLine {
    param(
        [string]$Line
    )
    $ReportLines.Add($Line) | Out-Null
}

function Add-StepResult {
    param(
        [string]$Name,
        [string]$Result,
        [string]$Evidence
    )
    Add-ReportLine "| $Name | $Result | $Evidence |"
}

Push-Location $ProjectRoot
try {
    if (!(Test-Path $Gradle)) {
        throw "Gradle wrapper not found at $Gradle"
    }

    if ($ReportPath.Trim().Length -eq 0) {
        $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
        $ReportPath = Join-Path $ProjectRoot "app/build/p0-stability-gate/$timestamp.md"
    } elseif (![System.IO.Path]::IsPathRooted($ReportPath)) {
        $ReportPath = Join-Path $ProjectRoot $ReportPath
    }

    Add-ReportLine "# P0 Stability Gate Report"
    Add-ReportLine ""
    Add-ReportLine "Date: $(Get-Date -Format o)"
    Add-ReportLine "Project: $ProjectRoot"
    Add-ReportLine "Skip device probe: $($SkipDeviceProbe.IsPresent)"
    Add-ReportLine "Include assembleDebug: $($IncludeAssemble.IsPresent)"
    Add-ReportLine ""
    Add-ReportLine "| Check | Result | Evidence |"
    Add-ReportLine "| --- | --- | --- |"

    Invoke-Checked "PowerShell syntax: playback-stability-smoke.ps1" {
        $tokens = $null
        $errors = $null
        [System.Management.Automation.Language.Parser]::ParseFile(
            (Join-Path $ProjectRoot "scripts/playback-stability-smoke.ps1"),
            [ref]$tokens,
            [ref]$errors
        ) | Out-Null
        if ($errors.Count -gt 0) {
            $errors | Format-List | Out-String | Write-Host
            exit 1
        }
        Write-Host "PowerShell syntax OK"
    }
    Add-StepResult "playback-stability-smoke.ps1 syntax" "Pass" "PowerShell parser"

    Invoke-Checked "StreamingViewModelTest" {
        & $Gradle :app:testDebugUnitTest --tests StreamingViewModelTest --console=plain
    } -Native
    Add-StepResult "StreamingViewModelTest" "Pass" ":app:testDebugUnitTest --tests StreamingViewModelTest"

    Invoke-Checked "EchoDatabaseHelperTest" {
        & $Gradle :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain
    } -Native
    Add-StepResult "EchoDatabaseHelperTest" "Pass" ":app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest"

    Invoke-Checked "feature playback unit tests" {
        & $Gradle :feature:playback:testDebugUnitTest --console=plain
    } -Native
    Add-StepResult "feature playback unit tests" "Pass" ":feature:playback:testDebugUnitTest"

    if ($IncludeAssemble) {
        Invoke-Checked "assembleDebug" {
            & $Gradle :app:assembleDebug --console=plain
        } -Native
        $debugApk = Join-Path $ProjectRoot "app/build/outputs/apk/debug/app-debug.apk"
        if (!(Test-Path $debugApk)) {
            throw "assembleDebug passed but debug APK was not found at $debugApk"
        }
        $debugApkItem = Get-Item $debugApk
        Add-StepResult "assembleDebug" "Pass" "$debugApk ($($debugApkItem.Length) bytes)"
    }

    if (!$SkipDeviceProbe) {
        $adb = Resolve-Adb
        if ($adb) {
            Invoke-Checked "adb devices" {
                & $adb devices
            } -Native
            $deviceLines = @(& $adb devices | Where-Object { $_ -match "^\S+\s+device$" })
            if ($deviceLines.Count -eq 0) {
                Add-StepResult "adb devices" "Fail" "No attached device; manual playback matrix not executed"
                throw "No attached Android device. Connect a device for playback acceptance or pass -SkipDeviceProbe for automated guards only."
            } else {
                Add-StepResult "adb devices" "Pass" ($deviceLines -join "; ")
            }
        } else {
            Write-Host ""
            Write-Host "==> adb devices"
            Add-StepResult "adb devices" "Fail" "adb.exe not found"
            throw "adb.exe not found. Install Android platform-tools or pass -SkipDeviceProbe for automated guards only."
        }
    } else {
        Add-StepResult "adb devices" "Skipped" "-SkipDeviceProbe"
    }
} finally {
    if ($ReportLines.Count -gt 0) {
        $reportDirectory = Split-Path $ReportPath -Parent
        New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
        $ReportLines | Out-File -FilePath $ReportPath -Encoding utf8
        Write-Host ""
        Write-Host "P0 stability gate report: $ReportPath"
    }
    Pop-Location
}
