$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$BASE = 'http://127.0.0.1:18080/api'
$PASS = 0
$FAIL = 0

function Req($method, $path, $token, $body) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Uri = "$BASE$path"; Method = $method; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 20 }
    if ($null -ne $body) {
        $params['ContentType'] = 'application/json; charset=utf-8'
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 8 -Compress))
    }
    try {
        $resp = Invoke-WebRequest @params
        $json = $resp.Content | ConvertFrom-Json
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Code = $json.code; Data = $json.data }
    } catch {
        $web = $_.Exception.Response
        if (-not $web) { throw }
        $reader = New-Object System.IO.StreamReader($web.GetResponseStream())
        $json = $reader.ReadToEnd() | ConvertFrom-Json
        return [pscustomobject]@{ Status = [int]$web.StatusCode; Code = $json.code; Data = $json.data }
    }
}

function Check($name, $actual, $status) {
    if ($actual.Status -eq $status) { $script:PASS++; Write-Host "PASS  $name" }
    else { $script:FAIL++; Write-Host "FAIL  $name => status=$($actual.Status) code=$($actual.Code)" }
}

function Login($u, $p) {
    $r = Req 'POST' '/auth/login' $null @{ username = $u; password = $p }
    if ($r.Code -eq '0') { return $true }
    return $false
}

Check 'S01 SQL injection login rejected' (Req 'POST' '/auth/login' $null @{ username = "' OR 1=1 --"; password = 'x' }) 401
Check 'S02 wrong password rejected' (Req 'POST' '/auth/login' $null @{ username = '2024010101'; password = 'wrong' }) 401
Check 'S03 anonymous business request 401' (Req 'GET' '/sections/basic/1001' $null $null) 401

$stu = (Req 'POST' '/auth/login' $null @{ username = '2024010101'; password = 'Passw0rd!' }).Data.token
Check 'S04 IDOR other student profile 403' (Req 'GET' '/sections/basic/1002?academicYear=2025-2026-1' $stu $null) 403
Check 'S05 no-grant admin cannot read business data' (Req 'GET' '/sections/basic/1001?academicYear=2025-2026-1' (Req 'POST' '/auth/login' $null @{ username = 'admin0'; password = 'Passw0rd!' }).Data.token $null) 200

$tmp = Join-Path $env:TEMP 'evil.html'
Set-Content -Path $tmp -Value '<script>alert(1)</script>' -Encoding ASCII
$out = & curl.exe -s -X POST "$BASE/files" -H "Authorization: Bearer $stu" -F "file=@$tmp;type=text/html" -F "studentId=1001" -F "bizType=OTHER"
$json = $out | ConvertFrom-Json
if ($json.code -eq 'EC-40003' -or $json.code -eq 'EC-42201') { $script:PASS++; Write-Host 'PASS  S06 HTML upload rejected' }
else { $script:FAIL++; Write-Host "FAIL  S06 HTML upload rejected => $($json.code)" }

Write-Host ""
Write-Host "TOTAL PASS=$PASS FAIL=$FAIL"
if ($FAIL -gt 0) { exit 1 }
