[CmdletBinding()]
param(
    [string]$JavaHome = 'D:\jdk-25.0.4.7-hotspot'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$workingDirectory = Join-Path $repoRoot 'build\catalog-source'
$responseFile = Join-Path $workingDirectory 'bsc5p.xml'
$downloadFile = Join-Path $workingDirectory 'bsc5p.download.xml'
$classesDirectory = Join-Path $workingDirectory 'classes'
$atlasFile = Join-Path $repoRoot 'shaderpack\SunRShader-AstralSky\shaders\textures\bsc5-stars.png'
$query = 'select name,ra,dec,vmag,bv_color from bsc5p where ra is not null and dec is not null and vmag is not null'

New-Item -ItemType Directory -Force -Path $workingDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $classesDirectory | Out-Null

& curl.exe --fail-with-body --max-time 120 --get 'https://heasarc.gsfc.nasa.gov/xamin/vo/tap/sync' `
    --data-urlencode 'REQUEST=doQuery' `
    --data-urlencode 'LANG=ADQL' `
    --data-urlencode "QUERY=$query" `
    --output $downloadFile
if ($LASTEXITCODE -ne 0) {
    if (-not (Test-Path -LiteralPath $responseFile -PathType Leaf)) {
        throw "HEASARC query failed with exit code $LASTEXITCODE and no cached response is available"
    }
    Write-Warning "HEASARC query failed with exit code $LASTEXITCODE; validating and using the cached response"
} else {
    Move-Item -LiteralPath $downloadFile -Destination $responseFile -Force
}

& (Join-Path $JavaHome 'bin\javac.exe') -d $classesDirectory (Join-Path $repoRoot 'tools\StarAtlasGenerator.java')
if ($LASTEXITCODE -ne 0) {
    throw "Star-atlas generator compilation failed with exit code $LASTEXITCODE"
}

& (Join-Path $JavaHome 'bin\java.exe') -cp $classesDirectory StarAtlasGenerator $responseFile $atlasFile
if ($LASTEXITCODE -ne 0) {
    throw "Star-atlas generation failed with exit code $LASTEXITCODE"
}
