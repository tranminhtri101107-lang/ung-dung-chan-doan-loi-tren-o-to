# Sinh bo luat chan doan tu MOT nguon du lieu duy nhat (file nay), de tai lieu va CSDL luon khop nhau:
#   docs\du-lieu\LUAT-CHAN-DOAN.md   (tai lieu, ghi nguon tung luat)
#   sql\05_seed_luat.sql     (nap NguyenNhan, Luat, DieuKienLuat vao SQL Server, chay lai nhieu lan khong loi)
# Chay: powershell -File tools\sinh-luat.ps1 [-BaoCaoDir <thu muc bao cao>]  (co BaoCaoDir thi sinh them bang cho bao cao)
# QUY UOC: diem tin cay la DO TAC GIA DAT theo thu tu thuong gap ma nguon liet ke (hang 1 -> 0.45, hang 2 -> 0.30,
#          hang 3 -> 0.20, hang 4 tro di -> 0.15 den 0.08); luat co them dieu kien PID dung nguong do tac gia chon
#          theo mo hinh mo phong (khong phai thong so cua hang xe).
param([string]$BaoCaoDir)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$docs = Join-Path $root 'docs\du-lieu'
$sqlDir = Join-Path $root 'sql'
$ngayTruyCap = '06/10/2026'

# ---------------- Nguon ----------------
$nguon = [ordered]@{
    'S1'  = @('https://apextechnation.com/articles/p0171-code', 'P0171: nguyên nhân theo thứ tự thường gặp, mẫu nhiên liệu cầm chừng/tải')
    'S2'  = @('https://apextechnation.com/articles/p0300-code', 'P0300: nguyên nhân theo thứ tự thường gặp')
    'S3'  = @('https://apextechnation.com/articles/p0301-code', 'P0301: nguyên nhân bỏ máy xy-lanh 1')
    'S4'  = @('https://apextechnation.com/articles/p0172-code', 'P0172: nguyên nhân hỗn hợp đậm')
    'S5'  = @('https://apextechnation.com/articles/p0335-code', 'P0335: cảm biến trục khuỷu, đồng hồ vòng tua')
    'S6'  = @('https://apextechnation.com/articles/p0420-code', 'P0420: hiệu suất bộ xúc tác')
    'S7'  = @('https://apextechnation.com/articles/p0500-vehicle-speed-sensor-code', 'P0500: cảm biến tốc độ xe, tốc độ đọc được bằng 0 khi đang chạy')
    'S8'  = @('https://apextechnation.com/articles/p0118-ect-high-code', 'P0118: nhiệt độ nước đọc -40 °C khi mạch hở (điện áp cao, điện trở rất lớn)')
    'S9'  = @('https://apextechnation.com/articles/p0110-p0113-iat-sensor-codes', 'P0113: nhiệt độ khí nạp đọc -40 °C khi mạch hở')
    'S10' = @('https://icarsoft.com/knowledge/article/p0171-diagnose-and-clear', 'P0171: nguyên nhân, "rough or high idle", so lưu lượng MAF ở cầm chừng')
    'S11' = @('https://www.icarsoft-us.com/blogs/obd-ii-code/p0217', 'P0217: nguyên nhân quá nhiệt, nhiệt độ tăng khi kẹt xe/cầm chừng thì nghi quạt')
    'S12' = @('https://www.icarsoft-us.com/blogs/news/p0562-code', 'P0562: nguyên nhân điện áp thấp (ắc quy, cực/mát, máy phát, dây đai)')
    'S13' = @('https://www.icarsoft-us.com/blogs/news/p0117-code', 'P0117: nguyên nhân (cảm biến, dây/giắc, mức nước làm mát). Lưu ý: trang này viết nhiệt độ đọc "lạnh" khi mạch thấp, mâu thuẫn với S14 và với đặc tính NTC; xem ghi chú')
    'S14' = @('https://www.obd-codes.com/p0117', 'P0117: điện áp tín hiệu rất thấp (khoảng dưới 0,2 V) tương ứng nhiệt độ rất cao (hơn 135 °C); rút giắc thì số đọc về cực lạnh. CHỈ XEM ĐƯỢC TÓM TẮT TRONG KẾT QUẢ TÌM KIẾM, trang chặn truy cập trực tiếp')
    'W1'  = @('https://en.wikipedia.org/wiki/OBD-II_PIDs', 'Công thức các PID Mode 01 (0x04, 0x05, 0x0C, 0x0D, 0x0F, 0x10, 0x11, 0x42)')
}

# ---------------- Nguyen nhan ----------------
$nn = [ordered]@{
    'N01' = @('Rò rỉ chân không hoặc khí lọt vào đường nạp', 'Kiểm tra ống chân không, gioăng cổ hút, ống gió sau cảm biến MAF, ống PCV; nghe tiếng rít; thử rò rỉ bằng khói. Rò chân không thường làm vòng tua cầm chừng cao hoặc không đều.')
    'N02' = @('Cảm biến MAF sai lệch (bẩn hoặc hỏng)', 'Vệ sinh và kiểm tra cảm biến MAF; so lưu lượng khí (g/s) ở cầm chừng với thông số của động cơ. Bẩn thường báo thiếu lưu lượng (hỗn hợp nhạt), hỏng có thể báo dư (hỗn hợp đậm).')
    'N03' = @('Áp suất nhiên liệu bất thường (bơm yếu, lọc tắc, bộ điều áp)', 'Đo áp suất ray nhiên liệu theo thông số hãng, kiểm tra lọc nhiên liệu, bơm và bộ điều áp; thử cả khi tăng tải.')
    'N04' = @('Kim phun rò rỉ hoặc bẩn', 'Kiểm tra kim phun (độ kín, lượng phun, cân bằng giữa các xy-lanh); kim phun rò thường làm đậm hỗn hợp ở cầm chừng.')
    'N05' = @('Bugi mòn', 'Tháo kiểm tra bugi, đo khe hở điện cực, thay theo chu kỳ bảo dưỡng; bugi quá hạn buộc bobin phải tăng điện áp đánh lửa.')
    'N06' = @('Bobin đánh lửa hỏng', 'Hoán đổi bobin giữa xy-lanh nghi ngờ và xy-lanh khác rồi xem lỗi có chuyển theo không (swap test); đo điện trở, kiểm tra giắc.')
    'N07' = @('Cảm biến nhiệt độ nước làm mát hoặc mạch tín hiệu hỏng', 'Đo điện trở cảm biến theo nhiệt độ, rút giắc xem số đọc có đổi không, kiểm tra dây tín hiệu ngắn mạch hoặc hở mạch, giắc ăn mòn.')
    'N08' = @('Cảm biến nhiệt độ khí nạp hoặc mạch tín hiệu hỏng', 'Kiểm tra dây và giắc cảm biến (hở mạch, ăn mòn, chân giắc lùi), đo điện trở cảm biến; nếu điện trở không đổi theo nhiệt độ thì thay cảm biến.')
    'N09' = @('Thiếu nước làm mát hoặc rò rỉ hệ thống làm mát', 'Kiểm tra mức nước khi động cơ nguội, tìm vết rò ở két nước, ống, nắp két; thử áp hệ thống làm mát.')
    'N10' = @('Quạt làm mát không hoạt động', 'Kiểm tra lệnh điều khiển quạt, rơ-le, cầu chì, mô-tơ quạt; xem nhiệt độ có tăng khi cầm chừng hoặc kẹt xe không.')
    'N11' = @('Van hằng nhiệt kẹt hoặc dòng nước làm mát bị hạn chế', 'Kiểm tra nhiệt độ hai đầu ống két, thử độ mở của van hằng nhiệt, kiểm tra dòng nước.')
    'N12' = @('Cảm biến vị trí trục khuỷu hoặc dây/giắc tín hiệu hỏng', 'Đo tín hiệu cảm biến bằng dao động ký, kiểm tra khe hở, giắc, dây và bánh răng/vòng tín hiệu; chú ý tín hiệu mất xung hoặc mất khi nóng máy.')
    'N13' = @('Bộ xúc tác xuống cấp', 'So tín hiệu cảm biến O2 trước và sau xúc tác; loại trừ bỏ máy, rò rỉ khí xả và cảm biến O2 trước khi kết luận thay bộ xúc tác.')
    'N14' = @('Cảm biến O2 sau xúc tác hỏng hoặc rò rỉ đường xả', 'Kiểm tra rò rỉ ống xả trước cảm biến, dây giắc và tín hiệu của cảm biến O2 sau xúc tác.')
    'N15' = @('Mất tín hiệu tốc độ xe (cảm biến, dây dẫn hoặc liên lạc ABS)', 'Theo dõi PID tốc độ khi chạy thử: bằng 0 là mất tín hiệu, nhảy loạn là dây hoặc cảm biến chập chờn; kiểm tra cảm biến tốc độ bánh xe/hộp số, vòng răng, dây và liên lạc giữa mô-đun ABS và ECM.')
    'N16' = @('Ắc quy yếu hoặc đầu cực, dây mát kém', 'Đo điện áp và thử tải ắc quy; làm sạch, siết chặt đầu cực và dây mát; kiểm tra rò điện ký sinh.')
    'N17' = @('Hệ thống sạc hỏng (máy phát, bộ điều áp, dây đai)', 'Đo điện áp khi nổ máy (thường khoảng 13,5 đến 14,7 V), kiểm tra máy phát, bộ điều áp, dây đai và độ căng.')
}

# ---------------- Luat ----------------
# Dieu kien: 'D:P0171' = co ma loi; 'P:0x11<12' = PID (hex) so sanh voi nguong (gia tri vat ly: %, C, vong/phut, km/h, g/s, V).
# Moi dong: ma luat, nguyen nhan, diem tin cay, danh sach dieu kien (AND), ghi chu co so, nguon
$luat = @(
    @('R01', 'N01', 0.45, @('D:P0171'),                                 'Rò chân không là nguyên nhân thường gặp số 1 của P0171',                        'S1;S10'),
    @('R02', 'N01', 0.75, @('D:P0171', 'P:0x11<12', 'P:0x0C>1100'),      'P0171 kèm cầm chừng (bướm ga dưới 12 %) mà vòng tua cao hơn 1100: khớp mô tả "cầm chừng cao/không đều" của rò chân không', 'S10'),
    @('R03', 'N01', 0.30, @('D:P0300'),                                 'Rò chân không là nguyên nhân thường gặp số 2 của P0300',                        'S2'),
    @('R04', 'N02', 0.30, @('D:P0171'),                                 'MAF bẩn là nguyên nhân thường gặp số 2 của P0171',                              'S1;S10'),
    @('R05', 'N02', 0.70, @('D:P0171', 'P:0x11<12', 'P:0x10<1.8'),       'P0171 kèm cầm chừng mà lưu lượng MAF dưới 1,8 g/s: MAF báo thiếu lưu lượng so với cầm chừng bình thường', 'S10'),
    @('R06', 'N02', 0.20, @('D:P0172'),                                 'MAF báo dư lưu lượng là nguyên nhân thường gặp số 3 của P0172',                  'S4'),
    @('R07', 'N02', 0.65, @('D:P0172', 'P:0x11<12', 'P:0x10>4.5'),      'P0172 kèm cầm chừng mà lưu lượng MAF trên 4,5 g/s: MAF báo dư lưu lượng', 'S4'),
    @('R08', 'N03', 0.20, @('D:P0171'),                                 'Bơm yếu/lọc tắc là nguyên nhân thường gặp số 3-4 của P0171',                    'S1;S10'),
    @('R09', 'N03', 0.30, @('D:P0172'),                                 'Áp suất nhiên liệu quá cao (bộ điều áp) là nguyên nhân thường gặp số 2 của P0172', 'S4'),
    @('R10', 'N03', 0.20, @('D:P0300'),                                 'Áp suất nhiên liệu thấp là nguyên nhân thường gặp số 3 của P0300',              'S2'),
    @('R11', 'N04', 0.45, @('D:P0172'),                                 'Kim phun rò là nguyên nhân thường gặp số 1 của P0172',                          'S4'),
    @('R12', 'N04', 0.15, @('D:P0301'),                                 'Kim phun xy-lanh 1 bẩn/rò nằm trong danh sách nguyên nhân của P0301',            'S3'),
    @('R13', 'N05', 0.45, @('D:P0300'),                                 'Bugi mòn là nguyên nhân thường gặp số 1 của P0300',                             'S2'),
    @('R14', 'N05', 0.40, @('D:P0301'),                                 'Bugi mòn/hỏng nằm trong danh sách nguyên nhân của P0301',                        'S3'),
    @('R15', 'N06', 0.35, @('D:P0301'),                                 'Bobin (coil-on-plug) hỏng nằm trong danh sách nguyên nhân của P0301',            'S3'),
    @('R16', 'N07', 0.55, @('D:P0117'),                                 'Cảm biến hỏng và dây/giắc là hai nguyên nhân đầu của P0117',                    'S13'),
    @('R17', 'N07', 0.85, @('D:P0117', 'P:0x05>=150'),                  'P0117 kèm nhiệt độ nước đọc từ 150 °C trở lên (vô lý vì mạch điện áp thấp giống nóng cực độ): lỗi cảm biến hoặc dây, không phải nóng thật', 'S14'),
    @('R18', 'N07', 0.55, @('D:P0118'),                                 'Dây đứt, giắc ăn mòn, cảm biến hỏng là các nguyên nhân đầu của P0118',          'S8'),
    @('R19', 'N07', 0.85, @('D:P0118', 'P:0x05<=-30'),                  'P0118 kèm nhiệt độ nước đọc -40 °C: mạch hở, cảm biến hoặc dây', 'S8'),
    @('R20', 'N08', 0.55, @('D:P0113'),                                 'Mạch hở, giắc ăn mòn, cảm biến hỏng là các nguyên nhân đầu của P0113',          'S9'),
    @('R21', 'N08', 0.85, @('D:P0113', 'P:0x0F<=-30'),                  'P0113 kèm nhiệt độ khí nạp đọc -40 °C: mạch hở, cảm biến hoặc dây', 'S9'),
    @('R22', 'N09', 0.45, @('D:P0217'),                                 'Thiếu nước làm mát hoặc rò rỉ là nguyên nhân số 1 của P0217',                    'S11'),
    @('R23', 'N10', 0.30, @('D:P0217'),                                 'Hệ thống quạt làm mát là nguyên nhân số 2 của P0217',                           'S11'),
    @('R24', 'N10', 0.60, @('D:P0217', 'P:0x05>=105', 'P:0x0D<10'),     'P0217 kèm nhiệt độ nước từ 105 °C trở lên khi xe đứng yên: nhiệt độ tăng ở cầm chừng/kẹt xe là dấu hiệu nghi quạt', 'S11'),
    @('R25', 'N11', 0.20, @('D:P0217'),                                 'Van hằng nhiệt/hạn chế dòng nước là nguyên nhân số 3 của P0217',                'S11'),
    @('R26', 'N12', 0.60, @('D:P0335'),                                 'Cảm biến hỏng và dây/giắc là hai nguyên nhân đầu của P0335',                    'S5'),
    @('R27', 'N12', 0.85, @('D:P0335', 'P:0x0C<100'),                   'P0335 kèm vòng tua đọc dưới 100: ECM không nhận được tín hiệu vòng tua', 'S5'),
    @('R28', 'N13', 0.55, @('D:P0420'),                                 'Bộ xúc tác mòn là nguyên nhân số 1 của P0420',                                  'S6'),
    @('R29', 'N14', 0.30, @('D:P0420'),                                 'Rò rỉ đường xả hoặc cảm biến O2 sau hỏng nằm trong danh sách nguyên nhân của P0420', 'S6'),
    @('R30', 'N15', 0.60, @('D:P0500'),                                 'Cảm biến, dây dẫn hoặc liên lạc ABS-ECM là các nguyên nhân của P0500',          'S7'),
    @('R31', 'N15', 0.85, @('D:P0500', 'P:0x0D<1', 'P:0x11>40'),        'P0500 kèm tốc độ đọc 0 khi bướm ga trên 40 % (đang chạy): tín hiệu tốc độ bị mất', 'S7'),
    @('R32', 'N16', 0.45, @('D:P0562'),                                 'Ắc quy yếu và đầu cực/dây mát kém là hai nguyên nhân đầu của P0562',             'S12'),
    @('R33', 'N17', 0.30, @('D:P0562'),                                 'Máy phát, bộ điều áp, dây đai là nguyên nhân tiếp theo của P0562',               'S12'),
    @('R34', 'N17', 0.70, @('D:P0562', 'P:0x42<12.5', 'P:0x0C>1500'),   'P0562 kèm điện áp dưới 12,5 V khi động cơ đang chạy trên 1500 vòng/phút: hệ thống sạc không nâng được điện áp', 'S12')
)

function Parse-Dk([string]$s) {
    if ($s.StartsWith('D:')) { return [pscustomobject]@{ Loai = 'DTC'; Dtc = $s.Substring(2); Pid = $null; Op = $null; Val = $null } }
    $m = [regex]::Match($s, '^P:0x([0-9A-Fa-f]{2})(<=|>=|<|>)(-?[0-9.]+)$')
    if (-not $m.Success) { throw "Dieu kien sai dinh dang: $s" }
    return [pscustomobject]@{ Loai = 'PID'; Dtc = $null; Pid = [Convert]::ToInt32($m.Groups[1].Value, 16); Op = $m.Groups[2].Value; Val = $m.Groups[3].Value }
}
function Esc([string]$s) { return $s.Replace("'", "''") }
function Vi([string]$s) { return $s.Replace('.', ',') }   # so thap phan kieu Viet cho van ban

# ---- kiem tra tinh nhat quan ----
foreach ($r in $luat) {
    if (-not $nn.Contains($r[1])) { throw "Luat $($r[0]) tham chieu nguyen nhan khong ton tai: $($r[1])" }
    foreach ($src in $r[5].Split(';')) { if (-not $nguon.Contains($src)) { throw "Luat $($r[0]) tham chieu nguon khong ton tai: $src" } }
    if ($r[2] -le 0 -or $r[2] -ge 1) { throw "Diem tin cay ngoai (0,1): $($r[0])" }
    if (-not ($r[3] | Where-Object { $_.StartsWith('D:') })) { throw "Luat $($r[0]) khong co dieu kien ma loi" }
}
$ids = $luat | ForEach-Object { $_[0] }
if (($ids | Select-Object -Unique).Count -ne $ids.Count) { throw 'Trung ma luat' }

# ---------------- Sinh SQL ----------------
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('/* ============================================================')
[void]$sb.AppendLine('   Script 05: nap bo luat chan doan (NguyenNhan, Luat, DieuKienLuat).')
[void]$sb.AppendLine('   SINH TU DONG boi tools/sinh-luat.ps1 - KHONG SUA TAY, sua file do roi chay lai.')
[void]$sb.AppendLine('   Noi dung, nguon va uoc luong diem tin cay: docs/du-lieu/LUAT-CHAN-DOAN.md.')
[void]$sb.AppendLine('   Chay lai nhieu lan khong loi (kiem tra ton tai truoc khi them).')
[void]$sb.AppendLine('   ============================================================ */')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('USE ChanDoanXe;')
[void]$sb.AppendLine('GO')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('-- Nguyen nhan')
[void]$sb.AppendLine('INSERT INTO dbo.NguyenNhan (TenNguyenNhan, GoiYKiemTra)')
[void]$sb.AppendLine('SELECT v.Ten, v.GoiY')
[void]$sb.AppendLine('FROM (VALUES')
$rows = @(); foreach ($k in $nn.Keys) { $rows += "    (N'$(Esc $nn[$k][0])', N'$(Esc $nn[$k][1])')" }
[void]$sb.AppendLine(($rows -join ",`r`n"))
[void]$sb.AppendLine(') AS v(Ten, GoiY)')
[void]$sb.AppendLine('WHERE NOT EXISTS (SELECT 1 FROM dbo.NguyenNhan n WHERE n.TenNguyenNhan = v.Ten);')
[void]$sb.AppendLine('GO')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('-- Luat (nhan dang bang cot MoTa, bat dau bang ma luat Rxx)')
[void]$sb.AppendLine('INSERT INTO dbo.Luat (MaNN, DiemTinCay, MoTa, Nguon)')
[void]$sb.AppendLine('SELECT n.MaNN, v.Diem, v.MoTa, v.Nguon')
[void]$sb.AppendLine('FROM (VALUES')
$rows = @()
foreach ($r in $luat) {
    $mt = "$($r[0]): $($r[4])"
    if ($mt.Length -gt 300) { throw "MoTa luat $($r[0]) dai hon 300 ky tu" }
    $ng = "$($r[5]) (docs/du-lieu/LUAT-CHAN-DOAN.md)"
    $rows += "    (N'$(Esc $nn[$r[1]][0])', CAST($($r[2].ToString([Globalization.CultureInfo]::InvariantCulture)) AS DECIMAL(4,3)), N'$(Esc $mt)', N'$(Esc $ng)')"
}
[void]$sb.AppendLine(($rows -join ",`r`n"))
[void]$sb.AppendLine(') AS v(Ten, Diem, MoTa, Nguon)')
[void]$sb.AppendLine('JOIN dbo.NguyenNhan n ON n.TenNguyenNhan = v.Ten')
[void]$sb.AppendLine('WHERE NOT EXISTS (SELECT 1 FROM dbo.Luat l WHERE l.MoTa = v.MoTa);')
[void]$sb.AppendLine('GO')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('-- Dieu kien cua tung luat (cac dieu kien cua mot luat ket hop bang AND)')
[void]$sb.AppendLine('INSERT INTO dbo.DieuKienLuat (MaLuat, Loai, MaDTC, MaPID, ToanTu, NguongGiaTri)')
[void]$sb.AppendLine('SELECT l.MaLuat, v.Loai, v.MaDTC, v.MaPID, v.ToanTu, v.Nguong')
[void]$sb.AppendLine('FROM (VALUES')
$rows = @()
foreach ($r in $luat) {
    foreach ($c in $r[3]) {
        $d = Parse-Dk $c
        $ma = if ($d.Dtc) { "'$($d.Dtc)'" } else { 'NULL' }
        $pidSql = if ($null -ne $d.Pid) { "CAST($($d.Pid) AS TINYINT)" } else { 'CAST(NULL AS TINYINT)' }
        $op = if ($d.Op) { "'$($d.Op)'" } else { 'NULL' }
        $val = if ($null -ne $d.Val) { "CAST($($d.Val) AS DECIMAL(12,4))" } else { 'CAST(NULL AS DECIMAL(12,4))' }
        $rows += "    ('$($r[0]):', '$($d.Loai)', $ma, $pidSql, $op, $val)"
    }
}
[void]$sb.AppendLine(($rows -join ",`r`n"))
[void]$sb.AppendLine(') AS v(Ma, Loai, MaDTC, MaPID, ToanTu, Nguong)')
[void]$sb.AppendLine('JOIN dbo.Luat l ON l.MoTa LIKE v.Ma + N''%''')
[void]$sb.AppendLine('WHERE NOT EXISTS (SELECT 1 FROM dbo.DieuKienLuat d')
[void]$sb.AppendLine('                  WHERE d.MaLuat = l.MaLuat AND d.Loai = v.Loai')
[void]$sb.AppendLine('                    AND ISNULL(d.MaDTC, '''') = ISNULL(v.MaDTC, '''') AND ISNULL(CAST(d.MaPID AS INT), -1) = ISNULL(CAST(v.MaPID AS INT), -1)')
[void]$sb.AppendLine('                    AND ISNULL(d.ToanTu, '''') = ISNULL(v.ToanTu, '''') AND ISNULL(d.NguongGiaTri, -999999) = ISNULL(v.Nguong, -999999));')
[void]$sb.AppendLine('GO')
$bom = New-Object Text.UTF8Encoding($true)
[IO.File]::WriteAllText((Join-Path $sqlDir '05_seed_luat.sql'), $sb.ToString(), $bom)

# ---------------- Sinh tai lieu ----------------
$md = New-Object System.Text.StringBuilder
[void]$md.AppendLine('# Bộ luật chẩn đoán (nạp vào CSDL bằng `sql/05_seed_luat.sql`)')
[void]$md.AppendLine('')
[void]$md.AppendLine('> Sinh tự động bởi `tools/sinh-luat.ps1`; muốn sửa luật thì sửa trong file đó rồi chạy lại. Nạp vào CSDL bằng `sql/05_seed_luat.sql`.')
[void]$md.AppendLine('')
[void]$md.AppendLine('## 1. Cách đọc')
[void]$md.AppendLine('')
[void]$md.AppendLine("- **Quan hệ 'mã lỗi → nguyên nhân khả dĩ' lấy từ tài liệu kỹ thuật công khai** ghi ở mục 4 (truy cập $ngayTruyCap). Đây là nguồn thứ cấp trên mạng, **không phải SAE J1979/J2012** và không phải tài liệu của một hãng xe cụ thể. Mỗi luật ghi mã nguồn (S1, S2, ...) tương ứng ở cột *Nguồn*.")
[void]$md.AppendLine('- **Điểm tin cậy do tác giả đặt**, không phải số đo thực nghiệm: nguyên nhân đứng đầu danh sách của nguồn lấy 0,45, thứ hai 0,30, thứ ba 0,20, từ thứ tư trở đi 0,15 đến 0,08. Luật có thêm điều kiện giá trị PID (so với mã lỗi một mình) được đặt cao hơn (0,60 đến 0,85) vì chứng cứ mạnh hơn. Điểm chỉ dùng để xếp hạng.')
[void]$md.AppendLine('- **Ngưỡng PID** (ví dụ vòng tua trên 1100 khi cầm chừng) do tác giả chọn theo mô hình mô phỏng của ECU giả lập, **không phải thông số của hãng xe**. Hướng của dấu hiệu (ví dụ nhiệt độ đọc -40 °C khi mạch hở) có nguồn; con số cụ thể thì không.')
[void]$md.AppendLine('- Một luật khớp khi **mọi** điều kiện của nó đúng (phép VÀ). Khi nhiều luật cùng ủng hộ một nguyên nhân, điểm được gộp theo công thức của MYCIN: c = c1 + c2 × (1 − c1).')
[void]$md.AppendLine('- **Mâu thuẫn giữa các nguồn:** về P0117 (mạch điện áp thấp), nguồn S13 viết cảm biến báo nhiệt độ lạnh còn nguồn S14 viết báo nhiệt độ cực nóng. Bộ luật theo S14 vì đúng với đặc tính cảm biến NTC (nóng thì điện trở giảm, điện áp tín hiệu giảm) và đối xứng với P0118 ở S8 (mạch hở, điện áp cao, báo -40 °C). S14 chỉ đọc được qua tóm tắt của công cụ tìm kiếm. Nên đối chiếu thêm với tài liệu của hãng.')
[void]$md.AppendLine('- Các luật chỉ dùng 8 PID của đề tài (không có PID hiệu chỉnh nhiên liệu), nên một số cặp nguyên nhân chỉ phân biệt được yếu (ví dụ bugi và bobin cùng gây P0301). Đây là giới hạn thật của hệ thống và được nêu trong báo cáo.')
[void]$md.AppendLine('')
[void]$md.AppendLine('## 2. Nguyên nhân khả dĩ (17)')
[void]$md.AppendLine('')
[void]$md.AppendLine('| Mã | Nguyên nhân | Gợi ý kiểm tra |')
[void]$md.AppendLine('|---|---|---|')
foreach ($k in $nn.Keys) { [void]$md.AppendLine("| $k | $($nn[$k][0]) | $($nn[$k][1]) |") }
[void]$md.AppendLine('')
[void]$md.AppendLine("## 3. Luật ($($luat.Count))")
[void]$md.AppendLine('')
[void]$md.AppendLine('Ký hiệu điều kiện: `DTC P0171` là mã lỗi đang lưu; `PID 0x11 < 12` là giá trị vật lý của PID (0x04 tải %, 0x05 nhiệt độ nước °C, 0x0C vòng tua, 0x0D tốc độ km/h, 0x0F nhiệt độ khí nạp °C, 0x10 MAF g/s, 0x11 bướm ga %, 0x42 điện áp V).')
[void]$md.AppendLine('')
[void]$md.AppendLine('| Luật | Nguyên nhân | Điều kiện (VÀ) | Điểm | Cơ sở | Nguồn |')
[void]$md.AppendLine('|---|---|---|---|---|---|')
foreach ($r in $luat) {
    $dks = ($r[3] | ForEach-Object { $d = Parse-Dk $_; if ($d.Dtc) { "DTC $($d.Dtc)" } else { "PID 0x$('{0:X2}' -f $d.Pid) $($d.Op) $(Vi $d.Val)" } }) -join '; '
    [void]$md.AppendLine("| $($r[0]) | $($r[1]) $($nn[$r[1]][0]) | $dks | $(Vi $r[2].ToString('0.00', [Globalization.CultureInfo]::InvariantCulture)) | $($r[4]) | $($r[5]) |")
}
[void]$md.AppendLine('')
[void]$md.AppendLine("## 4. Nguồn (truy cập $ngayTruyCap)")
[void]$md.AppendLine('')
foreach ($k in $nguon.Keys) { [void]$md.AppendLine("- **$k** $($nguon[$k][0]) : $($nguon[$k][1])") }
[void]$md.AppendLine('')
[void]$md.AppendLine('Các trang Advance Auto Parts, obd-codes.com, autobarn, engine-codes, go-parts, Edmunds chặn truy cập trực tiếp (HTTP 403) nên **không** dùng làm nguồn trích dẫn, trừ S14 ghi chú ở trên.')
[void]$md.AppendLine('')
[void]$md.AppendLine('## 5. Ghi chú')
[void]$md.AppendLine('')
[void]$md.AppendLine('1. Điểm tin cậy và ngưỡng PID của 34 luật ở mục 3 do tác giả đặt (xem mục 1).')
[void]$md.AppendLine('2. Nguồn S14 (P0117) chỉ xem được qua tóm tắt của công cụ tìm kiếm.')
[void]$md.AppendLine('3. Muốn đổi luật: sửa trong `sinh-luat.ps1`, chạy lại, nạp lại CSDL và chạy lại thực nghiệm (`quy-trinh-thuc-nghiem.ps1`).')
[IO.File]::WriteAllText((Join-Path $docs 'LUAT-CHAN-DOAN.md'), $md.ToString(), (New-Object Text.UTF8Encoding($false)))
"Da sinh $($luat.Count) luat, $($nn.Count) nguyen nhan."

# ---------------- Sinh doan van ban cho bao cao (noi-dung\phan-sinh) ----------------
$pidTen = @{ 4 = 'Tải động cơ'; 5 = 'Nhiệt độ nước'; 12 = 'Vòng tua'; 13 = 'Tốc độ xe'; 15 = 'Nhiệt độ khí nạp'; 16 = 'Lưu lượng MAF'; 17 = 'Bướm ga'; 66 = 'Điện áp' }
$pidDv = @{ 4 = '%'; 5 = '°C'; 12 = 'vòng/phút'; 13 = 'km/h'; 15 = '°C'; 16 = 'g/s'; 17 = '%'; 66 = 'V' }
$opVi = @{ '<' = '<'; '>' = '>'; '<=' = '≤'; '>=' = '≥' }
function DkVi([string]$c) {
    $d = Parse-Dk $c
    if ($d.Dtc) { return $d.Dtc }
    return "$($pidTen[[int]$d.Pid]) $($opVi[$d.Op]) $(Vi $d.Val) $($pidDv[[int]$d.Pid])"
}
if (-not $BaoCaoDir) { return }
$out2 = Join-Path $BaoCaoDir 'noi-dung\phan-sinh'
New-Item -ItemType Directory -Force $out2 | Out-Null
# Bang nguyen nhan: ma loi lien quan = cac ma loi xuat hien trong luat cua nguyen nhan
$b1 = New-Object System.Text.StringBuilder
[void]$b1.AppendLine('@bang nguyennhan | Các nguyên nhân khả dĩ trong cơ sở tri thức và mã lỗi liên quan | w=8,56,36 | size=10')
[void]$b1.AppendLine('| Mã | Nguyên nhân | Mã lỗi liên quan |')
foreach ($k in $nn.Keys) {
    $dtcs = $luat | Where-Object { $_[1] -eq $k } | ForEach-Object { $_[3] | Where-Object { $_.StartsWith('D:') } | ForEach-Object { $_.Substring(2) } } | Sort-Object -Unique
    [void]$b1.AppendLine("| $k | $($nn[$k][0]) | $($dtcs -join ', ') |")
}
[IO.File]::WriteAllText((Join-Path $out2 'bang-nguyen-nhan.md'), $b1.ToString(), (New-Object Text.UTF8Encoding($false)))
# Bang luat day du cho phu luc
$b2 = New-Object System.Text.StringBuilder
[void]$b2.AppendLine('@bang luat | Bộ luật chẩn đoán đầy đủ (các điều kiện của một luật kết hợp bằng VÀ) | w=7,31,36,8,18 | size=9')
[void]$b2.AppendLine('| Luật | Nguyên nhân | Điều kiện | Điểm | Nguồn |')
foreach ($r in $luat) {
    $dk = ($r[3] | ForEach-Object { DkVi $_ }) -join '; '
    [void]$b2.AppendLine("| $($r[0]) | $($r[1]) | $dk | $(Vi $r[2].ToString('0.00', [Globalization.CultureInfo]::InvariantCulture)) | $($r[5].Replace(';', ', ')) |")
}
[IO.File]::WriteAllText((Join-Path $out2 'bang-luat.md'), $b2.ToString(), (New-Object Text.UTF8Encoding($false)))
# Danh sach nguon cho phu luc
$b3 = New-Object System.Text.StringBuilder
[void]$b3.AppendLine('@bang nguonluat | Nguồn của bộ luật chẩn đoán (truy cập ngày 06/10/2026) | w=8,50,42 | size=9')
[void]$b3.AppendLine('| Mã | Địa chỉ | Nội dung dùng |')
foreach ($k in $nguon.Keys) { [void]$b3.AppendLine("| $k | $($nguon[$k][0]) | $($nguon[$k][1].Replace('|', '/')) |") }
[IO.File]::WriteAllText((Join-Path $out2 'bang-nguon-luat.md'), $b3.ToString(), (New-Object Text.UTF8Encoding($false)))
"Da sinh doan van ban bao cao vao $out2"