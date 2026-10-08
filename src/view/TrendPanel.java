package view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.swing.JPanel;

/** Biểu đồ đường cuộn theo thời gian thực kiểu máy hiện sóng: lưới mảnh, nhãn trục, giá trị mới nhất. */
public class TrendPanel extends JPanel {

    private static final int MAX_POINTS = 120;   // 120 mẫu x 0,5 giây = 1 phút gần nhất

    private final String title;
    private final String unit;
    private final double min;
    private final double max;
    private final Color color;
    private final Deque<Double> points = new ArrayDeque<>();

    public TrendPanel(String title, String unit, double min, double max, Color color) {
        this.title = title;
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.color = color;
        setOpaque(false);
    }

    public void addPoint(double v) {
        if (points.size() >= MAX_POINTS) {
            points.removeFirst();
        }
        points.addLast(v);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int left = 44;
        int top = 30;
        int w = getWidth() - left - 6;
        int h = getHeight() - top - 20;

        g.setFont(Theme.CAPS);
        g.setColor(Theme.TEXT_DIM);
        g.drawString(title.toUpperCase(java.util.Locale.of("vi")), 0, 12);
        if (!points.isEmpty()) {
            String last = Math.round(points.getLast()) + " " + unit;
            g.setFont(Theme.display(java.awt.Font.BOLD, 15));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(color);
            g.drawString(last, getWidth() - fm.stringWidth(last) - 6, 14);
        }

        // Lưới và nhãn trục tung
        g.setFont(Theme.SMALL);
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i <= 4; i++) {
            int gy = top + h * i / 4;
            g.setColor(i == 4 ? Theme.BORDER : Theme.RULE);
            g.drawLine(left, gy, left + w, gy);
            String s = String.valueOf((int) Math.round(max - (max - min) * i / 4));
            g.setColor(Theme.TEXT_FAINT);
            g.drawString(s, left - 6 - fm.stringWidth(s), gy + fm.getAscent() / 2 - 1);
        }
        for (int i = 0; i <= 6; i++) {
            int gx = left + w * i / 6;
            g.setColor(Theme.RULE);
            g.drawLine(gx, top, gx, top + h);
        }
        g.setColor(Theme.TEXT_FAINT);
        g.drawString("-60 s", left, top + h + 15);
        String now = "bây giờ";
        g.drawString(now, left + w - fm.stringWidth(now), top + h + 15);

        if (points.size() >= 2) {
            Path2D line = new Path2D.Double();
            double stepX = (double) w / (MAX_POINTS - 1);
            double x0 = left + w - (points.size() - 1) * stepX;
            int i = 0;
            double px = x0;
            for (double v : points) {
                px = x0 + i * stepX;
                double py = top + h * (1 - (Math.max(min, Math.min(max, v)) - min) / (max - min));
                if (i == 0) {
                    line.moveTo(px, py);
                } else {
                    line.lineTo(px, py);
                }
                i++;
            }
            Path2D area = new Path2D.Double(line);
            area.lineTo(px, top + h);
            area.lineTo(x0, top + h);
            area.closePath();
            g.setPaint(new GradientPaint(0, top, new Color(color.getRed(), color.getGreen(), color.getBlue(), 60),
                    0, top + h, new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)));
            g.fill(area);
            g.setColor(color);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(line);
        }
        g.dispose();
    }
}
