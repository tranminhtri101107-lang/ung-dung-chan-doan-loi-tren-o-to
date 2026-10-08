package view;

import java.util.function.DoubleConsumer;

import javax.swing.Timer;

/** Hiệu ứng chuyển động đơn giản bằng javax.swing.Timer (chạy trên luồng giao diện, khoảng 60 khung/giây). */
public final class Anim {

    private static final int FRAME_MS = 16;

    /** Gọi onFrame(t) với t chạy từ 0 đến 1 (đã làm chậm dần ở cuối) trong khoảng ms mili giây. */
    public static Timer run(int ms, DoubleConsumer onFrame, Runnable done) {
        long start = System.nanoTime();
        Timer t = new Timer(FRAME_MS, null);
        t.addActionListener(e -> {
            double p = Math.min(1.0, (System.nanoTime() - start) / 1e6 / ms);
            onFrame.accept(easeOut(p));
            if (p >= 1.0) {
                t.stop();
                if (done != null) {
                    done.run();
                }
            }
        });
        t.start();
        return t;
    }

    /** Đường cong chậm dần: nhanh lúc đầu, êm lúc cuối. */
    public static double easeOut(double p) {
        double q = 1 - p;
        return 1 - q * q * q;
    }

    /** Nội suy tuyến tính giữa a và b. */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private Anim() {
    }
}
