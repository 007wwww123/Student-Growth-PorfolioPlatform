$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$BASE = 'http://127.0.0.1:18080/api'
$N = 30
$times = @()
for ($i = 0; $i -lt $N; $i++) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try { Invoke-WebRequest -Uri "$BASE/actuator/health" -UseBasicParsing -TimeoutSec 10 | Out-Null } catch {}
    $sw.Stop()
    $times += $sw.Elapsed.TotalMilliseconds
}
$sorted = $times | Sort-Object
$avg = ($times | Measure-Object -Average).Average
$p95 = $sorted[[Math]::Floor($sorted.Count * 0.95) - 1]
Write-Host ("PERF health n={0} avg={1:N1}ms p95={2:N1}ms max={3:N1}ms" -f $N, $avg, $p95, ($sorted[-1]))
if ($avg -gt 500) { exit 1 }
