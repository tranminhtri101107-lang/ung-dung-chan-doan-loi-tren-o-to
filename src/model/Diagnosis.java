package model;

import java.util.List;

/** Một nguyên nhân khả dĩ đã xếp hạng: độ tin cậy sau khi gộp các luật khớp và danh sách luật đã khớp. */
public record Diagnosis(Cause cause, double confidence, List<Rule> firedRules) {
}
