package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import model.Cause;
import model.Condition;
import model.Rule;
import model.RuleBase;

/** Nạp cơ sở tri thức (bảng NguyenNhan, Luat, DieuKienLuat) từ CSDL. Mọi truy vấn dùng PreparedStatement. */
public class RuleDao {

    private final Database db;

    public RuleDao(Database db) {
        this.db = db;
    }

    public RuleBase load() throws SQLException {
        try (Connection c = db.getConnection()) {
            Map<Integer, Cause> causes = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT MaNN, TenNguyenNhan, GoiYKiemTra FROM dbo.NguyenNhan ORDER BY MaNN");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    causes.put(rs.getInt("MaNN"),
                            new Cause(rs.getInt("MaNN"), rs.getString("TenNguyenNhan"), rs.getString("GoiYKiemTra")));
                }
            }
            // Điều kiện gom theo luật; ORDER BY để thứ tự ổn định
            Map<Integer, List<Condition>> conditions = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT MaLuat, Loai, MaDTC, MaPID, ToanTu, NguongGiaTri FROM dbo.DieuKienLuat ORDER BY MaDK");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Condition cond;
                    if ("DTC".equals(rs.getString("Loai"))) {
                        cond = Condition.ofDtc(rs.getString("MaDTC").trim());
                    } else {
                        cond = Condition.ofPid(rs.getInt("MaPID"), rs.getString("ToanTu"),
                                rs.getBigDecimal("NguongGiaTri").doubleValue());
                    }
                    conditions.computeIfAbsent(rs.getInt("MaLuat"), k -> new ArrayList<>()).add(cond);
                }
            }
            List<Rule> rules = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT MaLuat, MaNN, DiemTinCay, MoTa, Nguon FROM dbo.Luat ORDER BY MaLuat");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("MaLuat");
                    rules.add(new Rule(id, rs.getInt("MaNN"), rs.getBigDecimal("DiemTinCay").doubleValue(),
                            rs.getString("MoTa"), rs.getString("Nguon"), conditions.getOrDefault(id, List.of())));
                }
            }
            return new RuleBase(causes, rules);
        }
    }
}
