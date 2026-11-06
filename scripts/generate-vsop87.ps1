[CmdletBinding()]
param(
    [string]$JavaHome = 'D:\jdk-25.0.4.7-hotspot'
)

# Regenerates the truncated VSOP87 coefficient tables used by both the Java
# reference implementation and the shader pack. The full-precision CSV series
# in tools\vsop87d are the authoritative input and are never rewritten here;
# see tools\vsop87d\README.md for how they were exported from PyMeeus.
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$dataDirectory = Join-Path $repoRoot 'tools\vsop87d'
$classesDirectory = Join-Path $repoRoot 'build\vsop87-classes'
$javaOutput = Join-Path $repoRoot 'src\main\java\dev\astralsky\astro\Vsop87Tables.java'
$glslOutput = Join-Path $repoRoot 'shaderpack\SunRShader-AstralSky\shaders\include\sky\vsop87.glsl'

if (-not (Test-Path -LiteralPath $dataDirectory -PathType Container)) {
    throw "VSOP87 data directory not found: $dataDirectory"
}

New-Item -ItemType Directory -Force -Path $classesDirectory | Out-Null

& (Join-Path $JavaHome 'bin\javac.exe') -d $classesDirectory (Join-Path $repoRoot 'tools\Vsop87Generator.java')
if ($LASTEXITCODE -ne 0) {
    throw "VSOP87 generator compilation failed with exit code $LASTEXITCODE"
}

& (Join-Path $JavaHome 'bin\java.exe') -cp $classesDirectory Vsop87Generator $dataDirectory $javaOutput $glslOutput
if ($LASTEXITCODE -ne 0) {
    throw "VSOP87 table generation failed with exit code $LASTEXITCODE"
}
