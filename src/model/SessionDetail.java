package model;

import java.util.List;
import java.util.Map;

/**
 * Chi tiết một phiên chẩn đoán: mã lỗi kèm mô tả (mã chưa có trong danh mục thì description = null),
 * ảnh chụp giá trị PID lúc phân tích, các nguyên nhân đã xếp hạng và nhật ký thao tác.
 */
public record SessionDetail(SessionInfo info, List<Dtc> dtcs, Map<Integer, Double> pids, List<RankedCause> causes,
        List<HistoryEntry> log) {

    /** Một dòng kết quả phân tích đã lưu (bảng KetQuaPhanTich). */
    public record RankedCause(int rank, String name, double score, String hint) {
    }
}
