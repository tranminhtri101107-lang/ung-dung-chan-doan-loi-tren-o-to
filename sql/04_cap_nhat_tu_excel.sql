/* Script 04: cap nhat DTC va PID tu docs/BANG-DTC-PID.xlsx (sinh tu dong, chay lai duoc).
   MucDo: Nghiem trong -> CRITICAL, Trung binh -> WARNING, Nhe -> INFO, 'Nhe / Trung binh' -> WARNING.
   DaXacMinh = 1 chi khi cot Trang thai trong Excel la 'Da xac minh'. */
USE ChanDoanXe;
GO
UPDATE dbo.DTC SET MoTaGoc=N'Random/Multiple Cylinder Misfire Detected', MoTaViet=N'Phát hiện bỏ máy ngẫu nhiên/nhiều xy-lanh', NhomHeThong=N'Đánh lửa / bỏ máy', MucDo='CRITICAL', Nguon=N'SAE J2012', DaXacMinh=1 WHERE MaDTC='P0300';
UPDATE dbo.DTC SET MoTaGoc=N'Cylinder 1 Misfire Detected', MoTaViet=N'Phát hiện bỏ máy xy-lanh số 1', NhomHeThong=N'Đánh lửa / bỏ máy', MucDo='CRITICAL', Nguon=N'SAE J2012', DaXacMinh=0 WHERE MaDTC='P0301';
UPDATE dbo.DTC SET MoTaGoc=N'System Too Lean (Bank 1)', MoTaViet=N'Hệ thống nhiên liệu quá nhạt (Nhánh 1)', NhomHeThong=N'Nhiên liệu / khí nạp', MucDo='WARNING', Nguon=N'SAE J2012', DaXacMinh=0 WHERE MaDTC='P0171';
UPDATE dbo.DTC SET MoTaGoc=N'System Too Rich (Bank 1)', MoTaViet=N'Hệ thống nhiên liệu quá đậm (Nhánh 1)', NhomHeThong=N'Nhiên liệu / khí nạp', MucDo='WARNING', Nguon=N'SAE J2012', DaXacMinh=0 WHERE MaDTC='P0172';
UPDATE dbo.DTC SET MoTaGoc=N'Intake Air Temperature Sensor 1 Circuit High', MoTaViet=N'Mạch cảm biến nhiệt độ khí nạp 1 điện áp cao', NhomHeThong=N'Nhiên liệu / khí nạp', MucDo='INFO', Nguon=N'SAE J2012', DaXacMinh=0 WHERE MaDTC='P0113';
UPDATE dbo.DTC SET MoTaGoc=N'Engine Coolant Temperature Sensor 1 Circuit Low', MoTaViet=N'Mạch cảm biến nhiệt độ nước làm mát 1 điện áp thấp', NhomHeThong=N'Làm mát', MucDo='WARNING', Nguon=N'SAE J2012', DaXacMinh=0 WHERE MaDTC='P0117';
UPDATE dbo.DTC SET MoTaGoc=N'Engine Coolant Temperature Sensor 1 Circuit High', MoTaViet=N'Mạch cảm biến nhiệt độ nước làm mát 1 điện áp cao', NhomHeThong=N'Làm mát', MucDo='WARNING', Nguon=N'SAE J2012', DaXacMinh=0 WHERE MaDTC='P0118';
UPDATE dbo.DTC SET MoTaGoc=N'Engine Coolant Over Temperature Condition', MoTaViet=N'Tình trạng nhiệt độ nước làm mát động cơ quá cao', NhomHeThong=N'Làm mát', MucDo='CRITICAL', Nguon=N'SAE J2012', DaXacMinh=0 WHERE MaDTC='P0217';
UPDATE dbo.DTC SET MoTaGoc=N'Crankshaft Position Sensor "A" Circuit', MoTaViet=N'Mạch cảm biến vị trí trục khuỷu "A"', NhomHeThong=N'Cảm biến trục khuỷu', MucDo='CRITICAL', Nguon=N'SAE J2012', DaXacMinh=0 WHERE MaDTC='P0335';
UPDATE dbo.DTC SET MoTaGoc=N'Catalyst System Efficiency Below Threshold (Bank 1)', MoTaViet=N'Hiệu suất hệ thống xúc tác dưới ngưỡng (Nhánh 1)', NhomHeThong=N'Khí thải', MucDo='WARNING', Nguon=N'SAE J2012', DaXacMinh=0 WHERE MaDTC='P0420';
UPDATE dbo.DTC SET MoTaGoc=N'Vehicle Speed Sensor "A"', MoTaViet=N'Cảm biến tốc độ xe "A"', NhomHeThong=N'Tốc độ xe', MucDo='WARNING', Nguon=N'SAE J2012', DaXacMinh=0 WHERE MaDTC='P0500';
UPDATE dbo.DTC SET MoTaGoc=N'System Voltage Low', MoTaViet=N'Điện áp hệ thống thấp', NhomHeThong=N'Điện / điện áp', MucDo='WARNING', Nguon=N'SAE J2012', DaXacMinh=0 WHERE MaDTC='P0562';
UPDATE dbo.PID SET Ten=N'Tải động cơ tính toán', SoByte=1, CongThuc=N'100/255 × A', DonVi=N'%', GiaTriMin=0, GiaTriMax=100, Nguon=N'SAE J1979', DaXacMinh=0 WHERE MaPID=4; -- 0x04
UPDATE dbo.PID SET Ten=N'Nhiệt độ nước làm mát', SoByte=1, CongThuc=N'A − 40', DonVi=N'°C', GiaTriMin=-40, GiaTriMax=215, Nguon=N'SAE J1979', DaXacMinh=0 WHERE MaPID=5; -- 0x05
UPDATE dbo.PID SET Ten=N'Vòng tua động cơ', SoByte=2, CongThuc=N'(256×A + B) / 4', DonVi=N'RPM', GiaTriMin=0, GiaTriMax=16383.75, Nguon=N'SAE J1979', DaXacMinh=0 WHERE MaPID=12; -- 0x0C
UPDATE dbo.PID SET Ten=N'Tốc độ xe', SoByte=1, CongThuc=N'A', DonVi=N'km/h', GiaTriMin=0, GiaTriMax=255, Nguon=N'SAE J1979', DaXacMinh=0 WHERE MaPID=13; -- 0x0D
UPDATE dbo.PID SET Ten=N'Nhiệt độ khí nạp', SoByte=1, CongThuc=N'A − 40', DonVi=N'°C', GiaTriMin=-40, GiaTriMax=215, Nguon=N'SAE J1979', DaXacMinh=0 WHERE MaPID=15; -- 0x0F
UPDATE dbo.PID SET Ten=N'Lưu lượng khí nạp (MAF)', SoByte=2, CongThuc=N'(256×A + B) / 100', DonVi=N'g/s', GiaTriMin=0, GiaTriMax=655.35, Nguon=N'SAE J1979', DaXacMinh=0 WHERE MaPID=16; -- 0x10
UPDATE dbo.PID SET Ten=N'Vị trí bướm ga', SoByte=1, CongThuc=N'100/255 × A', DonVi=N'%', GiaTriMin=0, GiaTriMax=100, Nguon=N'SAE J1979', DaXacMinh=0 WHERE MaPID=17; -- 0x11
UPDATE dbo.PID SET Ten=N'Điện áp mô-đun điều khiển', SoByte=2, CongThuc=N'(256×A + B) / 1000', DonVi=N'V', GiaTriMin=0, GiaTriMax=65.535, Nguon=N'SAE J1979', DaXacMinh=0 WHERE MaPID=66; -- 0x42
GO
SELECT COUNT(*) AS DTC_co_mo_ta, SUM(CAST(DaXacMinh AS int)) AS DTC_da_xac_minh FROM dbo.DTC WHERE MoTaGoc IS NOT NULL;
SELECT COUNT(*) AS PID_co_nguon, SUM(CAST(DaXacMinh AS int)) AS PID_da_xac_minh FROM dbo.PID WHERE Nguon IS NOT NULL;
GO
