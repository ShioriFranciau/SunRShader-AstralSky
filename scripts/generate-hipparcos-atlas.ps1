[CmdletBinding()]
param(
    [string]$JavaHome = 'D:\jdk-25.0.4.7-hotspot'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$workingDirectory = Join-Path $repoRoot 'build\catalog-source'
$partsDirectory = Join-Path $workingDirectory 'hipparcos-parts'
$classesDirectory = Join-Path $workingDirectory 'classes'
$atlasFile = Join-Path $repoRoot 'shaderpack\SunRShader-AstralSky\shaders\textures\hipparcos-stars.png'
$query = 'select name,ra,dec,vmag,bv_color from hipparcos where ra is not null and dec is not null and vmag is not null'

New-Item -ItemType Directory -Force -Path $workingDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $classesDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $partsDirectory | Out-Null

# The 118k-row catalogue exceeds the synchronous endpoint's result limit, so
# the download is paged into 24 right-ascension bands like the Tycho-2 script.
# The loop stays sequential because ForEach-Object -Parallel requires
# PowerShell 7 and each band response is only about one megabyte.
foreach ($band in 0..23) {
    $minimumRa = $band * 15
    $maximumRa = $minimumRa + 15
    $responseFile = Join-Path $partsDirectory ('hipparcos-{0:D2}.xml' -f $band)
    $validCache = (Test-Path -LiteralPath $responseFile -PathType Leaf) `
        -and (Select-String -Quiet -LiteralPath $responseFile -SimpleMatch '</VOTABLE>') `
        -and -not (Select-String -Quiet -LiteralPath $responseFile -SimpleMatch 'value="OVERFLOW"')
    if (-not $validCache) {
        $downloadFile = "$responseFile.download"
        $bandQuery = "$query and ra >= $minimumRa and ra < $maximumRa"
        & curl.exe --silent --show-error --fail-with-body --max-time 300 --get `
            'https://heasarc.gsfc.nasa.gov/xamin/vo/tap/sync' `
            --data-urlencode 'REQUEST=doQuery' `
            --data-urlencode 'LANG=ADQL' `
            --data-urlencode "QUERY=$bandQuery" `
            --output $downloadFile
        if ($LASTEXITCODE -ne 0 `
            -or -not (Select-String -Quiet -LiteralPath $downloadFile -SimpleMatch '</VOTABLE>') `
            -or (Select-String -Quiet -LiteralPath $downloadFile -SimpleMatch 'value="OVERFLOW"')) {
            throw "HEASARC Hipparcos query for RA band $minimumRa-$maximumRa failed or was truncated"
        }
        Move-Item -LiteralPath $downloadFile -Destination $responseFile -Force
    }
}

& (Join-Path $JavaHome 'bin\javac.exe') -d $classesDirectory (Join-Path $repoRoot 'tools\StarAtlasGenerator.java')
if ($LASTEXITCODE -ne 0) {
    throw "Star-atlas generator compilation failed with exit code $LASTEXITCODE"
}

& (Join-Path $JavaHome 'bin\java.exe') -Xmx2g -cp $classesDirectory StarAtlasGenerator $partsDirectory $atlasFile hipparcos
if ($LASTEXITCODE -ne 0) {
    throw "Star-atlas generation failed with exit code $LASTEXITCODE"
}
