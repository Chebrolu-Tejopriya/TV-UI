<# Builds and verifies an APK locally. Does not connect to or modify a TV. #>
[CmdletBinding()]
param(
    [string]$SdkRoot = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    [string]$JdkRoot = (Join-Path (Split-Path -Parent $PSScriptRoot) 'tools\jdk-17.0.20.1+1'),
    [string]$PythonExe = 'python'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$utf8 = New-Object System.Text.UTF8Encoding($false)
$buildTools = Join-Path $SdkRoot 'build-tools\34.0.0'
$androidJar = Join-Path $SdkRoot 'platforms\android-34\android.jar'
$javaCompiler = Join-Path $JdkRoot 'bin\javac.exe'
$jarTool = Join-Path $JdkRoot 'bin\jar.exe'
$keyTool = Join-Path $JdkRoot 'bin\keytool.exe'
$aapt2 = Join-Path $buildTools 'aapt2.exe'
$d8 = Join-Path $buildTools 'd8.bat'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'

function Invoke-BuildTool {
    param([string]$Label, [string]$ToolPath, [string[]]$ToolArgs)
    Write-Host $Label
    & $ToolPath @ToolArgs
    if ($LASTEXITCODE -ne 0) { throw "$Label failed (exit $LASTEXITCODE)." }
}

function Write-Utf8 {
    param([string]$FilePath, [string]$Text)
    [System.IO.File]::WriteAllText($FilePath, $Text, $utf8)
}

function Get-ConfigValue {
    param([string]$Name, [string]$Default)
    if ($configValues.ContainsKey($Name) -and $configValues[$Name] -ne '') {
        # Match build.sh's treatment of quotes and backslashes.
        return ($configValues[$Name] -replace '["\\]', '')
    }
    return $Default
}

function Get-ConfigNumber {
    param([string]$Name, [string]$Default)
    $value = Get-ConfigValue $Name $Default
    if ($value -notmatch '^-?[0-9]+(\.[0-9]+)?$') { throw "Invalid number for $Name in config.properties." }
    return $value
}

Push-Location $PSScriptRoot
$previousJavaHome = $env:JAVA_HOME
try {
    foreach ($required in @($javaCompiler, $jarTool, $keyTool, $aapt2, $d8, $zipalign, $apksigner,
            $androidJar, (Join-Path $buildTools 'core-lambda-stubs.jar'),
            (Join-Path $PSScriptRoot 'tools\package_apk.py'))) {
        if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required tool/file missing: $required" }
    }
    $pythonCommand = Get-Command $PythonExe -ErrorAction Stop
    $PythonExe = $pythonCommand.Source
    $env:JAVA_HOME = $JdkRoot

    if (-not (Test-Path -LiteralPath 'config.properties')) {
        Copy-Item -LiteralPath 'config.example.properties' -Destination 'config.properties'
        Write-Host 'Created config.properties with upstream defaults; no TMDB key.'
    }
    $configValues = @{}
    foreach ($configLine in [System.IO.File]::ReadAllLines((Join-Path $PSScriptRoot 'config.properties'))) {
        if ($configLine -match '^\s*([^#=\s]+)\s*=(.*)$') {
            $configValues[$Matches[1]] = $Matches[2].Trim()
        }
    }

    # A fresh directory per build preserves earlier artifacts and avoids recursive deletion.
    $runName = 'windows-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
    $stage = 'out/' + $runName
    $stagePath = Join-Path $PSScriptRoot $stage
    $genPath = Join-Path $stagePath 'gen\io\freemytv'
    New-Item -ItemType Directory -Path $genPath -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $stagePath 'classes') | Out-Null

    $disableAssistantLiteral = if ((Get-ConfigValue 'disable_assistant' 'false') -ceq 'true') { 'true' } else { 'false' }
    $showCryptoLiteral = if ((Get-ConfigValue 'show_crypto' 'true') -ceq 'true') { 'true' } else { 'false' }
    $configJava = @"
package io.freemytv;

/** Generated from config.properties by build-windows.ps1. */
final class Config {
    private Config() {}
    static final String TMDB_KEY = "$(Get-ConfigValue 'tmdb_key' '')";
    static final String TMDB_REGION = "$(Get-ConfigValue 'tmdb_region' 'US')";
    static final String LATITUDE = "$(Get-ConfigNumber 'latitude' '40.7128')";
    static final String LONGITUDE = "$(Get-ConfigNumber 'longitude' '-74.0060')";
    static final String CITY = "$(Get-ConfigValue 'city' '')";
    static final String LANGUAGE = "$(Get-ConfigValue 'language' 'auto')";
    static final String CURRENCY = "$(Get-ConfigValue 'currency' 'USD')";
    static final String TEMPERATURE = "$(Get-ConfigValue 'temperature' 'auto')";
    static final boolean DISABLE_ASSISTANT = $disableAssistantLiteral;
    static final boolean SHOW_CRYPTO = $showCryptoLiteral;
}
"@
    Write-Utf8 (Join-Path $genPath 'Config.java') $configJava

    Invoke-BuildTool 'Compile Android resources' $aapt2 @('compile', '--dir', 'app/res', '-o', "$stage/res.zip")
    Invoke-BuildTool 'Link Android resources (minimum API 28, target API 33)' $aapt2 @(
        'link', '-o', "$stage/base.apk", '-I', $androidJar,
        '--manifest', 'app/AndroidManifest.xml', '--java', "$stage/gen", "$stage/res.zip",
        '--min-sdk-version', '28', '--target-sdk-version', '33', '-A', 'app/assets', '-0', 'ttf', '-0', 'mp4'
    )

    $sources = @(Get-ChildItem -LiteralPath 'app/src', "$stage/gen" -Recurse -File -Filter '*.java' |
        Sort-Object FullName | ForEach-Object { $_.FullName })
    if ($sources.Count -eq 0) { throw 'No Java source files found.' }
    $javacOptions = @('-nowarn', '-Xlint:-options', '-source', '8', '-target', '8',
        '-bootclasspath', ($androidJar + ';' + (Join-Path $buildTools 'core-lambda-stubs.jar')),
        '-classpath', $androidJar, '-encoding', 'UTF-8', '-d', (Join-Path $stagePath 'classes')) + $sources
    $javacArgText = ($javacOptions | ForEach-Object { '"' + $_.Replace('\', '/').Replace('"', '\"') + '"' }) -join "`n"
    Write-Utf8 (Join-Path $stagePath 'javac.args') $javacArgText
    Invoke-BuildTool 'Compile Java sources' $javaCompiler @("@$stage/javac.args")

    # A classes JAR avoids Windows command-line length limits when invoking d8.bat.
    Invoke-BuildTool 'Package compiled classes' $jarTool @('--create', '--file', "$stage/classes.jar", '-C', "$stage/classes", '.')
    Invoke-BuildTool 'Create DEX bytecode (minimum API 28)' $d8 @(
        '--release', '--min-api', '28', '--lib', $androidJar, '--output', $stage, "$stage/classes.jar"
    )
    Invoke-BuildTool 'Package DEX and normalize Windows asset paths' $PythonExe @(
        'tools/package_apk.py', 'package', "$stage/base.apk", $stage, "$stage/with-code.apk"
    )
    Invoke-BuildTool 'Align APK' $zipalign @('-f', '-p', '4', "$stage/with-code.apk", "$stage/aligned.apk")

    $keystore = Join-Path $PSScriptRoot 'freemytv.keystore'
    if (-not (Test-Path -LiteralPath $keystore)) {
        Invoke-BuildTool 'Create local signing key (keep this for future updates)' $keyTool @(
            '-genkeypair', '-keystore', $keystore, '-alias', 'freemytv', '-keyalg', 'RSA',
            '-keysize', '2048', '-validity', '20000', '-storepass', 'freemytv',
            '-keypass', 'freemytv', '-dname', 'CN=Free My TV'
        )
    }
    Invoke-BuildTool 'Sign APK' $apksigner @(
        'sign', '--ks', $keystore, '--ks-pass', 'pass:freemytv', '--key-pass', 'pass:freemytv',
        '--out', "$stage/FreeMyTV.apk", "$stage/aligned.apk"
    )
    Invoke-BuildTool 'Verify APK signature' $apksigner @('verify', '--verbose', '--print-certs', "$stage/FreeMyTV.apk")
    Invoke-BuildTool 'Verify APK alignment' $zipalign @('-c', '-p', '4', "$stage/FreeMyTV.apk")
    Invoke-BuildTool 'Verify APK contents' $PythonExe @('tools/package_apk.py', 'verify', "$stage/FreeMyTV.apk", 'app/assets')

    $badging = & $aapt2 dump badging "$stage/FreeMyTV.apk"
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect finished APK metadata.' }
    Write-Utf8 (Join-Path $stagePath 'apk-badging.txt') ($badging -join "`n")
    if (-not ($badging -match "^package: name='io.freemytv'")) { throw 'Unexpected APK package name.' }
    if (-not ($badging -match "^sdkVersion:'28'$")) { throw 'Unexpected minimum SDK.' }
    if (-not ($badging -match "^targetSdkVersion:'33'$")) { throw 'Unexpected target SDK.' }
    $nativeCodeLines = @($badging | Where-Object { $_ -match '^native-code:' })
    if ($nativeCodeLines.Count -gt 0) { throw 'Unexpected native-code ABI restriction; review before installing.' }

    # Publish only after all checks pass. Never replace an existing signing key.
    Copy-Item -LiteralPath "$stage/FreeMyTV.apk" -Destination 'FreeMyTV.apk' -Force
    $apkHash = (Get-FileHash -LiteralPath 'FreeMyTV.apk' -Algorithm SHA256).Hash.ToLowerInvariant()
    $sourceRevision = (& git rev-parse HEAD) -join ''
    if ($LASTEXITCODE -ne 0) { throw 'Could not identify source revision.' }
    $trackedSourceChanges = @(& git diff --name-only)
    if ($LASTEXITCODE -ne 0) { throw 'Could not identify local source changes.' }
    $buildInfo = [ordered]@{
        SourceRevision = $sourceRevision
        TrackedSourceChanges = $trackedSourceChanges
        MinimumSdk = 28
        TargetSdk = 33
        CompileSdk = 34
        Sha256 = $apkHash
        SignatureVerified = $true
        AlignmentVerified = $true
        AssetContentsVerified = $true
        TmdbConfigured = ((Get-ConfigValue 'tmdb_key' '') -ne '')
        DisableAssistant = ((Get-ConfigValue 'disable_assistant' 'false') -ceq 'true')
        InstalledOnTv = $false
    }
    Write-Utf8 (Join-Path $stagePath 'build-info.json') ($buildInfo | ConvertTo-Json)
    Write-Host ('Built and verified: ' + (Join-Path $PSScriptRoot 'FreeMyTV.apk'))
    Write-Host ('SHA256: ' + $apkHash)
    Write-Host ('Build details: ' + (Join-Path $stagePath 'build-info.json'))
    Write-Host 'Nothing has been installed on the TV.'
}
finally {
    if ($null -eq $previousJavaHome) { Remove-Item Env:\JAVA_HOME -ErrorAction SilentlyContinue }
    else { $env:JAVA_HOME = $previousJavaHome }
    Pop-Location
}
