# Ứng dụng chẩn đoán lỗi trên ô tô trên nền mạng CAN bus mô phỏng

Phần mềm hỗ trợ kỹ thuật viên chẩn đoán lỗi ô tô qua OBD-II trong môi trường mô phỏng: đọc dữ liệu sống và mã lỗi (DTC) từ một ECU giả lập, gợi ý nguyên nhân khả dĩ bằng bộ luật có độ tin cậy, quản lý hồ sơ xe, lịch sử chẩn đoán và lịch sử bảo dưỡng.

Tác giả: Trần Minh Trí.

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

```
├─ run.bat                    biên dịch và chạy ứng dụng (chạy từ thư mục gốc)
├─ config.example.properties  mẫu cấu hình: chép thành config.properties rồi điền mật khẩu
├─ src/                       ứng dụng Java (MVC)
│  ├─ app/                    điểm khởi động (Main), đọc cấu hình (AppConfig)
│  ├─ view/                   giao diện Swing và thành phần tự vẽ
│  ├─ controller/             điều phối giữa giao diện và dữ liệu
│  ├─ service/                nguồn dữ liệu (Gateway/giả), giải mã PID, bộ suy luận, logic thuần
│  ├─ dao/                    truy vấn SQL Server (PreparedStatement)
│  └─ model/                  lớp dữ liệu (record)
├─ lib/                       FlatLaf 3.4, Microsoft JDBC Driver 12.2
├─ sql/                       script CSDL, chạy theo thứ tự 01 đến 07
├─ vm/                        ECU mô phỏng và Gateway (C++17, Ubuntu), script bật/tắt; archive-isotp/ là bản có ISO-TP
├─ tests/                     chương trình kiểm thử và chay-kiem-thu.ps1
├─ thuc-nghiem/               chạy thực nghiệm, phân tích MATLAB, số liệu (du-lieu/) và biểu đồ (ket-qua/)
├─ tools/                     sinh-luat.ps1 (nguồn duy nhất của bộ luật), kiem-tra-moi-truong.ps1
├─ lab-can-bus/               các node CAN đầu tiên (phanh, động cơ, nhiệt độ, logger) và số liệu đo độ trễ
└─ docs/                      tài liệu (mục lục ở docs/README.md)
```

## Chạy thử

1. **Cơ sở dữ liệu** (SQL Server Express, instance `.\SQLEXPRESS`): chạy lần lượt các script trong `sql/` bằng SSMS. Script 03 tạo tài khoản `chandoan_app`; nên đổi mật khẩu trong script trước khi chạy.
2. **Cấu hình**: chép `config.example.properties` thành `config.properties`, điền mật khẩu. `source=mock` dùng dữ liệu giả (không cần máy ảo); `source=gateway` đọc từ Gateway thật.
3. **Máy ảo** (khi dùng `source=gateway`): xem `vm/README.md`.
4. **Kiểm tra môi trường**: `powershell -ExecutionPolicy Bypass -File tools\kiem-tra-moi-truong.ps1`; thứ tự bật đầy đủ ở `docs/HUONG-DAN-CHAY.md`.
5. **Ứng dụng**: cần JDK 17 trở lên. Sửa đường dẫn `JDK` trong `run.bat` cho đúng máy rồi chạy `run.bat`, hoặc mở bằng VS Code và bấm F5.

## Kiểm thử và thực nghiệm

- `powershell -ExecutionPolicy Bypass -File tests\chay-kiem-thu.ps1` chạy kiểm thử bộ suy luận, bảo dưỡng và các quy tắc nghiệp vụ; thêm `-CoGateway` để kiểm thử đầu-cuối với máy ảo.
- `powershell -ExecutionPolicy Bypass -File thuc-nghiem\chay-thuc-nghiem.ps1` chạy 30 lần mỗi kịch bản rồi phân tích bằng MATLAB.

Kết quả 270 lần chạy: nguyên nhân đúng ở hạng 1 là 85,0 % (204/240 lần của 8 kịch bản lỗi), so với 50,0 % khi chỉ dùng mã lỗi và 50,4 % khi đoán ngẫu nhiên trong các nguyên nhân ứng viên; đọc mã lỗi mất trung bình khoảng 1,6 ms (độ trễ phần mềm). Kết quả chỉ có ý nghĩa trong môi trường mô phỏng: bộ luật và kịch bản do cùng một tác giả soạn.

## Giới hạn

`vcan` chỉ mô phỏng ở mức khung (không có điện áp, tốc độ bit, phân xử bit); một ECU, 12 DTC, 8 PID; danh mục DTC/PID đối chiếu với nguồn thứ cấp, không có bản gốc SAE J1979/J2012.

---
Phiên bản đầu tiên được thực hiện trong khuôn khổ học phần Đồ án Cơ sở 2, Trường ĐH Công nghệ Thông tin và Truyền thông Việt - Hàn (VKU), 2026.
