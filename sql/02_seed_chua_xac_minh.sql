/* ============================================================
   Script 02: nap danh muc ung vien. TAT CA CON O TRANG THAI CHUA XAC MINH (DaXacMinh = 0).
   - Chi co MA va nhom he thong; mo ta, muc do, nguon de trong cho den khi Tri tra nguon SAE J2012.
   - PID: cong thuc/min/max la DE XUAT, chua doi chieu SAE J1979.
   Khi da tra nguon, cap nhat dong tuong ung va dat DaXacMinh = 1.
   Nguon tong hop: docs/du-lieu/BANG-DTC-PID.xlsx
   ============================================================ */

USE ChanDoanXe;
GO

INSERT INTO dbo.DTC (MaDTC, NhomHeThong)
SELECT v.MaDTC, v.Nhom
FROM (VALUES
    ('P0300', N'Đánh lửa / bỏ máy'),
    ('P0301', N'Đánh lửa / bỏ máy'),
    ('P0171', N'Nhiên liệu / khí nạp'),
    ('P0172', N'Nhiên liệu / khí nạp'),
    ('P0113', N'Nhiên liệu / khí nạp'),
    ('P0117', N'Làm mát'),
    ('P0118', N'Làm mát'),
    ('P0217', N'Làm mát'),
    ('P0335', N'Cảm biến trục khuỷu'),
    ('P0420', N'Khí thải'),
    ('P0500', N'Tốc độ xe'),
    ('P0562', N'Điện / điện áp')
) AS v(MaDTC, Nhom)
WHERE NOT EXISTS (SELECT 1 FROM dbo.DTC d WHERE d.MaDTC = v.MaDTC);
GO

INSERT INTO dbo.PID (MaPID, Ten, SoByte, CongThuc, DonVi, GiaTriMin, GiaTriMax)
SELECT v.MaPID, v.Ten, v.SoByte, v.CongThuc, v.DonVi, v.GMin, v.GMax
FROM (VALUES
    (CAST(0x04 AS TINYINT), N'Tải động cơ tính toán',         CAST(1 AS TINYINT), N'100/255 × A',      N'%',    CAST(0 AS DECIMAL(12,4)),   CAST(100 AS DECIMAL(12,4))),
    (CAST(0x05 AS TINYINT), N'Nhiệt độ nước làm mát',         CAST(1 AS TINYINT), N'A − 40',           N'°C',   CAST(-40 AS DECIMAL(12,4)), CAST(215 AS DECIMAL(12,4))),
    (CAST(0x0C AS TINYINT), N'Vòng tua động cơ',              CAST(2 AS TINYINT), N'(256×A + B) / 4',  N'RPM',  CAST(0 AS DECIMAL(12,4)),   CAST(16383.75 AS DECIMAL(12,4))),
    (CAST(0x0D AS TINYINT), N'Tốc độ xe',                     CAST(1 AS TINYINT), N'A',                N'km/h', CAST(0 AS DECIMAL(12,4)),   CAST(255 AS DECIMAL(12,4))),
    (CAST(0x0F AS TINYINT), N'Nhiệt độ khí nạp',              CAST(1 AS TINYINT), N'A − 40',           N'°C',   CAST(-40 AS DECIMAL(12,4)), CAST(215 AS DECIMAL(12,4))),
    (CAST(0x10 AS TINYINT), N'Lưu lượng khí nạp (MAF)',       CAST(2 AS TINYINT), N'(256×A + B) / 100',N'g/s',  CAST(0 AS DECIMAL(12,4)),   CAST(655.35 AS DECIMAL(12,4))),
    (CAST(0x11 AS TINYINT), N'Vị trí bướm ga',                CAST(1 AS TINYINT), N'100/255 × A',      N'%',    CAST(0 AS DECIMAL(12,4)),   CAST(100 AS DECIMAL(12,4))),
    (CAST(0x42 AS TINYINT), N'Điện áp mô-đun điều khiển',     CAST(2 AS TINYINT), N'(256×A + B) / 1000',N'V',   CAST(0 AS DECIMAL(12,4)),   CAST(65.535 AS DECIMAL(12,4)))
) AS v(MaPID, Ten, SoByte, CongThuc, DonVi, GMin, GMax)
WHERE NOT EXISTS (SELECT 1 FROM dbo.PID p WHERE p.MaPID = v.MaPID);
GO

-- Kiem tra nhanh
SELECT COUNT(*) AS SoDTC FROM dbo.DTC;
SELECT COUNT(*) AS SoPID FROM dbo.PID;
GO
