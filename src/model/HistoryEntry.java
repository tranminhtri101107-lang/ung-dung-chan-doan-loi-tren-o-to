package model;

import java.time.LocalDateTime;

/** Một dòng lịch sử thao tác chẩn đoán. Sau này sẽ lưu xuống SQL Server. */
public record HistoryEntry(LocalDateTime time, String action, String detail) {
}
