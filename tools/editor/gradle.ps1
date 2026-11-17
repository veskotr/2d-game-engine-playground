# Runs editor Gradle tasks with JDK 21 from any directory, e.g.
#   .\gradle.ps1 build
#   .\gradle.ps1 createDistributable
# Uses the same Gradle user home as run-editor.ps1.
[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)][string[]]$Tasks,
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $PSScriptRoot 'find-jdk.ps1')
Use-Jdk21 $JavaHome $repoRoot
if (!$Tasks) { throw 'Pass one or more Gradle tasks, e.g. build or createDistributable.' }

$gradleArgs = @('-p', $PSScriptRoot, '-g', (Join-Path $PSScriptRoot '.gradle-user-home'), '--console', 'plain') + $Tasks
& (Join-Path $PSScriptRoot 'gradlew.bat') @gradleArgs
exit $LASTEXITCODE
