package dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import model.Vehicle;
import model.VehicleSummary;

/** Truy cập bảng Xe (UC01). Mọi truy vấn dùng PreparedStatement. */
public class VehicleDao {

    private static final String COLUMNS = "MaXe, BienSo, Hang, DongXe, NamSanXuat, GhiChu, VIN, SoKmHienTai, ChuXe, SoDienThoai";

    private final Database db;

    public VehicleDao(Database db) {
        this.db = db;
    }

    public List<Vehicle> findAll() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM dbo.Xe ORDER BY BienSo";
        try (Connection c = db.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<Vehicle> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new Vehicle(rs.getInt("MaXe"), rs.getString("BienSo"), rs.getString("Hang"),
                        rs.getString("DongXe"), intOrNull(rs, "NamSanXuat"), rs.getString("GhiChu"),
                        rs.getString("VIN"), intOrNull(rs, "SoKmHienTai"), rs.getString("ChuXe"),
                        rs.getString("SoDienThoai")));
            }
            return list;
        }
    }

    /** Thêm xe, trả về mã xe mới. */
    public int insert(Vehicle v) throws SQLException {
        String sql = "INSERT INTO dbo.Xe (BienSo, Hang, DongXe, NamSanXuat, GhiChu, VIN, SoKmHienTai, ChuXe, SoDienThoai) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection();
                PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, v);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    public void update(Vehicle v) throws SQLException {
        String sql = "UPDATE dbo.Xe SET BienSo = ?, Hang = ?, DongXe = ?, NamSanXuat = ?, GhiChu = ?, VIN = ?, "
                + "SoKmHienTai = ?, ChuXe = ?, SoDienThoai = ? WHERE MaXe = ?";
        try (Connection c = db.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, v);
            ps.setInt(10, v.id());
            ps.executeUpdate();
        }
    }

    /** Ghi số km mới cho xe, chỉ khi lớn hơn số km đang lưu (đồng hồ công-tơ-mét không chạy lùi). */
    public void raiseOdometer(int vehicleId, int km) throws SQLException {
        String sql = "UPDATE dbo.Xe SET SoKmHienTai = ? WHERE MaXe = ? AND (SoKmHienTai IS NULL OR SoKmHienTai < ?)";
        try (Connection c = db.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, km);
            ps.setInt(2, vehicleId);
            ps.setInt(3, km);
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        try (Connection c = db.getConnection();
                PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.Xe WHERE MaXe = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /** Tóm tắt hồ sơ xe cho thẻ xe: số phiên, lần chẩn đoán gần nhất kèm mã lỗi, số lần bảo dưỡng. */
    public VehicleSummary summary(int vehicleId) throws SQLException {
        String counts = "SELECT (SELECT COUNT(*) FROM dbo.PhienChanDoan WHERE MaXe = ?) AS SoPhien, "
                + "(SELECT MAX(ThoiGianBatDau) FROM dbo.PhienChanDoan WHERE MaXe = ?) AS PhienCuoi, "
                + "(SELECT COUNT(*) FROM dbo.BaoDuong WHERE MaXe = ?) AS SoBD, "
                + "(SELECT MAX(NgayBaoDuong) FROM dbo.BaoDuong WHERE MaXe = ?) AS BDCuoi";
        // Mã lỗi của phiên gần nhất có đọc được mã lỗi
        String lastDtcs = "SELECT d.MaDTC FROM dbo.PhienDTC d WHERE d.MaPhien = ("
                + "SELECT TOP 1 p.MaPhien FROM dbo.PhienChanDoan p "
                + "WHERE p.MaXe = ? AND EXISTS (SELECT 1 FROM dbo.PhienDTC x WHERE x.MaPhien = p.MaPhien) "
                + "ORDER BY p.ThoiGianBatDau DESC, p.MaPhien DESC) ORDER BY d.MaDTC";
        try (Connection c = db.getConnection()) {
            int sessions;
            int maint;
            Timestamp lastS;
            Date lastM;
            try (PreparedStatement ps = c.prepareStatement(counts)) {
                for (int i = 1; i <= 4; i++) {
                    ps.setInt(i, vehicleId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    sessions = rs.getInt("SoPhien");
                    lastS = rs.getTimestamp("PhienCuoi");
                    maint = rs.getInt("SoBD");
                    lastM = rs.getDate("BDCuoi");
                }
            }
            List<String> dtcs = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(lastDtcs)) {
                ps.setInt(1, vehicleId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        dtcs.add(rs.getString(1));
                    }
                }
            }
            return new VehicleSummary(sessions, lastS == null ? null : lastS.toLocalDateTime(), dtcs, maint,
                    lastM == null ? null : lastM.toLocalDate());
        }
    }

    private static Integer intOrNull(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static void bind(PreparedStatement ps, Vehicle v) throws SQLException {
        ps.setString(1, v.plate());
        ps.setString(2, v.brand());
        ps.setString(3, v.model());
        if (v.year() == null) {
            ps.setNull(4, Types.SMALLINT);
        } else {
            ps.setInt(4, v.year());
        }
        ps.setString(5, v.note());
        ps.setString(6, v.vin());
        if (v.odometerKm() == null) {
            ps.setNull(7, Types.INTEGER);
        } else {
            ps.setInt(7, v.odometerKm());
        }
        ps.setString(8, v.owner());
        ps.setString(9, v.phone());
    }
}
