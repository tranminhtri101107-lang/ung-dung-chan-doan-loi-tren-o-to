package view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.Locale;

import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Đồng hồ kiểu đồng hồ cơ trên táp-lô: cung 240 độ có vạch chia, vùng cảnh báo (hổ phách) và nguy hiểm (đỏ),
 * kim chạy mượt tới giá trị mới (không nhảy cóc), số hiển thị bên dưới.
 */
public class GaugePanel extends JPanel {

    private static final double START = 210; // độ, tính từ trục +X ngược chiều kim đồng hồ
    private static final double SWEEP = 240;

    private final String title;
    private final String unit;
    private final double min;
    private final double max;
    private final double warnAt;
    private final double dangerAt;
    private final double majorStep;
    private double shown;   // giá trị kim đang chỉ (đang chuyển động)
    private double target;  // giá trị thật mới nhất
    private Timer anim;

    public GaugePanel(String title, String unit, double min, double max, double warnAt, double dangerAt, double majorStep) {
        this.title = title;
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.warnAt = warnAt;
        this.dangerAt = dangerAt;
        this.majorStep = majorStep;
        this.shown = min;
        this.target = min;
        setOpaque(false);
        setPreferredSize(new Dimension(230, 230));
    }

    public void setValue(double v) {
        double to = Math.max(min, Math.min(max, v));
        if (Math.abs(to - target) < 1e-9) {
            return;
        }
        target = to;
        double from = shown;
        if (anim != null) {
            anim.stop();
        }
        anim = Anim.run(450, t -> {
            shown = Anim.lerp(from, to, t);
            repaint();
        }, null);
    }

    private double angleOf(double v) {
        return Math.toRadians(START - SWEEP * (v - min) / (max - min));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int size = Math.min(getWidth(), getHeight() - 30) - 16;
        double cx = getWidth() / 2.0;
        double cy = 8 + size / 2.0 + 4;
        double r = size / 2.0;

        // Vùng màu trên viền ngoài: cảnh báo và nguy hiểm
        float band = Math.max(5f, size / 30f);
        double rb = r - band / 2;
        g.setStroke(new BasicStroke(band, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(new Color(0xE7E4DE));
        g.draw(new Arc2D.Double(cx - rb, cy - rb, 2 * rb, 2 * rb, START, -SWEEP, Arc2D.OPEN));
        g.setColor(Theme.WARN);
        g.draw(arc(cx, cy, rb, warnAt, dangerAt));
        g.setColor(Theme.DANGER);
        g.draw(arc(cx, cy, rb, dangerAt, max));

        // Vạch chia: vạch lớn mỗi majorStep, 4 vạch nhỏ ở giữa
        double minor = majorStep / 5;
        g.setFont(Theme.display(Font.PLAIN, Math.max(9f, size / 19f)));
        FontMetrics fm = g.getFontMetrics();
        for (double v = min; v <= max + 1e-6; v += minor) {
            boolean major = Math.abs(Math.IEEEremainder(v - min, majorStep)) < 1e-6;
            double a = angleOf(v);
            double r1 = r - band - 2;
            double r2 = r1 - (major ? size / 13.0 : size / 26.0);
            g.setColor(major ? Theme.TEXT : Theme.TEXT_FAINT);
            g.setStroke(new BasicStroke(major ? 2f : 1f));
            g.draw(new Line2D.Double(cx + r1 * Math.cos(a), cy - r1 * Math.sin(a), cx + r2 * Math.cos(a), cy - r2 * Math.sin(a)));
            if (major) {
                String s = label(v);
                double rt = r2 - size / 14.0;
                g.setColor(Theme.TEXT_DIM);
                g.drawString(s, (float) (cx + rt * Math.cos(a) - fm.stringWidth(s) / 2.0),
                        (float) (cy - rt * Math.sin(a) + fm.getAscent() / 2.5));
            }
        }

        // Kim: thân than chì, mũi cam
        double a = angleOf(shown);
        double len = r - band - size / 9.0;
        double back = size / 14.0;
        double w = Math.max(2.5, size / 60.0);
        Path2D needle = new Path2D.Double();
        needle.moveTo(cx + len * Math.cos(a), cy - len * Math.sin(a));
        needle.lineTo(cx + w * Math.cos(a + Math.PI / 2), cy - w * Math.sin(a + Math.PI / 2));
        needle.lineTo(cx - back * Math.cos(a), cy + back * Math.sin(a));
        needle.lineTo(cx + w * Math.cos(a - Math.PI / 2), cy - w * Math.sin(a - Math.PI / 2));
        needle.closePath();
        g.setColor(Theme.ACCENT);
        g.fill(needle);
        double hub = size / 18.0;
        g.setColor(Theme.SIDEBAR);
        g.fill(new Ellipse2D.Double(cx - hub, cy - hub, 2 * hub, 2 * hub));

        // Số đọc được và đơn vị (màu theo vùng)
        Color valueColor = target >= dangerAt ? Theme.DANGER : target >= warnAt ? Theme.WARN : Theme.TEXT;
        String val = String.valueOf((int) Math.round(target));
        g.setFont(Theme.display(Font.BOLD, Math.max(16f, size / 7.5f)));
        fm = g.getFontMetrics();
        float vy = (float) (cy + r * 0.58);
        g.setColor(valueColor);
        g.drawString(val, (float) (cx - fm.stringWidth(val) / 2.0), vy);
        g.setFont(Theme.CAPS);
        fm = g.getFontMetrics();
        String u = unit.toUpperCase(Locale.ROOT);
        g.setColor(Theme.TEXT_DIM);
        g.drawString(u, (float) (cx - fm.stringWidth(u) / 2.0), vy + fm.getHeight());

        // Tên đồng hồ ở dưới cùng
        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        fm = g.getFontMetrics();
        String t = title.toUpperCase(Locale.of("vi"));
        g.setColor(Theme.TEXT);
        g.drawString(t, (float) (cx - fm.stringWidth(t) / 2.0), (float) Math.min(getHeight() - 6, cy + r + 18));
        g.dispose();
    }

    private Arc2D arc(double cx, double cy, double rb, double from, double to) {
        double a0 = START - SWEEP * (from - min) / (max - min);
        double a1 = START - SWEEP * (to - min) / (max - min);
        return new Arc2D.Double(cx - rb, cy - rb, 2 * rb, 2 * rb, a0, a1 - a0, Arc2D.OPEN);
    }

    /** Nhãn số trên mặt đồng hồ: vòng tua ghi theo nghìn (0 1 2 ... 7) cho gọn. */
    private String label(double v) {
        if (max >= 1000) {
            return String.valueOf((int) Math.round(v / 1000));
        }
        return String.valueOf((int) Math.round(v));
    }
}
