#!/bin/bash
# Khởi động ECU giả lập và Gateway ở chế độ nền để demo.
# Chạy trong thư mục chứa ecu_engine.cpp, gateway.cpp:  bash chay-demo.sh
# Dừng bằng:  bash dung-demo.sh
cd "$(dirname "$0")" || exit 1
mkdir -p logs

# 1. Bus CAN ảo phải có sẵn (mất mỗi khi máy ảo khởi động lại)
if ! ip link show vcan0 >/dev/null 2>&1; then
    echo "[LỖI] Chưa có vcan0. Chạy:  bash setup_vcan.sh  (cần mật khẩu sudo), rồi chạy lại script này."
    exit 1
fi
echo "[ĐẠT] vcan0 đã sẵn sàng"

# 2. Biên dịch lại nếu chưa có file chạy hoặc mã nguồn mới hơn
for prog in ecu_engine gateway; do
    if [ ! -x "$prog" ] || [ "$prog.cpp" -nt "$prog" ]; then
        echo "Biên dịch $prog ..."
        g++ -std=c++17 -Wall -O2 -o "$prog" "$prog.cpp" || { echo "[LỖI] Biên dịch $prog thất bại"; exit 1; }
    fi
done

# 3. Dừng bản cũ (nếu còn chạy) rồi chạy ở nền, ghi log
pkill -x ecu_engine 2>/dev/null
pkill -x gateway 2>/dev/null
sleep 0.5
setsid nohup ./ecu_engine vcan0 > logs/ecu.log 2>&1 < /dev/null &
sleep 0.5
setsid nohup ./gateway vcan0 5000 > logs/gateway.log 2>&1 < /dev/null &
sleep 1

# 4. Kiểm tra
ok=1
pgrep -x ecu_engine >/dev/null && echo "[ĐẠT] ECU đang chạy (PID $(pgrep -x ecu_engine))" || { echo "[LỖI] ECU không chạy, xem logs/ecu.log"; ok=0; }
pgrep -x gateway >/dev/null && echo "[ĐẠT] Gateway đang chạy (PID $(pgrep -x gateway)), cổng 5000" || { echo "[LỖI] Gateway không chạy, xem logs/gateway.log"; ok=0; }
ip -4 addr show | grep -o 'inet [0-9.]*' | grep -v 127.0.0.1 | sed 's/inet /Địa chỉ máy ảo: /'
[ $ok = 1 ] && echo "Sẵn sàng. Trên Windows chạy tools\\kiem-tra-moi-truong.ps1 rồi run.bat."
