# Thư mục `vm/` — mã C++ chạy trong máy ảo Ubuntu

Các file ở đây **không chạy trên Windows**. Chúng dùng SocketCAN của Linux. Soạn thảo trên Windows, chép sang máy ảo, biên dịch và chạy ở đó.

## Chép file sang máy ảo
Mở **PowerShell** trên Windows (không phải trong VM), chạy lệnh sau. Máy ảo sẽ hỏi mật khẩu tài khoản `tranminhtri`:

```powershell
scp "D:\Ung_dung_chan_doan_loi_tren_o_to\vm\ecu_engine.cpp" tranminhtri@192.168.35.128:~/he-thong-mo-phong-mang-CAN-Bus/
```

(Hoặc mở thư mục đó bằng VS Code Remote-SSH rồi kéo thả file vào.)

## Bước 1: kiểm thử ECU giả lập (`ecu_engine`)
Cần **3 cửa sổ terminal** trong máy ảo, đều ở thư mục `~/he-thong-mo-phong-mang-CAN-Bus`.

**Terminal 1** — chuẩn bị bus ảo, biên dịch và chạy ECU:
```bash
./setup_vcan.sh
g++ -std=c++17 -Wall -o ecu_engine ecu_engine.cpp
./ecu_engine vcan0
```
Phải thấy dòng `ECU giả lập chạy trên vcan0, 2 DTC...`. Nếu g++ báo lỗi, chép nguyên thông báo gửi lại.

**Terminal 2** — theo dõi mọi khung trên bus:
```bash
candump vcan0
```

**Terminal 3** — đóng vai máy chẩn đoán, gửi từng yêu cầu rồi so kết quả ở Terminal 2:

| Lệnh | Ý nghĩa | Kỳ vọng thấy ở `candump` |
|---|---|---|
| `cansend vcan0 7DF#02010C` | Mode 01, PID 0x0C (vòng tua) | `7E8 [8] 04 41 0C xx xx ...` (xx xx = vòng tua × 4; ví dụ 1500 vòng/phút là `17 70`) |
| `cansend vcan0 7DF#020105` | Mode 01, PID 0x05 (nhiệt độ nước) | `7E8 [8] 03 41 05 xx ...` (xx ≈ 0x7D = 125, tức 85 °C) |
| `cansend vcan0 7DF#0201FF` | PID không hỗ trợ | Không có phản hồi (ECU in "không hỗ trợ") |
| `cansend vcan0 7DF#0104` | Mode 04, xóa DTC | `7E8 [8] 01 44 ...` |
| `cansend vcan0 7DF#0103` | Mode 03, đọc DTC | Với 2 DTC mặc định: `7E8 [8] 06 43 02 03 00 01 71 00` (một khung đơn) |

Kiểm tra giải mã DTC: `03 00` = P0300, `01 71` = P0171.

Mỗi lần đọc chỉ trả tối đa 2 DTC (quyết định 06/10/2026, không dùng ISO-TP). Chạy ECU với quá 2 DTC sẽ bị từ chối.

Tiêm lỗi (kịch bản 0 đến 8, xem `docs/KICH-BAN.md`): `cansend vcan0 6F0#0201xx` (xx = số kịch bản dạng hex, ví dụ `6F0#020103` là kịch bản 3); ECU trả xác nhận ở `6F8`. Qua Gateway thì gõ `INJECT 3` ở cổng 5000.

Chạy ECU với DTC khác: `./ecu_engine vcan0 P0217 P0118`.

Bước 1 đã kiểm thử đạt (05/10/2026) với bản có ISO-TP; bản không ISO-TP kiểm thử lại ngày 06/10/2026 (xem CLAUDE.md). Bản ISO-TP cũ lưu ở `archive-isotp/` (không biên dịch).

## Bước 2: kiểm thử Gateway (`gateway`)
Chép cả hai file sang máy ảo (PowerShell trên Windows; thay tên thư mục nếu bạn đặt khác):

```powershell
scp "D:\Ung_dung_chan_doan_loi_tren_o_to\vm\gateway.cpp" tranminhtri@192.168.35.128:~/Ung_dung_chan_doan_loi_tren_o_to/
```

Trong máy ảo cần 3 cửa sổ, đều `cd ~/Ung_dung_chan_doan_loi_tren_o_to`. Bus `vcan0` phải còn (kiểm tra `ip link show vcan0`; mất thì `bash setup_vcan.sh`).

| Cửa sổ | Lệnh |
|---|---|
| **T1** (ECU) | `./ecu_engine vcan0` |
| **T2** (Gateway) | `g++ -std=c++17 -Wall -o gateway gateway.cpp` rồi `./gateway vcan0 5000` |
| **T3** (đóng vai ứng dụng Java) | `nc localhost 5000` |

Ở T3, ngay khi nối được phải thấy dòng `LIVE ...` chạy liên tục, ví dụ `LIVE 0C 1A44 1728...`, `LIVE 05 7D ...`. Rồi gõ từng lệnh (kết thúc bằng Enter) và so kết quả:

| Gõ ở T3 | Kỳ vọng |
|---|---|
| `PING` | `PONG` |
| `REQ 03` | `RSP 03 P0300 P0171` |
| `REQ 04` | `RSP 04 OK` |
| `REQ 03` (lần nữa) | `RSP 03` (không còn DTC) |
| `abc` | `ERR BAD_COMMAND` |

Dòng `LIVE` vẫn tiếp tục chạy xen giữa các câu trả lời, đó là bình thường. Thoát `nc` bằng Ctrl+C.

Kiểm tra mạng **từ Windows** (PowerShell trên Windows, Gateway đang chạy):

```powershell
Test-NetConnection 192.168.35.128 -Port 5000
```

Phải thấy `TcpTestSucceeded : True`. Nếu `False`, kiểm tra tường lửa của Ubuntu (`sudo ufw status`; nếu `active` thì cần `sudo ufw allow 5000/tcp`).

Đọc thử vài dòng LIVE từ Windows:

```powershell
$c = New-Object Net.Sockets.TcpClient('192.168.35.128',5000); $r = New-Object IO.StreamReader($c.GetStream()); 1..5 | ForEach-Object { $r.ReadLine() }; $c.Close()
```

Bước 2 đã kiểm thử đạt (05/10/2026), kể cả đọc dữ liệu và gửi `REQ 03` / `REQ 04` từ Windows.

## Bước 3: dùng Gateway trong ứng dụng Java (đã làm)
1. Trên máy ảo, chạy ECU và Gateway (hai cửa sổ, hoặc nền): `./ecu_engine vcan0` và `./gateway vcan0 5000`.
2. Trong `config.properties` (Windows) đặt `source=gateway`, `gateway.host=192.168.35.128`, `gateway.port=5000`. Muốn quay lại dữ liệu giả thì đặt `source=mock`.
3. Chạy ứng dụng (`run.bat`). Góc trên bên phải hiện chấm xanh và "Nguồn: Gateway ...:5000"; mất kết nối thì chấm đỏ kèm chữ "(mất kết nối)" và app tự nối lại.
