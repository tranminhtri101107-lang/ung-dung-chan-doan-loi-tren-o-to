package view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Dải tiến trình khi làm việc với ECU: các bước của luồng thật (gửi khung 0x7DF, chờ 0x7E8, tra CSDL...)
 * sáng dần theo thời gian; bước cuối chỉ hoàn tất khi kết quả thật trả về (hoặc chuyển đỏ khi lỗi).
 */
public class ScanStrip extends JComponent {

    private enum State { IDLE, RUNNING, DONE, ERROR }

    private String[] steps = { "Sẵn sàng" };
    private State state = State.IDLE;
    private int active;        // bước đang chạy
    private float phase;       // pha của vạch chạy trong bước đang chạy
    private String result = "Chọn một thao tác: đọc mã lỗi, xóa mã lỗi hoặc phân tích nguyên nhân.";
    private final Timer tick;

    public ScanStrip() {
        setPreferredSize(new Dimension(0, 62));
        tick = new Timer(40, e -> {
            phase = (phase + 0.06f) % 1f;
            if (phase < 0.06f && active < steps.length - 2) {
                active++;   // tự sang bước kế tiếp, giữ lại bước cuối chờ kết quả thật
            }
            repaint();
        });
    }

    /** Bắt đầu một thao tác với danh sách bước. */
    public void start(String... steps) {
        this.steps = steps;
        state = State.RUNNING;
        active = 0;
        phase = 0;
        result = "Đang thực hiện...";
        tick.start();
        repaint();
    }

    /** Kết thúc: ok = true thì mọi bước xanh, false thì bước đang chạy chuyển đỏ. */
    public void finish(boolean ok, String message) {
        if (state != State.RUNNING) {
            return;
        }
        tick.stop();
        state = ok ? State.DONE : State.ERROR;
        if (ok) {
            active = steps.length;
        }
        result = message;
        repaint();
    }

    public boolean running() {
        return state == State.RUNNING;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        g.setColor(Theme.CARD);
        g.fillRect(0, 0, w, getHeight());
        g.setColor(Theme.BORDER);
        g.drawRect(0, 0, w - 1, getHeight() - 1);

        int n = steps.length;
        int pad = 14;
        int segW = (w - 2 * pad - (n - 1) * 8) / Math.max(1, n);
        g.setFont(Theme.SMALL);
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < n; i++) {
            int x = pad + i * (segW + 8);
            Color c;
            if (state == State.IDLE) {
                c = Theme.RULE;
            } else if (i < active || state == State.DONE) {
                c = Theme.OK;
            } else if (i == active) {
                c = state == State.ERROR ? Theme.DANGER : Theme.ACCENT;
            } else {
                c = Theme.RULE;
            }
            g.setColor(c);
            g.fillRect(x, 12, segW, 5);
            if (state == State.RUNNING && i == active) {
                // Vạch sáng chạy qua bước đang thực hiện
                int bx = x + (int) (phase * (segW - 40));
                g.setColor(Color.WHITE);
                g.fillRect(bx, 12, 40, 5);
            }
            g.setColor(state == State.IDLE ? Theme.TEXT_FAINT : (i <= active ? Theme.TEXT : Theme.TEXT_FAINT));
            String s = (i + 1) + ". " + steps[i];
            while (fm.stringWidth(s) > segW && s.length() > 4) {
                s = s.substring(0, s.length() - 2);
            }
            g.drawString(s, x, 34);
        }
        g.setColor(state == State.ERROR ? Theme.DANGER : state == State.DONE ? Theme.OK : Theme.TEXT_DIM);
        g.setFont(Theme.FONT_BOLD);
        g.drawString(result, pad, 53);
        g.dispose();
    }
}
