[CmdletBinding()]
param(
    [ValidateSet('build', 'test', 'clean')]
    [string]$Task = 'build'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$javaHome = $env:JAVA_HOME

if ([string]::IsNullOrWhiteSpace($javaHome)) {
    throw 'JAVA_HOME must point to a Java 25 installation.'
}

$javaExecutable = Join-Path $javaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
    throw "Java executable not found: $javaExecutable"
}

# `java -version` writes to stderr. With $ErrorActionPreference = 'Stop' that
# stream would terminate the script before the version check can run, so lower
# the preference for this single call and restore it afterwards.
$previousPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $versionOutput = & $javaExecutable -version 2>&1 | Out-String
} finally {
    $ErrorActionPreference = $previousPreference
}
if ($versionOutput -notmatch 'version "25(?:\.|\")') {
    throw "AstralSky for Minecraft 26.3 requires Java 25. Detected: $versionOutput"
}

Push-Location $repoRoot
try {
    & (Join-Path $repoRoot 'gradlew.bat') "-Dorg.gradle.java.home=$javaHome" $Task
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle task '$Task' failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

