import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import dao.Database;
import dao.DtcDao;
import dao.MaintenanceDao;
import dao.SessionDao;
import dao.VehicleDao;
import model.DtcInfo;
import model.MaintenanceInterval;
import model.MaintenanceRecord;
import model.SessionDetail;
import model.SessionInfo;
import model.Vehicle;
import model.VehicleSummary;
import service.DtcDecoder;
import service.MaintenanceSchedule;
import service.MaintenanceSchedule.Reminder;
import service.MaintenanceSchedule.Status;
import service.ReportExporter;
import service.VehicleRules;

/**
 * Kiểm thử phần nâng cấp: logic thuần (chuẩn hóa biển số, VIN, số điện thoại, nhắc bảo dưỡng, giải nghĩa DTC)
 * và các truy vấn mới trên CSDL đang cấu hình trong config.properties (nên là CSDL thử ChanDoanXe_Test).
 * Dữ liệu thử dùng biển số bắt đầu bằng "99Z" và được xóa sạch ở cuối.
 */
public class KiemThuNangCap {

    static int fail;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "DAT   " : "LOI   ") + name);
        if (!ok) {
            fail++;
        }
    }

    static int sqlCode(Runnable r) {
        try {
            r.run();
            return 0;
        } catch (RuntimeException e) {
            return e.getCause() instanceof SQLException s ? s.getErrorCode() : -1;
        }
    }

    interface Sql {
        void run() throws SQLException;
    }

    static Runnable wrap(Sql s) {
        return () -> {
            try {
                s.run();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static void main(String[] a) throws Exception {
        // ---------------- biển số, VIN, số điện thoại
        check("bien so 30f25658 -> 30F-256.58", "30F-256.58".equals(VehicleRules.normalizePlate("30f25658")));
        check("bien so co khoang trang, dau cham", "51A-123.45".equals(VehicleRules.normalizePlate(" 51a 123.45 ")));
        check("bien so cu 4 so 29A-1234", "29A-1234".equals(VehicleRules.normalizePlate("29a1234")));
        check("bien so 2 chu seri 51LD-123.45", "51LD-123.45".equals(VehicleRules.normalizePlate("51LD12345")));
        check("bien so sai (thieu so) bi tu choi", VehicleRules.normalizePlate("30F-12") == null);
        check("bien so sai (chu truoc) bi tu choi", VehicleRules.normalizePlate("ABC-12345") == null);
        check("VIN hop le 17 ky tu", VehicleRules.validVin("MR053REH105123456"));
        check("VIN co chu O bi tu choi", !VehicleRules.validVin("MR053REH1O5123456"));
        check("VIN 16 ky tu bi tu choi", !VehicleRules.validVin("MR053REH10512345"));
        check("VIN viet thuong duoc viet hoa", "MR053REH105123456".equals(VehicleRules.cleanVin(" mr053reh105123456 ")));
        check("SDT 0905 123 456 hop le", VehicleRules.validPhone(VehicleRules.cleanPhone("0905 123 456")));
        check("SDT +84905123456 doi thanh 0905123456", "0905123456".equals(VehicleRules.cleanPhone("+84905123456")));
        check("SDT 9 so bi tu choi", !VehicleRules.validPhone("090512345"));

        // ---------------- nhắc bảo dưỡng
        LocalDate today = LocalDate.of(2026, 10, 7);
        MaintenanceInterval oil = new MaintenanceInterval(1, "Thay dầu động cơ", 5000, 6, "test");
        MaintenanceInterval plug = new MaintenanceInterval(2, "Thay bugi", 20000, 24, "test");
        MaintenanceInterval brake = new MaintenanceInterval(3, "Thay dầu phanh", 40000, 48, "test");
        List<MaintenanceRecord> recs = List.of(
                new MaintenanceRecord(1, 1, LocalDate.of(2026, 1, 10), 40200, "Thay dầu động cơ", 850000L, null),
                new MaintenanceRecord(2, 1, LocalDate.of(2026, 8, 1), 50500, "thay  DẦU động cơ", 900000L, null),
                new MaintenanceRecord(3, 1, LocalDate.of(2026, 6, 12), 46500, "Thay bugi", 480000L, null));
        List<Reminder> rs = MaintenanceSchedule.reminders(List.of(oil, plug, brake), recs, 55100, today);
        Reminder rOil = rs.stream().filter(r -> r.interval() == oil).findFirst().orElseThrow();
        check("lan gan nhat lay ban ghi moi nhat (khong phan biet hoa thuong, khoang trang)", rOil.last().id() == 2);
        check("han km = 50500 + 5000 = 55500", rOil.dueKm() == 55500);
        check("con 400 km (<= 10% chu ky 5.000) -> sap den", rOil.kmLeft() == 400 && rOil.status() == Status.SOON);
        check("con 700 km (> 10%) va 117 ngay -> con xa", MaintenanceSchedule.reminders(List.of(oil), recs, 54800, today).get(0).status() == Status.OK);
        Reminder rPlug = rs.stream().filter(r -> r.interval() == plug).findFirst().orElseThrow();
        check("bugi con xa (11.400 km)", rPlug.status() == Status.OK && rPlug.kmLeft() == 66500 - 55100);
        Reminder rBrake = rs.stream().filter(r -> r.interval() == brake).findFirst().orElseThrow();
        check("dau phanh chua lam -> chua co du lieu", rBrake.status() == Status.NO_DATA);
        check("xep theo muc khan: sap den truoc con xa truoc chua co du lieu",
                rs.get(0).status() == Status.SOON && rs.get(2).status() == Status.NO_DATA);
        List<Reminder> late = MaintenanceSchedule.reminders(List.of(oil), recs, 56000, today);
        check("vuot han km -> den han", late.get(0).status() == Status.OVERDUE && late.get(0).kmLeft() == -500);
        List<Reminder> byDate = MaintenanceSchedule.reminders(List.of(oil), recs, 50600, LocalDate.of(2027, 2, 1));
        check("qua 6 thang (ngay 01/02/2027) -> den han du con km", byDate.get(0).status() == Status.OVERDUE);
        List<Reminder> noKm = MaintenanceSchedule.reminders(List.of(oil), recs, null, today);
        check("khong biet so km xe -> dung so km lon nhat trong ho so (50500)", noKm.get(0).kmLeft() == 5000);
        check("km lon nhat truoc 01/07/2026 la 46500",
                MaintenanceSchedule.maxKmUntil(recs, LocalDate.of(2026, 7, 1), 0) == 46500);
        check("bo qua chinh ban ghi dang sua", MaintenanceSchedule.maxKmUntil(recs, LocalDate.of(2026, 8, 1), 2) == 46500);
        Map<String, Long> cost = MaintenanceSchedule.costByItem(recs, 2026);
        check("tong chi phi 2026 = 2.230.000", MaintenanceSchedule.totalCost(recs, 2026) == 2230000L);
        check("chi phi theo hang muc xep giam dan", cost.values().iterator().next() == 900000L);

        // ---------------- giải nghĩa DTC, phiếu chẩn đoán
        List<DtcDecoder.Part> parts = DtcDecoder.decode("P0171");
        check("P0171 tach 4 phan", parts.size() == 4 && parts.get(3).symbol().equals("71"));
        check("P0171: P la truyen dong", parts.get(0).meaning().startsWith("Truyền động"));
        check("P0171: nhom 1 la nhien lieu va khi nap", parts.get(2).meaning().contains("nhiên liệu"));
        check("P0301: nhom 3 la danh lua", DtcDecoder.decode("P0301").get(2).meaning().contains("đánh lửa"));
        check("ma sai do dai -> rong", DtcDecoder.decode("P01").isEmpty());

        // ---------------- truy vấn CSDL
        Database db = new Database();
        VehicleDao vd = new VehicleDao(db);
        MaintenanceDao md = new MaintenanceDao(db);
        SessionDao sd = new SessionDao(db);
        DtcDao dd = new DtcDao(db);
        int xe = vd.insert(new Vehicle(0, "99Z-000.01", "Toyota", "Vios", 2019, null, "MR053REH105999991", 30000,
                "Nguyễn Văn Thử", "0905000001"));
        try {
            Vehicle back = vd.findAll().stream().filter(v -> v.id() == xe).findFirst().orElseThrow();
            check("luu va doc lai VIN, so km, chu xe, SDT", "MR053REH105999991".equals(back.vin())
                    && back.odometerKm() == 30000 && "Nguyễn Văn Thử".equals(back.owner()) && "0905000001".equals(back.phone()));
            int dup = sqlCode(wrap(() -> vd.insert(new Vehicle(0, "99Z-000.02", null, null, null, null,
                    "MR053REH105999991", null, null, null))));
            check("trung VIN bi tu choi (2601)", dup == 2601 || dup == 2627);
            int bad = sqlCode(wrap(() -> vd.insert(new Vehicle(0, "99Z-000.03", null, null, null, null,
                    "MR053REH1O5999991", null, null, null))));
            check("VIN co chu O bi CSDL tu choi (547)", bad == 547);
            vd.raiseOdometer(xe, 25000);
            check("so km khong giam khi ghi so nho hon",
                    vd.findAll().stream().filter(v -> v.id() == xe).findFirst().orElseThrow().odometerKm() == 30000);
            vd.raiseOdometer(xe, 31000);
            check("so km tang khi ghi so lon hon",
                    vd.findAll().stream().filter(v -> v.id() == xe).findFirst().orElseThrow().odometerKm() == 31000);

            md.insert(new MaintenanceRecord(0, xe, LocalDate.of(2026, 9, 1), 30500, "Thay dầu động cơ", 800000L, null));
            int p = sd.createSession(xe);
            sd.record(p, "DOC_DTC", "test", List.of("P0171"));
            VehicleSummary sum = vd.summary(xe);
            check("tom tat xe: 1 phien, 1 lan bao duong, ma loi gan nhat P0171",
                    sum.sessions() == 1 && sum.maintenances() == 1 && sum.lastDtcs().equals(List.of("P0171")));
            List<SessionInfo> ss = sd.sessionsOfVehicle(xe);
            check("danh sach phien co 1 phien, ma P0171, thao tac Doc DTC",
                    ss.size() == 1 && ss.get(0).dtcs().equals(List.of("P0171")) && ss.get(0).actions().contains("Đọc DTC"));
            SessionDetail det = sd.detail(ss.get(0), dd);
            check("chi tiet phien co mo ta tieng Viet cua P0171 va 1 dong nhat ky",
                    det.dtcs().get(0).description() != null && det.log().size() == 1);
            String html = ReportExporter.html(vd.findAll().stream().filter(v -> v.id() == xe).findFirst().orElseThrow(),
                    det, java.time.LocalDateTime.now());
            check("phieu chan doan co bien so, VIN va ma loi", html.contains("99Z-000.01") && html.contains("MR053REH105999991")
                    && html.contains("P0171"));
            check("phieu thoat ky tu HTML", ReportExporter.html(new Vehicle(0, "<b>", null, null, null, null), det,
                    java.time.LocalDateTime.now()).contains("&lt;b&gt;"));

            DtcInfo info = dd.info("P0171");
            check("tra cuu P0171 co it nhat 3 luat, xep diem giam dan", info != null && info.rules().size() >= 3
                    && info.rules().get(0).score() >= info.rules().get(info.rules().size() - 1).score());
            check("P0171 da gap trong it nhat 1 phien", info.sessions() >= 1);
            check("ma khong ton tai -> null", dd.info("P9999") == null);

            int before = md.intervals().size();
            md.saveInterval(new MaintenanceInterval(0, "Hạng mục thử 99Z", 1000, null, "test"));
            MaintenanceInterval mine = md.intervals().stream().filter(i -> i.item().equals("Hạng mục thử 99Z")).findFirst().orElseThrow();
            md.saveInterval(new MaintenanceInterval(mine.id(), mine.item(), 2000, 3, "test"));
            MaintenanceInterval upd = md.intervals().stream().filter(i -> i.id() == mine.id()).findFirst().orElseThrow();
            check("them va sua chu ky bao duong", upd.km() == 2000 && upd.months() == 3);
            int none = sqlCode(wrap(() -> md.saveInterval(new MaintenanceInterval(0, "Hạng mục rỗng 99Z", null, null, "test"))));
            check("chu ky khong co km lan thang bi tu choi (547)", none == 547);
            md.deleteInterval(mine.id());
            check("xoa chu ky", md.intervals().size() == before);
        } finally {
            // Dọn dữ liệu thử: phiên (xóa theo dây chuyền các bảng con), bảo dưỡng, xe
            try (var c = db.getConnection(); var st = c.createStatement()) {
                st.executeUpdate("DELETE FROM dbo.PhienChanDoan WHERE MaXe IN (SELECT MaXe FROM dbo.Xe WHERE BienSo LIKE '99Z-%')");
                st.executeUpdate("DELETE FROM dbo.BaoDuong WHERE MaXe IN (SELECT MaXe FROM dbo.Xe WHERE BienSo LIKE '99Z-%')");
                st.executeUpdate("DELETE FROM dbo.Xe WHERE BienSo LIKE '99Z-%'");
                st.executeUpdate("DELETE FROM dbo.ChuKyBaoDuong WHERE HangMuc LIKE N'%99Z'");
            }
        }
        System.out.println(fail == 0 ? "TAT CA DAT" : ("CO " + fail + " LOI"));
    }
}
