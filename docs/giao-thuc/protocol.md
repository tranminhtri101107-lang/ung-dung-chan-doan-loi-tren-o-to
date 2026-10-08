# Giao thức (bản nháp)

Có hai tầng giao thức: tầng CAN giữa Gateway và ECU, tầng TCP giữa ứng dụng Java và Gateway.

```
Java (Windows) --TCP, văn bản--> Gateway (VM) --CAN OBD-II--> ECU giả lập (VM, vcan0)
```

## 1. Tầng CAN: OBD-II (Gateway ⇄ ECU)

ID chuẩn 11 bit. Mỗi khung CAN dài 8 byte. Byte đầu là độ dài phần dữ liệu còn lại.

| Việc | ID | Dữ liệu |
|---|---|---|
| Tester gửi yêu cầu (quảng bá) | `0x7DF` | `[số_byte, mode, tham_số...]` |
| ECU trả lời | `0x7E8` | `[số_byte, mode + 0x40, ...]` |
| Tester gửi yêu cầu riêng cho ECU số 1 | `0x7E0` | như yêu cầu quảng bá (hiện chưa dùng) |

| Mode | Yêu cầu | Phản hồi |
|---|---|---|
| 01 (dữ liệu sống) | `02 01 PID` | `(2+n) 41 PID A [B]` |
| 03 (đọc DTC) | `01 03` | `43 N` + 2 byte cho mỗi DTC |
| 04 (xóa DTC) | `01 04` | `01 44` |

### Mã hóa DTC (2 byte)
Bit 15-14: hệ (P=0, C=1, B=2, U=3). Bit 13-12: chữ số thứ hai (0-3). Ba ký tự cuối: mỗi ký tự 4 bit. Ví dụ `P0300` là `03 00`, `P0171` là `01 71`.

### Tiêm lỗi (ngoài OBD-II, chỉ cho mô phỏng)
| Việc | ID | Dữ liệu |
|---|---|---|
| Gateway chọn kịch bản k cho ECU | `0x6F0` | `[02 01 k]` |
| ECU xác nhận | `0x6F8` | `[03 01 k trạng_thái]` (0 = đã áp dụng, 1 = kịch bản không có) |

Kịch bản 0 là xe khỏe, 1 đến 8 là các kịch bản lỗi (docs/giao-thuc/KICH-BAN.md). Mỗi kịch bản đặt lại danh sách DTC (tối đa 2) và độ lệch dữ liệu sống.

### Giới hạn số DTC (không dùng ISO-TP)
Phản hồi Mode 03 dài `2 + 2N` byte (N là số DTC). Một khung CAN chứa tối đa 7 byte dữ liệu (byte đầu là độ dài), nên **mỗi lần đọc chỉ trả tối đa 2 DTC** (6 byte), luôn nằm gọn trong một khung đơn. ECU từ chối khởi động nếu được cấu hình quá 2 DTC. Quyết định 06/10/2026: không cài ISO-TP (First/Consecutive Frame, Flow Control); bản có ISO-TP đã kiểm thử đạt được lưu ở `vm/archive-isotp/`.

### PID hỗ trợ và công thức (cần xác minh với SAE J1979)
Xem `BANG-DTC-PID.xlsx`. Công thức: `0x04` = 100/255·A (%), `0x05` = A−40 (°C), `0x0C` = (256A+B)/4 (RPM), `0x0D` = A (km/h), `0x0F` = A−40 (°C), `0x10` = (256A+B)/100 (g/s), `0x11` = 100/255·A (%), `0x42` = (256A+B)/1000 (V).

## 2. Tầng TCP: Java ⇄ Gateway (đã viết `vm/gateway.cpp`, chưa chạy thử)

Văn bản, mỗi dòng một thông điệp, kết thúc bằng `\n`. Gateway lắng nghe cổng 5000, mỗi lúc phục vụ một client (client mới thay client cũ). Gateway **chỉ hỏi ECU khi có client** kết nối. Kiểm tra bằng `nc localhost 5000` trong máy ảo.

| Chiều | Thông điệp | Ý nghĩa |
|---|---|---|
| Gateway → Java | `LIVE <pid_hex> <byte_thô_hex> <timestamp_us>` | Gateway hỏi lần lượt 8 PID (cách nhau 50 ms) và đẩy giá trị mới. Ví dụ `LIVE 0C 1A44 1728130000123456`. `timestamp_us` là giờ của máy ảo (micro giây từ 1970) |
| Java → Gateway | `REQ 03` | Đọc DTC |
| Gateway → Java | `RSP 03 P0300 P0171 ...` | Danh sách DTC (tối đa 2) giải mã thành chữ. Không có DTC thì chỉ `RSP 03` |
| Java → Gateway | `REQ 04` | Xóa DTC |
| Gateway → Java | `RSP 04 OK` | ECU xác nhận |
| Java → Gateway | `PING` | Kiểm tra sống |
| Gateway → Java | `PONG` | Trả lời PING |
| Java → Gateway | `INJECT <k>` | Chọn kịch bản lỗi k (0 đến 8) trong ECU mô phỏng |
| Gateway → Java | `RSP INJECT OK <k>` / `ERR BAD_SCENARIO` / `ERR TIMEOUT INJECT` | ECU đã áp dụng / không có kịch bản đó / ECU không xác nhận trong 1 giây |
| Gateway → Java | `ERR TIMEOUT 03` / `ERR TIMEOUT 04` | ECU không trả lời trong 2 giây |
| Gateway → Java | `ERR NEGATIVE <mode> <mã>` | ECU trả phản hồi âm (7F) |
| Gateway → Java | `ERR BAD_RESPONSE 03` | Phản hồi Mode 03 sai độ dài |
| Gateway → Java | `ERR BAD_COMMAND` / `ERR BUSY` | Lệnh lạ / hàng đợi lệnh đầy (tối đa 8) |

PID không hỗ trợ hoặc ECU không trả lời trong 300 ms thì Gateway im lặng bỏ qua (chỉ ghi log), không báo lỗi cho client.

Giải mã giá trị thô sang đơn vị kỹ thuật làm ở phía Java (theo công thức PID), Gateway chỉ chuyển tiếp byte.

Về thiết kế Gateway: dùng **một luồng với `select()`** chờ đồng thời socket CAN và socket TCP, mỗi lúc chỉ một yêu cầu CAN đang chờ phản hồi. Cách này tránh khóa (`mutex`) và lỗi tranh chấp dữ liệu giữa luồng; trade-off là mã tuần tự, không song song.
