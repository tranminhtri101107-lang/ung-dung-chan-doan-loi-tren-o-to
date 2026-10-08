/* ============================================================
   Script 03: tao tai khoan SQL rieng cho ung dung Java (khong dung 'sa').
   BUOC BAT BUOC TRUOC KHI CHAY: thay <DOI_MAT_KHAU> bang mat khau cua BAN
   (it nhat 8 ky tu, co chu hoa, chu thuong, so). Dung chinh mat khau do trong config.properties.
   KHONG luu mat khau that vao file nay hoac dua len git.
   Chay bang SSMS, ket noi bang Windows Authentication.
   ============================================================ */

USE master;
GO
-- Neu tai khoan da ton tai (vi du chay lan truoc voi mat khau khac) thi DAT LAI mat khau,
-- khong bo qua. CHECK_POLICY = OFF vi day la CSDL hoc tap tren may ca nhan.
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'chandoan_app')
    CREATE LOGIN chandoan_app WITH PASSWORD = N'123456', CHECK_POLICY = OFF;
ELSE
    ALTER LOGIN chandoan_app WITH PASSWORD = N'123456', CHECK_POLICY = OFF;
GO

USE ChanDoanXe;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'chandoan_app')
    CREATE USER chandoan_app FOR LOGIN chandoan_app;
GO
-- Chi cap quyen doc/ghi du lieu, khong cap quyen sua cau truc bang
ALTER ROLE db_datareader ADD MEMBER chandoan_app;
ALTER ROLE db_datawriter ADD MEMBER chandoan_app;
GO
