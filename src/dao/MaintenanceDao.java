package dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import model.MaintenanceInterval;
import model.MaintenanceRecord;

/** Truy cập bảng BaoDuong (UC11). Mọi truy vấn dùng PreparedStatement. */
public class MaintenanceDao {

    private final Database db;

    public MaintenanceDao(Database db) {
        this.db = db;
    }

    /** Các lần bảo dưỡng của một xe, mới nhất trước. */
    public List<MaintenanceRecord> findByVehicle(int vehicleId) throws SQLException {
        String sql = "SELECT MaBD, MaXe, NgayBaoDuong, SoKm, HangMuc, ChiPhi, GhiChu FROM dbo.BaoDuong "
                + "WHERE MaXe = ? ORDER BY NgayBaoDuong DESC, MaBD DESC";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<MaintenanceRecord> list = new ArrayList<>();
                while (rs.next()) {
                    int km = rs.getInt("SoKm");
                    Integer kmValue = rs.wasNull() ? null : km;
                    BigDecimal cost = rs.getBigDecimal("ChiPhi");
                    list.add(new MaintenanceRecord(rs.getInt("MaBD"), rs.getInt("MaXe"),
                            rs.getObject("NgayBaoDuong", LocalDate.class), kmValue, rs.getString("HangMuc"),
                            cost == null ? null : cost.longValue(), rs.getString("GhiChu")));
                }
                return list;
            }
        }
    }

    /** Thêm một lần bảo dưỡng, trả về mã mới. */
    public int insert(MaintenanceRecord r) throws SQLException {
        String sql = "INSERT INTO dbo.BaoDuong (NgayBaoDuong, SoKm, HangMuc, ChiPhi, GhiChu, MaXe) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection();
                PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, r);
            ps.setInt(6, r.vehicleId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    public void update(MaintenanceRecord r) throws SQLException {
        String sql = "UPDATE dbo.BaoDuong SET NgayBaoDuong = ?, SoKm = ?, HangMuc = ?, ChiPhi = ?, GhiChu = ? "
                + "WHERE MaBD = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, r);
            ps.setInt(6, r.id());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        try (Connection c = db.getConnection();
                PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.BaoDuong WHERE MaBD = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /** Toàn bộ chu kỳ bảo dưỡng theo hạng mục (bảng ChuKyBaoDuong). */
    public List<MaintenanceInterval> intervals() throws SQLException {
        String sql = "SELECT MaCK, HangMuc, ChuKyKm, ChuKyThang, Nguon FROM dbo.ChuKyBaoDuong ORDER BY HangMuc";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<MaintenanceInterval> list = new ArrayList<>();
            while (rs.next()) {
                int km = rs.getInt("ChuKyKm");
                Integer kmValue = rs.wasNull() ? null : km;
                int mo = rs.getInt("ChuKyThang");
                Integer moValue = rs.wasNull() ? null : mo;
                list.add(new MaintenanceInterval(rs.getInt("MaCK"), rs.getString("HangMuc"), kmValue, moValue,
                        rs.getString("Nguon")));
            }
            return list;
        }
    }

    /** Thêm (id = 0) hoặc sửa một chu kỳ bảo dưỡng. */
    public void saveInterval(MaintenanceInterval iv) throws SQLException {
        String sql = iv.id() == 0
                ? "INSERT INTO dbo.ChuKyBaoDuong (ChuKyKm, ChuKyThang, Nguon, HangMuc) VALUES (?, ?, ?, ?)"
                : "UPDATE dbo.ChuKyBaoDuong SET ChuKyKm = ?, ChuKyThang = ?, Nguon = ?, HangMuc = ? WHERE MaCK = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (iv.km() == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, iv.km());
            }
            if (iv.months() == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, iv.months());
            }
            ps.setString(3, iv.source());
            ps.setString(4, iv.item());
            if (iv.id() != 0) {
                ps.setInt(5, iv.id());
            }
            ps.executeUpdate();
        }
    }

    public void deleteInterval(int id) throws SQLException {
        try (Connection c = db.getConnection();
                PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.ChuKyBaoDuong WHERE MaCK = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /** Gán 5 cột chung (ngày, km, hạng mục, chi phí, ghi chú) vào tham số 1-5. */
    private static void bind(PreparedStatement ps, MaintenanceRecord r) throws SQLException {
        ps.setObject(1, r.date());
        if (r.km() == null) {
            ps.setNull(2, Types.INTEGER);
        } else {
            ps.setInt(2, r.km());
        }
        ps.setString(3, r.item());
        if (r.cost() == null) {
            ps.setNull(4, Types.DECIMAL);
        } else {
            ps.setBigDecimal(4, BigDecimal.valueOf(r.cost()));
        }
        ps.setString(5, r.note());
    }
}
