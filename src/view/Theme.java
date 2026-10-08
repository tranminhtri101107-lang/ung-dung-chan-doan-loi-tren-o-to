package view;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;

/**
 * Bảng màu và phông chữ dùng chung, phong cách "máy chẩn đoán xưởng":
 * nền trắng ngà, thanh bên than chì, màu nhấn cam tín hiệu, góc vuông, viền mảnh.
 */
public final class Theme {

    // Nền và bề mặt
    public static final Color BG = new Color(0xF4F3EF);          // trắng ngà
    public static final Color CARD = new Color(0xFFFFFF);        // bề mặt khung nội dung
    public static final Color CARD_ALT = new Color(0xFAF9F6);    // hàng chẵn, vùng phụ
    public static final Color HOVER = new Color(0xFDF1EA);       // hàng đang rê chuột
    public static final Color SELECT = new Color(0xFBE3D3);      // hàng đang chọn
    public static final Color BORDER = new Color(0xD6D3CC);
    public static final Color RULE = new Color(0xE7E4DE);        // đường kẻ dòng mảnh

    // Chữ
    public static final Color TEXT = new Color(0x1C1C1C);
    public static final Color TEXT_DIM = new Color(0x5F5B55);
    public static final Color TEXT_FAINT = new Color(0x8C877F);

    // Thanh bên than chì
    public static final Color SIDEBAR = new Color(0x23252A);
    public static final Color SIDEBAR_HOVER = new Color(0x2E3137);
    public static final Color SIDEBAR_TEXT = new Color(0xC9C7C2);
    public static final Color SIDEBAR_DIM = new Color(0x7D7B77);

    // Màu nhấn và mức độ
    public static final Color ACCENT = new Color(0xE8590C);      // cam tín hiệu
    public static final Color ACCENT_DARK = new Color(0xC2470A);
    public static final Color DANGER = new Color(0xB42318);      // nghiêm trọng
    public static final Color WARN = new Color(0xB7791F);        // cảnh báo (hổ phách)
    public static final Color INFO = new Color(0x2F5D8A);        // thông tin (xanh thép)
    public static final Color OK = new Color(0x2F7D4F);          // tốt (xanh rêu)

    // Phông: Segoe UI cho mọi chữ tiếng Việt; Bahnschrift (kiểu DIN, có sẵn trên Windows 10/11) chỉ dùng cho
    // số liệu, biển số, mã lỗi vì Bahnschrift thiếu dấu tiếng Việt khi viết hoa. Lùi về Segoe UI nếu máy thiếu.
    private static final String DISPLAY = has("Bahnschrift") ? "Bahnschrift" : "Segoe UI";
    public static final Font FONT = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font CAPS = new Font("Segoe UI", Font.BOLD, 11);    // nhãn in hoa nhỏ
    public static final Font TITLE = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font HEADING = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font NUMBER = new Font(DISPLAY, Font.BOLD, 26);     // số liệu lớn
    public static final Font MONO = new Font("Consolas", Font.PLAIN, 13);   // mã lỗi, khung CAN
    public static final Font MONO_BOLD = new Font("Consolas", Font.BOLD, 13);

    /** Phông kiểu DIN cho số và chữ Latin không dấu (số đo, biển số, mã lỗi). */
    public static Font display(int style, float size) {
        return new Font(DISPLAY, style, Math.round(size));
    }

    private static boolean has(String family) {
        return Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                .contains(family);
    }

    private Theme() {
    }
}
