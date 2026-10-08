package model;

/** Mức độ nghiêm trọng của một mã lỗi (DTC). */
public enum Severity {
    INFO("Thông tin"),
    WARNING("Cảnh báo"),
    CRITICAL("Nghiêm trọng");

    private final String label;

    Severity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
