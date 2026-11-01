param([switch]$ClientOnly)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$classes = Join-Path $root ('out\build-' + [guid]::NewGuid().ToString('N'))
$dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $classes, $dist | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'src') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& javac --release 17 -encoding UTF-8 -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Compile failed. Install JDK 17 or newer and check PATH.' }
$target = if ($ClientOnly) { 'client' } else { 'both' }
& java --source 17 (Join-Path $root 'tools\BuildJars.java') $classes $dist (Join-Path $root 'resources') (Join-Path $root 'lib') $target
if ($LASTEXITCODE -ne 0) { throw 'JAR build failed.' }
if ($ClientOnly) { Write-Host 'Built dist/TreasureRushClient.jar (Java 17+).' }
else { Write-Host 'Built dist/TreasureRushServer.jar and dist/TreasureRushClient.jar (Java 17+).' }
