# Chu kỳ bảo dưỡng dùng cho chức năng "Nhắc bảo dưỡng"

Ứng dụng tính hạn bảo dưỡng tiếp theo của từng hạng mục: **hạn = lần làm gần nhất + chu kỳ**, theo km hoặc theo tháng, mốc nào đến trước thì tính trước. Kỹ thuật viên sửa được chu kỳ ngay trong ứng dụng vì mỗi hãng, mỗi đời xe và mỗi điều kiện sử dụng có khuyến cáo khác nhau.

## Nguồn
- **T1** Toyota Việt Nam, "Dịch vụ bảo dưỡng", https://www.toyota.com.vn/dich-vu/dich-vu-bao-duong: các cấp bảo dưỡng định kỳ 5.000 km hoặc 6 tháng, 10.000 km hoặc 12 tháng, 20.000 km hoặc 24 tháng, 40.000 km hoặc 48 tháng, "tùy điều kiện nào đến trước". Truy cập 07/10/2026.
- **T2** Toyota Việt Nam, "4 mốc bảo dưỡng xe ô tô", https://www.toyota.com.vn/tin-tuc/thong-tin-bo-tro/bao-duong-xe-o-to-39113: hạng mục ở từng cấp (thay dầu ở cấp 5.000 km; thay bugi ở cấp 20.000 km; thay lọc nhiên liệu, dầu phanh, dầu hộp số ở cấp 40.000 km). Truy cập 07/10/2026.

## Bảng chu kỳ

| Hạng mục | Chu kỳ km | Chu kỳ tháng | Nguồn | Ghi chú |
|---|---|---|---|---|
| Thay dầu động cơ | 5.000 | 6 | T1, T2 (cấp nhỏ) | |
| Thay lọc dầu | 10.000 | 12 | T1 (cấp trung) | Có nguồn ghi thay cùng lần thay dầu |
| Thay bugi | 20.000 | 24 | T2 (cấp trung bình lớn) | Bugi iridium thường dài hơn nhiều; sửa theo xe |
| Thay lọc nhiên liệu | 40.000 | 48 | T2 (cấp lớn) | |
| Thay dầu phanh | 40.000 | 48 | T2 (cấp lớn) | |
| Thay dầu hộp số | 40.000 | 48 | T2 (cấp lớn) | |

Không đưa vào bảng các hạng mục mà nguồn không ghi chu kỳ rõ ràng (lọc gió động cơ, nước làm mát, ắc quy). Các hạng mục này vẫn nhập được ở tab Bảo dưỡng như bình thường, chỉ không có nhắc hạn cho tới khi kỹ thuật viên tự đặt chu kỳ.

## Nạp vào CSDL
`sql/07_seed_chu_ky_bao_duong.sql` (chạy lại nhiều lần không lỗi, không ghi đè chu kỳ đã sửa trong ứng dụng).
