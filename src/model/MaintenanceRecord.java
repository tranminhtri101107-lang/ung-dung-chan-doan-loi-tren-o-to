package model;

import java.time.LocalDate;

/**
 * Một lần bảo dưỡng của một xe (UC11). id = 0 khi chưa lưu vào CSDL;
 * km và cost (đồng) có thể null; item là hạng mục bắt buộc.
 */
public record MaintenanceRecord(int id, int vehicleId, LocalDate date, Integer km, String item, Long cost,
        String note) {
}
