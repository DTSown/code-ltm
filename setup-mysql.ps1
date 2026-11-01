param([switch]$ImportTsv)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
Push-Location $root
try {
    $configuration = Join-Path $root 'config\database.properties'
    if (-not (Test-Path -LiteralPath $configuration)) {
        Copy-Item -LiteralPath (Join-Path $root 'config\database.example.properties') -Destination $configuration
    }
    $version = '9.7.0'
    $library = Join-Path $root 'lib'
    $driver = Join-Path $library "mysql-connector-j-$version.jar"
    New-Item -ItemType Directory -Force -Path $library | Out-Null
    if (-not (Test-Path -LiteralPath $driver)) {
        $download = "$driver.download"
        $url = "https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/$version/mysql-connector-j-$version.jar"
        try {
            Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $download
            $expected = (Invoke-WebRequest -UseBasicParsing -Uri "$url.sha256").Content.Trim()
            $actual = (Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash
            if ($actual -ine $expected) { throw 'Connector/J checksum mismatch.' }
            Move-Item -LiteralPath $download -Destination $driver
        } finally {
            if (Test-Path -LiteralPath $download) { Remove-Item -LiteralPath $download }
        }
    }
    & (Join-Path $root 'build.ps1')
    $arguments = @('-cp', (Join-Path $root 'dist\TreasureRushServer.jar'), 'server.DatabaseSetup')
    if ($ImportTsv) { $arguments += (Join-Path $root 'data\server-data.tsv') }
    & java @arguments
    if ($LASTEXITCODE -ne 0) { throw 'MySQL setup failed. Check config/database.properties and that MySQL Server is running.' }
} finally { Pop-Location }
