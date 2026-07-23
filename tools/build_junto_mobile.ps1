param(
    [string]$GoRoot = "",
    [switch]$BootstrapGo,
    [switch]$SkipGoTests
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $repoRoot "third_party\junto-mobile"
$toolRoot = Join-Path $repoRoot ".tools\gomobile"
$binRoot = Join-Path $toolRoot "bin"
$aarRoot = Join-Path $repoRoot "feature\together\libs"
$aarPath = Join-Path $aarRoot "junto-mobile.aar"
$checksumPath = Join-Path $sourceRoot "junto-mobile.aar.sha256"
$requiredGo = "go1.26.4"
$xMobileCommit = "6129f5b"

function Assert-NativeSuccess([string]$Action) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Action failed with exit code $LASTEXITCODE"
    }
}

if ($BootstrapGo -and [string]::IsNullOrWhiteSpace($GoRoot)) {
    $goArchive = Join-Path $toolRoot "$requiredGo.windows-amd64.zip"
    $goInstall = Join-Path $toolRoot $requiredGo
    if (-not (Test-Path -LiteralPath $goInstall)) {
        New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null
        Invoke-WebRequest `
            -Uri "https://go.dev/dl/$requiredGo.windows-amd64.zip" `
            -OutFile $goArchive
        $expandRoot = Join-Path $toolRoot "expand-$requiredGo"
        New-Item -ItemType Directory -Force -Path $expandRoot | Out-Null
        Expand-Archive -LiteralPath $goArchive -DestinationPath $expandRoot -Force
        Move-Item -LiteralPath (Join-Path $expandRoot "go") -Destination $goInstall
        Remove-Item -LiteralPath $expandRoot -Recurse -Force
    }
    $GoRoot = $goInstall
}

$goExe = if ([string]::IsNullOrWhiteSpace($GoRoot)) {
    (Get-Command go -ErrorAction Stop).Source
} else {
    (Resolve-Path -LiteralPath (Join-Path $GoRoot "bin\go.exe")).Path
}

$actualGo = & $goExe version
Assert-NativeSuccess "go version"
if ($actualGo -notmatch [regex]::Escape($requiredGo)) {
    throw "Expected $requiredGo, got: $actualGo"
}

New-Item -ItemType Directory -Force -Path $binRoot, $aarRoot | Out-Null
$env:PATH = "$binRoot;$(Split-Path -Parent $goExe);$env:PATH"
$env:GOBIN = $binRoot
& $goExe install "golang.org/x/mobile/cmd/gomobile@$xMobileCommit"
Assert-NativeSuccess "install gomobile"
& $goExe install "golang.org/x/mobile/cmd/gobind@$xMobileCommit"
Assert-NativeSuccess "install gobind"

$gomobile = Join-Path $binRoot "gomobile.exe"
& $gomobile init
Assert-NativeSuccess "gomobile init"
Push-Location $sourceRoot
try {
    if (-not $SkipGoTests) {
        & $goExe test ./...
        Assert-NativeSuccess "go test ./..."
    }
    & $gomobile bind `
        -target="android/arm64,android/amd64" `
        -androidapi=23 `
        -javapkg="app.yukine.junto" `
        -ldflags="-checklinkname=0" `
        -o $aarPath `
        ./mobile
    Assert-NativeSuccess "gomobile bind"
} finally {
    Pop-Location
}

$entries = [System.IO.Compression.ZipFile]::OpenRead($aarPath)
try {
    $names = $entries.Entries.FullName
    foreach ($required in @(
        "jni/arm64-v8a/libgojni.so",
        "jni/x86_64/libgojni.so",
        "classes.jar"
    )) {
        if ($required -notin $names) {
            throw "AAR smoke check failed: missing $required"
        }
    }
} finally {
    $entries.Dispose()
}

$hash = (Get-FileHash -LiteralPath $aarPath -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath $checksumPath -Encoding ascii -NoNewline `
    -Value "$hash  feature/together/libs/junto-mobile.aar"
Write-Host "Built $aarPath"
Write-Host "SHA-256 $hash"
