param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'

if ((Test-Path -LiteralPath $envPath) -and -not $Force) {
    throw '.env already exists. Use -Force only when you intend to rotate local credentials.'
}

function New-RandomHex([int]$byteCount) {
    $bytes = New-Object byte[] $byteCount
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($bytes)
    }
    finally {
        $random.Dispose()
    }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

$content = @(
    'JWT_ISSUER=local-gateway'
    'JWT_AUDIENCE=gateway-sample-api'
    "JWT_SECRET=$(New-RandomHex 32)"
    'JWT_TTL=1h'
    'DEMO_USERNAME=demo'
    "DEMO_PASSWORD=$(New-RandomHex 16)"
    "GRAFANA_ADMIN_PASSWORD=$(New-RandomHex 24)"
) -join [Environment]::NewLine

$utf8WithoutBom = New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllText($envPath, $content + [Environment]::NewLine, $utf8WithoutBom)
Write-Host "Created $envPath with random local credentials. Values were not printed."
