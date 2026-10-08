# Đối chiếu nguồn cho 12 DTC và 8 PID (nạp vào CSDL bằng `sql/06_doi_chieu_nguon.sql`)

Ngày truy cập tất cả nguồn: 06/10/2026. Không có tài liệu SAE J1979/J2012 gốc (tài liệu trả phí), nên dùng **nguồn thứ cấp** và đối chiếu ít nhất hai nguồn độc lập cho mỗi dòng. `BANG-DTC-PID.xlsx` không bị sửa (Trí tự xóa dòng ví dụ mẫu và sửa sheet "Nguồn" nếu cần); CSDL đã được cập nhật `DaXacMinh = 1` và cột `Nguon` cho 12 DTC và 8 PID.

## Quy ước mới cho cột `DaXacMinh`
`DaXacMinh = 1` nghĩa là **đã đối chiếu với ít nhất hai nguồn thứ cấp độc lập và không có mâu thuẫn**, không có nghĩa là đã đọc bản gốc của SAE. Cột `Nguon` ghi tên nguồn kèm ngày truy cập. Trong báo cáo phải viết đúng như vậy: "theo SAE J1979 (qua các nguồn trên)", không viết như thể đã đọc tiêu chuẩn.

## Nguồn
- **W1** Wikipedia, "OBD-II PIDs", mục Service 01 - Show current data, https://en.wikipedia.org/wiki/OBD-II_PIDs (bảng PID, công thức, min, max, đơn vị; bài này trích lại SAE J1979).
- **P1** Mã nguồn thư viện python-OBD, `obd/commands.py`, `obd/decoders.py`, `obd/UnitsAndScaling.py` (https://github.com/brendan-w/python-OBD): khai báo lệnh, hàm giải mã và hệ số tỉ lệ của từng PID (UAS theo SAE J1979).
- **P2** Mã nguồn python-OBD, `obd/codes.py`: tên 12 DTC (từ các định nghĩa DTC chuẩn).
- **T1** Tiêu đề và nội dung các trang kỹ thuật: apextechnation.com (P0171, P0172, P0300, P0301, P0113, P0118, P0335, P0420, P0500), icarsoft-us.com (P0117, P0217, P0562); địa chỉ đầy đủ ở `docs/LUAT-CHAN-DOAN.md`.

## 8 PID
Công thức trong Excel: tất cả giống hệt W1. Với P1: PID 0x04, 0x11 dùng `v * 100.0 / 255.0` (bằng A/2.55 của W1, bằng 100/255 × A của Excel); 0x05, 0x0F dùng `v - 40`; 0x0C dùng UAS 0x07 hệ số 0,25 (bằng (256A+B)/4); 0x0D dùng UAS 0x09 hệ số 1; 0x10 dùng UAS 0x27 hệ số 0,01 (bằng (256A+B)/100); 0x42 dùng UAS 0x0B hệ số 0,001 (bằng (256A+B)/1000).

| PID | Tên (Excel) | Công thức (Excel) | W1 | P1 | Min-Max (Excel) | Min-Max (W1) | Kết luận |
|---|---|---|---|---|---|---|---|
| 0x04 | Tải động cơ tính toán | 100/255 × A | A/2.55 | ×100/255 | 0-100 % | 0-100 % | Khớp |
| 0x05 | Nhiệt độ nước làm mát | A − 40 | A − 40 | v − 40 | −40-215 °C | −40-215 °C | Khớp |
| 0x0C | Vòng tua động cơ | (256A+B)/4 | (256A+B)/4 | UAS 0,25 rpm/bit | 0-16383,75 | 0-16 383,75 | Khớp |
| 0x0D | Tốc độ xe | A | A | UAS 1 km/h/bit | 0-255 | 0-255 | Khớp |
| 0x0F | Nhiệt độ khí nạp | A − 40 | A − 40 | v − 40 | −40-215 °C | −40-215 °C | Khớp |
| 0x10 | Lưu lượng khí nạp (MAF) | (256A+B)/100 | (256A+B)/100 | UAS 0,01 g/s/bit | 0-655,35 | 0-655,35 | Khớp |
| 0x11 | Vị trí bướm ga | 100/255 × A | A/2.55 | ×100/255 | 0-100 % | 0-100 % | Khớp |
| 0x42 | Điện áp mô-đun điều khiển | (256A+B)/1000 | (256A+B)/1000 | UAS 0,001 V/bit | 0-65,535 | 0-65,535 | Khớp |

Đề xuất: 8 PID đặt `DaXacMinh = 1`; cột `Nguon` ghi "Wikipedia OBD-II PIDs; python-OBD (commands.py, UnitsAndScaling.py); truy cập 06/10/2026".

Lưu ý: min/max chỉ có một nguồn (W1) và đây là phạm vi biểu diễn của byte dữ liệu, không phải giới hạn vật lý của động cơ.

## 12 DTC
Tên gốc (tiếng Anh) trong Excel so với P2 và T1:

| Mã | Mô tả gốc (Excel) | P2 (python-OBD) | T1 | Kết luận |
|---|---|---|---|---|
| P0300 | Random/Multiple Cylinder Misfire Detected | giống | "Random/Multiple Cylinder Misfire" | Khớp |
| P0301 | Cylinder 1 Misfire Detected | giống | "Cylinder 1 Misfire" | Khớp |
| P0171 | System Too Lean (Bank 1) | "System Too Lean" | "System Too Lean Bank 1" | Khớp (P2 bỏ "Bank 1") |
| P0172 | System Too Rich (Bank 1) | "System Too Rich" | "System Too Rich Bank 1" | Khớp (P2 bỏ "Bank 1") |
| P0113 | Intake Air Temperature Sensor 1 Circuit High | giống | "Intake Air Temperature Sensor Circuit High Input" | Khớp |
| P0117 | Engine Coolant Temperature Sensor 1 Circuit Low | "Engine Coolant Temperature Circuit Low" | "Engine Coolant Temperature Sensor Circuit Low Input" | Khớp (P2 bỏ "Sensor 1") |
| P0118 | Engine Coolant Temperature Sensor 1 Circuit High | "Engine Coolant Temperature Circuit High" | "Engine Coolant Temperature Sensor Circuit High Input" | Khớp (P2 bỏ "Sensor 1") |
| P0217 | Engine Coolant Over Temperature Condition | giống | "Engine Coolant Over Temperature Condition" | Khớp |
| P0335 | Crankshaft Position Sensor "A" Circuit | giống | "Crankshaft Position Sensor Circuit Malfunction" | Khớp |
| P0420 | Catalyst System Efficiency Below Threshold (Bank 1) | "Catalyst System Efficiency Below Threshold" | "Catalyst System Efficiency Below Threshold" | Khớp (P2 bỏ "Bank 1") |
| P0500 | Vehicle Speed Sensor "A" | giống | "Vehicle Speed Sensor Circuit Malfunction" | Khớp |
| P0562 | System Voltage Low | giống | "System Voltage Low" | Khớp |

Không có mâu thuẫn về ý nghĩa. Khác biệt chỉ là các nguồn rút gọn "Bank 1" hoặc "Sensor 1"; tên đầy đủ trong Excel là cách viết đầy đủ của định nghĩa chuẩn nên **giữ nguyên Excel**.

Đề xuất: 12 DTC đặt `DaXacMinh = 1`, cột `Nguon` ghi "python-OBD codes.py; apextechnation/icarsoft-us (xem docs/DOI-CHIEU-NGUON.md); truy cập 06/10/2026".

**Việc còn mở (không thể làm bằng nguồn thứ cấp):**
- **Mô tả tiếng Việt** do tác giả dịch; **mức độ nghiêm trọng** do tác giả đề xuất (nhẹ/trung bình/nghiêm trọng, ánh xạ sang Thông tin/Cảnh báo/Nghiêm trọng): SAE không quy định, không có nguồn để đối chiếu. Báo cáo đã nêu rõ.
- Trong Excel, dòng đầu tiên của sheet DTC (P0301 với các ô `<chép nguyên văn từ SAE J2012>`) và dòng đầu của sheet PID (0x0C với các ô `<...>`) là **dòng ví dụ mẫu**, nên xóa trước khi nộp. Sheet "Nguồn" còn ghi mục tiêu "30-40 DTC, 8-10 PID", nên sửa thành 12 DTC, 8 PID cho khớp đề cương.
- Mã P0420: Excel ghi mức độ "Nhẹ / Trung bình", đã quy về "Cảnh báo".
