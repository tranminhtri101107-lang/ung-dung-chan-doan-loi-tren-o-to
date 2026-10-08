package service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quy tắc kiểm tra và chuẩn hóa thông tin xe (không phụ thuộc giao diện hay CSDL nên kiểm thử độc lập được).
 * Biển số ô tô Việt Nam: 2 số mã tỉnh + 1-2 chữ seri + gạch + 4 số (biển cũ) hoặc 5 số dạng 123.45.
 */
public final class VehicleRules {

    // Biển số sau khi bỏ khoảng trắng, gạch, chấm: 2 số + 1-2 chữ cái + 4-5 số (ví dụ 30F25658, 51LD12345)
    private static final Pattern PLATE_RAW = Pattern.compile("^(\\d{2})([A-ZĐ]{1,2})(\\d{4,5})$");
    // VIN theo ISO 3779: 17 ký tự, chữ số và chữ in hoa trừ I, O, Q
    private static final Pattern VIN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");
    // Số điện thoại Việt Nam: 10 số, bắt đầu bằng 0
    private static final Pattern PHONE = Pattern.compile("^0\\d{9}$");

    /**
     * Chuẩn hóa biển số về dạng hiển thị chuẩn: "30f 25658" -> "30F-256.58", "29a1234" -> "29A-1234".
     * Trả null nếu không đúng định dạng biển số ô tô.
     */
    public static String normalizePlate(String input) {
        if (input == null) {
            return null;
        }
        String raw = input.toUpperCase(Locale.ROOT).replaceAll("[\\s.\\-]", "");
        Matcher m = PLATE_RAW.matcher(raw);
        if (!m.matches()) {
            return null;
        }
        String digits = m.group(3);
        String tail = digits.length() == 5 ? digits.substring(0, 3) + "." + digits.substring(3) : digits;
        return m.group(1) + m.group(2) + "-" + tail;
    }

    /** VIN đã viết hoa, bỏ khoảng trắng; trả null nếu rỗng. */
    public static String cleanVin(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return input.toUpperCase(Locale.ROOT).replaceAll("\\s", "");
    }

    public static boolean validVin(String vin) {
        return vin != null && VIN.matcher(vin).matches();
    }

    /** Số điện thoại đã bỏ khoảng trắng, chấm, gạch; đổi +84 thành 0; trả null nếu rỗng. */
    public static String cleanPhone(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String p = input.replaceAll("[\\s.\\-]", "");
        if (p.startsWith("+84")) {
            p = "0" + p.substring(3);
        }
        return p;
    }

    public static boolean validPhone(String phone) {
        return phone != null && PHONE.matcher(phone).matches();
    }

    private VehicleRules() {
    }
}
