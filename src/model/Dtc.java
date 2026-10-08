package model;

/**
 * Một mã lỗi chẩn đoán (Diagnostic Trouble Code), ví dụ P0300.
 * description, severity, group có thể null khi mã chưa được tra nguồn (verified = false).
 */
public record Dtc(String code, String description, Severity severity, String group, boolean verified) {

    /** Dùng cho dữ liệu giả: chưa có nhóm, coi như chưa xác minh. */
    public Dtc(String code, String description, Severity severity) {
        this(code, description, severity, null, false);
    }
}
