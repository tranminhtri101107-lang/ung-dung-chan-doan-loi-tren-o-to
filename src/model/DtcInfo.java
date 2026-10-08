package model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Thông tin đầy đủ của một mã lỗi cho trang chi tiết ở tab Tra cứu DTC: mô tả gốc và tiếng Việt, nguồn đối chiếu,
 * các luật có điều kiện là mã này (nguyên nhân, điểm, điều kiện PID thêm, gợi ý kiểm tra) và số lần gặp trong xưởng.
 */
public record DtcInfo(Dtc dtc, String original, String source, List<RuleRow> rules, int vehicles, int sessions,
        LocalDateTime lastSeen) {

    /** Một luật liên quan: mã luật, nguyên nhân, điểm, các điều kiện PID kèm theo (đã viết thành chữ), gợi ý. */
    public record RuleRow(int ruleId, String cause, double score, String extra, String hint) {
    }
}
