$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sqlDir = Join-Path $root 'backend/src/main/resources/db/sql'
$outDir = Join-Path $root 'backend/src/main/resources/db/migration'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$out = Join-Path $outDir 'V1__baseline.sql'
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('-- 阶段 C: Flyway baseline, generated from db/sql/01..29; do not edit by hand.')
[void]$sb.AppendLine('-- Regenerate: powershell -File scripts/build-flyway-baseline.ps1')
Get-ChildItem $sqlDir -Filter '*.sql' | Sort-Object Name | ForEach-Object {
    [void]$sb.AppendLine('')
    [void]$sb.AppendLine('-- >>> ' + $_.Name)
    [void]$sb.AppendLine([System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8))
}
[System.IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "generated $out"
