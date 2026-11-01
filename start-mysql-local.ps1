param([string]$MySqlHome = 'C:\Program Files\MySQL\MySQL Server 9.6')
$ErrorActionPreference = 'Stop'
$server = Join-Path $MySqlHome 'bin\mysqld.exe'
if (-not (Test-Path -LiteralPath $server)) { throw 'MySQL Server not found. Set -MySqlHome to its installation directory.' }
$probe = New-Object Net.Sockets.TcpClient
try {
    $pending = $probe.BeginConnect('127.0.0.1', 3306, $null, $null)
    if ($pending.AsyncWaitHandle.WaitOne(1000)) {
        try { $probe.EndConnect($pending); Write-Host 'Port 3306 is already in use; no new MySQL instance started.'; return }
        catch [Net.Sockets.SocketException] { }
    }
} finally { $probe.Close() }
$data = Join-Path $PSScriptRoot 'data\mysql-local'
$pidFile = Join-Path $data 'server.pid'
if (Test-Path -LiteralPath $pidFile) {
    $previousId = 0
    if ([int]::TryParse((Get-Content -LiteralPath $pidFile -Raw).Trim(), [ref]$previousId)) {
        $previous = Get-Process -Id $previousId -ErrorAction SilentlyContinue
        if ($null -ne $previous -and $previous.ProcessName -eq 'mysqld') {
            Write-Host 'Waiting for the previous local MySQL process to finish shutting down...'
            if (-not $previous.WaitForExit(30000)) { throw 'Previous MySQL process is still running. Read data/mysql-local/server.log.' }
        }
    }
}
$systemTables = Join-Path $data 'mysql'
if (-not (Test-Path -LiteralPath $systemTables)) {
    if ((Test-Path -LiteralPath $data) -and @(Get-ChildItem -LiteralPath $data -Force).Count -ne 0) {
        throw 'data/mysql-local contains files but has no MySQL system tables. Inspect it before initializing.'
    }
    New-Item -ItemType Directory -Force -Path $data | Out-Null
    & $server --no-defaults --initialize-insecure "--basedir=$MySqlHome" "--datadir=$data" --console
    if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed.' }
}
$serverArguments = @('--no-defaults', ('--basedir="{0}"' -f $MySqlHome), ('--datadir="{0}"' -f $data),
    '--bind-address=127.0.0.1', '--port=3306', '--mysqlx=OFF',
    ('--log-error="{0}"' -f (Join-Path $data 'server.log')),
    ('--pid-file="{0}"' -f (Join-Path $data 'server.pid')))
$process = Start-Process -FilePath $server -ArgumentList $serverArguments -WindowStyle Hidden -PassThru
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    Start-Sleep -Seconds 1
    if ($process.HasExited) { throw "MySQL exited. Read $data\server.log" }
    $probe = New-Object Net.Sockets.TcpClient
    try {
        $pending = $probe.BeginConnect('127.0.0.1', 3306, $null, $null)
        if ($pending.AsyncWaitHandle.WaitOne(500)) {
            try {
                $probe.EndConnect($pending)
                Write-Host 'Local MySQL ready at 127.0.0.1:3306 (root, empty password).'
                return
            } catch [Net.Sockets.SocketException] { }
        }
    } finally { $probe.Close() }
}
throw "MySQL startup timed out. Read $data\server.log"
