# Shared by VS Code's launch configuration and task. Run from any directory.
[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$EditorArgs = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $PSScriptRoot 'find-jdk.ps1')
Use-Jdk21 $JavaHome $repoRoot

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
