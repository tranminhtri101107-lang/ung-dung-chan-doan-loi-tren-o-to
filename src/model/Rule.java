package model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Luật IF-THEN: NẾU mọi điều kiện đều đúng THÌ nguyên nhân causeId khả dĩ với độ tin cậy confidence (0 đến 1).
 * description là cơ sở của luật, source là mã nguồn tài liệu (xem docs/LUAT-CHAN-DOAN.md).
 */
public record Rule(int id, int causeId, double confidence, String description, String source,
        List<Condition> conditions) {

    public boolean fires(Set<String> storedDtcs, Map<Integer, Double> pidValues) {
        return !conditions.isEmpty() && conditions.stream().allMatch(c -> c.holds(storedDtcs, pidValues));
    }
}
