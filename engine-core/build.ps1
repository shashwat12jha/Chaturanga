param([switch]$SkipTests)

$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot
$Build = Join-Path $Root 'build'
$TempRoot = Join-Path ([IO.Path]::GetTempPath()) ('chaturanga-build-' + [guid]::NewGuid().ToString('N'))
$MainClasses = Join-Path $TempRoot 'classes'
$TestClasses = Join-Path $TempRoot 'test-classes'

try {
    New-Item -ItemType Directory -Path $MainClasses, $TestClasses -Force | Out-Null
    $MainSources = Get-ChildItem (Join-Path $Root 'src/main/java') -Recurse -Filter *.java | ForEach-Object FullName
    & javac -encoding UTF-8 -Xlint:all -Werror -d $MainClasses $MainSources
    if ($LASTEXITCODE -ne 0) { throw 'Main compilation failed' }

    if (-not $SkipTests) {
        $TestSources = Get-ChildItem (Join-Path $Root 'src/test/java') -Recurse -Filter *.java | ForEach-Object FullName
        & javac -encoding UTF-8 -Xlint:all -Werror -cp $MainClasses -d $TestClasses $TestSources
        if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
        & java -ea -cp "$MainClasses;$TestClasses" io.chaturanga.engine.EngineCoreTest
        if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
    }

    $Manifest = Join-Path $TempRoot 'MANIFEST.MF'
    $TempJar = Join-Path $TempRoot 'chaturanga-engine.jar'
    Set-Content -LiteralPath $Manifest -Encoding ASCII -Value "Main-Class: io.chaturanga.engine.uci.UciMain`r`n"
    & jar --create --file $TempJar --manifest $Manifest -C $MainClasses .
    if ($LASTEXITCODE -ne 0) { throw 'JAR creation failed' }

    if (Test-Path $Build) { Remove-Item -LiteralPath $Build -Recurse -Force }
    New-Item -ItemType Directory -Path $Build -Force | Out-Null
    Copy-Item -LiteralPath $TempJar -Destination (Join-Path $Build 'chaturanga-engine.jar')
    Write-Host "Built: $(Join-Path $Build 'chaturanga-engine.jar')"
} finally {
    if (Test-Path $TempRoot) { Remove-Item -LiteralPath $TempRoot -Recurse -Force }
}
