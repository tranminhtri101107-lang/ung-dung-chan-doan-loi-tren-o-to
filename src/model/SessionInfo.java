package model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tóm tắt một phiên chẩn đoán cho danh sách ở tab Lịch sử: thời gian, các mã lỗi đã đọc,
 * nguyên nhân hạng 1 (nếu đã phân tích) và các loại thao tác trong phiên.
 */
public record SessionInfo(int id, LocalDateTime start, List<String> dtcs, String topCause, Double topScore,
        List<String> actions) {
}
