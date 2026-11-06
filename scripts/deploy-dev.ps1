[CmdletBinding()]
param(
    [string]$InstanceDir = 'D:\PCL2\.minecraft\versions\26.3-Fabric',
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$resolvedInstance = (Resolve-Path -LiteralPath $InstanceDir).Path
$sourcePack = Join-Path $repoRoot 'shaderpack\SunRShader-AstralSky'
$shaderpacksDir = Join-Path $resolvedInstance 'shaderpacks'
$targetPack = Join-Path $shaderpacksDir 'SunRShader-AstralSky'
$modsDir = Join-Path $resolvedInstance 'mods'

if ($Build) {
    & (Join-Path $PSScriptRoot 'build.ps1') -Task build
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }
}

if (-not (Test-Path -LiteralPath $sourcePack -PathType Container)) {
    throw "Shader pack source not found: $sourcePack"
}

New-Item -ItemType Directory -Path $shaderpacksDir -Force | Out-Null
New-Item -ItemType Directory -Path $modsDir -Force | Out-Null

$fullShaderpacksDir = [System.IO.Path]::GetFullPath($shaderpacksDir).TrimEnd('\')
$fullTargetPack = [System.IO.Path]::GetFullPath($targetPack)
if (-not $fullTargetPack.StartsWith($fullShaderpacksDir + '\', [System.StringComparison]::OrdinalIgnoreCase) -or
    [System.IO.Path]::GetFileName($fullTargetPack) -ne 'SunRShader-AstralSky') {
    throw "Refusing to replace unexpected shader-pack target: $fullTargetPack"
}

if (Test-Path -LiteralPath $fullTargetPack) {
    Remove-Item -LiteralPath $fullTargetPack -Recurse -Force
}
Copy-Item -LiteralPath $sourcePack -Destination $fullTargetPack -Recurse

$builtJar = Get-ChildItem -LiteralPath (Join-Path $repoRoot 'build\libs') -File -Filter 'astralsky-*.jar' |
    Where-Object { $_.Name -notmatch '-sources\.jar$' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($null -ne $builtJar) {
    try {
        Get-ChildItem -LiteralPath $modsDir -File -Filter 'astralsky-*.jar' | Remove-Item -Force
        Copy-Item -LiteralPath $builtJar.FullName -Destination (Join-Path $modsDir $builtJar.Name)
        Write-Host "Deployed mod: $($builtJar.Name)"
    } catch [System.IO.IOException] {
        Write-Warning 'Minecraft is using the current AstralSky JAR. The shader pack was updated; close Minecraft and redeploy to update the mod JAR.'
    }
} else {
    Write-Warning 'No built AstralSky mod JAR was found; only the shader pack was deployed.'
}

Write-Host "Deployed shader pack: $fullTargetPack"

