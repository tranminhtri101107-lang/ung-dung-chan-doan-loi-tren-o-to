package view;

import java.awt.Graphics;
import java.awt.LayoutManager;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Khung nội dung kiểu xưởng: nền trắng, viền mảnh, góc vuông. */
public class Card extends JPanel {

    public Card(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(new EmptyBorder(14, 16, 14, 16));
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Theme.CARD);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Theme.BORDER);
        g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        super.paintComponent(g);
    }
}
