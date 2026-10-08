# Hướng dẫn chạy demo khi bảo vệ

## Chuẩn bị trước buổi bảo vệ (một lần)
- Máy dùng để trình chiếu đã cài: JDK 17 trở lên, SQL Server Express (`.\SQLEXPRESS`) có CSDL `ChanDoanXe` đã nạp đủ script `sql/01` đến `sql/07`, VMware có máy ảo Ubuntu.
- `config.properties` có `source=gateway`, đúng mật khẩu và đúng `gateway.host` (IP máy ảo).
- Trong CSDL có sẵn 2-3 xe mẫu và vài lần bảo dưỡng để màn hình không trống.
- Quay sẵn **video demo dự phòng** 3-5 phút phòng khi máy ảo hoặc mạng gặp sự cố.

## Thứ tự bật (khoảng 3 phút)
1. **SQL Server**: thường tự chạy cùng Windows. Nếu chưa chạy, mở `services.msc` và khởi động "SQL Server (SQLEXPRESS)".
2. **Máy ảo**: bật Ubuntu trong VMware, mở terminal, `cd` vào thư mục chứa `ecu_engine.cpp` và `gateway.cpp`.
3. **Bus CAN ảo**: `bash setup_vcan.sh` (cần mật khẩu sudo; phải chạy lại mỗi lần máy ảo khởi động).
4. **ECU và Gateway**: `bash chay-demo.sh`. Script tự biên dịch khi cần, chạy cả hai ở nền và in [ĐẠT]/[LỖI].
5. Muốn hội đồng thấy khung CAN thì mở thêm một terminal chạy `candump vcan0`.
6. **Kiểm tra từ Windows**: `powershell -ExecutionPolicy Bypass -File kiem-tra-demo.ps1` phải in "TẤT CẢ ĐẠT".
7. **Mở ứng dụng**: chạy `run.bat`. Góc trên phải hiện chấm xanh và nguồn Gateway.

Tắt sau khi xong: `bash dung-demo.sh` trong máy ảo.

## Kịch bản demo (khoảng 5 phút)
1. **Quản lý xe**: chọn xe, chỉ thẻ thông tin xe (biển số, VIN, số km, lần chẩn đoán gần nhất).
2. **Dữ liệu sống**: kim đồng hồ chạy, biểu đồ vòng tua và nhiệt độ; chỉ terminal `candump` đang có khung 0x7DF/0x7E8.
3. **Chẩn đoán lỗi**: đọc mã lỗi, rồi chọn "Kịch bản 1 - Rò rỉ chân không", Áp dụng, Phân tích nguyên nhân. Hạng 1 là rò chân không 0,86; giải thích công thức MYCIN 0,45 + 0,75 × (1 − 0,45).
4. Chọn kịch bản 3, Phân tích: nguyên nhân đúng chỉ đứng hạng 3. Nói thẳng đây là hạn chế (8 PID không phân biệt được).
5. **Xóa mã lỗi** (có hộp xác nhận), rồi sang **Lịch sử** mở chi tiết phiên vừa làm và xuất phiếu chẩn đoán.
6. **Bảo dưỡng**: chỉ khối nhắc bảo dưỡng, thêm một lần bảo dưỡng.
7. **Tra cứu DTC**: mở P0171, chỉ phần giải nghĩa mã và các nguyên nhân lấy từ bộ luật.

## Lỗi thường gặp
| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `candump vcan0` báo "No such device" | Máy ảo vừa khởi động lại, mất vcan0 | `bash setup_vcan.sh` |
| Chấm đỏ "mất kết nối" trên ứng dụng | Gateway chưa chạy hoặc sai IP | `bash chay-demo.sh`; xem IP bằng `ip a`, sửa `gateway.host` |
| Không ping được máy ảo | Card mạng VMware tắt hoặc đổi IP | Kiểm tra VMnet8 trên Windows và chế độ NAT của máy ảo |
| Ứng dụng báo không đọc được CSDL | SQL Server tắt hoặc sai mật khẩu | Khởi động dịch vụ; kiểm tra `config.properties` |
| Đồng hồ đứng yên ở 0 | ECU bị dừng | `bash chay-demo.sh` (chạy lại cả hai) |
| Máy ảo hỏng hẳn | | Đổi `source=mock` trong `config.properties` để chạy dữ liệu giả, hoặc chiếu video dự phòng |
