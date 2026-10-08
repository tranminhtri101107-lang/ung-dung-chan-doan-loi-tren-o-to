package service;

/**
 * Đổi byte thô của PID (Mode 01) sang giá trị vật lý. A và B là byte dữ liệu thứ nhất và thứ hai.
 * Công thức lấy từ docs/du-lieu/BANG-DTC-PID.xlsx (đang ở trạng thái "cần xác minh" với SAE J1979);
 * khớp với công thức mà ECU giả lập dùng để mã hóa.
 */
public final class PidDecoder {

    public static final int PID_LOAD = 0x04;
    public static final int PID_COOLANT = 0x05;
    public static final int PID_RPM = 0x0C;
    public static final int PID_SPEED = 0x0D;
    public static final int PID_INTAKE_TEMP = 0x0F;
    public static final int PID_MAF = 0x10;
    public static final int PID_THROTTLE = 0x11;
    public static final int PID_VOLTAGE = 0x42;

    private PidDecoder() {
    }

    /** Tên tham số tiếng Việt của PID (dùng cho bảng PID và phiếu chẩn đoán). */
    public static String name(int pid) {
        return switch (pid) {
            case PID_LOAD -> "Tải động cơ tính toán";
            case PID_COOLANT -> "Nhiệt độ nước làm mát";
            case PID_RPM -> "Tốc độ quay động cơ";
            case PID_SPEED -> "Tốc độ xe";
            case PID_INTAKE_TEMP -> "Nhiệt độ khí nạp";
            case PID_MAF -> "Lưu lượng khí nạp (MAF)";
            case PID_THROTTLE -> "Vị trí bướm ga";
            case PID_VOLTAGE -> "Điện áp mô-đun điều khiển";
            default -> String.format("PID 0x%02X", pid);
        };
    }

    /** Đơn vị của PID. */
    public static String unit(int pid) {
        return switch (pid) {
            case PID_LOAD, PID_THROTTLE -> "%";
            case PID_COOLANT, PID_INTAKE_TEMP -> "°C";
            case PID_RPM -> "vòng/phút";
            case PID_SPEED -> "km/h";
            case PID_MAF -> "g/s";
            case PID_VOLTAGE -> "V";
            default -> "";
        };
    }

    /** Trả về giá trị vật lý, hoặc NaN nếu PID chưa hỗ trợ hay thiếu byte. */
    public static double decode(int pid, int[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Double.NaN;
        }
        int a = bytes[0];
        int b = bytes.length > 1 ? bytes[1] : 0;
        int ab = 256 * a + b;
        return switch (pid) {
            case PID_LOAD, PID_THROTTLE -> a * 100.0 / 255.0;  // %
            case PID_COOLANT, PID_INTAKE_TEMP -> a - 40;        // °C
            case PID_RPM -> ab / 4.0;                           // vòng/phút
            case PID_SPEED -> a;                                // km/h
            case PID_MAF -> ab / 100.0;                         // g/s
            case PID_VOLTAGE -> ab / 1000.0;                    // V
            default -> Double.NaN;
        };
    }
}
