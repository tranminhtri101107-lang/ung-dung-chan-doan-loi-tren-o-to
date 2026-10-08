package model;

/**
 * Chu kỳ bảo dưỡng của một hạng mục (bảng ChuKyBaoDuong): sau km hoặc sau số tháng, mốc nào đến trước.
 * km hoặc months có thể null (nhưng không đồng thời); source ghi nguồn tham khảo.
 */
public record MaintenanceInterval(int id, String item, Integer km, Integer months, String source) {
}
