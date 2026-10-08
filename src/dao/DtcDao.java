package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import model.Dtc;
import model.DtcInfo;
import model.Severity;
import service.PidDecoder;

/** Truy cập bảng DTC. Mọi truy vấn dùng PreparedStatement (không nối chuỗi từ người dùng). */
public class DtcDao {

    private static final String COLUMNS = "MaDTC, MoTaGoc, MoTaViet, NhomHeThong, MucDo, DaXacMinh";
    private static final String SEARCH_SQL =
            "SELECT " + COLUMNS + " FROM dbo.DTC "
            + "WHERE MaDTC LIKE ? OR MoTaGoc LIKE ? OR MoTaViet LIKE ? OR NhomHeThong LIKE ? "
            + "ORDER BY MaDTC";

    private final Database db;

    public DtcDao(Database db) {
        this.db = db;
    }

    /** Tìm theo mã, mô tả hoặc nhóm; từ khóa rỗng thì trả về toàn bộ danh mục. */
    public List<Dtc> search(String keyword) throws SQLException {
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        try (Connection c = db.getConnection();
                PreparedStatement ps = c.prepareStatement(SEARCH_SQL)) {
            for (int i = 1; i <= 4; i++) {
                ps.setString(i, like);
            }
            List<Dtc> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
            return result;
        }
    }

    /** Tra các mã trong danh mục; mã không có trong CSDL sẽ vắng mặt trong kết quả. */
    public Map<String, Dtc> findByCodes(List<String> codes) throws SQLException {
        if (codes.isEmpty()) {
            return Map.of();
        }
        // Chỉ nối dấu ? (số lượng theo danh sách), giá trị mã luôn đi qua tham số
        String marks = String.join(",", Collections.nCopies(codes.size(), "?"));
        String sql = "SELECT " + COLUMNS + " FROM dbo.DTC WHERE MaDTC IN (" + marks + ")";
        try (Connection c = db.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < codes.size(); i++) {
                ps.setString(i + 1, codes.get(i));
            }
            Map<String, Dtc> result = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Dtc d = map(rs);
                    result.put(d.code(), d);
                }
            }
            return result;
        }
    }

    /**
     * Thông tin đầy đủ của một mã cho trang chi tiết: mô tả gốc, nguồn, các luật có điều kiện là mã này
     * (xếp theo điểm giảm dần, kèm điều kiện thêm viết thành chữ) và số lần gặp trong xưởng. Trả null nếu không có mã.
     */
    public DtcInfo info(String code) throws SQLException {
        try (Connection c = db.getConnection()) {
            Dtc dtc;
            String original;
            String source;
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + ", Nguon FROM dbo.DTC WHERE MaDTC = ?")) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    dtc = map(rs);
                    original = rs.getString("MoTaGoc");
                    source = rs.getString("Nguon");
                }
            }
            // Điều kiện của mọi luật có nhắc tới mã này (để viết phần "điều kiện thêm")
            Map<Integer, List<String>> extra = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT d.MaLuat, d.Loai, d.MaDTC, d.MaPID, d.ToanTu, d.NguongGiaTri FROM dbo.DieuKienLuat d "
                            + "WHERE d.MaLuat IN (SELECT MaLuat FROM dbo.DieuKienLuat WHERE MaDTC = ?) ORDER BY d.MaDK")) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String text;
                        if ("DTC".equals(rs.getString("Loai"))) {
                            String other = rs.getString("MaDTC").trim();
                            if (other.equals(code)) {
                                continue;
                            }
                            text = "có " + other;
                        } else {
                            int pid = rs.getInt("MaPID");
                            text = PidDecoder.name(pid) + " " + rs.getString("ToanTu").trim() + " "
                                    + rs.getBigDecimal("NguongGiaTri").stripTrailingZeros().toPlainString().replace('.', ',')
                                    + " " + PidDecoder.unit(pid);
                        }
                        extra.computeIfAbsent(rs.getInt("MaLuat"), k -> new ArrayList<>()).add(text);
                    }
                }
            }
            List<DtcInfo.RuleRow> rules = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT l.MaLuat, n.TenNguyenNhan, l.DiemTinCay, n.GoiYKiemTra FROM dbo.Luat l "
                            + "JOIN dbo.NguyenNhan n ON n.MaNN = l.MaNN "
                            + "WHERE l.MaLuat IN (SELECT MaLuat FROM dbo.DieuKienLuat WHERE MaDTC = ?) "
                            + "ORDER BY l.DiemTinCay DESC, l.MaLuat")) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt(1);
                        List<String> ex = extra.getOrDefault(id, List.of());
                        rules.add(new DtcInfo.RuleRow(id, rs.getString(2), rs.getBigDecimal(3).doubleValue(),
                                ex.isEmpty() ? "" : String.join("; ", ex), rs.getString(4)));
                    }
                }
            }
            int vehicles;
            int sessions;
            java.sql.Timestamp last;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(DISTINCT p.MaXe), COUNT(DISTINCT d.MaPhien), MAX(d.ThoiGianPhatHien) FROM dbo.PhienDTC d "
                            + "JOIN dbo.PhienChanDoan p ON p.MaPhien = d.MaPhien WHERE d.MaDTC = ?")) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    vehicles = rs.getInt(1);
                    sessions = rs.getInt(2);
                    last = rs.getTimestamp(3);
                }
            }
            return new DtcInfo(dtc, original, source, rules, vehicles, sessions, last == null ? null : last.toLocalDateTime());
        }
    }

    private static Dtc map(ResultSet rs) throws SQLException {
        String viet = rs.getString("MoTaViet");
        String desc = viet != null ? viet : rs.getString("MoTaGoc");
        String muc = rs.getString("MucDo");
        Severity sev = muc == null ? null : Severity.valueOf(muc);
        return new Dtc(rs.getString("MaDTC").trim(), desc, sev,
                rs.getString("NhomHeThong"), rs.getBoolean("DaXacMinh"));
    }
}
