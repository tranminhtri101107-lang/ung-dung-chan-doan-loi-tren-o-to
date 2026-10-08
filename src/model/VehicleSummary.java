package model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tóm tắt hồ sơ một xe cho thẻ xe: số phiên chẩn đoán, lần gần nhất và các mã lỗi đọc được lần đó,
 * số lần bảo dưỡng và ngày bảo dưỡng gần nhất. Các mốc thời gian là null nếu chưa có.
 */
public record VehicleSummary(int sessions, LocalDateTime lastSession, List<String> lastDtcs,
        int maintenances, LocalDate lastMaintenance) {

    public static final VehicleSummary EMPTY = new VehicleSummary(0, null, List.of(), 0, null);
}
