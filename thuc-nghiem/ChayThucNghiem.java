import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import app.AppConfig;
import dao.Database;
import dao.RuleDao;
import model.Diagnosis;
import model.Dtc;
import model.Rule;
import model.RuleBase;
import service.GatewayDataSource;
import service.InferenceEngine;
import service.ScenarioCatalog;

/**
 * Chạy thực nghiệm trên ECU mô phỏng thật qua Gateway (docs/giao-thuc/KICH-BAN.md):
 * mỗi kịch bản lặp N lần theo thứ tự xáo trộn (cố định hạt giống 42 để lặp lại được), mỗi lần:
 *   INJECT k -> chờ ổn định -> đọc DTC (đo khứ hồi REQ 03 -> RSP 03) -> chụp 8 PID -> chạy bộ suy luận -> ghi một dòng CSV.
 * Cách chạy: xem run-thuc-nghiem.ps1. Đối số: tệp CSV đầu ra, số lần mỗi kịch bản (mặc định 30).
 */
public class ChayThucNghiem {

    static final long SETTLE_MS = 2500;  // chờ ECU/Gateway cập nhật PID sau khi đổi kịch bản

    /** Hạng (từ 1) của nguyên nhân đúng trong kết quả, 0 nếu không có. */
    static int rankOf(List<Diagnosis> result, String trueCause) {
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).cause().name().equals(trueCause)) {
                return i + 1;
            }
        }
        return 0;
    }

    static String csv(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\"";
    }

    static String num(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        Locale.setDefault(Locale.ROOT);

        AppConfig config = new AppConfig();
        GatewayDataSource source = new GatewayDataSource(config.get("gateway.host", "192.168.35.128"),
                config.getInt("gateway.port", 5000));
        for (int i = 0; i < 50 && !source.isConnected(); i++) {
            Thread.sleep(100);
        }
        if (!source.isConnected()) {
            throw new IllegalStateException("Không nối được Gateway");
        }
        RuleBase base = new RuleDao(new Database()).load();
        InferenceEngine engine = new InferenceEngine(base);
        // Đối chứng: chỉ dùng luật theo mã lỗi (bỏ mọi luật có điều kiện PID) để thấy dữ liệu sống đóng góp bao nhiêu
        List<Rule> dtcOnly = base.rules().stream()
                .filter(r -> r.conditions().stream().allMatch(c -> c.dtc() != null)).toList();
        InferenceEngine engineDtcOnly = new InferenceEngine(new RuleBase(base.causes(), dtcOnly));

        // Danh sách lượt chạy: mỗi kịch bản (kể cả xe khỏe) n lần, xáo trộn để tránh ảnh hưởng của kịch bản liền trước
        List<Integer> order = new ArrayList<>();
        for (ScenarioCatalog.Scenario s : ScenarioCatalog.ALL) {
            for (int i = 0; i < n; i++) {
                order.add(s.id());
            }
        }
        Collections.shuffle(order, new Random(42));

        out.getParentFile().mkdirs();
        try (BufferedWriter w = Files.newBufferedWriter(out.toPath(), StandardCharsets.UTF_8)) {
            w.write("run,scenario_id,scenario,true_cause,dtcs,n_dtc,top1,top1_score,rank_true,top1_ok,top3_ok,n_causes,"
                    + "rank_dtc_only,top1_dtc_only,top3_dtc_only,inject_ms,req03_ms,infer_us,thr,rpm,speed,coolant,iat,maf,volt\n");
            int run = 0;
            for (int id : order) {
                run++;
                ScenarioCatalog.Scenario s = ScenarioCatalog.ALL.get(id);
                long t0 = System.nanoTime();
                source.inject(id);
                double injectMs = (System.nanoTime() - t0) / 1e6;
                Thread.sleep(SETTLE_MS);

                long t1 = System.nanoTime();
                List<Dtc> dtcs = source.readDtcs();
                double req03Ms = (System.nanoTime() - t1) / 1e6;
                Map<Integer, Double> pids = source.readPids();

                Set<String> codes = new TreeSet<>();
                dtcs.forEach(d -> codes.add(d.code()));
                long t2 = System.nanoTime();
                List<Diagnosis> result = engine.diagnose(codes, pids);
                double inferUs = (System.nanoTime() - t2) / 1e3;

                int rank = rankOf(result, s.trueCause());  // 0 = nguyên nhân đúng không có trong kết quả
                boolean top1 = rank == 1, top3 = rank >= 1 && rank <= 3;
                int rankD = rankOf(engineDtcOnly.diagnose(codes, pids), s.trueCause());
                boolean top1D = rankD == 1, top3D = rankD >= 1 && rankD <= 3;
                w.write(String.join(",", String.valueOf(run), String.valueOf(id), csv(s.name()), csv(s.trueCause()),
                        csv(String.join(" ", codes)), String.valueOf(codes.size()),
                        csv(result.isEmpty() ? "" : result.get(0).cause().name()),
                        result.isEmpty() ? "" : num(result.get(0).confidence()), String.valueOf(rank),
                        s.trueCause() == null ? "" : (top1 ? "1" : "0"), s.trueCause() == null ? "" : (top3 ? "1" : "0"),
                        String.valueOf(result.size()), String.valueOf(rankD),
                        s.trueCause() == null ? "" : (top1D ? "1" : "0"), s.trueCause() == null ? "" : (top3D ? "1" : "0"),
                        num(injectMs), num(req03Ms), num(inferUs),
                        num(pids.getOrDefault(0x11, Double.NaN)), num(pids.getOrDefault(0x0C, Double.NaN)),
                        num(pids.getOrDefault(0x0D, Double.NaN)), num(pids.getOrDefault(0x05, Double.NaN)),
                        num(pids.getOrDefault(0x0F, Double.NaN)), num(pids.getOrDefault(0x10, Double.NaN)),
                        num(pids.getOrDefault(0x42, Double.NaN))) + "\n");
                w.flush();
                if (run % 20 == 0) {
                    System.out.println("Đã chạy " + run + "/" + order.size());
                }
            }
        }
        source.inject(0);  // trả ECU về xe khỏe
        System.out.println("Xong, ghi " + out.getAbsolutePath());
        System.exit(0);
    }
}
