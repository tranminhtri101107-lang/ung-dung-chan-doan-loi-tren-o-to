# Ứng dụng chẩn đoán lỗi trên ô tô trên nền mạng CAN bus mô phỏng

Đồ án Cơ sở 2 (HK1 2026-2027), Trần Minh Trí (25AS055), Trường ĐH Công nghệ Thông tin và Truyền thông Việt - Hàn. GVHD: ThS. Phan Thị Quỳnh Hương.

Phần mềm hỗ trợ kỹ thuật viên chẩn đoán lỗi ô tô qua OBD-II trong môi trường mô phỏng: đọc dữ liệu sống và mã lỗi (DTC) từ một ECU giả lập, gợi ý nguyên nhân khả dĩ bằng bộ luật có độ tin cậy, quản lý hồ sơ xe, lịch sử chẩn đoán và lịch sử bảo dưỡng.

## Kiến trúc

```
ECU động cơ mô phỏng (C++)  <-- vcan0 (SocketCAN) -->  Gateway (C++)  <-- TCP 5000 -->  Ứng dụng Java Swing  <-- JDBC -->  SQL Server
            máy ảo Ubuntu                                                                         Windows
```

- OBD-II: yêu cầu 0x7DF, phản hồi 0x7E8; Mode 01 (8 PID), 03 (đọc tối đa 2 DTC, không dùng ISO-TP), 04 (xóa DTC).
- Tiêm lỗi: 9 kịch bản (0 là xe khỏe), khung điều khiển 0x6F0 / xác nhận 0x6F8, lệnh Gateway `INJECT k`.
- Bộ suy luận: 17 nguyên nhân, 34 luật IF-THEN lưu trong CSDL, gộp độ tin cậy theo MYCIN.
- Giao diện kiểu máy chẩn đoán xưởng (sáng, đọc rõ trên máy chiếu), tự vẽ bằng Java2D: đồng hồ có kim, biểu đồ, icon, hiệu ứng; không thư viện ngoài ngoài FlatLaf.
- Quản lý xe (biển số chuẩn hóa, VIN, số km, chủ xe), lịch sử theo phiên và xuất phiếu chẩn đoán HTML, nhắc bảo dưỡng theo chu kỳ (Toyota Việt Nam), tra cứu DTC kèm giải nghĩa mã và nguyên nhân từ bộ luật.

## Cấu trúc thư mục

| Thư mục | Nội dung |
|---|---|
| `src/` | Ứng dụng Java (MVC): `app`, `view`, `controller`, `service` (DataSource, InferenceEngine, PidDecoder), `dao`, `model` |
| `lib/` | Thư viện FlatLaf 3.4 và Microsoft JDBC Driver 12.2 |
| `sql/` | Script SQL Server chạy theo thứ tự 01 đến 07 (tạo bảng, dữ liệu DTC/PID, tài khoản ứng dụng, mô tả DTC/PID, luật, đối chiếu nguồn, chu kỳ bảo dưỡng) |
| `vm/` | ECU mô phỏng và Gateway (C++17, chạy trên Ubuntu), `setup_vcan.sh`, `chay-demo.sh` / `dung-demo.sh`; hướng dẫn ở `vm/README.md` |
| `matlab/` | Phân tích kết quả thực nghiệm (khoảng tin cậy Wilson, biểu đồ) |
| `docs/` | Tài liệu kỹ thuật: giao thức (`protocol.md`), kịch bản lỗi, bộ luật và nguồn, ERD, use-case, đối chiếu nguồn DTC/PID |
| `lab-can-bus/` | Mã các bài lab CAN bus trước đây (node phanh, động cơ, nhiệt độ, logger) và số liệu đo độ trễ |

## Chạy thử

1. **Cơ sở dữ liệu** (SQL Server Express, instance `.\SQLEXPRESS`): chạy lần lượt các script trong `sql/` bằng SSMS. Script 03 tạo tài khoản `chandoan_app`; nên đổi mật khẩu trong script trước khi chạy.
2. **Cấu hình**: chép `config.example.properties` thành `config.properties`, điền mật khẩu. `source=mock` dùng dữ liệu giả (không cần máy ảo); `source=gateway` đọc từ Gateway thật.
3. **Máy ảo** (khi dùng `source=gateway`): xem `vm/README.md` (`setup_vcan.sh`, biên dịch bằng `g++ -std=c++17`, chạy `./ecu_engine vcan0` và `./gateway vcan0 5000`).
4. **Kiểm tra trước khi chạy**: `kiem-tra-demo.ps1` (SQL Server, CSDL, máy ảo, Gateway); thứ tự bật đầy đủ ở `docs/HUONG-DAN-CHAY-DEMO.md`.
5. **Ứng dụng**: cần JDK 17 trở lên. Sửa đường dẫn `JDK` trong `run.bat` cho đúng máy rồi chạy `run.bat`, hoặc mở bằng VS Code và bấm F5.

## Kết quả thực nghiệm (270 lần chạy)

Nguyên nhân đúng ở hạng 1: 85,0 % (204/240 lần của 8 kịch bản lỗi), so với 50,0 % khi chỉ dùng mã lỗi và 50,4 % khi đoán ngẫu nhiên trong các nguyên nhân ứng viên; đọc mã lỗi mất trung bình khoảng 1,6 ms (độ trễ phần mềm). Kết quả chỉ có ý nghĩa trong môi trường mô phỏng: bộ luật và kịch bản do cùng một tác giả soạn.

## Giới hạn

`vcan` chỉ mô phỏng ở mức khung (không có điện áp, tốc độ bit, phân xử bit); một ECU, 12 DTC, 8 PID; danh mục DTC/PID đối chiếu với nguồn thứ cấp, không có bản gốc SAE J1979/J2012.
