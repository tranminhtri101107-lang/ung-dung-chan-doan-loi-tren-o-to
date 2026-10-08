package view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

/** Ô biển số vẽ giống biển xe Việt Nam (nền trắng, viền đen, chữ đậm). Chưa có biển số thì vẽ ô gạch đứt. */
public class PlateBadge extends JComponent {

    private static final String NONE = "CHƯA CHỌN XE";
    private final float fontSize;
    private String plate;

    public PlateBadge(float fontSize) {
        this.fontSize = fontSize;
        setOpaque(false);
        setPlate(null);
    }

    public void setPlate(String plate) {
        this.plate = plate;
        FontMetrics fm = getFontMetrics(font(plate));
        String text = plate == null ? NONE : plate;
        setPreferredSize(new Dimension(fm.stringWidth(text) + 30, Math.round(fontSize * 1.9f)));
        setMinimumSize(getPreferredSize());
        revalidate();
        repaint();
    }

    /** Biển số dùng phông kiểu DIN; dòng "CHƯA CHỌN XE" có dấu nên dùng Segoe UI. */
    private Font font(String p) {
        return p == null ? new Font("Segoe UI", Font.BOLD, Math.round(fontSize * 0.75f)) : Theme.display(Font.BOLD, fontSize);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth() - 3;
        int h = getHeight() - 3;
        g.setFont(font(plate));
        FontMetrics fm = g.getFontMetrics();
        String text = plate == null ? NONE : plate;
        if (plate == null) {
            g.setColor(Theme.TEXT_FAINT);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 5, 4 }, 0));
            g.drawRect(1, 1, w, h);
        } else {
            g.setColor(Color.WHITE);
            g.fillRoundRect(1, 1, w, h, 6, 6);
            g.setColor(Theme.TEXT);
            g.setStroke(new BasicStroke(2.2f));
            g.drawRoundRect(1, 1, w, h, 6, 6);
        }
        g.drawString(text, 1 + (w - fm.stringWidth(text)) / 2, 1 + (h + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
    }
}
