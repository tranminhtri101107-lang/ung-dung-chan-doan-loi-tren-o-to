# Thuc nghiem: chay N lan moi kich ban tren ECU + Gateway that -> du-lieu\ket_qua_thuc_nghiem.csv,
# roi PhanTichThucNghiem.java phan tich -> cac bang tom tat trong du-lieu\ va bieu do trong ket-qua\.
# Chay: powershell -ExecutionPolicy Bypass -File thuc-nghiem\chay-thuc-nghiem.ps1 [-N 30] [-ConfigDir <thu muc chua config.properties>] [-ChiPhanTich]
#   -ChiPhanTich : khong chay lai thuc nghiem, chi phan tich lai tep CSV da co (khong can may ao)
# Yeu cau khi chay thuc nghiem: ECU va Gateway dang chay tren may ao; CSDL (cua ConfigDir) da nap sql/05_seed_luat.sql.
param([int]$N = 30, [string]$ConfigDir, [switch]$ChiPhanTich)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $ConfigDir) { $ConfigDir = $root }
$jdk = ((Get-Content (Join-Path $root 'run.bat') | Where-Object { $_ -match '^set JDK=' }) -split '=', 2)[1].Trim() + '\bin'
$du = Join-Path $PSScriptRoot 'du-lieu'
$kq = Join-Path $PSScriptRoot 'ket-qua'
$csv = Join-Path $du 'ket_qua_thuc_nghiem.csv'
$tmp = Join-Path $env:TEMP 'thucnghiem'
New-Item -ItemType Directory -Force $du, $kq, $tmp | Out-Null

if (-not $ChiPhanTich) {
    & "$jdk\javac.exe" -encoding UTF-8 -cp "$root\bin;$root\lib\*" -d $tmp (Join-Path $PSScriptRoot 'ChayThucNghiem.java')
    if ($LASTEXITCODE -ne 0) { throw 'Bien dich that bai (chay run.bat mot lan de co bin\)' }
    Push-Location $ConfigDir   # Database doc config.properties o thu muc hien hanh
    try {
        & "$jdk\java.exe" '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -cp "$tmp;$root\bin;$root\lib\*" ChayThucNghiem $csv $N
    } finally { Pop-Location }
}

# Phan tich: chi dung thu vien chuan cua Java nen chay thang tu ma nguon
& "$jdk\java.exe" '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' (Join-Path $PSScriptRoot 'PhanTichThucNghiem.java') $csv $du $kq
if ($LASTEXITCODE -ne 0) { throw 'Phan tich that bai' }
"Xong: so lieu o $du, bieu do o $kq"
