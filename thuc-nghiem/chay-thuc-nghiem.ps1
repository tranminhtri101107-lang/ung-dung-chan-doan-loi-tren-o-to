# Thuc nghiem: chay N lan moi kich ban tren ECU + Gateway that -> du-lieu\ket_qua_thuc_nghiem.csv,
# roi MATLAB phan tich -> bieu do trong ket-qua\ va cac bang tom tat trong du-lieu\.
# Chay: powershell -ExecutionPolicy Bypass -File thuc-nghiem\chay-thuc-nghiem.ps1 [-N 30] [-ConfigDir <thu muc chua config.properties>] [-BoQuaMatlab]
# Yeu cau: ECU va Gateway dang chay tren may ao; CSDL (cua ConfigDir) da nap sql/05_seed_luat.sql.
param([int]$N = 30, [string]$ConfigDir, [switch]$BoQuaMatlab)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $ConfigDir) { $ConfigDir = $root }
$jdk = ((Get-Content (Join-Path $root 'run.bat') | Where-Object { $_ -match '^set JDK=' }) -split '=', 2)[1].Trim() + '\bin'
$du = Join-Path $PSScriptRoot 'du-lieu'
$kq = Join-Path $PSScriptRoot 'ket-qua'
$tmp = Join-Path $env:TEMP 'thucnghiem'
New-Item -ItemType Directory -Force $du, $kq, $tmp | Out-Null

& "$jdk\javac.exe" -encoding UTF-8 -cp "$root\bin;$root\lib\*" -d $tmp (Join-Path $PSScriptRoot 'ChayThucNghiem.java')
if ($LASTEXITCODE -ne 0) { throw 'Bien dich that bai (chay run.bat mot lan de co bin\)' }
Push-Location $ConfigDir   # Database doc config.properties o thu muc hien hanh
try {
    & "$jdk\java.exe" '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -cp "$tmp;$root\bin;$root\lib\*" ChayThucNghiem (Join-Path $du 'ket_qua_thuc_nghiem.csv') $N
} finally { Pop-Location }
if ($BoQuaMatlab) { return }

Push-Location $PSScriptRoot
try {
    & 'C:\Program Files\MATLAB\R2025a\bin\matlab.exe' -batch "phan_tich_thuc_nghiem('$du\ket_qua_thuc_nghiem.csv', '$kq', '$du')"
    if ($LASTEXITCODE -ne 0) { throw 'MATLAB that bai' }
} finally { Pop-Location }
"Xong: so lieu o $du, bieu do o $kq"
