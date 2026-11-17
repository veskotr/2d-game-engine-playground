# Dot-sourced by the editor scripts: selects a 64-bit JDK 21 and puts it first on PATH.
# Prefers the caller's JDK, then the configured native build/PATH and common installs.
function Use-Jdk21([string]$JavaHome, [string]$RepoRoot) {
    $candidates = @($JavaHome)
    $cache = Join-Path $RepoRoot 'build/debug/CMakeCache.txt'
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
}
