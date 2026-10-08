package view;

import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Thông báo ngắn trượt lên ở góc dưới bên phải cửa sổ rồi tự ẩn sau vài giây. */
public class Toast extends JComponent {

    public enum Kind { INFO, OK, ERROR }

    private final String text;
    private final Color color;

    private Toast(String text, Kind kind) {
        this.text = text;
        this.color = switch (kind) {
            case OK -> Theme.OK;
            case ERROR -> Theme.DANGER;
            case INFO -> Theme.ACCENT;
        };
    }

    public static void show(Component anchor, String text, Kind kind) {
        JRootPane root = SwingUtilities.getRootPane(anchor);
        if (root == null) {
            return;
        }
        JLayeredPane layer = root.getLayeredPane();
        Toast t = new Toast(text, kind);
        FontMetrics fm = t.getFontMetrics(Theme.FONT_BOLD);
        int w = Math.min(560, fm.stringWidth(text) + 44);
        int h = 42;
        t.setSize(w, h);
        int x = layer.getWidth() - w - 24;
        int yEnd = layer.getHeight() - h - 24;
        int yStart = layer.getHeight() + 4;
        t.setLocation(x, yStart);
        layer.add(t, JLayeredPane.POPUP_LAYER);
        Anim.run(260, p -> t.setLocation(x, (int) Anim.lerp(yStart, yEnd, p)), null);
        Timer hide = new Timer(3200, e -> Anim.run(220, p -> t.setLocation(x, (int) Anim.lerp(yEnd, yStart, p)), () -> {
            layer.remove(t);
            layer.repaint();
        }));
        hide.setRepeats(false);
        hide.start();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Theme.SIDEBAR);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(color);
        g.fillRect(0, 0, 4, getHeight());
        g.setFont(Theme.FONT_BOLD);
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        String s = text;
        while (fm.stringWidth(s) > getWidth() - 32 && s.length() > 4) {
            s = s.substring(0, s.length() - 4) + "...";
        }
        g.drawString(s, 18, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
    }
}
