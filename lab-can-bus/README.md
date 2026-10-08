# Bài lab CAN bus (môn An toàn và an ninh trên ô tô)

Mã các bài thực hành trước khi làm đồ án, chạy trên `vcan0` (tạo bằng `../vm/setup_vcan.sh`):

- `brake_node.cpp`, `brake_node_auto.cpp`: node phanh (thủ công / tự động).
- `engine_node.cpp`, `temp_node.cpp`: node động cơ và nhiệt độ (lưu lượng nền).
- `logger.cpp`: ghi khung CAN ra `can_log.csv`.
- `phan_tich_can_log.m`: phân tích độ trễ bằng MATLAB, vẽ `bieu_do_so_sanh_do_tre.png`.

Biên dịch: `g++ -std=c++17 -o <tên> <tên>.cpp`. Kết quả đo: phanh gấp trung bình 166,12 µs (σ 46,60), phanh thường 188,99 µs (σ 145,03, có một giá trị ngoại lai khoảng 1550 µs).
