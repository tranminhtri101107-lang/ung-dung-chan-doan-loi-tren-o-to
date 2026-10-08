# Hướng dẫn chạy

## Cài đặt (một lần)
- Windows: JDK 17 trở lên, SQL Server Express (`.\SQLEXPRESS`), VMware có máy ảo Ubuntu (cài `can-utils`, `g++`).
- CSDL `ChanDoanXe`: chạy lần lượt `sql/01` đến `sql/07` bằng SSMS.
- Chép `config.example.properties` thành `config.properties`, điền mật khẩu CSDL; `source=gateway` đọc từ máy ảo, `source=mock` dùng dữ liệu giả (không cần máy ảo); `gateway.host` là IP máy ảo.
- Chép `vm/ecu_engine.cpp`, `vm/gateway.cpp`, `vm/setup_vcan.sh`, `vm/chay-demo.sh`, `vm/dung-demo.sh` vào một thư mục trên máy ảo (xem `vm/README.md`).

## Thứ tự bật (khoảng 3 phút)
1. **SQL Server**: thường tự chạy cùng Windows. Nếu chưa chạy, mở `services.msc` và khởi động "SQL Server (SQLEXPRESS)".
2. **Máy ảo**: bật Ubuntu trong VMware, mở terminal, `cd` vào thư mục chứa `ecu_engine.cpp` và `gateway.cpp`.
3. **Bus CAN ảo**: `bash setup_vcan.sh` (cần mật khẩu sudo; phải chạy lại mỗi lần máy ảo khởi động).
4. **ECU và Gateway**: `bash chay-demo.sh`. Script tự biên dịch khi cần, chạy cả hai ở nền và in [ĐẠT]/[LỖI].
5. Muốn xem khung CAN thì mở thêm một terminal chạy `candump vcan0`.
6. **Kiểm tra từ Windows**: `powershell -ExecutionPolicy Bypass -File tools\kiem-tra-moi-truong.ps1` phải in "TẤT CẢ ĐẠT".
7. **Mở ứng dụng**: chạy `run.bat` ở thư mục gốc dự án. Cuối thanh bên hiện chấm xanh và nguồn Gateway.

Tắt sau khi xong: `bash dung-demo.sh` trong máy ảo.

## Lỗi thường gặp
| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `candump vcan0` báo "No such device" | Máy ảo vừa khởi động lại, mất vcan0 | `bash setup_vcan.sh` |
| Chấm đỏ "mất kết nối" trên ứng dụng | Gateway chưa chạy hoặc sai IP | `bash chay-demo.sh`; xem IP bằng `ip a`, sửa `gateway.host` |
| Không ping được máy ảo | Card mạng VMware tắt hoặc đổi IP | Kiểm tra VMnet8 trên Windows và chế độ NAT của máy ảo |
| Ứng dụng báo không đọc được CSDL | SQL Server tắt hoặc sai mật khẩu | Khởi động dịch vụ; kiểm tra `config.properties` |
| Đồng hồ đứng yên ở 0 | ECU bị dừng | `bash chay-demo.sh` (chạy lại cả hai) |
| Máy ảo hỏng hẳn | | Đổi `source=mock` trong `config.properties` để chạy dữ liệu giả |
