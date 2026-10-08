package view;

import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import model.Severity;

/** Vẽ mức độ nghiêm trọng thành nhãn kiểu tem (khung màu, chữ in hoa) trong ô bảng. */
public class SeverityTag extends DefaultTableCellRenderer {

    private Color color = Theme.TEXT_FAINT;
    private String label = "";

    public static Color colorOf(Severity s) {
        if (s == null) {
            return Theme.TEXT_FAINT;
        }
        return switch (s) {
            case CRITICAL -> Theme.DANGER;
            case WARNING -> Theme.WARN;
            case INFO -> Theme.INFO;
        };
    }

    public static String labelOf(Severity s) {
        return s == null ? "CHƯA PHÂN LOẠI" : s.label().toUpperCase(Locale.of("vi"));
    }

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
        super.getTableCellRendererComponent(t, "", sel, false, r, c);
        Severity s = v instanceof Severity sv ? sv : null;
        color = colorOf(s);
        label = labelOf(s);
        return this;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        paintTag((Graphics2D) g0, label, color, 10, (getHeight() - 20) / 2);
    }

    /** Vẽ một nhãn tem tại (x, y), cao 20 px; trả về độ rộng đã vẽ. */
    public static int paintTag(Graphics2D g0, String label, Color color, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(Theme.CAPS);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(label) + 14;
        int h = 20;
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 28));
        g.fillRect(x, y, w, h);
        g.setColor(color);
        g.drawRect(x, y, w - 1, h - 1);
        g.drawString(label, x + 7, y + (h + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
        return w;
    }
}
