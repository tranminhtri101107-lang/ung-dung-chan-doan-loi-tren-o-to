package service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import model.MaintenanceInterval;
import model.MaintenanceRecord;

/**
 * Tính nhắc bảo dưỡng và thống kê chi phí (thuần logic, không phụ thuộc giao diện hay CSDL).
 * Hạn tiếp theo = lần làm gần nhất của hạng mục + chu kỳ (km và/hoặc tháng), mốc nào đến trước thì tính trước.
 */
public final class MaintenanceSchedule {

    /** Còn ≤ 10 % chu kỳ km hoặc ≤ 30 ngày thì coi là "sắp đến". */
    public static final double SOON_KM_RATIO = 0.10;
    public static final int SOON_DAYS = 30;

    public enum Status {
        OVERDUE("Đến hạn"), SOON("Sắp đến"), OK("Còn xa"), NO_DATA("Chưa có dữ liệu");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Nhắc cho một hạng mục: lần gần nhất (có thể null), hạn km và ngày, phần còn lại, trạng thái. */
    public record Reminder(MaintenanceInterval interval, MaintenanceRecord last, Integer dueKm, LocalDate dueDate,
            Integer kmLeft, Long daysLeft, Status status) {
    }

    /**
     * Tính nhắc cho mọi hạng mục có chu kỳ. currentKm là số km hiện tại của xe (null nếu chưa biết; khi đó dùng
     * số km lớn nhất từng ghi trong hồ sơ bảo dưỡng). Kết quả xếp theo mức khẩn: đến hạn, sắp đến, còn xa, chưa có dữ liệu.
     */
    public static List<Reminder> reminders(List<MaintenanceInterval> intervals, List<MaintenanceRecord> records,
            Integer currentKm, LocalDate today) {
        Integer km = currentKm != null ? currentKm
                : records.stream().map(MaintenanceRecord::km).filter(k -> k != null).max(Integer::compare).orElse(null);
        List<Reminder> out = new ArrayList<>();
        for (MaintenanceInterval iv : intervals) {
            MaintenanceRecord last = records.stream()
                    .filter(r -> sameItem(r.item(), iv.item()))
                    .max(Comparator.comparing(MaintenanceRecord::date)
                            .thenComparing(r -> r.km() == null ? -1 : r.km()))
                    .orElse(null);
            if (last == null) {
                out.add(new Reminder(iv, null, null, null, null, null, Status.NO_DATA));
                continue;
            }
            Integer dueKm = iv.km() != null && last.km() != null ? last.km() + iv.km() : null;
            LocalDate dueDate = iv.months() != null ? last.date().plusMonths(iv.months()) : null;
            Integer kmLeft = dueKm != null && km != null ? dueKm - km : null;
            Long daysLeft = dueDate != null ? ChronoUnit.DAYS.between(today, dueDate) : null;
            out.add(new Reminder(iv, last, dueKm, dueDate, kmLeft, daysLeft, status(iv, kmLeft, daysLeft)));
        }
        out.sort(Comparator.comparing(Reminder::status)
                .thenComparing(r -> r.daysLeft() == null ? Long.MAX_VALUE : r.daysLeft()));
        return out;
    }

    static Status status(MaintenanceInterval iv, Integer kmLeft, Long daysLeft) {
        if ((kmLeft != null && kmLeft <= 0) || (daysLeft != null && daysLeft <= 0)) {
            return Status.OVERDUE;
        }
        boolean soonKm = kmLeft != null && iv.km() != null && kmLeft <= iv.km() * SOON_KM_RATIO;
        boolean soonDay = daysLeft != null && daysLeft <= SOON_DAYS;
        if (soonKm || soonDay) {
            return Status.SOON;
        }
        if (kmLeft == null && daysLeft == null) {
            return Status.NO_DATA;   // có lần làm nhưng thiếu số km nên chưa tính được
        }
        return Status.OK;
    }

    /** So khớp hạng mục không phân biệt hoa thường và khoảng trắng thừa. */
    public static boolean sameItem(String a, String b) {
        return a != null && b != null
                && a.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT)
                        .equals(b.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
    }

    /**
     * Số km lớn nhất đã ghi ở các lần bảo dưỡng không muộn hơn ngày cho trước (bỏ qua bản ghi đang sửa).
     * Dùng để cảnh báo khi nhập số km nhỏ hơn lần trước (đồng hồ công-tơ-mét không chạy lùi).
     */
    public static Integer maxKmUntil(List<MaintenanceRecord> records, LocalDate date, int excludeId) {
        return records.stream()
                .filter(r -> r.id() != excludeId && r.km() != null && !r.date().isAfter(date))
                .map(MaintenanceRecord::km).max(Integer::compare).orElse(null);
    }

    /** Tổng chi phí theo hạng mục (năm = null là mọi năm), xếp giảm dần. */
    public static Map<String, Long> costByItem(List<MaintenanceRecord> records, Integer year) {
        Map<String, Long> m = new LinkedHashMap<>();
        records.stream().filter(r -> year == null || r.date().getYear() == year)
                .forEach(r -> m.merge(r.item(), r.cost() == null ? 0L : r.cost(), Long::sum));
        Map<String, Long> sorted = new LinkedHashMap<>();
        m.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    public static long totalCost(List<MaintenanceRecord> records, Integer year) {
        return records.stream().filter(r -> year == null || r.date().getYear() == year)
                .mapToLong(r -> r.cost() == null ? 0L : r.cost()).sum();
    }

    private MaintenanceSchedule() {
    }
}
