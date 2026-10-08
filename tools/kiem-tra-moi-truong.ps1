# Kiểm tra môi trường trước khi chạy ứng dụng (cấu hình, JDK, SQL Server, CSDL, máy ảo, Gateway), in ĐẠT/LỖI kèm cách sửa.
# Chạy trên Windows (PowerShell):  powershell -ExecutionPolicy Bypass -File tools\kiem-tra-moi-truong.ps1
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$root = Split-Path -Parent $PSScriptRoot   # thư mục gốc dự án
$loi = 0
function Dat($m) { Write-Host "[ĐẠT] $m" -ForegroundColor Green }
function Loi($m, $sua) { Write-Host "[LỖI] $m" -ForegroundColor Red; if ($sua) { Write-Host "       Cách sửa: $sua" -ForegroundColor Yellow }; $script:loi++ }

# 1. Tệp cấu hình
$cfgPath = Join-Path $root 'config.properties'
$cfg = @{}
if (Test-Path $cfgPath) {
    foreach ($l in Get-Content $cfgPath -Encoding UTF8) {
        if ($l -match '^\s*([^#=\s]+)\s*=\s*(.*)$') { $cfg[$Matches[1]] = $Matches[2].Trim() }
    }
    Dat "Có config.properties"
} else { Loi "Thiếu config.properties" "Chép config.example.properties thành config.properties và điền mật khẩu" }
$src = $cfg['source']; if (-not $src) { $src = 'mock' }
if ($src -eq 'gateway') { Dat "Nguồn dữ liệu: gateway (dữ liệu thật từ máy ảo)" }
else { Write-Host "[CHÚ Ý] Nguồn dữ liệu đang là '$src' (dữ liệu giả). Đổi thành source=gateway để demo với máy ảo." -ForegroundColor Yellow }

# 2. JDK trong run.bat
$runBat = Get-Content (Join-Path $root 'run.bat') -Encoding UTF8 | Where-Object { $_ -match '^set JDK=' }
$jdk = if ($runBat) { ($runBat -split '=', 2)[1].Trim() } else { '' }
if ($jdk -and (Test-Path (Join-Path $jdk 'bin\javac.exe'))) { Dat "Có JDK: $jdk" }
else { Loi "Không thấy JDK theo run.bat ($jdk)" "Sửa dòng 'set JDK=' trong run.bat cho đúng thư mục JDK 17 trở lên" }

# 3. SQL Server
$svc = Get-Service -Name 'MSSQL$SQLEXPRESS' -ErrorAction SilentlyContinue
if ($svc -and $svc.Status -eq 'Running') { Dat "Dịch vụ SQL Server (SQLEXPRESS) đang chạy" }
else { Loi "Dịch vụ SQL Server (SQLEXPRESS) chưa chạy" "Mở services.msc, khởi động 'SQL Server (SQLEXPRESS)'" }

$dbHost = if ($cfg['db.host']) { $cfg['db.host'] } else { 'localhost' }
$dbPort = if ($cfg['db.port']) { $cfg['db.port'] } else { '1433' }
$dbName = if ($cfg['db.name']) { $cfg['db.name'] } else { 'ChanDoanXe' }
try {
    $cs = "Server=$dbHost,$dbPort;Database=$dbName;User Id=$($cfg['db.user']);Password=$($cfg['db.password']);Connect Timeout=5"
    $cn = New-Object System.Data.SqlClient.SqlConnection $cs
    $cn.Open()
    $cmd = $cn.CreateCommand()
    $cmd.CommandText = 'SELECT (SELECT COUNT(*) FROM dbo.DTC), (SELECT COUNT(*) FROM dbo.Luat), (SELECT COUNT(*) FROM dbo.Xe)'
    $r = $cmd.ExecuteReader(); [void]$r.Read()
    $nDtc = $r.GetInt32(0); $nLuat = $r.GetInt32(1); $nXe = $r.GetInt32(2); $r.Close(); $cn.Close()
    Dat "Đăng nhập CSDL $dbName được ($nDtc mã lỗi, $nLuat luật, $nXe xe)"
    if ($nLuat -eq 0) { Loi "CSDL chưa có luật chẩn đoán" "Chạy sql/05_seed_luat.sql trong SSMS" }
    if ($nXe -eq 0) { Write-Host "[CHÚ Ý] Chưa có xe nào; thêm một xe ở tab Quản lý xe trước khi demo." -ForegroundColor Yellow }
} catch { Loi "Không đăng nhập được CSDL $dbName ($($_.Exception.InnerException.Message))" "Kiểm tra mật khẩu trong config.properties và chế độ SQL Server Authentication" }

# 4. Máy ảo và Gateway (chỉ khi dùng nguồn gateway)
if ($src -eq 'gateway') {
    $gw = if ($cfg['gateway.host']) { $cfg['gateway.host'] } else { '192.168.35.128' }
    $port = if ($cfg['gateway.port']) { [int]$cfg['gateway.port'] } else { 5000 }
    if (Test-Connection -ComputerName $gw -Count 1 -Quiet) { Dat "Ping được máy ảo $gw" }
    else { Loi "Không ping được máy ảo $gw" "Bật máy ảo trong VMware; kiểm tra IP bằng lệnh 'ip a' trong máy ảo và sửa gateway.host" }
    try {
        $c = New-Object Net.Sockets.TcpClient
        $iar = $c.BeginConnect($gw, $port, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne(3000)) { throw 'quá thời gian' }
        $c.EndConnect($iar)
        $s = $c.GetStream(); $s.ReadTimeout = 3000
        $line = (New-Object IO.StreamReader($s)).ReadLine()
        $c.Close()
        if ($line -like 'LIVE*') { Dat "Gateway cổng $port trả dữ liệu sống ($line)" }
        else { Loi "Gateway trả dòng lạ: $line" "Chạy lại 'bash chay-demo.sh' trong máy ảo" }
    } catch { Loi "Không nối được Gateway $gw`:$port" "Trong máy ảo: bash setup_vcan.sh (nếu vừa khởi động lại) rồi bash chay-demo.sh" }
}

Write-Host ''
if ($loi -eq 0) { Write-Host 'TẤT CẢ ĐẠT. Chạy run.bat để mở ứng dụng.' -ForegroundColor Green }
else { Write-Host "Còn $loi lỗi, sửa theo hướng dẫn ở trên rồi chạy lại." -ForegroundColor Red }
