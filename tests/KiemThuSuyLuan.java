import java.util.List;
import java.util.Map;
import java.util.Set;

import dao.Database;
import dao.RuleDao;
import model.Cause;
import model.Condition;
import model.Diagnosis;
import model.Rule;
import model.RuleBase;
import service.InferenceEngine;
import service.ScenarioCatalog;

/**
 * Kiểm thử bộ suy luận: phép gộp độ tin cậy, ngữ nghĩa điều kiện, xếp hạng, và bộ luật thật nạp từ CSDL.
 * Chạy trong thư mục có config.properties trỏ tới CSDL đã nạp sql/05_seed_luat.sql.
 * Không cần ECU/Gateway: dùng ảnh chụp PID mẫu cho từng kịch bản (giá trị điển hình trong khoảng của ECU mô phỏng).
 */
public class KiemThuSuyLuan {
    static int fail = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "DAT   " : "LOI   ") + name);
        if (!ok) fail++;
    }

    static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    // Ảnh chụp PID điển hình: tải, nước, vòng tua, tốc độ, khí nạp, MAF, bướm ga, điện áp
    static Map<Integer, Double> pids(double thr, double rpm, double speed, double cool, double iat, double maf, double volt) {
        return Map.of(0x04, 15 + thr * 0.8, 0x05, cool, 0x0C, rpm, 0x0D, speed, 0x0F, iat, 0x10, maf, 0x11, thr, 0x42, volt);
    }

    static int rankOf(List<Diagnosis> r, String cause) {
        for (int i = 0; i < r.size(); i++) {
            if (r.get(i).cause().name().equals(cause)) return i + 1;
        }
        return -1;
    }

    public static void main(String[] a) throws Exception {
        // ---- 1. Công thức gộp (MYCIN) ----
        check("gộp 0,45 và 0,40 = 0,67", near(InferenceEngine.combine(0.45, 0.40), 0.67));
        check("gộp có tính giao hoán", near(InferenceEngine.combine(0.30, 0.75), InferenceEngine.combine(0.75, 0.30)));
        check("gộp luôn nhỏ hơn 1 và không nhỏ hơn mỗi thành phần", InferenceEngine.combine(0.9, 0.9) < 1 && InferenceEngine.combine(0.5, 0.2) >= 0.5);

        // ---- 2. Điều kiện ----
        Set<String> dtc = Set.of("P0171");
        Map<Integer, Double> p = Map.of(0x0C, 1200.0);
        check("điều kiện mã lỗi đúng/sai", Condition.ofDtc("P0171").holds(dtc, p) && !Condition.ofDtc("P0300").holds(dtc, p));
        check("điều kiện PID: > và <=", Condition.ofPid(0x0C, ">", 1100).holds(dtc, p) && Condition.ofPid(0x0C, "<=", 1200).holds(dtc, p)
                && !Condition.ofPid(0x0C, "<", 1200).holds(dtc, p));
        check("PID chưa có dữ liệu thì điều kiện sai", !Condition.ofPid(0x10, "<", 99).holds(dtc, p));

        // ---- 3. Bộ suy luận trên bộ luật nhỏ ----
        Cause c1 = new Cause(1, "A", ""), c2 = new Cause(2, "B", ""), c3 = new Cause(3, "C", "");
        List<Rule> small = List.of(
                new Rule(1, 1, 0.5, "", "", List.of(Condition.ofDtc("X"))),
                new Rule(2, 1, 0.5, "", "", List.of(Condition.ofDtc("X"), Condition.ofPid(0x0C, ">", 1000))),
                new Rule(3, 2, 0.75, "", "", List.of(Condition.ofDtc("X"))),
                new Rule(4, 3, 0.75, "", "", List.of(Condition.ofDtc("X"))),
                new Rule(5, 3, 0.9, "", "", List.of(Condition.ofDtc("Y"))));
        InferenceEngine mini = new InferenceEngine(new RuleBase(Map.of(1, c1, 2, c2, 3, c3), small));
        List<Diagnosis> r = mini.diagnose(Set.of("X"), Map.of(0x0C, 1500.0));
        check("hai luật cùng nguyên nhân được gộp (0,5 và 0,5 -> 0,75) và xếp hạng 1 theo mã khi hòa", r.size() == 3 && r.get(0).cause().id() == 1
                && near(r.get(0).confidence(), 0.75) && r.get(0).firedRules().size() == 2);
        check("điểm bằng nhau thì xếp theo mã nguyên nhân tăng dần", r.get(1).cause().id() == 2 && r.get(2).cause().id() == 3);
        r = mini.diagnose(Set.of("X"), Map.of(0x0C, 800.0));
        check("luật thiếu điều kiện PID không khớp, luật còn lại vẫn khớp", rankOf(r, "A") == 3 && near(r.stream().filter(d -> d.cause().id() == 1).findFirst().get().confidence(), 0.5));
        check("không luật nào khớp thì danh sách rỗng", mini.diagnose(Set.of("Z"), Map.of()).isEmpty());
        check("luật không có điều kiện nào thì không bao giờ khớp", new InferenceEngine(new RuleBase(Map.of(1, c1),
                List.of(new Rule(9, 1, 0.9, "", "", List.of())))).diagnose(Set.of("X"), Map.of()).isEmpty());

        // ---- 4. Bộ luật thật trong CSDL ----
        RuleBase base = new RuleDao(new Database()).load();
        check("nạp đủ 17 nguyên nhân và 34 luật", base.causes().size() == 17 && base.rules().size() == 34);
        check("mọi luật có ít nhất một điều kiện mã lỗi", base.rules().stream().allMatch(x -> x.conditions().stream().anyMatch(c -> c.dtc() != null)));
        check("mọi luật tham chiếu nguyên nhân có thật", base.rules().stream().allMatch(x -> base.causes().containsKey(x.causeId())));
        check("điểm tin cậy trong (0, 1)", base.rules().stream().allMatch(x -> x.confidence() > 0 && x.confidence() < 1));
        InferenceEngine eng = new InferenceEngine(base);
        check("nguyên nhân đúng của mọi kịch bản có trong CSDL", ScenarioCatalog.ALL.stream().filter(s -> s.trueCause() != null)
                .allMatch(s -> base.causes().values().stream().anyMatch(c -> c.name().equals(s.trueCause()))));

        // Mỗi kịch bản với ảnh chụp PID điển hình: kỳ vọng hạng của nguyên nhân đúng
        Object[][] cases = {
                // id, DTC, ảnh chụp PID, hạng kỳ vọng
                { 1, Set.of("P0171"), pids(6, 1250, 0, 87, 25, 2.9, 13.8), 1 },
                { 2, Set.of("P0171"), pids(6, 950, 0, 87, 25, 1.4, 13.8), 1 },
                { 3, Set.of("P0171"), pids(30, 2100, 45, 87, 25, 7.0, 13.8), 3 },
                { 4, Set.of("P0300", "P0301"), pids(20, 1900, 25, 87, 25, 6.5, 13.8), 1 },
                { 5, Set.of("P0217"), pids(6, 950, 0, 110, 25, 2.8, 13.8), 1 },
                { 6, Set.of("P0117"), pids(10, 1000, 0, 215, 25, 2.8, 13.8), 1 },
                { 7, Set.of("P0500"), pids(60, 3600, 0, 87, 25, 20, 13.8), 1 },
                { 8, Set.of("P0562"), pids(35, 2600, 70, 87, 25, 12, 11.2), 1 },
        };
        for (Object[] c : cases) {
            ScenarioCatalog.Scenario s = ScenarioCatalog.ALL.get((Integer) c[0]);
            @SuppressWarnings("unchecked")
            List<Diagnosis> res = eng.diagnose((Set<String>) c[1], (Map<Integer, Double>) c[2]);
            int rank = rankOf(res, s.trueCause());
            StringBuilder top = new StringBuilder();
            for (int i = 0; i < Math.min(3, res.size()); i++) top.append(String.format("%s=%.3f; ", res.get(i).cause().name(), res.get(i).confidence()));
            check("kịch bản " + s.label() + ": nguyên nhân đúng hạng " + rank + " (kỳ vọng " + c[3] + ")  [" + top + "]", rank == (Integer) c[3]);
        }
        check("xe khỏe (không DTC) không bị kết luận", eng.diagnose(Set.of(), pids(30, 2000, 40, 88, 25, 6, 13.8)).isEmpty());

        // Các mã còn lại trong danh mục, không có kịch bản: kiểm tra riêng
        check("P0118 + nhiệt độ nước -40 -> cảm biến nhiệt độ nước hạng 1, 0,9325",
                near(eng.diagnose(Set.of("P0118"), pids(10, 900, 0, -40, 25, 2.6, 13.8)).get(0).confidence(), 0.9325));
        check("P0113 + khí nạp -40 -> cảm biến nhiệt độ khí nạp hạng 1",
                eng.diagnose(Set.of("P0113"), pids(10, 900, 0, 88, -40, 2.6, 13.8)).get(0).cause().name().startsWith("Cảm biến nhiệt độ khí nạp"));
        check("P0335 + vòng tua 0 -> cảm biến trục khuỷu, 0,94",
                near(eng.diagnose(Set.of("P0335"), pids(10, 0, 0, 88, 25, 2.6, 13.8)).get(0).confidence(), 0.94));
        check("P0420 -> bộ xúc tác hạng 1, O2 sau hạng 2",
                eng.diagnose(Set.of("P0420"), pids(10, 900, 0, 88, 25, 2.6, 13.8)).get(0).cause().name().equals("Bộ xúc tác xuống cấp"));
        check("P0172 -> kim phun rò hạng 1",
                eng.diagnose(Set.of("P0172"), pids(10, 900, 0, 88, 25, 2.6, 13.8)).get(0).cause().name().equals("Kim phun rò rỉ hoặc bẩn"));
        check("P0171 khi ECU chưa trả PID nào vẫn xếp theo mã lỗi (rò chân không hạng 1)",
                eng.diagnose(Set.of("P0171"), Map.of()).get(0).cause().name().startsWith("Rò rỉ chân không"));

        System.out.println(fail == 0 ? "TAT CA DAT" : ("CO " + fail + " LOI"));
        System.exit(fail == 0 ? 0 : 1);
    }
}
