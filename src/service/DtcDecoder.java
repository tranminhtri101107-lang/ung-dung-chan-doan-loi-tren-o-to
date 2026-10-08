package service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Giải nghĩa từng ký tự của mã lỗi DTC 5 ký tự (ví dụ P0171) theo cấu trúc của SAE J2012.
 * Ý nghĩa chữ số thứ ba (nhóm hệ thống con) chỉ áp dụng cho mã truyền động (P) chuẩn; nguồn đối chiếu:
 * Motive "What are DTCs" (gomotive.com/blog/what-does-dtc-mean) và Azuga "How do you read an OBD-II DTC"
 * (fleet-azuga.helpscoutdocs.com/article/829), truy cập 07/10/2026.
 */
public final class DtcDecoder {

    /** Một ký tự của mã và ý nghĩa của nó. */
    public record Part(String symbol, String role, String meaning) {
    }

    public static List<Part> decode(String code) {
        List<Part> parts = new ArrayList<>();
        if (code == null || code.trim().length() != 5) {
            return parts;
        }
        String c = code.trim().toUpperCase(Locale.ROOT);
        parts.add(new Part(c.substring(0, 1), "Hệ thống", switch (c.charAt(0)) {
            case 'P' -> "Truyền động (động cơ, hộp số)";
            case 'C' -> "Khung gầm";
            case 'B' -> "Thân xe";
            case 'U' -> "Mạng truyền thông";
            default -> "Không xác định";
        }));
        parts.add(new Part(c.substring(1, 2), "Loại mã", switch (c.charAt(1)) {
            case '0' -> "Mã chuẩn (SAE)";
            case '1' -> "Mã riêng của nhà sản xuất";
            case '2', '3' -> "Dành riêng, tùy hệ thống";
            default -> "Không xác định";
        }));
        String sub = c.charAt(0) == 'P' && (c.charAt(1) == '0') ? switch (c.charAt(2)) {
            case '1', '2' -> "Đo lường nhiên liệu và khí nạp";
            case '3' -> "Hệ thống đánh lửa, bỏ máy";
            case '4' -> "Kiểm soát khí thải phụ";
            case '5' -> "Điều khiển tốc độ xe và cầm chừng";
            case '6' -> "Mạch máy tính và tín hiệu ra";
            case '7', '8' -> "Hộp số";
            default -> "Nhóm hệ thống con";
        } : "Nhóm hệ thống con (theo quy định của hệ thống)";
        parts.add(new Part(c.substring(2, 3), "Nhóm", sub));
        parts.add(new Part(c.substring(3, 5), "Lỗi cụ thể", "Số thứ tự lỗi trong nhóm"));
        return parts;
    }

    private DtcDecoder() {
    }
}
