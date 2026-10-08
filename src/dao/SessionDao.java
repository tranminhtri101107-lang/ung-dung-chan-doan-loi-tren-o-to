package dao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import model.Diagnosis;
import model.Dtc;
import model.HistoryEntry;
import model.SessionDetail;
import model.SessionInfo;

/** Truy cập các bảng PhienChanDoan, PhienDTC, NhatKyThaoTac (UC05, UC06, UC08). */
public class SessionDao {

    private final Database db;

    public SessionDao(Database db) {
        this.db = db;
    }

    /** Tên thao tác hiển thị trên tab Lịch sử, tương ứng cột LoaiThaoTac. */
    public static String actionLabel(String type) {
        return switch (type) {
            case "KET_NOI" -> "Kết nối";
            case "DOC_DTC" -> "Đọc DTC";
            case "XOA_DTC" -> "Xóa DTC";
            case "XEM_PID" -> "Xem dữ liệu sống";
            case "PHAN_TICH" -> "Phân tích";
            default -> type;
        };
    }

    /** Mở một phiên chẩn đoán mới cho xe, trả về mã phiên. */
    public int createSession(int vehicleId) throws SQLException {
        try (Connection c = db.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dbo.PhienChanDoan (MaXe) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, vehicleId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Không lấy được mã phiên mới");
                }
                return keys.getInt(1);
            }
        }
    }

    /** Ghi các DTC đọc được vào phiên (bỏ qua mã đã ghi) và nhật ký thao tác, trong một giao dịch. */
    public void record(int sessionId, String type, String detail, List<String> dtcCodes) throws SQLException {
        String insertDtc = "IF NOT EXISTS (SELECT 1 FROM dbo.PhienDTC WHERE MaPhien = ? AND MaDTC = ?) "
                + "INSERT INTO dbo.PhienDTC (MaPhien, MaDTC) VALUES (?, ?)";
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(insertDtc)) {
                    for (String code : dtcCodes) {
                        ps.setInt(1, sessionId);
                        ps.setString(2, code);
                        ps.setInt(3, sessionId);
                        ps.setString(4, code);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dbo.NhatKyThaoTac (MaPhien, LoaiThaoTac, ChiTiet) VALUES (?, ?, ?)")) {
                    ps.setInt(1, sessionId);
                    ps.setString(2, type);
                    ps.setString(3, detail);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE dbo.PhienChanDoan SET ThoiGianKetThuc = SYSDATETIME() WHERE MaPhien = ?")) {
                    ps.setInt(1, sessionId);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * Ghi một lần phân tích nguyên nhân (UC07) vào phiên, trong một giao dịch: mã lỗi đã đọc (PhienDTC),
     * ảnh chụp giá trị PID (MauPID), danh sách nguyên nhân kèm thứ hạng (KetQuaPhanTich) và dòng nhật ký PHAN_TICH.
     * Bảng KetQuaPhanTich cấm trùng (phiên, nguyên nhân) và (phiên, thứ hạng), nên mỗi lần phân tích phải dùng phiên mới.
     */
    public void saveAnalysis(int sessionId, List<String> dtcCodes, Map<Integer, Double> pids,
            List<Diagnosis> diagnoses, String detail) throws SQLException {
        String insertDtc = "IF NOT EXISTS (SELECT 1 FROM dbo.PhienDTC WHERE MaPhien = ? AND MaDTC = ?) "
                + "INSERT INTO dbo.PhienDTC (MaPhien, MaDTC) VALUES (?, ?)";
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(insertDtc)) {
                    for (String code : dtcCodes) {
                        ps.setInt(1, sessionId);
                        ps.setString(2, code);
                        ps.setInt(3, sessionId);
                        ps.setString(4, code);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dbo.MauPID (MaPhien, MaPID, GiaTriThuc) VALUES (?, ?, ?)")) {
                    for (Map.Entry<Integer, Double> e : pids.entrySet()) {
                        ps.setInt(1, sessionId);
                        ps.setInt(2, e.getKey());
                        ps.setBigDecimal(3, BigDecimal.valueOf(e.getValue()).setScale(4, RoundingMode.HALF_UP));
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dbo.KetQuaPhanTich (MaPhien, MaNN, DiemTinCay, ThuHang) VALUES (?, ?, ?, ?)")) {
                    int rank = 1;
                    for (Diagnosis d : diagnoses) {
                        ps.setInt(1, sessionId);
                        ps.setInt(2, d.cause().id());
                        ps.setBigDecimal(3, BigDecimal.valueOf(d.confidence()).setScale(3, RoundingMode.HALF_UP));
                        ps.setInt(4, rank++);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dbo.NhatKyThaoTac (MaPhien, LoaiThaoTac, ChiTiet) VALUES (?, 'PHAN_TICH', ?)")) {
                    ps.setInt(1, sessionId);
                    ps.setString(2, detail);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE dbo.PhienChanDoan SET ThoiGianKetThuc = SYSDATETIME() WHERE MaPhien = ?")) {
                    ps.setInt(1, sessionId);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Toàn bộ thao tác của mọi phiên thuộc một xe, mới nhất ở trên cùng (UC08). */
    public List<HistoryEntry> historyOfVehicle(int vehicleId) throws SQLException {
        String sql = "SELECT n.ThoiGian, n.LoaiThaoTac, n.ChiTiet "
                + "FROM dbo.NhatKyThaoTac n JOIN dbo.PhienChanDoan p ON p.MaPhien = n.MaPhien "
                + "WHERE p.MaXe = ? ORDER BY n.ThoiGian DESC, n.MaNK DESC";
        try (Connection c = db.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            List<HistoryEntry> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new HistoryEntry(rs.getTimestamp("ThoiGian").toLocalDateTime(),
                            actionLabel(rs.getString("LoaiThaoTac")), rs.getString("ChiTiet")));
                }
            }
            return list;
        }
    }

    /**
     * Các phiên chẩn đoán có ít nhất một thao tác của một xe, mới nhất trước, kèm mã lỗi (gộp bằng STRING_AGG),
     * nguyên nhân hạng 1 và các loại thao tác trong phiên.
     */
    public List<SessionInfo> sessionsOfVehicle(int vehicleId) throws SQLException {
        String sql = "SELECT p.MaPhien, p.ThoiGianBatDau, "
                + "(SELECT STRING_AGG(RTRIM(d.MaDTC), ',') WITHIN GROUP (ORDER BY d.MaDTC) FROM dbo.PhienDTC d WHERE d.MaPhien = p.MaPhien) AS DTCs, "
                + "(SELECT TOP 1 n.TenNguyenNhan FROM dbo.KetQuaPhanTich k JOIN dbo.NguyenNhan n ON n.MaNN = k.MaNN "
                + "  WHERE k.MaPhien = p.MaPhien AND k.ThuHang = 1) AS NguyenNhan1, "
                + "(SELECT TOP 1 k.DiemTinCay FROM dbo.KetQuaPhanTich k WHERE k.MaPhien = p.MaPhien AND k.ThuHang = 1) AS Diem1, "
                + "(SELECT STRING_AGG(t.LoaiThaoTac, ',') FROM (SELECT DISTINCT LoaiThaoTac FROM dbo.NhatKyThaoTac "
                + "  WHERE MaPhien = p.MaPhien) t) AS ThaoTac "
                + "FROM dbo.PhienChanDoan p WHERE p.MaXe = ? "
                + "AND EXISTS (SELECT 1 FROM dbo.NhatKyThaoTac n WHERE n.MaPhien = p.MaPhien) "
                + "ORDER BY p.ThoiGianBatDau DESC, p.MaPhien DESC";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            List<SessionInfo> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(info(rs));
                }
            }
            return list;
        }
    }

    /** Chi tiết một phiên: mã lỗi kèm mô tả, ảnh chụp PID, kết quả phân tích có thứ hạng, nhật ký. */
    public SessionDetail detail(SessionInfo info, DtcDao catalog) throws SQLException {
        Map<String, Dtc> known = catalog.findByCodes(info.dtcs());
        List<Dtc> dtcs = new ArrayList<>();
        for (String code : info.dtcs()) {
            dtcs.add(known.getOrDefault(code, new Dtc(code, null, null)));
        }
        try (Connection c = db.getConnection()) {
            Map<Integer, Double> pids = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT MaPID, GiaTriThuc FROM dbo.MauPID WHERE MaPhien = ? ORDER BY MaMau")) {
                ps.setInt(1, info.id());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        pids.put(rs.getInt("MaPID"), rs.getBigDecimal("GiaTriThuc").doubleValue());
                    }
                }
            }
            List<SessionDetail.RankedCause> causes = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT k.ThuHang, n.TenNguyenNhan, k.DiemTinCay, n.GoiYKiemTra FROM dbo.KetQuaPhanTich k "
                            + "JOIN dbo.NguyenNhan n ON n.MaNN = k.MaNN WHERE k.MaPhien = ? ORDER BY k.ThuHang")) {
                ps.setInt(1, info.id());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        causes.add(new SessionDetail.RankedCause(rs.getInt(1), rs.getString(2),
                                rs.getBigDecimal(3).doubleValue(), rs.getString(4)));
                    }
                }
            }
            List<HistoryEntry> log = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ThoiGian, LoaiThaoTac, ChiTiet FROM dbo.NhatKyThaoTac WHERE MaPhien = ? ORDER BY ThoiGian, MaNK")) {
                ps.setInt(1, info.id());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        log.add(new HistoryEntry(rs.getTimestamp(1).toLocalDateTime(), actionLabel(rs.getString(2)),
                                rs.getString(3)));
                    }
                }
            }
            return new SessionDetail(info, dtcs, pids, causes, log);
        }
    }

    private static SessionInfo info(ResultSet rs) throws SQLException {
        String dtcs = rs.getString("DTCs");
        String acts = rs.getString("ThaoTac");
        BigDecimal score = rs.getBigDecimal("Diem1");
        List<String> actions = new ArrayList<>();
        if (acts != null) {
            for (String a : acts.split(",")) {
                actions.add(actionLabel(a));
            }
        }
        return new SessionInfo(rs.getInt("MaPhien"), rs.getTimestamp("ThoiGianBatDau").toLocalDateTime(),
                dtcs == null ? List.of() : List.of(dtcs.split(",")), rs.getString("NguyenNhan1"),
                score == null ? null : score.doubleValue(), actions);
    }
}
