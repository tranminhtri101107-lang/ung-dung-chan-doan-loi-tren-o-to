/* ============================================================
   Script 05: nap bo luat chan doan (NguyenNhan, Luat, DieuKienLuat).
   SINH TU DONG boi tools/sinh-luat.ps1 - KHONG SUA TAY, sua file do roi chay lai.
   Noi dung, nguon va uoc luong diem tin cay: docs/du-lieu/LUAT-CHAN-DOAN.md.
   Chay lai nhieu lan khong loi (kiem tra ton tai truoc khi them).
   ============================================================ */

USE ChanDoanXe;
GO

-- Nguyen nhan
INSERT INTO dbo.NguyenNhan (TenNguyenNhan, GoiYKiemTra)
SELECT v.Ten, v.GoiY
FROM (VALUES
    (N'Rò rỉ chân không hoặc khí lọt vào đường nạp', N'Kiểm tra ống chân không, gioăng cổ hút, ống gió sau cảm biến MAF, ống PCV; nghe tiếng rít; thử rò rỉ bằng khói. Rò chân không thường làm vòng tua cầm chừng cao hoặc không đều.'),
    (N'Cảm biến MAF sai lệch (bẩn hoặc hỏng)', N'Vệ sinh và kiểm tra cảm biến MAF; so lưu lượng khí (g/s) ở cầm chừng với thông số của động cơ. Bẩn thường báo thiếu lưu lượng (hỗn hợp nhạt), hỏng có thể báo dư (hỗn hợp đậm).'),
    (N'Áp suất nhiên liệu bất thường (bơm yếu, lọc tắc, bộ điều áp)', N'Đo áp suất ray nhiên liệu theo thông số hãng, kiểm tra lọc nhiên liệu, bơm và bộ điều áp; thử cả khi tăng tải.'),
    (N'Kim phun rò rỉ hoặc bẩn', N'Kiểm tra kim phun (độ kín, lượng phun, cân bằng giữa các xy-lanh); kim phun rò thường làm đậm hỗn hợp ở cầm chừng.'),
    (N'Bugi mòn', N'Tháo kiểm tra bugi, đo khe hở điện cực, thay theo chu kỳ bảo dưỡng; bugi quá hạn buộc bobin phải tăng điện áp đánh lửa.'),
    (N'Bobin đánh lửa hỏng', N'Hoán đổi bobin giữa xy-lanh nghi ngờ và xy-lanh khác rồi xem lỗi có chuyển theo không (swap test); đo điện trở, kiểm tra giắc.'),
    (N'Cảm biến nhiệt độ nước làm mát hoặc mạch tín hiệu hỏng', N'Đo điện trở cảm biến theo nhiệt độ, rút giắc xem số đọc có đổi không, kiểm tra dây tín hiệu ngắn mạch hoặc hở mạch, giắc ăn mòn.'),
    (N'Cảm biến nhiệt độ khí nạp hoặc mạch tín hiệu hỏng', N'Kiểm tra dây và giắc cảm biến (hở mạch, ăn mòn, chân giắc lùi), đo điện trở cảm biến; nếu điện trở không đổi theo nhiệt độ thì thay cảm biến.'),
    (N'Thiếu nước làm mát hoặc rò rỉ hệ thống làm mát', N'Kiểm tra mức nước khi động cơ nguội, tìm vết rò ở két nước, ống, nắp két; thử áp hệ thống làm mát.'),
    (N'Quạt làm mát không hoạt động', N'Kiểm tra lệnh điều khiển quạt, rơ-le, cầu chì, mô-tơ quạt; xem nhiệt độ có tăng khi cầm chừng hoặc kẹt xe không.'),
    (N'Van hằng nhiệt kẹt hoặc dòng nước làm mát bị hạn chế', N'Kiểm tra nhiệt độ hai đầu ống két, thử độ mở của van hằng nhiệt, kiểm tra dòng nước.'),
    (N'Cảm biến vị trí trục khuỷu hoặc dây/giắc tín hiệu hỏng', N'Đo tín hiệu cảm biến bằng dao động ký, kiểm tra khe hở, giắc, dây và bánh răng/vòng tín hiệu; chú ý tín hiệu mất xung hoặc mất khi nóng máy.'),
    (N'Bộ xúc tác xuống cấp', N'So tín hiệu cảm biến O2 trước và sau xúc tác; loại trừ bỏ máy, rò rỉ khí xả và cảm biến O2 trước khi kết luận thay bộ xúc tác.'),
    (N'Cảm biến O2 sau xúc tác hỏng hoặc rò rỉ đường xả', N'Kiểm tra rò rỉ ống xả trước cảm biến, dây giắc và tín hiệu của cảm biến O2 sau xúc tác.'),
    (N'Mất tín hiệu tốc độ xe (cảm biến, dây dẫn hoặc liên lạc ABS)', N'Theo dõi PID tốc độ khi chạy thử: bằng 0 là mất tín hiệu, nhảy loạn là dây hoặc cảm biến chập chờn; kiểm tra cảm biến tốc độ bánh xe/hộp số, vòng răng, dây và liên lạc giữa mô-đun ABS và ECM.'),
    (N'Ắc quy yếu hoặc đầu cực, dây mát kém', N'Đo điện áp và thử tải ắc quy; làm sạch, siết chặt đầu cực và dây mát; kiểm tra rò điện ký sinh.'),
    (N'Hệ thống sạc hỏng (máy phát, bộ điều áp, dây đai)', N'Đo điện áp khi nổ máy (thường khoảng 13,5 đến 14,7 V), kiểm tra máy phát, bộ điều áp, dây đai và độ căng.')
) AS v(Ten, GoiY)
WHERE NOT EXISTS (SELECT 1 FROM dbo.NguyenNhan n WHERE n.TenNguyenNhan = v.Ten);
GO

-- Luat (nhan dang bang cot MoTa, bat dau bang ma luat Rxx)
INSERT INTO dbo.Luat (MaNN, DiemTinCay, MoTa, Nguon)
SELECT n.MaNN, v.Diem, v.MoTa, v.Nguon
FROM (VALUES
    (N'Rò rỉ chân không hoặc khí lọt vào đường nạp', CAST(0.45 AS DECIMAL(4,3)), N'R01: Rò chân không là nguyên nhân thường gặp số 1 của P0171', N'S1;S10 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Rò rỉ chân không hoặc khí lọt vào đường nạp', CAST(0.75 AS DECIMAL(4,3)), N'R02: P0171 kèm cầm chừng (bướm ga dưới 12 %) mà vòng tua cao hơn 1100: khớp mô tả "cầm chừng cao/không đều" của rò chân không', N'S10 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Rò rỉ chân không hoặc khí lọt vào đường nạp', CAST(0.3 AS DECIMAL(4,3)), N'R03: Rò chân không là nguyên nhân thường gặp số 2 của P0300', N'S2 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Cảm biến MAF sai lệch (bẩn hoặc hỏng)', CAST(0.3 AS DECIMAL(4,3)), N'R04: MAF bẩn là nguyên nhân thường gặp số 2 của P0171', N'S1;S10 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Cảm biến MAF sai lệch (bẩn hoặc hỏng)', CAST(0.7 AS DECIMAL(4,3)), N'R05: P0171 kèm cầm chừng mà lưu lượng MAF dưới 1,8 g/s: MAF báo thiếu lưu lượng so với cầm chừng bình thường', N'S10 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Cảm biến MAF sai lệch (bẩn hoặc hỏng)', CAST(0.2 AS DECIMAL(4,3)), N'R06: MAF báo dư lưu lượng là nguyên nhân thường gặp số 3 của P0172', N'S4 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Cảm biến MAF sai lệch (bẩn hoặc hỏng)', CAST(0.65 AS DECIMAL(4,3)), N'R07: P0172 kèm cầm chừng mà lưu lượng MAF trên 4,5 g/s: MAF báo dư lưu lượng', N'S4 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Áp suất nhiên liệu bất thường (bơm yếu, lọc tắc, bộ điều áp)', CAST(0.2 AS DECIMAL(4,3)), N'R08: Bơm yếu/lọc tắc là nguyên nhân thường gặp số 3-4 của P0171', N'S1;S10 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Áp suất nhiên liệu bất thường (bơm yếu, lọc tắc, bộ điều áp)', CAST(0.3 AS DECIMAL(4,3)), N'R09: Áp suất nhiên liệu quá cao (bộ điều áp) là nguyên nhân thường gặp số 2 của P0172', N'S4 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Áp suất nhiên liệu bất thường (bơm yếu, lọc tắc, bộ điều áp)', CAST(0.2 AS DECIMAL(4,3)), N'R10: Áp suất nhiên liệu thấp là nguyên nhân thường gặp số 3 của P0300', N'S2 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Kim phun rò rỉ hoặc bẩn', CAST(0.45 AS DECIMAL(4,3)), N'R11: Kim phun rò là nguyên nhân thường gặp số 1 của P0172', N'S4 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Kim phun rò rỉ hoặc bẩn', CAST(0.15 AS DECIMAL(4,3)), N'R12: Kim phun xy-lanh 1 bẩn/rò nằm trong danh sách nguyên nhân của P0301', N'S3 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Bugi mòn', CAST(0.45 AS DECIMAL(4,3)), N'R13: Bugi mòn là nguyên nhân thường gặp số 1 của P0300', N'S2 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Bugi mòn', CAST(0.4 AS DECIMAL(4,3)), N'R14: Bugi mòn/hỏng nằm trong danh sách nguyên nhân của P0301', N'S3 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Bobin đánh lửa hỏng', CAST(0.35 AS DECIMAL(4,3)), N'R15: Bobin (coil-on-plug) hỏng nằm trong danh sách nguyên nhân của P0301', N'S3 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Cảm biến nhiệt độ nước làm mát hoặc mạch tín hiệu hỏng', CAST(0.55 AS DECIMAL(4,3)), N'R16: Cảm biến hỏng và dây/giắc là hai nguyên nhân đầu của P0117', N'S13 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Cảm biến nhiệt độ nước làm mát hoặc mạch tín hiệu hỏng', CAST(0.85 AS DECIMAL(4,3)), N'R17: P0117 kèm nhiệt độ nước đọc từ 150 °C trở lên (vô lý vì mạch điện áp thấp giống nóng cực độ): lỗi cảm biến hoặc dây, không phải nóng thật', N'S14 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Cảm biến nhiệt độ nước làm mát hoặc mạch tín hiệu hỏng', CAST(0.55 AS DECIMAL(4,3)), N'R18: Dây đứt, giắc ăn mòn, cảm biến hỏng là các nguyên nhân đầu của P0118', N'S8 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Cảm biến nhiệt độ nước làm mát hoặc mạch tín hiệu hỏng', CAST(0.85 AS DECIMAL(4,3)), N'R19: P0118 kèm nhiệt độ nước đọc -40 °C: mạch hở, cảm biến hoặc dây', N'S8 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Cảm biến nhiệt độ khí nạp hoặc mạch tín hiệu hỏng', CAST(0.55 AS DECIMAL(4,3)), N'R20: Mạch hở, giắc ăn mòn, cảm biến hỏng là các nguyên nhân đầu của P0113', N'S9 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Cảm biến nhiệt độ khí nạp hoặc mạch tín hiệu hỏng', CAST(0.85 AS DECIMAL(4,3)), N'R21: P0113 kèm nhiệt độ khí nạp đọc -40 °C: mạch hở, cảm biến hoặc dây', N'S9 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Thiếu nước làm mát hoặc rò rỉ hệ thống làm mát', CAST(0.45 AS DECIMAL(4,3)), N'R22: Thiếu nước làm mát hoặc rò rỉ là nguyên nhân số 1 của P0217', N'S11 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Quạt làm mát không hoạt động', CAST(0.3 AS DECIMAL(4,3)), N'R23: Hệ thống quạt làm mát là nguyên nhân số 2 của P0217', N'S11 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Quạt làm mát không hoạt động', CAST(0.6 AS DECIMAL(4,3)), N'R24: P0217 kèm nhiệt độ nước từ 105 °C trở lên khi xe đứng yên: nhiệt độ tăng ở cầm chừng/kẹt xe là dấu hiệu nghi quạt', N'S11 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Van hằng nhiệt kẹt hoặc dòng nước làm mát bị hạn chế', CAST(0.2 AS DECIMAL(4,3)), N'R25: Van hằng nhiệt/hạn chế dòng nước là nguyên nhân số 3 của P0217', N'S11 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Cảm biến vị trí trục khuỷu hoặc dây/giắc tín hiệu hỏng', CAST(0.6 AS DECIMAL(4,3)), N'R26: Cảm biến hỏng và dây/giắc là hai nguyên nhân đầu của P0335', N'S5 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Cảm biến vị trí trục khuỷu hoặc dây/giắc tín hiệu hỏng', CAST(0.85 AS DECIMAL(4,3)), N'R27: P0335 kèm vòng tua đọc dưới 100: ECM không nhận được tín hiệu vòng tua', N'S5 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Bộ xúc tác xuống cấp', CAST(0.55 AS DECIMAL(4,3)), N'R28: Bộ xúc tác mòn là nguyên nhân số 1 của P0420', N'S6 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Cảm biến O2 sau xúc tác hỏng hoặc rò rỉ đường xả', CAST(0.3 AS DECIMAL(4,3)), N'R29: Rò rỉ đường xả hoặc cảm biến O2 sau hỏng nằm trong danh sách nguyên nhân của P0420', N'S6 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Mất tín hiệu tốc độ xe (cảm biến, dây dẫn hoặc liên lạc ABS)', CAST(0.6 AS DECIMAL(4,3)), N'R30: Cảm biến, dây dẫn hoặc liên lạc ABS-ECM là các nguyên nhân của P0500', N'S7 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Mất tín hiệu tốc độ xe (cảm biến, dây dẫn hoặc liên lạc ABS)', CAST(0.85 AS DECIMAL(4,3)), N'R31: P0500 kèm tốc độ đọc 0 khi bướm ga trên 40 % (đang chạy): tín hiệu tốc độ bị mất', N'S7 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Ắc quy yếu hoặc đầu cực, dây mát kém', CAST(0.45 AS DECIMAL(4,3)), N'R32: Ắc quy yếu và đầu cực/dây mát kém là hai nguyên nhân đầu của P0562', N'S12 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Hệ thống sạc hỏng (máy phát, bộ điều áp, dây đai)', CAST(0.3 AS DECIMAL(4,3)), N'R33: Máy phát, bộ điều áp, dây đai là nguyên nhân tiếp theo của P0562', N'S12 (docs/du-lieu/LUAT-CHAN-DOAN.md)'),
    (N'Hệ thống sạc hỏng (máy phát, bộ điều áp, dây đai)', CAST(0.7 AS DECIMAL(4,3)), N'R34: P0562 kèm điện áp dưới 12,5 V khi động cơ đang chạy trên 1500 vòng/phút: hệ thống sạc không nâng được điện áp', N'S12 (docs/du-lieu/LUAT-CHAN-DOAN.md)')
) AS v(Ten, Diem, MoTa, Nguon)
JOIN dbo.NguyenNhan n ON n.TenNguyenNhan = v.Ten
WHERE NOT EXISTS (SELECT 1 FROM dbo.Luat l WHERE l.MoTa = v.MoTa);
GO

-- Dieu kien cua tung luat (cac dieu kien cua mot luat ket hop bang AND)
INSERT INTO dbo.DieuKienLuat (MaLuat, Loai, MaDTC, MaPID, ToanTu, NguongGiaTri)
SELECT l.MaLuat, v.Loai, v.MaDTC, v.MaPID, v.ToanTu, v.Nguong
FROM (VALUES
    ('R01:', 'DTC', 'P0171', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R02:', 'DTC', 'P0171', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R02:', 'PID', NULL, CAST(17 AS TINYINT), '<', CAST(12 AS DECIMAL(12,4))),
    ('R02:', 'PID', NULL, CAST(12 AS TINYINT), '>', CAST(1100 AS DECIMAL(12,4))),
    ('R03:', 'DTC', 'P0300', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R04:', 'DTC', 'P0171', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R05:', 'DTC', 'P0171', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R05:', 'PID', NULL, CAST(17 AS TINYINT), '<', CAST(12 AS DECIMAL(12,4))),
    ('R05:', 'PID', NULL, CAST(16 AS TINYINT), '<', CAST(1.8 AS DECIMAL(12,4))),
    ('R06:', 'DTC', 'P0172', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R07:', 'DTC', 'P0172', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R07:', 'PID', NULL, CAST(17 AS TINYINT), '<', CAST(12 AS DECIMAL(12,4))),
    ('R07:', 'PID', NULL, CAST(16 AS TINYINT), '>', CAST(4.5 AS DECIMAL(12,4))),
    ('R08:', 'DTC', 'P0171', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R09:', 'DTC', 'P0172', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R10:', 'DTC', 'P0300', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R11:', 'DTC', 'P0172', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R12:', 'DTC', 'P0301', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R13:', 'DTC', 'P0300', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R14:', 'DTC', 'P0301', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R15:', 'DTC', 'P0301', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R16:', 'DTC', 'P0117', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R17:', 'DTC', 'P0117', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R17:', 'PID', NULL, CAST(5 AS TINYINT), '>=', CAST(150 AS DECIMAL(12,4))),
    ('R18:', 'DTC', 'P0118', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R19:', 'DTC', 'P0118', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R19:', 'PID', NULL, CAST(5 AS TINYINT), '<=', CAST(-30 AS DECIMAL(12,4))),
    ('R20:', 'DTC', 'P0113', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R21:', 'DTC', 'P0113', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R21:', 'PID', NULL, CAST(15 AS TINYINT), '<=', CAST(-30 AS DECIMAL(12,4))),
    ('R22:', 'DTC', 'P0217', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R23:', 'DTC', 'P0217', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R24:', 'DTC', 'P0217', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R24:', 'PID', NULL, CAST(5 AS TINYINT), '>=', CAST(105 AS DECIMAL(12,4))),
    ('R24:', 'PID', NULL, CAST(13 AS TINYINT), '<', CAST(10 AS DECIMAL(12,4))),
    ('R25:', 'DTC', 'P0217', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R26:', 'DTC', 'P0335', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R27:', 'DTC', 'P0335', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R27:', 'PID', NULL, CAST(12 AS TINYINT), '<', CAST(100 AS DECIMAL(12,4))),
    ('R28:', 'DTC', 'P0420', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R29:', 'DTC', 'P0420', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R30:', 'DTC', 'P0500', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R31:', 'DTC', 'P0500', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R31:', 'PID', NULL, CAST(13 AS TINYINT), '<', CAST(1 AS DECIMAL(12,4))),
    ('R31:', 'PID', NULL, CAST(17 AS TINYINT), '>', CAST(40 AS DECIMAL(12,4))),
    ('R32:', 'DTC', 'P0562', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R33:', 'DTC', 'P0562', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R34:', 'DTC', 'P0562', CAST(NULL AS TINYINT), NULL, CAST(NULL AS DECIMAL(12,4))),
    ('R34:', 'PID', NULL, CAST(66 AS TINYINT), '<', CAST(12.5 AS DECIMAL(12,4))),
    ('R34:', 'PID', NULL, CAST(12 AS TINYINT), '>', CAST(1500 AS DECIMAL(12,4)))
) AS v(Ma, Loai, MaDTC, MaPID, ToanTu, Nguong)
JOIN dbo.Luat l ON l.MoTa LIKE v.Ma + N'%'
WHERE NOT EXISTS (SELECT 1 FROM dbo.DieuKienLuat d
                  WHERE d.MaLuat = l.MaLuat AND d.Loai = v.Loai
                    AND ISNULL(d.MaDTC, '') = ISNULL(v.MaDTC, '') AND ISNULL(CAST(d.MaPID AS INT), -1) = ISNULL(CAST(v.MaPID AS INT), -1)
                    AND ISNULL(d.ToanTu, '') = ISNULL(v.ToanTu, '') AND ISNULL(d.NguongGiaTri, -999999) = ISNULL(v.Nguong, -999999));
GO
