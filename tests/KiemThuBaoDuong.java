import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import dao.Database;
import dao.MaintenanceDao;
import dao.VehicleDao;
import model.MaintenanceRecord;
import model.Vehicle;

/** Kiểm thử đầu-cuối DAO bảo dưỡng với SQL Server thật; dọn dữ liệu thử ở cuối. */
public class KiemThuBaoDuong {
    static int fail = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "DAT   " : "LOI   ") + name);
        if (!ok) fail++;
    }

    static int sqlCode(Runnable2 r) {
        try {
            r.run();
            return 0;
        } catch (SQLException e) {
            return e.getErrorCode();
        }
    }

    interface Runnable2 { void run() throws SQLException; }

    public static void main(String[] a) throws Exception {
        Database db = new Database();
        VehicleDao vd = new VehicleDao(db);
        MaintenanceDao md = new MaintenanceDao(db);
        int xe = vd.insert(new Vehicle(0, "TEST-BD-01", "Toyota", "Vios", 2019, "xe thu bao duong"));
        int xe2 = vd.insert(new Vehicle(0, "TEST-BD-02", null, null, null, null));
        try {
            check("xe moi chua co ban ghi", md.findByVehicle(xe).isEmpty());
            int a1 = md.insert(new MaintenanceRecord(0, xe, LocalDate.of(2026, 1, 10), 45000, "Thay dầu động cơ", 850000L, "dầu 5W-30"));
            int a2 = md.insert(new MaintenanceRecord(0, xe, LocalDate.of(2026, 9, 20), null, "Thay bugi", null, null));
            int a3 = md.insert(new MaintenanceRecord(0, xe, LocalDate.of(2026, 9, 20), 52000, "Thay lọc gió", 120000L, null));
            md.insert(new MaintenanceRecord(0, xe2, LocalDate.of(2026, 5, 5), 1000, "Xe khác", 1L, null));
            List<MaintenanceRecord> l = md.findByVehicle(xe);
            check("liet ke dung 3 ban ghi cua xe (khong lan xe khac)", l.size() == 3);
            check("sap xep ngay moi nhat truoc, cung ngay thi ma lon truoc", l.get(0).id() == a3 && l.get(1).id() == a2 && l.get(2).id() == a1);
            MaintenanceRecord r1 = l.get(2);
            check("doc dung tieng Viet, km, chi phi, ghi chu", r1.item().equals("Thay dầu động cơ") && r1.km() == 45000 && r1.cost() == 850000L && "dầu 5W-30".equals(r1.note()));
            MaintenanceRecord r2 = l.get(1);
            check("km, chi phi, ghi chu null doc la null", r2.km() == null && r2.cost() == null && r2.note() == null);
            check("ngay doc dung", r1.date().equals(LocalDate.of(2026, 1, 10)));

            md.update(new MaintenanceRecord(a1, xe, LocalDate.of(2026, 2, 1), 46000, "Thay dầu và lọc dầu", 900000L, null));
            MaintenanceRecord u = md.findByVehicle(xe).stream().filter(r -> r.id() == a1).findFirst().get();
            check("cap nhat thanh cong", u.km() == 46000 && u.item().equals("Thay dầu và lọc dầu") && u.cost() == 900000L && u.note() == null && u.date().equals(LocalDate.of(2026, 2, 1)));

            check("km am bi CSDL tu choi (547)", sqlCode(() -> md.insert(new MaintenanceRecord(0, xe, LocalDate.now(), -1, "x", null, null))) == 547);
            check("chi phi am bi tu choi (547)", sqlCode(() -> md.insert(new MaintenanceRecord(0, xe, LocalDate.now(), null, "x", -5L, null))) == 547);
            check("hang muc trong bi tu choi (547)", sqlCode(() -> md.insert(new MaintenanceRecord(0, xe, LocalDate.now(), null, "   ", null, null))) == 547);
            check("xe khong ton tai bi tu choi (547)", sqlCode(() -> md.insert(new MaintenanceRecord(0, 99999999, LocalDate.now(), null, "x", null, null))) == 547);
            check("khong xoa duoc xe khi con ho so bao duong (547)", sqlCode(() -> vd.delete(xe)) == 547);
            check("chuoi co dau nhay SQL duoc luu nguyen van", md.insert(new MaintenanceRecord(0, xe, LocalDate.now(), null, "a'; DROP TABLE dbo.Xe;--", null, null)) > 0
                    && md.findByVehicle(xe).stream().anyMatch(r -> r.item().equals("a'; DROP TABLE dbo.Xe;--")));

            md.delete(a2);
            check("xoa 1 ban ghi", md.findByVehicle(xe).stream().noneMatch(r -> r.id() == a2));
            check("xoa khong anh huong xe khac", md.findByVehicle(xe2).size() == 1);
        } finally {
            for (int id : new int[] { xe, xe2 }) {
                try (var c = db.getConnection(); var s = c.createStatement()) {
                    s.executeUpdate("DELETE FROM dbo.BaoDuong WHERE MaXe = " + id);
                    s.executeUpdate("DELETE FROM dbo.Xe WHERE MaXe = " + id);
                }
            }
            try (var c = db.getConnection(); var s = c.createStatement();
                    var rs = s.executeQuery("SELECT (SELECT COUNT(*) FROM dbo.BaoDuong), (SELECT COUNT(*) FROM dbo.Xe WHERE BienSo LIKE 'TEST-BD%')")) {
                rs.next();
                System.out.println("Con lai sau khi don: BaoDuong=" + rs.getInt(1) + ", xe thu=" + rs.getInt(2));
            }
        }
        System.out.println(fail == 0 ? "TAT CA DAT" : ("CO " + fail + " LOI"));
        System.exit(fail == 0 ? 0 : 1);
    }
}
