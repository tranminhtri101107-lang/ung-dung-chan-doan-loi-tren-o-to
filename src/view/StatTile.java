package view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.text.NumberFormat;
import java.util.Locale;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Ô số liệu: nhãn in hoa, số lớn (đếm lên khi đổi giá trị), dòng phụ. */
public class StatTile extends Card {

    private static final NumberFormat NF = NumberFormat.getIntegerInstance(Locale.of("vi", "VN"));
    private final JLabel value = new JLabel("-");
    private final JLabel sub = new JLabel(" ");
    private Timer anim;

    public StatTile(String label) {
        super(new BorderLayout());
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.add(Ui.caps(label));
        value.setFont(Theme.NUMBER);
        value.setForeground(Theme.TEXT);
        box.add(value);
        sub.setFont(Theme.SMALL);
        sub.setForeground(Theme.TEXT_DIM);
        box.add(sub);
        add(box, BorderLayout.CENTER);
    }

    /** Hiển thị số nguyên, đếm lên từ 0; suffix ví dụ " đ" hoặc " km". */
    public void setNumber(long target, String suffix, Color color) {
        stop();
        value.setForeground(color == null ? Theme.TEXT : color);
        anim = Anim.run(500, t -> value.setText(NF.format(Math.round(target * t)) + suffix), null);
    }

    public void setText(String text, Color color) {
        stop();
        value.setForeground(color == null ? Theme.TEXT : color);
        value.setText(text);
    }

    public void setSub(String text) {
        sub.setText(text == null || text.isEmpty() ? " " : text);
    }

    private void stop() {
        if (anim != null) {
            anim.stop();
        }
    }
}
