package service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import model.Dtc;
import model.HistoryEntry;
import model.SessionDetail;
import model.Vehicle;

/**
 * Tạo phiếu chẩn đoán (UC09) dạng HTML in được: thông tin xe, mã lỗi, ảnh chụp PID, nguyên nhân khả dĩ
 * và nhật ký của một phiên. Mở bằng trình duyệt rồi "In → Lưu dưới dạng PDF" nếu cần, không cần thư viện ngoài.
 */
public final class ReportExporter {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public static String html(Vehicle v, SessionDetail d, LocalDateTime printedAt) {
        StringBuilder b = new StringBuilder(4096);
        b.append("<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">")
                .append("<title>Phiếu chẩn đoán ").append(esc(v.plate())).append("</title><style>")
                .append("body{font-family:'Segoe UI',Arial,sans-serif;color:#1c1c1c;margin:32px auto;max-width:820px;font-size:14px}")
                .append("header{display:flex;justify-content:space-between;align-items:flex-end;border-bottom:3px solid #e8590c;padding-bottom:10px}")
                .append("h1{font-size:22px;margin:0}h2{font-size:13px;text-transform:uppercase;letter-spacing:.06em;color:#5f5b55;margin:22px 0 6px}")
                .append(".plate{border:2px solid #1c1c1c;border-radius:4px;padding:4px 12px;font:700 20px Bahnschrift,'Segoe UI',sans-serif}")
                .append("table{border-collapse:collapse;width:100%}th,td{text-align:left;padding:6px 8px;border-bottom:1px solid #e7e4de;vertical-align:top}")
                .append("th{font-size:11px;text-transform:uppercase;color:#5f5b55;background:#faf9f6;border-bottom:2px solid #d6d3cc}")
                .append(".code{font-family:Consolas,monospace;font-weight:700}.num{text-align:right;font-family:Consolas,monospace}")
                .append(".meta td{border:none;padding:2px 12px 2px 0}.meta td:first-child{color:#5f5b55;width:150px}")
                .append(".note{margin-top:26px;font-size:12px;color:#5f5b55;border-top:1px solid #d6d3cc;padding-top:8px}")
                .append(".sign{display:flex;justify-content:space-between;margin-top:40px;text-align:center}.sign div{width:40%}")
                .append("@media print{body{margin:0}}</style></head><body>");

        b.append("<header><div><div style=\"font:700 13px 'Segoe UI';color:#e8590c\">AUTODIAG · XƯỞNG DỊCH VỤ</div>")
                .append("<h1>Phiếu chẩn đoán lỗi</h1></div><div class=\"plate\">").append(esc(v.plate())).append("</div></header>");

        b.append("<h2>Thông tin xe và phiên</h2><table class=\"meta\">");
        row(b, "Xe", v.name().isEmpty() ? "-" : v.name() + (v.year() == null ? "" : " (" + v.year() + ")"));
        row(b, "Số VIN", v.vin());
        row(b, "Số km", v.odometerKm() == null ? null : String.format(Locale.of("vi", "VN"), "%,d km", v.odometerKm()));
        row(b, "Chủ xe", v.owner() == null ? null : v.owner() + (v.phone() == null ? "" : " · " + v.phone()));
        row(b, "Phiên chẩn đoán", "Số " + d.info().id() + ", bắt đầu " + DT.format(d.info().start()));
        row(b, "Ngày in phiếu", DT.format(printedAt));
        b.append("</table>");

        b.append("<h2>Mã lỗi đọc được (OBD-II Mode 03)</h2>");
        if (d.dtcs().isEmpty()) {
            b.append("<p>Không có mã lỗi.</p>");
        } else {
            b.append("<table><tr><th>Mã</th><th>Mô tả</th><th>Mức độ</th></tr>");
            for (Dtc x : d.dtcs()) {
                b.append("<tr><td class=\"code\">").append(esc(x.code())).append("</td><td>")
                        .append(esc(x.description() == null ? "Mã chưa có trong danh mục" : x.description()))
                        .append("</td><td>").append(esc(x.severity() == null ? "-" : x.severity().label())).append("</td></tr>");
            }
            b.append("</table>");
        }

        if (!d.pids().isEmpty()) {
            b.append("<h2>Dữ liệu sống lúc phân tích (OBD-II Mode 01)</h2><table><tr><th>PID</th><th>Tham số</th>")
                    .append("<th class=\"num\">Giá trị</th><th>Đơn vị</th></tr>");
            for (Map.Entry<Integer, Double> e : d.pids().entrySet()) {
                b.append("<tr><td class=\"code\">").append(String.format("0x%02X", e.getKey())).append("</td><td>")
                        .append(esc(PidDecoder.name(e.getKey()))).append("</td><td class=\"num\">")
                        .append(String.format(Locale.of("vi", "VN"), "%.2f", e.getValue())).append("</td><td>")
                        .append(esc(PidDecoder.unit(e.getKey()))).append("</td></tr>");
            }
            b.append("</table>");
        }

        if (!d.causes().isEmpty()) {
            b.append("<h2>Nguyên nhân khả dĩ (bộ suy luận dựa trên luật)</h2><table><tr><th>Hạng</th><th>Nguyên nhân</th>")
                    .append("<th class=\"num\">Độ tin cậy</th><th>Gợi ý kiểm tra</th></tr>");
            for (SessionDetail.RankedCause c : d.causes()) {
                b.append("<tr><td>").append(c.rank()).append("</td><td>").append(esc(c.name())).append("</td><td class=\"num\">")
                        .append(String.format(Locale.of("vi", "VN"), "%.2f", c.score())).append("</td><td>")
                        .append(esc(c.hint())).append("</td></tr>");
            }
            b.append("</table>");
        }

        b.append("<h2>Nhật ký thao tác</h2><table><tr><th>Thời gian</th><th>Thao tác</th><th>Chi tiết</th></tr>");
        for (HistoryEntry e : d.log()) {
            b.append("<tr><td>").append(DT.format(e.time())).append("</td><td>").append(esc(e.action()))
                    .append("</td><td>").append(esc(e.detail())).append("</td></tr>");
        }
        b.append("</table>");

        b.append("<div class=\"sign\"><div>Kỹ thuật viên<br><br><br>(ký, ghi rõ họ tên)</div>")
                .append("<div>Khách hàng<br><br><br>(ký, ghi rõ họ tên)</div></div>")
                .append("<p class=\"note\">Độ tin cậy là điểm xếp hạng của bộ luật, không phải xác suất. Nguyên nhân khả dĩ chỉ là gợi ý ")
                .append("để kỹ thuật viên kiểm tra, cần xác nhận bằng kiểm tra thực tế. Dữ liệu sinh từ ECU mô phỏng ")
                .append("(Đồ án Cơ sở 2, môi trường CAN bus ảo).</p></body></html>");
        return b.toString();
    }

    private static void row(StringBuilder b, String k, String v) {
        b.append("<tr><td>").append(esc(k)).append("</td><td>").append(esc(v == null ? "-" : v)).append("</td></tr>");
    }

    /** Thoát ký tự HTML để chuỗi nhập từ người dùng không phá cấu trúc phiếu. */
    static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Tên tệp gợi ý, ví dụ PhieuChanDoan_51A-12345_20261005_0912.html. */
    public static String fileName(Vehicle v, SessionDetail d) {
        return "PhieuChanDoan_" + v.plate().replaceAll("[^0-9A-Za-z-]", "") + "_"
                + DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").format(d.info().start()) + ".html";
    }

    private ReportExporter() {
    }
}
