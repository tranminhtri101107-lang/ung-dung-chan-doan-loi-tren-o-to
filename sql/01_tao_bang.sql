/* ============================================================
   CSDL: ChanDoanXe  -  Phan mem chan doan loi o to cho xuong dich vu
   Script 01: tao co so du lieu va cac bang (SQL Server)
   Nguon thiet ke: docs/ERD.md, docs/erd.drawio
   Chay bang SSMS: mo file, bam Execute (F5).
   ============================================================ */

IF DB_ID(N'ChanDoanXe') IS NULL
    CREATE DATABASE ChanDoanXe;
GO

USE ChanDoanXe;
GO

/* ---------------- NHOM DANH MUC ---------------- */

-- DTC: ma loi. Dinh dang SAE J2012: 1 chu (P/B/C/U) + 4 ky tu, ky tu thu 2 la 0-3, ba ky tu sau la hex.
IF OBJECT_ID(N'dbo.DTC') IS NULL
CREATE TABLE dbo.DTC (
    MaDTC        CHAR(5)       NOT NULL,
    MoTaGoc      NVARCHAR(200) NULL,        -- chep nguyen van tu nguon (tieng Anh)
    MoTaViet     NVARCHAR(300) NULL,
    NhomHeThong  NVARCHAR(50)  NULL,
    MucDo        VARCHAR(10)   NULL,        -- INFO / WARNING / CRITICAL (khop enum Severity trong Java)
    Nguon        NVARCHAR(200) NULL,        -- tai lieu + muc/trang
    DaXacMinh    BIT           NOT NULL CONSTRAINT DF_DTC_DaXacMinh DEFAULT 0,
    CONSTRAINT PK_DTC PRIMARY KEY (MaDTC),
    CONSTRAINT CK_DTC_MaDTC CHECK (MaDTC LIKE '[PBCU][0-3][0-9A-F][0-9A-F][0-9A-F]'),
    CONSTRAINT CK_DTC_MucDo CHECK (MucDo IS NULL OR MucDo IN ('INFO','WARNING','CRITICAL'))
);
GO

-- PID: thong so doc o Mode 01. MaPID luu dang so (0..255); hien thi dang hex o giao dien.
IF OBJECT_ID(N'dbo.PID') IS NULL
CREATE TABLE dbo.PID (
    MaPID      TINYINT       NOT NULL,
    Ten        NVARCHAR(100) NOT NULL,
    SoByte     TINYINT       NOT NULL,
    CongThuc   NVARCHAR(100) NULL,         -- A, B la byte du lieu thu 1, 2
    DonVi      NVARCHAR(20)  NULL,
    GiaTriMin  DECIMAL(12,4) NULL,
    GiaTriMax  DECIMAL(12,4) NULL,
    Nguon      NVARCHAR(200) NULL,
    DaXacMinh  BIT           NOT NULL CONSTRAINT DF_PID_DaXacMinh DEFAULT 0,
    CONSTRAINT PK_PID PRIMARY KEY (MaPID),
    CONSTRAINT CK_PID_SoByte CHECK (SoByte BETWEEN 1 AND 4)
);
GO

-- NguyenNhan: nguyen nhan co the cua loi (dau ra cua he chuyen gia)
IF OBJECT_ID(N'dbo.NguyenNhan') IS NULL
CREATE TABLE dbo.NguyenNhan (
    MaNN           INT IDENTITY(1,1) NOT NULL,
    TenNguyenNhan  NVARCHAR(150)     NOT NULL,
    GoiYKiemTra    NVARCHAR(500)     NULL,
    CONSTRAINT PK_NguyenNhan PRIMARY KEY (MaNN),
    CONSTRAINT UQ_NguyenNhan_Ten UNIQUE (TenNguyenNhan)
);
GO

-- Luat: luat IF-THEN ung voi mot nguyen nhan, kem diem tin cay (0..1). Noi dung luat phai co nguon.
IF OBJECT_ID(N'dbo.Luat') IS NULL
CREATE TABLE dbo.Luat (
    MaLuat      INT IDENTITY(1,1) NOT NULL,
    MaNN        INT               NOT NULL,
    DiemTinCay  DECIMAL(4,3)      NOT NULL,
    MoTa        NVARCHAR(300)     NULL,
    Nguon       NVARCHAR(200)     NULL,
    CONSTRAINT PK_Luat PRIMARY KEY (MaLuat),
    CONSTRAINT FK_Luat_NguyenNhan FOREIGN KEY (MaNN) REFERENCES dbo.NguyenNhan (MaNN),
    CONSTRAINT CK_Luat_Diem CHECK (DiemTinCay BETWEEN 0 AND 1)
);
GO

-- DieuKienLuat: cac dieu kien (AND) cua mot luat; moi dieu kien theo DTC hoac theo gia tri PID.
IF OBJECT_ID(N'dbo.DieuKienLuat') IS NULL
CREATE TABLE dbo.DieuKienLuat (
    MaDK          INT IDENTITY(1,1) NOT NULL,
    MaLuat        INT               NOT NULL,
    Loai          VARCHAR(3)        NOT NULL,     -- 'DTC' hoac 'PID'
    MaDTC         CHAR(5)           NULL,
    MaPID         TINYINT           NULL,
    ToanTu        VARCHAR(2)        NULL,         -- dung khi Loai = 'PID'
    NguongGiaTri  DECIMAL(12,4)     NULL,
    CONSTRAINT PK_DieuKienLuat PRIMARY KEY (MaDK),
    CONSTRAINT FK_DK_Luat FOREIGN KEY (MaLuat) REFERENCES dbo.Luat (MaLuat) ON DELETE CASCADE,
    CONSTRAINT FK_DK_DTC  FOREIGN KEY (MaDTC)  REFERENCES dbo.DTC (MaDTC),
    CONSTRAINT FK_DK_PID  FOREIGN KEY (MaPID)  REFERENCES dbo.PID (MaPID),
    CONSTRAINT CK_DK_Loai   CHECK (Loai IN ('DTC','PID')),
    CONSTRAINT CK_DK_ToanTu CHECK (ToanTu IS NULL OR ToanTu IN ('>','>=','<','<=','=','<>')),
    -- Loai 'DTC' chi co MaDTC; Loai 'PID' chi co MaPID kem toan tu va nguong
    CONSTRAINT CK_DK_NhatQuan CHECK (
        (Loai = 'DTC' AND MaDTC IS NOT NULL AND MaPID IS NULL)
        OR
        (Loai = 'PID' AND MaPID IS NOT NULL AND MaDTC IS NULL AND ToanTu IS NOT NULL AND NguongGiaTri IS NOT NULL)
    )
);
GO

/* ---------------- NHOM NGHIEP VU ---------------- */

IF OBJECT_ID(N'dbo.Xe') IS NULL
CREATE TABLE dbo.Xe (
    MaXe        INT IDENTITY(1,1) NOT NULL,
    BienSo      NVARCHAR(15)      NOT NULL,
    Hang        NVARCHAR(50)      NULL,
    DongXe      NVARCHAR(50)      NULL,
    NamSanXuat  SMALLINT          NULL,
    GhiChu      NVARCHAR(200)     NULL,
    VIN         CHAR(17)          NULL,            -- so khung, 17 ky tu (ISO 3779)
    SoKmHienTai INT               NULL,
    ChuXe       NVARCHAR(100)     NULL,
    SoDienThoai VARCHAR(15)       NULL,
    CONSTRAINT PK_Xe PRIMARY KEY (MaXe),
    CONSTRAINT UQ_Xe_BienSo UNIQUE (BienSo),
    CONSTRAINT CK_Xe_Nam CHECK (NamSanXuat IS NULL OR NamSanXuat BETWEEN 1950 AND 2100),
    CONSTRAINT CK_Xe_VIN CHECK (VIN IS NULL OR
        (LEN(VIN) = 17 AND VIN COLLATE Latin1_General_BIN NOT LIKE '%[^0-9ABCDEFGHJKLMNPRSTUVWXYZ]%')),
    CONSTRAINT CK_Xe_SoKm CHECK (SoKmHienTai IS NULL OR SoKmHienTai >= 0)
);
GO
-- Hai xe khong duoc trung VIN (bo qua xe chua nhap VIN); chi muc co loc can QUOTED_IDENTIFIER ON
SET QUOTED_IDENTIFIER ON;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_Xe_VIN' AND object_id = OBJECT_ID(N'dbo.Xe'))
    CREATE UNIQUE INDEX UX_Xe_VIN ON dbo.Xe (VIN) WHERE VIN IS NOT NULL;
GO

-- ChuKyBaoDuong: chu ky bao duong theo hang muc (km va/hoac thang), dung de nhac bao duong
IF OBJECT_ID(N'dbo.ChuKyBaoDuong') IS NULL
CREATE TABLE dbo.ChuKyBaoDuong (
    MaCK        INT IDENTITY(1,1) NOT NULL,
    HangMuc     NVARCHAR(100)     NOT NULL,
    ChuKyKm     INT               NULL,
    ChuKyThang  INT               NULL,
    Nguon       NVARCHAR(200)     NULL,
    CONSTRAINT PK_ChuKyBaoDuong PRIMARY KEY (MaCK),
    CONSTRAINT UQ_ChuKy_HangMuc UNIQUE (HangMuc),
    CONSTRAINT CK_ChuKy_CoChuKy CHECK (ChuKyKm IS NOT NULL OR ChuKyThang IS NOT NULL),
    CONSTRAINT CK_ChuKy_Km CHECK (ChuKyKm IS NULL OR ChuKyKm > 0),
    CONSTRAINT CK_ChuKy_Thang CHECK (ChuKyThang IS NULL OR ChuKyThang > 0)
);
GO

-- BaoDuong: lich su bao duong cua xe (UC11). Khong xoa day chuyen: xe con ho so bao duong thi khong xoa duoc xe.

IF OBJECT_ID(N'dbo.BaoDuong') IS NULL
CREATE TABLE dbo.BaoDuong (
    MaBD          INT IDENTITY(1,1) NOT NULL,
    MaXe          INT               NOT NULL,
    NgayBaoDuong  DATE              NOT NULL,
    SoKm          INT               NULL,
    HangMuc       NVARCHAR(100)     NOT NULL,
    ChiPhi        DECIMAL(12,0)     NULL,          -- dong Viet Nam
    GhiChu        NVARCHAR(300)     NULL,
    CONSTRAINT PK_BaoDuong PRIMARY KEY (MaBD),
    CONSTRAINT FK_BaoDuong_Xe FOREIGN KEY (MaXe) REFERENCES dbo.Xe (MaXe),
    CONSTRAINT CK_BaoDuong_SoKm  CHECK (SoKm IS NULL OR SoKm >= 0),
    CONSTRAINT CK_BaoDuong_ChiPhi CHECK (ChiPhi IS NULL OR ChiPhi >= 0),
    CONSTRAINT CK_BaoDuong_HangMuc CHECK (LEN(LTRIM(RTRIM(HangMuc))) > 0)
);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_BaoDuong_MaXe' AND object_id = OBJECT_ID(N'dbo.BaoDuong'))
    CREATE INDEX IX_BaoDuong_MaXe ON dbo.BaoDuong (MaXe, NgayBaoDuong DESC);
GO

IF OBJECT_ID(N'dbo.PhienChanDoan') IS NULL
CREATE TABLE dbo.PhienChanDoan (
    MaPhien          INT IDENTITY(1,1) NOT NULL,
    MaXe             INT               NOT NULL,
    ThoiGianBatDau   DATETIME2(0)      NOT NULL CONSTRAINT DF_Phien_BatDau DEFAULT SYSDATETIME(),
    ThoiGianKetThuc  DATETIME2(0)      NULL,
    GhiChu           NVARCHAR(300)     NULL,
    CONSTRAINT PK_PhienChanDoan PRIMARY KEY (MaPhien),
    CONSTRAINT FK_Phien_Xe FOREIGN KEY (MaXe) REFERENCES dbo.Xe (MaXe),
    CONSTRAINT CK_Phien_ThoiGian CHECK (ThoiGianKetThuc IS NULL OR ThoiGianKetThuc >= ThoiGianBatDau)
);
CREATE INDEX IX_Phien_MaXe ON dbo.PhienChanDoan (MaXe);
GO

-- PhienDTC: bang noi nhieu-nhieu giua phien va DTC doc duoc
IF OBJECT_ID(N'dbo.PhienDTC') IS NULL
CREATE TABLE dbo.PhienDTC (
    MaPhien           INT          NOT NULL,
    MaDTC             CHAR(5)      NOT NULL,
    ThoiGianPhatHien  DATETIME2(0) NOT NULL CONSTRAINT DF_PhienDTC_TG DEFAULT SYSDATETIME(),
    CONSTRAINT PK_PhienDTC PRIMARY KEY (MaPhien, MaDTC),
    CONSTRAINT FK_PhienDTC_Phien FOREIGN KEY (MaPhien) REFERENCES dbo.PhienChanDoan (MaPhien) ON DELETE CASCADE,
    CONSTRAINT FK_PhienDTC_DTC   FOREIGN KEY (MaDTC)   REFERENCES dbo.DTC (MaDTC)
);
GO

-- MauPID: anh chup gia tri PID tai thoi diem doc DTC / phan tich (khong luu lien tuc)
IF OBJECT_ID(N'dbo.MauPID') IS NULL
CREATE TABLE dbo.MauPID (
    MaMau       BIGINT IDENTITY(1,1) NOT NULL,
    MaPhien     INT                  NOT NULL,
    MaPID       TINYINT              NOT NULL,
    ThoiGian    DATETIME2(3)         NOT NULL CONSTRAINT DF_MauPID_TG DEFAULT SYSDATETIME(),
    GiaTriThuc  DECIMAL(12,4)        NOT NULL,
    CONSTRAINT PK_MauPID PRIMARY KEY (MaMau),
    CONSTRAINT FK_MauPID_Phien FOREIGN KEY (MaPhien) REFERENCES dbo.PhienChanDoan (MaPhien) ON DELETE CASCADE,
    CONSTRAINT FK_MauPID_PID   FOREIGN KEY (MaPID)   REFERENCES dbo.PID (MaPID)
);
CREATE INDEX IX_MauPID_MaPhien ON dbo.MauPID (MaPhien);
GO

-- KetQuaPhanTich: nguyen nhan he chuyen gia de xuat cho moi phien, kem thu hang (de do top-1/top-3)
IF OBJECT_ID(N'dbo.KetQuaPhanTich') IS NULL
CREATE TABLE dbo.KetQuaPhanTich (
    MaKQ        INT IDENTITY(1,1) NOT NULL,
    MaPhien     INT               NOT NULL,
    MaNN        INT               NOT NULL,
    DiemTinCay  DECIMAL(4,3)      NOT NULL,
    ThuHang     TINYINT           NOT NULL,
    CONSTRAINT PK_KetQuaPhanTich PRIMARY KEY (MaKQ),
    CONSTRAINT FK_KQ_Phien FOREIGN KEY (MaPhien) REFERENCES dbo.PhienChanDoan (MaPhien) ON DELETE CASCADE,
    CONSTRAINT FK_KQ_NN    FOREIGN KEY (MaNN)    REFERENCES dbo.NguyenNhan (MaNN),
    CONSTRAINT UQ_KQ_Phien_NN  UNIQUE (MaPhien, MaNN),
    CONSTRAINT UQ_KQ_Phien_Hang UNIQUE (MaPhien, ThuHang),
    CONSTRAINT CK_KQ_Diem CHECK (DiemTinCay BETWEEN 0 AND 1),
    CONSTRAINT CK_KQ_Hang CHECK (ThuHang >= 1)
);
GO

-- NhatKyThaoTac: du lieu cho tab "Lich su"
IF OBJECT_ID(N'dbo.NhatKyThaoTac') IS NULL
CREATE TABLE dbo.NhatKyThaoTac (
    MaNK         INT IDENTITY(1,1) NOT NULL,
    MaPhien      INT               NOT NULL,
    LoaiThaoTac  VARCHAR(20)       NOT NULL,
    ThoiGian     DATETIME2(0)      NOT NULL CONSTRAINT DF_NK_TG DEFAULT SYSDATETIME(),
    ChiTiet      NVARCHAR(300)     NULL,
    CONSTRAINT PK_NhatKyThaoTac PRIMARY KEY (MaNK),
    CONSTRAINT FK_NK_Phien FOREIGN KEY (MaPhien) REFERENCES dbo.PhienChanDoan (MaPhien) ON DELETE CASCADE,
    CONSTRAINT CK_NK_Loai CHECK (LoaiThaoTac IN ('KET_NOI','DOC_DTC','XOA_DTC','XEM_PID','PHAN_TICH'))
);
CREATE INDEX IX_NK_MaPhien ON dbo.NhatKyThaoTac (MaPhien);
GO
