/* ============================================================
   Script 06: ghi ket qua doi chieu nguon (docs/du-lieu/DOI-CHIEU-NGUON.md) vao danh muc DTC va PID.
   DaXacMinh = 1 nghia la "da doi chieu it nhat hai nguon thu cap doc lap, khong mau thuan"
   (KHONG phai da doc ban goc SAE J1979/J2012). Chay lai nhieu lan khong loi.
   ============================================================ */

USE ChanDoanXe;
GO

UPDATE dbo.PID
SET Nguon = N'Wikipedia OBD-II PIDs; python-OBD (commands.py, UnitsAndScaling.py); truy cập 06/10/2026',
    DaXacMinh = 1
WHERE MaPID IN (0x04, 0x05, 0x0C, 0x0D, 0x0F, 0x10, 0x11, 0x42);
GO

UPDATE dbo.DTC
SET Nguon = N'python-OBD codes.py; apextechnation, icarsoft-us (docs/du-lieu/DOI-CHIEU-NGUON.md); truy cập 06/10/2026',
    DaXacMinh = 1
WHERE MaDTC IN ('P0300', 'P0301', 'P0171', 'P0172', 'P0113', 'P0117', 'P0118', 'P0217', 'P0335', 'P0420', 'P0500', 'P0562');
GO
