package model;

import java.util.Map;
import java.util.Set;

/**
 * Một điều kiện của luật (bảng DieuKienLuat). Có hai loại:
 * - theo mã lỗi: dtc khác null, đúng khi mã đó đang được lưu trong ECU;
 * - theo PID: pid, op, threshold khác null, đúng khi giá trị vật lý của PID thỏa phép so sánh với ngưỡng.
 * PID chưa có dữ liệu thì điều kiện coi như sai (không đủ chứng cứ).
 */
public record Condition(String dtc, Integer pid, String op, Double threshold) {

    public static Condition ofDtc(String code) {
        return new Condition(code, null, null, null);
    }

    public static Condition ofPid(int pid, String op, double threshold) {
        return new Condition(null, pid, op, threshold);
    }

    public boolean holds(Set<String> storedDtcs, Map<Integer, Double> pidValues) {
        if (dtc != null) {
            return storedDtcs.contains(dtc);
        }
        Double v = pidValues.get(pid);
        if (v == null || v.isNaN()) {
            return false;
        }
        return switch (op) {
            case ">" -> v > threshold;
            case ">=" -> v >= threshold;
            case "<" -> v < threshold;
            case "<=" -> v <= threshold;
            case "=" -> v.doubleValue() == threshold;
            case "<>" -> v.doubleValue() != threshold;
            default -> false;
        };
    }
}
