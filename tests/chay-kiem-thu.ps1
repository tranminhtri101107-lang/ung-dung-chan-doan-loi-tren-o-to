# Bien dich ung dung (src -> bin) roi chay lan luot cac chuong trinh kiem thu, in DAT/LOI tung cai.
# Chay: powershell -ExecutionPolicy Bypass -File tests\chay-kiem-thu.ps1 [-CoGateway] [-ConfigDir <thu muc chua config.properties>]
#   -CoGateway : chay them KiemThuPhanTich (can ECU + Gateway dang chay tren may ao)
#   -ConfigDir : CSDL dung de kiem thu (nen tro toi config cua CSDL thu ChanDoanXe_Test); mac dinh la goc du an
param([switch]$CoGateway, [string]$ConfigDir)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $ConfigDir) { $ConfigDir = $root }
$jdk = ((Get-Content (Join-Path $root 'run.bat') | Where-Object { $_ -match '^set JDK=' }) -split '=', 2)[1].Trim() + '\bin'
$tmp = Join-Path $env:TEMP 'kiem-thu'
New-Item -ItemType Directory -Force (Join-Path $root 'bin'), $tmp | Out-Null

$src = Get-ChildItem (Join-Path $root 'src') -Recurse -Filter *.java | ForEach-Object FullName
& "$jdk\javac.exe" -encoding UTF-8 -cp "$root\lib\*" -d "$root\bin" $src
if ($LASTEXITCODE -ne 0) { throw 'Bien dich src that bai' }
& "$jdk\javac.exe" -encoding UTF-8 -cp "$root\bin;$root\lib\*" -d $tmp (Get-ChildItem $PSScriptRoot -Filter *.java | ForEach-Object FullName)
if ($LASTEXITCODE -ne 0) { throw 'Bien dich kiem thu that bai' }

$ds = 'KiemThuSuyLuan', 'KiemThuBaoDuong', 'KiemThuNangCap'
if ($CoGateway) { $ds += 'KiemThuPhanTich' }
$loi = 0
Push-Location $ConfigDir   # Database doc config.properties o thu muc hien hanh
try {
    foreach ($k in $ds) {
        $out = & "$jdk\java.exe" '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -cp "$tmp;$root\bin;$root\lib\*" $k 2>&1
        if (($out | Select-Object -Last 1) -match 'TAT CA DAT') { Write-Host "[DAT] $k" -ForegroundColor Green }
        else { Write-Host "[LOI] $k" -ForegroundColor Red; $out | Where-Object { $_ -match 'LOI|Exception' } | Select-Object -First 10; $loi++ }
    }
} finally { Pop-Location }
if ($loi -eq 0) { 'TAT CA DAT' } else { "CO $loi CHUONG TRINH LOI"; exit 1 }
