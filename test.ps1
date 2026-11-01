param([switch]$JarGui, [switch]$MySql, [string]$ServerIp = '127.0.0.1', [int]$ServerPort = 5000)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
Push-Location $root
try {
    & (Join-Path $root 'build.ps1')
    $classes = Join-Path $root ('out\tests-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $classes | Out-Null
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'src') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
    $testFiles = @(Get-ChildItem -LiteralPath (Join-Path $root 'tests') -Filter '*.java')
    if ($MySql -and -not $JarGui) {
        # Run the persistence suite independently of unrelated UI/trap regressions.
        $selected = @('MapGenerationTest.java', 'GameLifecycleIntegrationTest.java',
            'RankingPersistenceIntegrationTest.java', 'MySqlPersistenceIntegrationTest.java')
        $testFiles = @($testFiles | Where-Object { $_.Name -in $selected })
    }
    $sources += @($testFiles | ForEach-Object { $_.FullName })
    & javac --release 17 -encoding UTF-8 -d $classes @sources
    if ($LASTEXITCODE -ne 0) { throw 'Test compile failed.' }
    $tests = @('MapGenerationTest', 'TreasureCollectionTest', 'InviteIntegrationTest', 'MoveIntegrationTest',
        'GameTimerIntegrationTest', 'GameLifecycleIntegrationTest', 'RankingPersistenceIntegrationTest',
        'GamePanelRenderTest', 'ResultPanelRenderTest', 'StatisticsPanelRenderTest', 'LoginPanelRenderTest',
        'LobbyPanelRenderTest', 'ClientConnectionTest', 'RoomNameTest',
        'server.TrapMechanicsTest', 'TrapIntegrationTest', 'TrapClientRenderTest', 'AudioResourceTest', 'client.AudioUiTest')
    if ($MySql -and -not $JarGui) {
        $tests = @('MapGenerationTest', 'GameLifecycleIntegrationTest', 'RankingPersistenceIntegrationTest')
    }
    foreach ($test in $tests) {
        & java '-Djava.awt.headless=true' -cp ($classes + [IO.Path]::PathSeparator + (Join-Path $root 'resources')) $test
        if ($LASTEXITCODE -ne 0) { throw "$test FAILED" }
    }
    Write-Host ($tests.Count.ToString() + '/' + $tests.Count + ' regression tests PASS')
    if ($MySql) {
        & java -cp ($classes + [IO.Path]::PathSeparator + (Join-Path $root 'lib\*')) MySqlPersistenceIntegrationTest
        if ($LASTEXITCODE -ne 0) { throw 'MySQL integration test FAILED' }
    }
    if ($JarGui) {
        & java -cp $classes JarGuiIntegrationTest $ServerIp $ServerPort
        if ($LASTEXITCODE -ne 0) { throw 'JAR GUI test FAILED' }
    }
} finally { Pop-Location }
