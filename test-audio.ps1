$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
Push-Location $root
try {
    if (Test-Path -LiteralPath (Join-Path $root 'dist\TreasureRushServer.jar')) {
        & (Join-Path $root 'build.ps1') -ClientOnly
    } else { & (Join-Path $root 'build.ps1') }
    $classes = Join-Path $root ('out\audio-tests-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $classes | Out-Null
    $clientJar = Join-Path $root 'dist\TreasureRushClient.jar'
    $sources = @('AudioResourceTest.java', 'AudioUiTest.java', 'MainFrameAudioIntegrationTest.java') |
        ForEach-Object { Join-Path (Join-Path $root 'tests') $_ }
    & javac --release 17 -encoding UTF-8 -cp $clientJar -d $classes @sources
    if ($LASTEXITCODE -ne 0) { throw 'Audio test compile failed.' }
    $classpath = $classes + [IO.Path]::PathSeparator + $clientJar
    & java '-Djava.awt.headless=true' -cp $classpath AudioResourceTest
    if ($LASTEXITCODE -ne 0) { throw 'Packaged audio resource test failed.' }
    & java '-Djava.awt.headless=true' -cp $classpath client.AudioUiTest
    if ($LASTEXITCODE -ne 0) { throw 'Audio UI test failed.' }
    & java '-Djava.awt.headless=false' -cp $classpath client.MainFrameAudioIntegrationTest
    if ($LASTEXITCODE -ne 0) { throw 'MainFrame audio integration test failed.' }
    Write-Host '3/3 audio suites PASS (built client JAR; no visible test windows).'
} finally { Pop-Location }
