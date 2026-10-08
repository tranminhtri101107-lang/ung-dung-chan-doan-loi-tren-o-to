package view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/** Bộ icon nét mảnh tự vẽ bằng Java2D (không cần tệp ảnh hay thư viện). Lưới vẽ 24 x 24, tự co theo kích thước. */
public final class Icons implements Icon {

    public enum Kind { CAR, GAUGE, SCAN, HISTORY, WRENCH, SEARCH, WARN, EXPORT, PLUS, SAVE, TRASH, CHECK, PLAY, CLEAR }

    private final Kind kind;
    private final int size;
    private final Color color;

    public Icons(Kind kind, int size, Color color) {
        this.kind = kind;
        this.size = size;
        this.color = color;
    }

    public static Icon of(Kind kind, int size, Color color) {
        return new Icons(kind, size, color);
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.translate(x, y);
        g.scale(size / 24.0, size / 24.0);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (kind) {
            case CAR -> {
                Path2D body = new Path2D.Double();
                body.moveTo(3, 16);
                body.lineTo(3, 12);
                body.lineTo(5.5, 7);
                body.lineTo(18.5, 7);
                body.lineTo(21, 12);
                body.lineTo(21, 16);
                body.closePath();
                g.draw(body);
                g.draw(new Line2D.Double(3, 12, 21, 12));
                g.draw(new Ellipse2D.Double(5.5, 15, 3.5, 3.5));
                g.draw(new Ellipse2D.Double(15, 15, 3.5, 3.5));
            }
            case GAUGE -> {
                g.draw(new Arc2D.Double(3, 4, 18, 18, -30, 240, Arc2D.OPEN));
                g.draw(new Line2D.Double(12, 13, 16.5, 8.5));
                g.fill(new Ellipse2D.Double(10.8, 11.8, 2.4, 2.4));
            }
            case SCAN -> {
                g.draw(new RoundRectangle2D.Double(4, 3, 16, 18, 2, 2));
                g.draw(new Rectangle2D.Double(7, 6, 10, 6));
                Path2D wave = new Path2D.Double();
                wave.moveTo(7.5, 10);
                wave.lineTo(10, 10);
                wave.lineTo(11.5, 7.5);
                wave.lineTo(13, 10.5);
                wave.lineTo(16.5, 10.5);
                g.draw(wave);
                g.draw(new Line2D.Double(8, 16, 16, 16));
                g.draw(new Line2D.Double(8, 18.5, 13, 18.5));
            }
            case HISTORY -> {
                g.draw(new Arc2D.Double(3.5, 3.5, 17, 17, 100, 300, Arc2D.OPEN));
                g.draw(new Line2D.Double(12, 7.5, 12, 12));
                g.draw(new Line2D.Double(12, 12, 15, 14));
                g.draw(new Line2D.Double(3.5, 6, 5.2, 9.2));
                g.draw(new Line2D.Double(5.2, 9.2, 8.5, 8));
            }
            case WRENCH -> {
                Path2D w = new Path2D.Double();
                w.moveTo(14.5, 4);
                w.curveTo(11.5, 4.5, 10.5, 7.5, 11.5, 10);
                w.lineTo(4, 17.5);
                w.lineTo(6.5, 20);
                w.lineTo(14, 12.5);
                w.curveTo(16.5, 13.5, 19.5, 12.5, 20, 9.5);
                w.lineTo(17.5, 12);
                w.lineTo(15, 11.5);
                w.lineTo(14.5, 9);
                w.lineTo(17, 6.5);
                w.closePath();
                g.draw(w);
            }
            case SEARCH -> {
                g.draw(new Ellipse2D.Double(4, 4, 12, 12));
                g.draw(new Line2D.Double(14.5, 14.5, 20, 20));
            }
            case WARN -> {
                Path2D t = new Path2D.Double();
                t.moveTo(12, 3.5);
                t.lineTo(21, 19.5);
                t.lineTo(3, 19.5);
                t.closePath();
                g.draw(t);
                g.draw(new Line2D.Double(12, 9.5, 12, 14));
                g.fill(new Ellipse2D.Double(11, 15.8, 2, 2));
            }
            case EXPORT -> {
                g.draw(new Line2D.Double(12, 3.5, 12, 14));
                g.draw(new Line2D.Double(8, 7.5, 12, 3.5));
                g.draw(new Line2D.Double(16, 7.5, 12, 3.5));
                Path2D tray = new Path2D.Double();
                tray.moveTo(4, 12);
                tray.lineTo(4, 20);
                tray.lineTo(20, 20);
                tray.lineTo(20, 12);
                g.draw(tray);
            }
            case PLUS -> {
                g.draw(new Line2D.Double(12, 5, 12, 19));
                g.draw(new Line2D.Double(5, 12, 19, 12));
            }
            case SAVE -> {
                g.draw(new Rectangle2D.Double(4, 4, 16, 16));
                g.draw(new Rectangle2D.Double(8, 4, 8, 5));
                g.draw(new Rectangle2D.Double(7.5, 13, 9, 7));
            }
            case TRASH -> {
                g.draw(new Line2D.Double(4, 6.5, 20, 6.5));
                g.draw(new Line2D.Double(9.5, 6.5, 10, 3.5));
                g.draw(new Line2D.Double(10, 3.5, 14, 3.5));
                g.draw(new Line2D.Double(14, 3.5, 14.5, 6.5));
                Path2D bin = new Path2D.Double();
                bin.moveTo(6, 6.5);
                bin.lineTo(7, 20.5);
                bin.lineTo(17, 20.5);
                bin.lineTo(18, 6.5);
                g.draw(bin);
                g.draw(new Line2D.Double(10, 10, 10, 17));
                g.draw(new Line2D.Double(14, 10, 14, 17));
            }
            case CHECK -> {
                Path2D ck = new Path2D.Double();
                ck.moveTo(4.5, 12.5);
                ck.lineTo(9.5, 17.5);
                ck.lineTo(19.5, 6.5);
                g.draw(ck);
            }
            case PLAY -> {
                Path2D p = new Path2D.Double();
                p.moveTo(7, 4.5);
                p.lineTo(19, 12);
                p.lineTo(7, 19.5);
                p.closePath();
                g.draw(p);
            }
            case CLEAR -> {
                g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
                g.draw(new Line2D.Double(8.5, 8.5, 15.5, 15.5));
                g.draw(new Line2D.Double(15.5, 8.5, 8.5, 15.5));
            }
        }
        g.dispose();
    }
}
