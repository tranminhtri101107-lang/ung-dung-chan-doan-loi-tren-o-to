package service;

import java.util.List;

/**
 * Danh mục kịch bản lỗi mà ECU mô phỏng hỗ trợ (cùng thứ tự với bảng kịch bản trong vm/ecu_engine.cpp).
 * trueCause là nguyên nhân đúng (nhãn chuẩn, đúng bằng TenNguyenNhan trong CSDL) dùng khi đo độ chính xác;
 * kịch bản 0 là xe khỏe nên không có nguyên nhân. Mô tả từng kịch bản: docs/giao-thuc/KICH-BAN.md.
 */
public final class ScenarioCatalog {

    public record Scenario(int id, String name, String trueCause) {
        /** Chữ hiển thị trong hộp chọn. */
        public String label() {
            return id + " - " + name;
        }
    }

    public static final List<Scenario> ALL = List.of(
            new Scenario(0, "Xe khỏe", null),
            new Scenario(1, "Rò rỉ chân không", "Rò rỉ chân không hoặc khí lọt vào đường nạp"),
            new Scenario(2, "Cảm biến MAF bẩn", "Cảm biến MAF sai lệch (bẩn hoặc hỏng)"),
            new Scenario(3, "Bơm xăng yếu", "Áp suất nhiên liệu bất thường (bơm yếu, lọc tắc, bộ điều áp)"),
            new Scenario(4, "Bugi mòn", "Bugi mòn"),
            new Scenario(5, "Quạt làm mát hỏng", "Quạt làm mát không hoạt động"),
            new Scenario(6, "Cảm biến nhiệt độ nước ngắn mạch", "Cảm biến nhiệt độ nước làm mát hoặc mạch tín hiệu hỏng"),
            new Scenario(7, "Mất tín hiệu tốc độ xe", "Mất tín hiệu tốc độ xe (cảm biến, dây dẫn hoặc liên lạc ABS)"),
            new Scenario(8, "Máy phát hỏng", "Hệ thống sạc hỏng (máy phát, bộ điều áp, dây đai)"));

    private ScenarioCatalog() {
    }
}
