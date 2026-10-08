import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import app.AppConfig;
import controller.DiagnosticController;
import controller.VehicleController;
import dao.Database;
import dao.DtcDao;
import dao.RuleDao;
import dao.SessionDao;
import dao.VehicleDao;
import model.Diagnosis;
import model.Dtc;
import model.Vehicle;
import service.GatewayDataSource;
import service.ScenarioCatalog;

/**
 * Kiểm thử đầu-cuối chức năng phân tích nguyên nhân (UC07) qua DiagnosticController với Gateway + ECU + SQL Server thật.
 * Dùng xe thử tạm, dọn sạch cuối cùng. Chạy trong thư mục có config.properties (xem tests/chay-kiem-thu.ps1 -CoGateway).
 */
public class KiemThuPhanTich {
    static int fail = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "DAT   " : "LOI   ") + name);
        if (!ok) fail++;
    }

    static int count(Database db, String sql, int arg) throws Exception {
        try (Connection c = db.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setInt(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Chạy hàm trên luồng giao diện (controller gọi callback bằng EDT) rồi chờ cho tới khi có kết quả hoặc lỗi. */
    static <T> T waitFor(CopyOnWriteArrayList<T> sink, CopyOnWriteArrayList<String> errors, int before, Runnable action) throws Exception {
        int sinkBefore = sink.size(), errBefore = errors.size();
        action.run();
        for (int i = 0; i < 100; i++) {
            Thread.sleep(100);
            if (sink.size() > sinkBefore) return sink.get(sink.size() - 1);
            if (errors.size() > errBefore) return null;
        }
        throw new IllegalStateException("Quá thời gian chờ");
    }

    public static void main(String[] a) throws Exception {
        AppConfig config = new AppConfig();
        Database db = new Database();
        VehicleDao vdao = new VehicleDao(db);
        int xe = vdao.insert(new Vehicle(0, "TEST-PT-01", "Toyota", "Vios", 2019, "xe thu phan tich"));
        try {
            GatewayDataSource source = new GatewayDataSource(config.get("gateway.host", "192.168.35.128"), config.getInt("gateway.port", 5000));
            for (int i = 0; i < 50 && !source.isConnected(); i++) Thread.sleep(100);
            VehicleController vc = new VehicleController(vdao);
            DiagnosticController dc = new DiagnosticController(source, vc, new SessionDao(db), new DtcDao(db), new RuleDao(db));
            CopyOnWriteArrayList<List<Diagnosis>> results = new CopyOnWriteArrayList<>();
            CopyOnWriteArrayList<String> errors = new CopyOnWriteArrayList<>();
            CopyOnWriteArrayList<String> infos = new CopyOnWriteArrayList<>();
            CopyOnWriteArrayList<List<Dtc>> dtcLists = new CopyOnWriteArrayList<>();
            dc.addAnalysisListener(results::add);
            dc.addErrorListener(errors::add);
            dc.addInfoListener(infos::add);
            dc.addDtcListener(dtcLists::add);

            // 1. Chưa chọn xe
            int e0 = errors.size();
            javax.swing.SwingUtilities.invokeAndWait(dc::analyze);
            Thread.sleep(300);
            check("phân tích khi chưa chọn xe: báo lỗi, không có kết quả", errors.size() == e0 + 1 && results.isEmpty()
                    && errors.get(errors.size() - 1).contains("chọn xe"));

            // Chọn xe
            javax.swing.SwingUtilities.invokeAndWait(() -> vc.select(new Vehicle(xe, "TEST-PT-01", "Toyota", "Vios", 2019, null)));

            // 2. Chọn kịch bản không tồn tại
            e0 = errors.size();
            javax.swing.SwingUtilities.invokeAndWait(() -> dc.injectScenario(8));  // hợp lệ, để chắc chắn ECU đang ở trạng thái đã biết
            Thread.sleep(500);
            // kịch bản 99 không nằm trong ScenarioCatalog nên gọi thẳng qua nguồn dữ liệu
            boolean badRejected = false;
            try {
                source.inject(99);
            } catch (java.io.IOException ex) {
                badRejected = ex.getMessage().contains("99");
            }
            check("chọn kịch bản không có (99): bị từ chối, thông báo nêu rõ số kịch bản", badRejected);

            // 3. Kịch bản 1 (rò rỉ chân không)
            javax.swing.SwingUtilities.invokeAndWait(() -> dc.injectScenario(1));
            Thread.sleep(3500);
            check("chọn kịch bản 1: có thông báo", !infos.isEmpty() && infos.get(infos.size() - 1).contains("Rò rỉ chân không"));
            int before = results.size();
            final int[] holder = {0};
            List<Diagnosis> r1 = waitFor(results, errors, before, () -> dc.analyze());
            check("kịch bản 1: có kết quả, nguyên nhân đúng ở hạng 1 và điểm 0,8625 (làm tròn 0,863 khi lưu)",
                    r1 != null && !r1.isEmpty() && r1.get(0).cause().name().equals(ScenarioCatalog.ALL.get(1).trueCause())
                            && Math.abs(r1.get(0).confidence() - 0.8625) < 1e-9);
            Thread.sleep(500);
            int s1 = count(db, "SELECT MAX(MaPhien) FROM dbo.PhienChanDoan WHERE MaXe = ?", xe);
            check("đã ghi phiên mới: 1 mã lỗi, 8 mẫu PID, đủ kết quả có thứ hạng liên tục, 1 dòng nhật ký PHAN_TICH",
                    count(db, "SELECT COUNT(*) FROM dbo.PhienDTC WHERE MaPhien = ?", s1) == 1
                            && count(db, "SELECT COUNT(*) FROM dbo.MauPID WHERE MaPhien = ?", s1) == 8
                            && count(db, "SELECT COUNT(*) FROM dbo.KetQuaPhanTich WHERE MaPhien = ?", s1) == r1.size()
                            && count(db, "SELECT MAX(ThuHang) FROM dbo.KetQuaPhanTich WHERE MaPhien = ?", s1) == r1.size()
                            && count(db, "SELECT COUNT(*) FROM dbo.NhatKyThaoTac WHERE MaPhien = ? AND LoaiThaoTac = 'PHAN_TICH'", s1) == 1);

            // 4. Hai lần liên tiếp: phiên riêng, không vi phạm ràng buộc duy nhất
            List<Diagnosis> r1b = waitFor(results, errors, 0, () -> dc.analyze());
            Thread.sleep(500);
            int s2 = count(db, "SELECT MAX(MaPhien) FROM dbo.PhienChanDoan WHERE MaXe = ?", xe);
            check("phân tích lần hai: mỗi lần một phiên riêng, không lỗi ràng buộc", r1b != null && s2 > s1
                    && count(db, "SELECT COUNT(*) FROM dbo.KetQuaPhanTich WHERE MaPhien = ?", s2) == r1b.size() && errors.stream().noneMatch(e -> e.contains("lưu")));

            // 5. Xe khỏe
            javax.swing.SwingUtilities.invokeAndWait(() -> dc.injectScenario(0));
            Thread.sleep(3000);
            List<Diagnosis> r0 = waitFor(results, errors, 0, () -> dc.analyze());
            Thread.sleep(500);
            int s3 = count(db, "SELECT MAX(MaPhien) FROM dbo.PhienChanDoan WHERE MaXe = ?", xe);
            check("xe khỏe: không có kết luận (danh sách rỗng), không ghi kết quả, vẫn ghi nhật ký",
                    r0 != null && r0.isEmpty() && count(db, "SELECT COUNT(*) FROM dbo.KetQuaPhanTich WHERE MaPhien = ?", s3) == 0
                            && count(db, "SELECT COUNT(*) FROM dbo.NhatKyThaoTac WHERE MaPhien = ? AND LoaiThaoTac = 'PHAN_TICH'", s3) == 1);

            // 6. Mất kết nối ECU: ngắt bằng kịch bản không đổi được; kiểm tra lỗi khi nguồn mock không hỗ trợ tiêm lỗi
            service.DataSource mock = new service.MockDataSource();
            boolean unsupported = false;
            try {
                mock.inject(1);
            } catch (java.io.IOException ex) {
                unsupported = ex.getMessage().contains("không hỗ trợ");
            }
            check("nguồn dữ liệu giả không hỗ trợ tiêm lỗi: báo rõ", unsupported);

            // trả ECU về xe khỏe
            source.inject(0);
        } finally {
            try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
                s.executeUpdate("DELETE FROM dbo.PhienChanDoan WHERE MaXe = " + xe);
                s.executeUpdate("DELETE FROM dbo.Xe WHERE MaXe = " + xe);
            }
        }
        System.out.println(fail == 0 ? "TAT CA DAT" : ("CO " + fail + " LOI"));
        System.exit(fail == 0 ? 0 : 1);
    }
}
