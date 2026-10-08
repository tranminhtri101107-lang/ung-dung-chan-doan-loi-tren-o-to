# Kịch bản lỗi thực nghiệm (tiêm lỗi vào ECU mô phỏng)

Cập nhật 06/10/2026. Bảng kịch bản nằm trong `vm/ecu_engine.cpp` (hàm `scenarios()`); danh sách tên và nguyên nhân đúng (nhãn chuẩn) nằm trong `src/service/ScenarioCatalog.java`. Hai nơi phải khớp nhau.

## Cách tiêm lỗi
- Ứng dụng (hoặc chương trình thực nghiệm) gửi `INJECT k` tới Gateway qua TCP; Gateway gửi khung CAN `0x6F0` dữ liệu `[02 01 k]` (ngoài phạm vi OBD-II, chỉ dùng cho mô phỏng); ECU chọn kịch bản k, đặt lại danh sách DTC và độ lệch dữ liệu sống, rồi xác nhận bằng khung `0x6F8` dữ liệu `[03 01 k trạng_thái]` (0 = đã áp dụng, 1 = kịch bản không có). Gateway trả `RSP INJECT OK k` hoặc `ERR BAD_SCENARIO`.
- Mỗi kịch bản có **tối đa 2 DTC** (do giới hạn một khung đơn của Mode 03). Các hiện tượng còn lại thể hiện qua dữ liệu sống.
- ECU cộng nhiễu ngẫu nhiên vào mọi PID (bước ngẫu nhiên của vòng tua, bướm ga, lưu lượng MAF, điện áp...), nên giá trị đọc được khác nhau giữa các lần chạy.

## Danh sách kịch bản

| Mã | Tên | Mã lỗi | Dữ liệu sống bị lệch | Nguyên nhân đúng (nhãn chuẩn) |
|---|---|---|---|---|
| 0 | Xe khỏe | không | không (bướm ga 5-60 %) | không có lỗi |
| 1 | Rò rỉ chân không | P0171 | cầm chừng (bướm ga 5-9 %), vòng tua cao hơn bình thường khoảng 500 | Rò rỉ chân không hoặc khí lọt vào đường nạp |
| 2 | Cảm biến MAF bẩn | P0171 | cầm chừng, lưu lượng MAF bằng 55 % bình thường | Cảm biến MAF sai lệch (bẩn hoặc hỏng) |
| 3 | Bơm xăng yếu | P0171 | không lệch (có tải, bướm ga 20-50 %) | Áp suất nhiên liệu bất thường (bơm yếu, lọc tắc, bộ điều áp) |
| 4 | Bugi mòn | P0300, P0301 | không lệch | Bugi mòn |
| 5 | Quạt làm mát hỏng | P0217 | xe đứng yên, nhiệt độ nước cao hơn bình thường 22 °C (khoảng 110 °C) | Quạt làm mát không hoạt động |
| 6 | Cảm biến nhiệt độ nước ngắn mạch | P0117 | nhiệt độ nước luôn đọc 215 °C | Cảm biến nhiệt độ nước làm mát hoặc mạch tín hiệu hỏng |
| 7 | Mất tín hiệu tốc độ xe | P0500 | đang chạy (bướm ga 45-70 %) nhưng tốc độ đọc 0 | Mất tín hiệu tốc độ xe (cảm biến, dây dẫn hoặc liên lạc ABS) |
| 8 | Máy phát hỏng | P0562 | động cơ nổ (bướm ga 22-50 %) nhưng điện áp chỉ khoảng 11,2 V | Hệ thống sạc hỏng (máy phát, bộ điều áp, dây đai) |

## Thiết kế có chủ đích
- **Có các cặp khó phân biệt thật:** kịch bản 3 (bơm xăng yếu) cho cùng mã lỗi P0171 với kịch bản 1 và 2 nhưng không có dấu hiệu PID đặc trưng, nên nguyên nhân đúng chỉ có thể xếp hạng 3 (rò chân không và MAF được luật xếp trên vì thường gặp hơn). Đây là giới hạn thật của hệ thống chỉ có 8 PID.
- **Kịch bản 4 có hai mã lỗi** (P0300 và P0301), thử việc gộp độ tin cậy của nhiều luật.
- **Kịch bản 0 (xe khỏe)** kiểm tra không có cảnh báo sai.

## Giới hạn phải nêu trong báo cáo (tính trung thực của thực nghiệm)
- Bộ luật và các kịch bản do **cùng một tác giả** soạn, kịch bản được dựng theo đúng dấu hiệu mà luật tìm; vì vậy độ chính xác đo được **chỉ chứng minh hệ thống chạy đúng như thiết kế** trong môi trường mô phỏng, không chứng minh khả năng chẩn đoán xe thật.
- Ngưỡng PID trong luật do tác giả chọn theo mô hình mô phỏng, không phải thông số của hãng xe.
- 9 kịch bản (gồm xe khỏe) không bao phủ hết 17 nguyên nhân và 12 mã lỗi: P0113, P0118, P0172, P0335, P0420 có luật nhưng không có kịch bản thực nghiệm (có kiểm thử đơn vị riêng ở `KiemThuSuyLuan.java`).
- Số đo thời gian là độ trễ phần mềm (TCP, Gateway, `vcan`), không phải độ trễ của bus CAN thật.
