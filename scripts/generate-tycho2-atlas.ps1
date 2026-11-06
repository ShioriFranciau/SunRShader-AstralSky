[CmdletBinding()]
param(
    [string]$JavaHome = 'D:\jdk-25.0.4.7-hotspot'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$workingDirectory = Join-Path $repoRoot 'build\catalog-source'
$partsDirectory = Join-Path $workingDirectory 'tycho2-parts'
$classesDirectory = Join-Path $workingDirectory 'tycho-classes'
$atlasFile = Join-Path $repoRoot 'shaderpack\SunRShader-AstralSky\shaders\textures\tycho2-stars.png'
$query = 'select name,mean_ra,mean_dec,bt_mag,vt_mag from tycho2 where mean_ra is not null and mean_dec is not null'

New-Item -ItemType Directory -Force -Path $workingDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $classesDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $partsDirectory | Out-Null

# The loops stay sequential because ForEach-Object -Parallel requires
# PowerShell 7; a cold cache downloads 144 band responses one by one.
foreach ($band in 0..71) {
    $minimumRa = $band * 5
    $maximumRa = $minimumRa + 5
    $responseFile = Join-Path $partsDirectory ('tycho2-{0:D2}.xml' -f $band)
    $validCache = (Test-Path -LiteralPath $responseFile -PathType Leaf) `
        -and (Select-String -Quiet -LiteralPath $responseFile -SimpleMatch '</VOTABLE>') `
        -and -not (Select-String -Quiet -LiteralPath $responseFile -SimpleMatch 'value="OVERFLOW"')
    if (-not $validCache) {
        $downloadFile = "$responseFile.download"
        $bandQuery = "$query and mean_ra >= $minimumRa and mean_ra < $maximumRa"
        & curl.exe --silent --show-error --fail-with-body --max-time 300 --get `
            'https://heasarc.gsfc.nasa.gov/xamin/vo/tap/sync' `
            --data-urlencode 'REQUEST=doQuery' `
            --data-urlencode 'LANG=ADQL' `
            --data-urlencode "QUERY=$bandQuery" `
            --output $downloadFile
        if ($LASTEXITCODE -ne 0 `
            -or -not (Select-String -Quiet -LiteralPath $downloadFile -SimpleMatch '</VOTABLE>') `
            -or (Select-String -Quiet -LiteralPath $downloadFile -SimpleMatch 'value="OVERFLOW"')) {
            throw "HEASARC Tycho-2 query for RA band $minimumRa-$maximumRa failed or was truncated"
        }
        Move-Item -LiteralPath $downloadFile -Destination $responseFile -Force
    }
}

$fallbackQuery = 'select name,observed_ra,observed_dec,bt_mag,vt_mag from tycho2 where mean_ra is null and observed_ra is not null and observed_dec is not null'
foreach ($band in 0..71) {
    $minimumRa = $band * 5
    $maximumRa = $minimumRa + 5
    $responseFile = Join-Path $partsDirectory ('tycho2-fallback-{0:D2}.xml' -f $band)
    $validCache = (Test-Path -LiteralPath $responseFile -PathType Leaf) `
        -and (Select-String -Quiet -LiteralPath $responseFile -SimpleMatch '</VOTABLE>') `
        -and -not (Select-String -Quiet -LiteralPath $responseFile -SimpleMatch 'value="OVERFLOW"')
    if (-not $validCache) {
        $downloadFile = "$responseFile.download"
        $bandQuery = "$fallbackQuery and observed_ra >= $minimumRa and observed_ra < $maximumRa"
        & curl.exe --silent --show-error --fail-with-body --max-time 300 --get `
            'https://heasarc.gsfc.nasa.gov/xamin/vo/tap/sync' `
            --data-urlencode 'REQUEST=doQuery' `
            --data-urlencode 'LANG=ADQL' `
            --data-urlencode "QUERY=$bandQuery" `
            --output $downloadFile
        if ($LASTEXITCODE -ne 0 `
            -or -not (Select-String -Quiet -LiteralPath $downloadFile -SimpleMatch '</VOTABLE>') `
            -or (Select-String -Quiet -LiteralPath $downloadFile -SimpleMatch 'value="OVERFLOW"')) {
            throw "HEASARC Tycho-2 fallback query for RA band $minimumRa-$maximumRa failed or was truncated"
        }
        Move-Item -LiteralPath $downloadFile -Destination $responseFile -Force
    }
}

& (Join-Path $JavaHome 'bin\javac.exe') -d $classesDirectory `
    (Join-Path $repoRoot 'tools\StarAtlasGenerator.java')
if ($LASTEXITCODE -ne 0) {
    throw "Star-atlas generator compilation failed with exit code $LASTEXITCODE"
}

# The 16384 by 8192 atlas raster alone needs around 540 MB of heap, plus the
# hipparcos bright-star reference that is merged into it.
& (Join-Path $JavaHome 'bin\java.exe') -Xmx5g -cp $classesDirectory `
    StarAtlasGenerator $partsDirectory $atlasFile tycho2
if ($LASTEXITCODE -ne 0) {
    throw "Tycho-2 atlas generation failed with exit code $LASTEXITCODE"
}
