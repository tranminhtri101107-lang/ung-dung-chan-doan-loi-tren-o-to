import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;

/**
 * Phân tích kết quả thực nghiệm chẩn đoán (CSV do ChayThucNghiem.java ghi ra, mỗi dòng là một lần chạy).
 * Đầu ra: các bảng tóm tắt (tom_tat_*.csv, tom_tat_thuc_nghiem.txt) và 2 biểu đồ PNG
 * (m-do-chinh-xac.png: độ chính xác theo kịch bản; m-do-tre.png: phân bố độ trễ đọc mã lỗi).
 * Chạy: xem chay-thuc-nghiem.ps1. Đối số: tệp CSV, thư mục ghi bảng tóm tắt, thư mục ghi hình.
 * Chỉ dùng thư viện chuẩn của Java (đọc tệp, tính thống kê, vẽ bằng Java2D).
 */
public class PhanTichThucNghiem {

    static final int SO_NGUYEN_NHAN = 17;   // số nguyên nhân khả dĩ trong cơ sở tri thức
    static final double Z = 1.959964;       // z của mức tin cậy 95 %

    // ================= Đọc CSV =================

    /** Tách một dòng CSV, hiểu dấu ngoặc kép (tên nguyên nhân có thể chứa dấu phẩy). */
    static List<String> tachDong(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean trongNgoac = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (trongNgoac && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    trongNgoac = !trongNgoac;
                }
            } else if (c == ',' && !trongNgoac) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    /** Một lần chạy: chỉ giữ các cột cần cho phân tích. */
    record Lan(int kichBan, String ten, boolean top1, boolean top3, boolean top1MaLoi, boolean top3MaLoi,
            int soUngVien, double injectMs, double req03Ms, double suyLuanUs) {
    }

    static List<Lan> docCsv(Path csv) throws Exception {
        List<String> dong = Files.readAllLines(csv, StandardCharsets.UTF_8);
        List<String> tieuDe = tachDong(dong.get(0).replace("﻿", ""));
        Map<String, Integer> cot = new HashMap<>();
        for (int i = 0; i < tieuDe.size(); i++) {
            cot.put(tieuDe.get(i), i);
        }
        List<Lan> ds = new ArrayList<>();
        for (String d : dong.subList(1, dong.size())) {
            if (d.isBlank()) {
                continue;
            }
            List<String> f = tachDong(d);
            ds.add(new Lan(Integer.parseInt(f.get(cot.get("scenario_id"))), f.get(cot.get("scenario")),
                    f.get(cot.get("top1_ok")).equals("1"), f.get(cot.get("top3_ok")).equals("1"),
                    f.get(cot.get("top1_dtc_only")).equals("1"), f.get(cot.get("top3_dtc_only")).equals("1"),
                    Integer.parseInt(f.get(cot.get("n_causes"))), Double.parseDouble(f.get(cot.get("inject_ms"))),
                    Double.parseDouble(f.get(cot.get("req03_ms"))), Double.parseDouble(f.get(cot.get("infer_us")))));
        }
        return ds;
    }

    // ================= Thống kê =================

    /** Khoảng tin cậy Wilson 95 % cho tỉ lệ s/n: {cận dưới, cận trên}. */
    static double[] wilson(int s, int n) {
        double p = (double) s / n;
        double c = (p + Z * Z / (2 * n)) / (1 + Z * Z / n);
        double h = Z * Math.sqrt(p * (1 - p) / n + Z * Z / (4.0 * n * n)) / (1 + Z * Z / n);
        return new double[] { Math.max(0, c - h), Math.min(1, c + h) };
    }

    static double trungBinh(double[] x) {
        return Arrays.stream(x).average().orElse(0);
    }

    /** Độ lệch chuẩn mẫu (chia n - 1). */
    static double doLechChuan(double[] x) {
        double tb = trungBinh(x);
        double s = 0;
        for (double v : x) {
            s += (v - tb) * (v - tb);
        }
        return Math.sqrt(s / (x.length - 1));
    }

    /** Phân vị p (0-100) theo nội suy tuyến tính giữa hai phần tử đã sắp xếp. */
    static double phanVi(double[] x, double p) {
        double[] s = x.clone();
        Arrays.sort(s);
        double pos = (s.length - 1) * p / 100;
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        return s[lo] + (pos - lo) * (s[hi] - s[lo]);
    }

    static double trungVi(double[] x) {
        return phanVi(x, 50);
    }

    static double[] layCot(List<Lan> ds, java.util.function.ToDoubleFunction<Lan> f) {
        return ds.stream().mapToDouble(f).toArray();
    }

    static int dem(List<Lan> ds, java.util.function.Predicate<Lan> f) {
        return (int) ds.stream().filter(f).count();
    }

    /** Thống kê một kịch bản lỗi. */
    record KichBan(int id, String ten, int n, double top1, double[] ci1, double top3, double[] ci3,
            double top1MaLoi, double top3MaLoi, double ungVien, double ngauNhienUv) {
    }

    // ================= Chương trình chính =================

    public static void main(String[] args) throws Exception {
        Path csv = Path.of(args.length > 0 ? args[0] : "du-lieu/ket_qua_thuc_nghiem.csv");
        Path outDir = Path.of(args.length > 1 ? args[1] : "du-lieu");
        Path hinhDir = Path.of(args.length > 2 ? args[2] : "ket-qua");
        Files.createDirectories(outDir);
        Files.createDirectories(hinhDir);

        List<Lan> tatCa = docCsv(csv);
        List<Lan> loi = tatCa.stream().filter(l -> l.kichBan() > 0).toList();   // kịch bản 0 là xe khỏe
        List<Lan> khoe = tatCa.stream().filter(l -> l.kichBan() == 0).toList();
        double ngauNhien1 = 1.0 / SO_NGUYEN_NHAN;
        double ngauNhien3 = 3.0 / SO_NGUYEN_NHAN;

        // ---- Theo từng kịch bản ----
        Map<Integer, List<Lan>> theoKb = new TreeMap<>();
        for (Lan l : loi) {
            theoKb.computeIfAbsent(l.kichBan(), k -> new ArrayList<>()).add(l);
        }
        List<KichBan> kb = new ArrayList<>();
        for (var e : theoKb.entrySet()) {
            List<Lan> s = e.getValue();
            int n = s.size();
            int d1 = dem(s, Lan::top1);
            int d3 = dem(s, Lan::top3);
            kb.add(new KichBan(e.getKey(), s.get(0).ten(), n, (double) d1 / n, wilson(d1, n), (double) d3 / n,
                    wilson(d3, n), (double) dem(s, Lan::top1MaLoi) / n, (double) dem(s, Lan::top3MaLoi) / n,
                    trungBinh(layCot(s, Lan::soUngVien)), trungBinh(layCot(s, l -> 1.0 / l.soUngVien()))));
        }
        StringBuilder sb = new StringBuilder("id,ten,n,top1,top1_lo,top1_hi,top3,top3_lo,top3_hi,top1_dtc,top3_dtc,ung_vien,ngau_nhien_uv\n");
        for (KichBan k : kb) {
            sb.append(String.join(",", String.valueOf(k.id()), k.ten(), String.valueOf(k.n()), so(k.top1()),
                    so(k.ci1()[0]), so(k.ci1()[1]), so(k.top3()), so(k.ci3()[0]), so(k.ci3()[1]), so(k.top1MaLoi()),
                    so(k.top3MaLoi()), so(k.ungVien()), so(k.ngauNhienUv()))).append('\n');
        }
        Files.writeString(outDir.resolve("tom_tat_theo_kich_ban.csv"), sb, StandardCharsets.UTF_8);

        // ---- Toàn bộ ----
        int n = loi.size();
        int s1 = dem(loi, Lan::top1), s3 = dem(loi, Lan::top3);
        int d1 = dem(loi, Lan::top1MaLoi), d3 = dem(loi, Lan::top3MaLoi);
        double[] ci1 = wilson(s1, n), ci3 = wilson(s3, n), cid1 = wilson(d1, n), cid3 = wilson(d3, n);
        int khoeBaoSai = dem(khoe, l -> l.soUngVien() > 0);   // xe khỏe mà vẫn có kết luận
        double ngauNhienUv = trungBinh(layCot(loi, l -> 1.0 / l.soUngVien()));

        // ---- Độ trễ ----
        double[] req03 = layCot(tatCa, Lan::req03Ms);
        double[] inject = layCot(tatCa, Lan::injectMs);
        double[] suyLuan = layCot(tatCa, Lan::suyLuanUs);
        StringBuilder tr = new StringBuilder("Row,tb,dlc,trung_vi,p95,max,min\n");
        for (var e : Map.of("req03_ms", req03, "inject_ms", inject, "infer_us", suyLuan).entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(k -> List.of("req03_ms", "inject_ms", "infer_us").indexOf(k))))
                .toList()) {
            double[] x = e.getValue();
            tr.append(String.join(",", e.getKey(), so(trungBinh(x)), so(doLechChuan(x)), so(trungVi(x)), so(phanVi(x, 95)),
                    so(Arrays.stream(x).max().orElse(0)), so(Arrays.stream(x).min().orElse(0)))).append('\n');
        }
        Files.writeString(outDir.resolve("tom_tat_do_tre.csv"), tr, StandardCharsets.UTF_8);
        // Ngoại lai: lớn hơn trung vị + 3 × 1,4826 × MAD (độ lệch tuyệt đối trung vị)
        double tv = trungVi(req03);
        double mad = trungVi(Arrays.stream(req03).map(v -> Math.abs(v - tv)).toArray());
        int ngoaiLai = (int) Arrays.stream(req03).filter(v -> v > tv + 3 * 1.4826 * mad).count();

        String tong = "n,top1_dung,top1_lo,top1_hi,top3_dung,top3_lo,top3_hi,top1_dtc_dung,top1_dtc_lo,top1_dtc_hi,"
                + "top3_dtc_dung,top3_dtc_lo,top3_dtc_hi,ngau_nhien_1,ngau_nhien_3,khoe_n,khoe_bao_sai,ngoai_lai,tong_lan,ngau_nhien_uv\n"
                + String.join(",", String.valueOf(n), String.valueOf(s1), so(ci1[0]), so(ci1[1]), String.valueOf(s3), so(ci3[0]),
                        so(ci3[1]), String.valueOf(d1), so(cid1[0]), so(cid1[1]), String.valueOf(d3), so(cid3[0]), so(cid3[1]),
                        so(ngauNhien1), so(ngauNhien3), String.valueOf(khoe.size()), String.valueOf(khoeBaoSai),
                        String.valueOf(ngoaiLai), String.valueOf(tatCa.size()), so(ngauNhienUv))
                + "\n";
        Files.writeString(outDir.resolve("tom_tat_tong.csv"), tong, StandardCharsets.UTF_8);

        // ---- Văn bản tóm tắt ----
        String txt = String.join("\n",
                f("Tổng số lần chạy: %d (kịch bản lỗi: %d, xe khỏe: %d)", tatCa.size(), n, khoe.size()),
                f("Đoán ngẫu nhiên (%d nguyên nhân): hạng 1 = %.1f%%, top-3 = %.1f%%", SO_NGUYEN_NHAN, 100 * ngauNhien1, 100 * ngauNhien3),
                f("Đoán ngẫu nhiên hạng 1 trong ứng viên của mã lỗi (trung bình): %.1f%%", 100 * ngauNhienUv),
                f("Hạng 1: %d/%d = %.1f%% (KTC 95%% %.1f-%.1f)", s1, n, 100.0 * s1 / n, 100 * ci1[0], 100 * ci1[1]),
                f("Top-3 : %d/%d = %.1f%% (KTC 95%% %.1f-%.1f)", s3, n, 100.0 * s3 / n, 100 * ci3[0], 100 * ci3[1]),
                f("Chỉ dùng mã lỗi (bỏ luật PID): hạng 1 = %.1f%% (KTC %.1f-%.1f), top-3 = %.1f%% (KTC %.1f-%.1f)",
                        100.0 * d1 / n, 100 * cid1[0], 100 * cid1[1], 100.0 * d3 / n, 100 * cid3[0], 100 * cid3[1]),
                f("Xe khỏe có kết luận sai: %.1f%% (%d/%d)", 100.0 * khoeBaoSai / khoe.size(), khoeBaoSai, khoe.size()),
                f("Khứ hồi REQ 03 -> RSP 03 (ms): TB %.3f, độ lệch chuẩn %.3f, trung vị %.3f, P95 %.3f, lớn nhất %.3f, nhỏ nhất %.3f; ngoại lai %d",
                        trungBinh(req03), doLechChuan(req03), tv, phanVi(req03, 95), Arrays.stream(req03).max().orElse(0),
                        Arrays.stream(req03).min().orElse(0), ngoaiLai),
                f("Khứ hồi INJECT (ms): TB %.3f, độ lệch chuẩn %.3f, trung vị %.3f, P95 %.3f", trungBinh(inject),
                        doLechChuan(inject), trungVi(inject), phanVi(inject, 95)),
                f("Thời gian suy luận (µs): TB %.1f, trung vị %.1f, P95 %.1f, lớn nhất %.1f", trungBinh(suyLuan),
                        trungVi(suyLuan), phanVi(suyLuan, 95), Arrays.stream(suyLuan).max().orElse(0))) + "\n";
        Files.writeString(outDir.resolve("tom_tat_thuc_nghiem.txt"), txt, StandardCharsets.UTF_8);

        veDoChinhXac(kb, ngauNhien1, ngauNhien3, hinhDir.resolve("m-do-chinh-xac.png").toFile());
        veDoTre(req03, hinhDir.resolve("m-do-tre.png").toFile());
        System.out.print(txt);
    }

    static String so(double v) {
        return String.valueOf(v);
    }

    static String f(String mau, Object... v) {
        return String.format(Locale.ROOT, mau, v);
    }

    /** Số thập phân kiểu Việt (dấu phẩy) cho nhãn trên hình. */
    static String vi(String s) {
        return s.replace('.', ',');
    }

    // ================= Vẽ biểu đồ (Java2D) =================

    static final Color XANH = new Color(46, 115, 199);
    static final Color CAM = new Color(237, 158, 51);
    static final Color LUOI = new Color(225, 225, 225);
    static final int K = 2;   // hệ số phóng: vẽ ở độ phân giải gấp đôi cho nét in rõ

    /** Khung chung: nền trắng, trục, lưới ngang 0-100 %, tiêu đề. Trả về vùng vẽ {x, y, w, h}. */
    static Graphics2D taoHinh(BufferedImage img, String tieuDe) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(K, K);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("Segoe UI", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(tieuDe, (img.getWidth() / K - fm.stringWidth(tieuDe)) / 2, 24);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        return g;
    }

    static void chuGiua(Graphics2D g, String s, double x, double y) {
        g.drawString(s, (float) (x - g.getFontMetrics().stringWidth(s) / 2.0), (float) y);
    }

    static void chuDoc(Graphics2D g, String s, double x, double y) {
        var old = g.getTransform();
        g.translate(x, y);
        g.rotate(-Math.PI / 2);
        chuGiua(g, s, 0, 0);
        g.setTransform(old);
    }

    static void veDoChinhXac(List<KichBan> kb, double nn1, double nn3, File out) throws Exception {
        int W = 900, H = 450;
        BufferedImage img = new BufferedImage(W * K, H * K, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = taoHinh(img, f("Độ chính xác chẩn đoán theo kịch bản (%d lần mỗi kịch bản, khoảng tin cậy Wilson 95 %%)",
                kb.isEmpty() ? 0 : kb.get(0).n()));
        int x0 = 70, y0 = 45, w = W - x0 - 25, h = 280;   // vùng vẽ
        java.util.function.DoubleUnaryOperator Y = v -> y0 + h - v / 1.1 * h;
        // Lưới và trục tung 0-100 %
        for (int p = 0; p <= 100; p += 20) {
            double y = Y.applyAsDouble(p / 100.0);
            g.setColor(LUOI);
            g.drawLine(x0, (int) y, x0 + w, (int) y);
            g.setColor(Color.BLACK);
            String s = p + " %";
            g.drawString(s, x0 - 8 - g.getFontMetrics().stringWidth(s), (int) y + 4);
        }
        g.drawRect(x0, y0, w, h);
        chuDoc(g, "Tỉ lệ nguyên nhân đúng", 18, y0 + h / 2.0);
        // Cột: hạng 1 (xanh) và trong 3 hạng đầu (cam), có thanh sai số là khoảng tin cậy
        double nhom = (double) w / kb.size();
        double bw = nhom * 0.34;
        for (int i = 0; i < kb.size(); i++) {
            KichBan k = kb.get(i);
            double cx = x0 + nhom * (i + 0.5);
            veCot(g, cx - bw, bw, k.top1(), k.ci1(), XANH, Y);
            veCot(g, cx, bw, k.top3(), k.ci3(), CAM, Y);
            // Mốc: đoán ngẫu nhiên hạng 1 trong ứng viên của mã lỗi (hình thoi)
            veHinhThoi(g, cx - bw / 2, Y.applyAsDouble(k.ngauNhienUv()));
            g.setColor(Color.BLACK);
            chuGiua(g, "KB" + k.id(), cx, y0 + h + 18);
        }
        chuGiua(g, "Kịch bản", x0 + w / 2.0, y0 + h + 36);
        // Đường mốc đoán ngẫu nhiên trong 17 nguyên nhân
        BasicStroke gach = new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 6, 4 }, 0);
        g.setStroke(gach);
        g.setColor(XANH);
        g.drawLine(x0, (int) Y.applyAsDouble(nn1), x0 + w, (int) Y.applyAsDouble(nn1));
        g.setColor(CAM);
        g.drawLine(x0, (int) Y.applyAsDouble(nn3), x0 + w, (int) Y.applyAsDouble(nn3));
        g.setStroke(new BasicStroke(1));
        // Chú thích hai cột dưới hình
        int ly = y0 + h + 58;
        oChuThich(g, x0, ly, XANH, "Hạng 1", false);
        oChuThich(g, x0 + 420, ly, CAM, "Trong 3 hạng đầu", false);
        oChuThich(g, x0, ly + 20, XANH, vi(f("Đoán ngẫu nhiên hạng 1 trong 17 nguyên nhân (%.1f %%)", 100 * nn1)), true);
        oChuThich(g, x0 + 420, ly + 20, CAM, vi(f("Đoán ngẫu nhiên top-3 trong 17 nguyên nhân (%.1f %%)", 100 * nn3)), true);
        veHinhThoi(g, x0 + 9, ly + 40 - 4);
        g.setColor(Color.BLACK);
        g.drawString("Đoán ngẫu nhiên hạng 1 trong ứng viên của mã lỗi", x0 + 26, ly + 40);
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    static void veCot(Graphics2D g, double xTrai, double bw, double v, double[] ci,
            Color mau, java.util.function.DoubleUnaryOperator Y) {
        double yv = Y.applyAsDouble(v), y0 = Y.applyAsDouble(0);
        g.setColor(mau);
        g.fill(new java.awt.geom.Rectangle2D.Double(xTrai, yv, bw, y0 - yv));
        // Thanh sai số: khoảng tin cậy Wilson
        double cx = xTrai + bw / 2, lo = Y.applyAsDouble(ci[0]), hi = Y.applyAsDouble(ci[1]);
        g.setColor(Color.BLACK);
        g.draw(new java.awt.geom.Line2D.Double(cx, lo, cx, hi));
        g.draw(new java.awt.geom.Line2D.Double(cx - 4, lo, cx + 4, lo));
        g.draw(new java.awt.geom.Line2D.Double(cx - 4, hi, cx + 4, hi));
    }

    static void veHinhThoi(Graphics2D g, double cx, double cy) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy - 6);
        p.lineTo(cx + 5, cy);
        p.lineTo(cx, cy + 6);
        p.lineTo(cx - 5, cy);
        p.closePath();
        g.setColor(Color.WHITE);
        g.fill(p);
        g.setColor(Color.BLACK);
        g.draw(p);
    }

    static void oChuThich(Graphics2D g, int x, int y, Color mau, String chu, boolean laDuong) {
        g.setColor(mau);
        if (laDuong) {
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 6, 4 }, 0));
            g.drawLine(x, y - 4, x + 18, y - 4);
            g.setStroke(new BasicStroke(1));
        } else {
            g.fillRect(x, y - 10, 18, 10);
        }
        g.setColor(Color.BLACK);
        g.drawString(chu, x + 26, y);
    }

    static void veDoTre(double[] lat, File out) throws Exception {
        int W = 900, H = 400;
        BufferedImage img = new BufferedImage(W * K, H * K, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = taoHinh(img, f("Phân bố độ trễ đọc mã lỗi (%d lần đo)", lat.length));
        int x0 = 70, y0 = 45, w = W - x0 - 25, h = 280;
        // 40 khoảng đều từ nhỏ nhất tới phân vị 99,5 (bỏ đuôi ngoại lai để hình không bị dẹt)
        double min = Arrays.stream(lat).min().orElse(0);
        double max = Math.min(Arrays.stream(lat).max().orElse(1), phanVi(lat, 99.5));
        int soKhoang = 40;
        int[] dem = new int[soKhoang];
        for (double v : lat) {
            if (v > max) {
                continue;
            }
            dem[Math.min(soKhoang - 1, (int) ((v - min) / (max - min) * soKhoang))]++;
        }
        int demMax = Arrays.stream(dem).max().orElse(1);
        int tran = (int) Math.ceil(demMax / 5.0) * 5;
        java.util.function.DoubleUnaryOperator X = v -> x0 + (v - min) / (max - min) * w;
        // Lưới ngang và trục tung (số lần)
        for (int c = 0; c <= tran; c += Math.max(1, tran / 5)) {
            int y = (int) (y0 + h - (double) c / tran * h);
            g.setColor(LUOI);
            g.drawLine(x0, y, x0 + w, y);
            g.setColor(Color.BLACK);
            String s = String.valueOf(c);
            g.drawString(s, x0 - 8 - g.getFontMetrics().stringWidth(s), y + 4);
        }
        double bw = (double) w / soKhoang;
        g.setColor(XANH);
        for (int i = 0; i < soKhoang; i++) {
            double bh = (double) dem[i] / tran * h;
            g.fill(new java.awt.geom.Rectangle2D.Double(x0 + i * bw + 0.5, y0 + h - bh, bw - 1, bh));
        }
        g.setColor(Color.BLACK);
        g.drawRect(x0, y0, w, h);
        // Trục hoành: 6 vạch chia
        for (int i = 0; i <= 5; i++) {
            double v = min + (max - min) * i / 5;
            chuGiua(g, vi(f("%.2f", v)), X.applyAsDouble(v), y0 + h + 18);
        }
        chuGiua(g, "Độ trễ khứ hồi REQ 03 → RSP 03 (ms)", x0 + w / 2.0, y0 + h + 38);
        chuDoc(g, "Số lần đo", 18, y0 + h / 2.0);
        // Đường trung vị (đen) và P95 (đỏ)
        double tv = trungVi(lat), p95 = phanVi(lat, 95);
        BasicStroke gach = new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 6, 4 }, 0);
        g.setStroke(gach);
        g.setColor(Color.BLACK);
        g.draw(new java.awt.geom.Line2D.Double(X.applyAsDouble(tv), y0, X.applyAsDouble(tv), y0 + h));
        g.setColor(Color.RED);
        g.draw(new java.awt.geom.Line2D.Double(X.applyAsDouble(p95), y0, X.applyAsDouble(p95), y0 + h));
        // Chú thích góc trên phải
        int lx = x0 + w - 170, ly = y0 + 22;
        g.setColor(Color.WHITE);
        g.fillRect(lx - 8, ly - 16, 170, 44);
        g.setColor(Color.BLACK);
        g.drawLine(lx, ly - 4, lx + 18, ly - 4);
        g.setColor(Color.RED);
        g.drawLine(lx, ly + 16, lx + 18, ly + 16);
        g.setStroke(new BasicStroke(1));
        g.setColor(Color.BLACK);
        g.drawString(vi(f("Trung vị %.2f ms", tv)), lx + 26, ly);
        g.drawString(vi(f("P95 %.2f ms", p95)), lx + 26, ly + 20);
        g.dispose();
        ImageIO.write(img, "png", out);
    }
}
