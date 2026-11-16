# Shared by VS Code's launch configuration and task. Run from any directory.
[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$EditorArgs = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path

# Prefer the caller's JDK, then the configured native build/PATH and common installs.
$candidates = @($JavaHome)
$cache = Join-Path $repoRoot 'build/debug/CMakeCache.txt'
if (Test-Path $cache) {
    $include = Get-Content $cache | Select-String '^JAVA_INCLUDE_PATH:PATH=(.*)/include$' | Select-Object -First 1
    if ($include) { $candidates += $include.Matches[0].Groups[1].Value }
}
$javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
if ($javaCommand) { $candidates += Split-Path (Split-Path $javaCommand.Source -Parent) -Parent }
foreach ($pattern in @('C:/java/jdk-21*', "$env:ProgramFiles/Java/jdk-21*", "$env:ProgramFiles/Eclipse Adoptium/jdk-21*")) {
    $candidates += Get-Item $pattern -ErrorAction SilentlyContinue | Where-Object { $_.PSIsContainer } | Select-Object -ExpandProperty FullName
}
$jdk = $candidates | Where-Object {
    $_ -and (Test-Path (Join-Path $_ 'bin/javac.exe')) -and
    (Test-Path (Join-Path $_ 'release')) -and
    (Select-String -Path (Join-Path $_ 'release') -Pattern '^JAVA_VERSION="21[.\"]' -Quiet)
} | Select-Object -First 1
if (!$jdk) { throw 'JDK 21 was not found. Set JAVA_HOME to a 64-bit JDK 21 and restart VS Code.' }
$env:JAVA_HOME = (Resolve-Path $jdk).Path
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Push-Location $repoRoot
try {
    & cmake --preset debug -DSLE_BUILD_EDITOR=ON
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & cmake --build build/debug --config Debug --target sle_editor_native --parallel 4
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $gradleArgs = @('-p', $PSScriptRoot, '-g', (Join-Path $PSScriptRoot '.gradle-user-home'), 'run', '--console', 'plain', '--no-daemon')
    if ($EditorArgs) { $gradleArgs += "--args=$EditorArgs" }
    & (Join-Path $PSScriptRoot 'gradlew.bat') @gradleArgs
    exit $LASTEXITCODE
} finally { Pop-Location }
