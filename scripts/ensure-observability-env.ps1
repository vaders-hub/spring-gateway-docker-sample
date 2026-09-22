$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'

if (-not (Test-Path -LiteralPath $envPath)) {
    throw 'Create .env first by running scripts/new-local-env.ps1.'
}

$existing = Get-Content -LiteralPath $envPath
if ($existing | Where-Object { $_ -match '^GRAFANA_ADMIN_PASSWORD=' }) {
    Write-Host 'GRAFANA_ADMIN_PASSWORD already exists. No changes made.'
    exit 0
}

$bytes = New-Object byte[] 24
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $random.GetBytes($bytes)
}
finally {
    $random.Dispose()
}
$password = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
[IO.File]::AppendAllText(
    $envPath,
    "GRAFANA_ADMIN_PASSWORD=$password" + [Environment]::NewLine,
    (New-Object Text.UTF8Encoding($false)))
Write-Host 'Added a random Grafana admin password to the Git-ignored .env. Value was not printed.'
