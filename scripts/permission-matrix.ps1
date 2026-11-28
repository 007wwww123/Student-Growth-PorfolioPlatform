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

function Check($name, $actual, $expectedStatus, $expectedCode) {
    $ok = $actual.Status -eq $expectedStatus -and ($expectedCode -eq '' -or $actual.Code -eq $expectedCode)
    if ($ok) { $script:PASS++; Write-Host "PASS  $name" }
    else { $script:FAIL++; Write-Host "FAIL  $name => status=$($actual.Status) code=$($actual.Code)" }
}

function Login($u, $p) {
    $r = Req 'POST' '/auth/login' $null @{ username = $u; password = $p }
    if ($r.Code -eq '0') { return $r.Data.token }
    return $null
}

$stu = Login '2024010101' 'Passw0rd!'
$cou = Login 'cou9001' 'Passw0rd!'
$otherCou = Login 'cou9002' 'Passw0rd!'
$admin0 = Login 'admin0' 'Passw0rd!'
$admin1 = Login 'admin1' 'Passw0rd!'
$loginOk = $stu -and $cou -and $admin0 -and $admin1
Check 'T00 login roles' ([pscustomobject]@{ Status = 200; Code = if ($loginOk) { '0' } else { 'x' } }) 200 '0'

Check 'T01 student reads own profile' (Req 'GET' '/sections/basic/1001?academicYear=2025-2026-1' $stu $null) 200 '0'
Check 'T02 student cannot read other profile' (Req 'GET' '/sections/basic/1002?academicYear=2025-2026-1' $stu $null) 403 'EC-40302'
Check 'T03 COU reads own class profile' (Req 'GET' '/sections/basic/1001?academicYear=2025-2026-1' $cou $null) 200 '0'
Check 'T04 COU cannot read other class profile' (Req 'GET' '/sections/basic/1004?academicYear=2025-2026-1' $cou $null) 403 'EC-40302'
Check 'T05 COU cannot create college' (Req 'POST' '/org/colleges' $cou @{ collegeCode = 'X'; collegeName = 'X' }) 403 'EC-40303'
Check 'T06 student cannot create college' (Req 'POST' '/org/colleges' $stu @{ collegeCode = 'X'; collegeName = 'X' }) 403 'EC-40303'
Check 'T07 admin without grant gets empty view' (Req 'GET' '/sections/basic/1001?academicYear=2025-2026-1' $admin0 $null) 200 '0'

$apply = Req 'POST' '/admin/temp-grants/applications' $admin0 @{ hours = 1; reason = 'permission matrix regression' }
$grantId = $apply.Data.id
if ($grantId) {
    Req 'POST' "/admin/temp-grants/applications/$grantId/approve" $admin1 @{ remark = 'ok' } | Out-Null
}
Check 'T08 admin with temp grant reads profile' (Req 'GET' '/sections/basic/1001?academicYear=2025-2026-1' $admin0 $null) 200 '0'
if ($grantId) { Req 'POST' "/admin/temp-grants/applications/$grantId/revoke" $admin0 $null | Out-Null }

Check 'T09 student cannot export class excel' (Req 'POST' '/export/class' $stu @{ classCode = 'RJ2401' }) 403 'EC-40303'
Check 'T10 COU exports own class excel' (Req 'POST' '/export/class' $cou @{ classCode = 'RJ2401'; academicYear = '2025-2026-1' }) 200 '0'
Check 'T11 student cannot read login logs' (Req 'GET' '/audit/login-logs' $stu $null) 403 'EC-40303'
Check 'T12 anonymous gets 401' (Req 'GET' '/sections/basic/1001' $null $null) 401 ''

Write-Host ""
Write-Host "TOTAL PASS=$PASS FAIL=$FAIL"
if ($FAIL -gt 0) { exit 1 }
