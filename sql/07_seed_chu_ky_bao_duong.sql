/* ============================================================
   Script 07: nap chu ky bao duong (docs/du-lieu/CHU-KY-BAO-DUONG.md).
   Nguon: cac cap bao duong dinh ky cua Toyota Viet Nam (toyota.com.vn), truy cap 07/10/2026.
   Chay lai nhieu lan khong loi: chi them hang muc chua co, khong ghi de chu ky da sua trong ung dung.
   ============================================================ */

USE ChanDoanXe;
GO

DECLARE @Nguon NVARCHAR(200) = N'Toyota Việt Nam, các cấp bảo dưỡng định kỳ (toyota.com.vn), truy cập 07/10/2026';

INSERT INTO dbo.ChuKyBaoDuong (HangMuc, ChuKyKm, ChuKyThang, Nguon)
SELECT v.HangMuc, v.ChuKyKm, v.ChuKyThang, @Nguon
FROM (VALUES
    (N'Thay dầu động cơ',    5000,  6),
    (N'Thay lọc dầu',       10000, 12),
    (N'Thay bugi',          20000, 24),
    (N'Thay lọc nhiên liệu', 40000, 48),
    (N'Thay dầu phanh',     40000, 48),
    (N'Thay dầu hộp số',    40000, 48)
) AS v (HangMuc, ChuKyKm, ChuKyThang)
WHERE NOT EXISTS (SELECT 1 FROM dbo.ChuKyBaoDuong c WHERE c.HangMuc = v.HangMuc);
GO
