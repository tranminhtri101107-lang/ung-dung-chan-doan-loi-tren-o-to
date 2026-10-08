package view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

/** Các thành phần giao diện dùng chung để mọi tab có cùng một phong cách. */
public final class Ui {

    /** Nút chính: nền cam, chữ trắng. */
    public static JButton primary(String text, Icons.Kind icon) {
        JButton b = base(text, icon, Color.WHITE);
        b.setBackground(Theme.ACCENT);
        b.setForeground(Color.WHITE);
        b.putClientProperty("JButton.buttonType", null);
        b.putClientProperty("FlatLaf.style", "borderWidth: 0; focusWidth: 0; hoverBackground: #C2470A; pressedBackground: #A63D08");
        return b;
    }

    /** Nút phụ: nền trắng, viền mảnh. */
    public static JButton secondary(String text, Icons.Kind icon) {
        JButton b = base(text, icon, Theme.TEXT);
        b.setBackground(Theme.CARD);
        b.setForeground(Theme.TEXT);
        b.putClientProperty("FlatLaf.style", "focusWidth: 0; hoverBackground: #FDF1EA; borderColor: #C9C5BD");
        return b;
    }

    /** Nút nguy hiểm (xóa): chữ đỏ sẫm. */
    public static JButton danger(String text, Icons.Kind icon) {
        JButton b = base(text, icon, Theme.DANGER);
        b.setBackground(Theme.CARD);
        b.setForeground(Theme.DANGER);
        b.putClientProperty("FlatLaf.style", "focusWidth: 0; hoverBackground: #FBEAE8; borderColor: #E3B4AE");
        return b;
    }

    private static JButton base(String text, Icons.Kind icon, Color iconColor) {
        JButton b = new JButton(text);
        if (icon != null) {
            b.setIcon(Icons.of(icon, 16, iconColor));
            b.setIconTextGap(6);
        }
        b.setFont(Theme.FONT_BOLD);
        b.setFocusPainted(false);
        b.setMargin(new java.awt.Insets(6, 12, 6, 14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Nhãn in hoa nhỏ dùng làm tiêu đề ô nhập hoặc nhóm thông tin. */
    public static JLabel caps(String text) {
        JLabel l = new JLabel(text.toUpperCase(java.util.Locale.of("vi")));
        l.setFont(Theme.CAPS);
        l.setForeground(Theme.TEXT_DIM);
        return l;
    }

    /** Ô nhập có nhãn ở trên và (nếu có) dòng báo lỗi ở dưới. */
    public static JPanel field(String label, JComponent input, JLabel error) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel l = caps(label);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        input.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(l);
        p.add(Box.createVerticalStrut(4));
        // Giữ ô nhập đúng chiều cao một dòng (BoxLayout mặc định kéo giãn theo chiều dọc)
        input.setMaximumSize(new Dimension(Integer.MAX_VALUE, input.getPreferredSize().height));
        p.add(input);
        if (error != null) {
            error.setFont(Theme.SMALL);
            error.setForeground(Theme.DANGER);
            error.setText(" ");
            error.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(error);
        }
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    public static JPanel row(int gap, Component... items) {
        JPanel p = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, gap, 0));
        p.setOpaque(false);
        for (Component c : items) {
            p.add(c);
        }
        return p;
    }

    /** Bảng kiểu xưởng: kẻ dòng mảnh, tiêu đề in hoa, sáng nền khi rê chuột. */
    public static JTable table(TableModel model) {
        HoverTable t = new HoverTable(model);
        t.setRowHeight(34);
        t.setShowHorizontalLines(true);
        t.setShowVerticalLines(false);
        t.setGridColor(Theme.RULE);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setFillsViewportHeight(true);
        t.setBackground(Theme.CARD);
        t.setSelectionBackground(Theme.SELECT);
        t.setSelectionForeground(Theme.TEXT);
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setDefaultRenderer(new HeaderRenderer());
        t.getTableHeader().setPreferredSize(new Dimension(0, 32));
        t.setDefaultRenderer(Object.class, padded());
        t.setDefaultRenderer(Integer.class, padded());
        return t;
    }

    /** Bộ vẽ ô có lề trái phải cho dễ đọc. */
    public static DefaultTableCellRenderer padded() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, false, r, c);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                return this;
            }
        };
    }

    /** Bộ vẽ ô dùng phông đơn cách (mã lỗi, số liệu kỹ thuật). */
    public static DefaultTableCellRenderer mono() {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int c) {
                super.getTableCellRendererComponent(t, v, sel, false, row, c);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                setFont(Theme.MONO_BOLD);
                return this;
            }
        };
        return r;
    }

    /**
     * Bộ vẽ ô xuống dòng khi chữ dài (thay vì cắt "..."): tự tăng chiều cao hàng cho vừa nội dung.
     * Dùng cho cột mô tả, nguyên nhân, điều kiện.
     */
    public static TableCellRenderer wrap() {
        return new TableCellRenderer() {
            private final javax.swing.JTextArea area = new javax.swing.JTextArea();

            {
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                area.setBorder(new EmptyBorder(8, 10, 8, 10));
                area.setFont(Theme.FONT);
            }

            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                area.setText(v == null ? "" : v.toString());
                area.setForeground(Theme.TEXT);
                area.setBackground(sel ? Theme.SELECT : t.getBackground());
                area.setSize(t.getColumnModel().getColumn(c).getWidth(), Short.MAX_VALUE);
                // Chỉ tăng chiều cao hàng (không giảm) nên hàng cao bằng ô cao nhất trong hàng
                int need = area.getPreferredSize().height;
                if (t.getRowHeight(r) < need) {
                    t.setRowHeight(r, need);
                }
                return area;
            }
        };
    }

    /** Đặt độ rộng cố định cho các cột (0 = để cột đó giãn theo khung). */
    public static void widths(JTable t, int... w) {
        for (int i = 0; i < w.length && i < t.getColumnCount(); i++) {
            if (w[i] > 0) {
                t.getColumnModel().getColumn(i).setMinWidth(w[i]);
                t.getColumnModel().getColumn(i).setMaxWidth(w[i]);
            }
        }
    }

    public static JScrollPane scroll(Component c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(BorderFactory.createEmptyBorder());
        s.getViewport().setBackground(Theme.CARD);
        return s;
    }

    /** Dòng trạng thái nhỏ (thông tin màu xám, lỗi màu đỏ). */
    public static JLabel status(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT);
        l.setForeground(Theme.TEXT_DIM);
        return l;
    }

    public static void info(JLabel l, String text) {
        l.setForeground(Theme.TEXT_DIM);
        l.setText(text);
    }

    public static void error(JLabel l, String text) {
        l.setForeground(Theme.DANGER);
        l.setText(text);
    }

    /** Khối "trống" giữa vùng nội dung khi chưa có dữ liệu (ví dụ chưa chọn xe). */
    public static JPanel empty(Icons.Kind icon, String title, String hint) {
        JPanel p = new JPanel(new java.awt.GridBagLayout());
        p.setOpaque(false);
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel i = new JLabel(Icons.of(icon, 40, Theme.TEXT_FAINT));
        JLabel t = new JLabel(title);
        t.setFont(Theme.HEADING);
        t.setForeground(Theme.TEXT_DIM);
        JLabel h = new JLabel(hint);
        h.setForeground(Theme.TEXT_FAINT);
        for (JComponent c : new JComponent[] { i, t, h }) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
            box.add(c);
            box.add(Box.createVerticalStrut(6));
        }
        p.add(box);
        return p;
    }

    /** Bảng có hiệu ứng sáng nền theo hàng đang rê chuột và nền xen kẽ nhẹ. */
    private static class HoverTable extends JTable {
        private int hoverRow = -1;

        HoverTable(TableModel m) {
            super(m);
            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int r = rowAtPoint(e.getPoint());
                    if (r != hoverRow) {
                        hoverRow = r;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverRow = -1;
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        @Override
        public Component prepareRenderer(TableCellRenderer r, int row, int col) {
            Component c = super.prepareRenderer(r, row, col);
            if (!isRowSelected(row)) {
                c.setBackground(row == hoverRow ? Theme.HOVER : (row % 2 == 0 ? Theme.CARD : Theme.CARD_ALT));
            }
            return c;
        }
    }

    /** Tiêu đề cột in hoa nhỏ, nền ngà, vạch dưới đậm. */
    private static class HeaderRenderer extends DefaultTableCellRenderer {
        HeaderRenderer() {
            setHorizontalAlignment(SwingConstants.LEFT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, v == null ? "" : v.toString().toUpperCase(java.util.Locale.of("vi")),
                    false, false, r, c);
            setFont(Theme.CAPS);
            setForeground(Theme.TEXT_DIM);
            setBackground(Theme.CARD_ALT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.BORDER), new EmptyBorder(0, 10, 0, 10)));
            return this;
        }
    }

    /** Vẽ biểu tượng nhỏ trước chữ (dùng cho nhãn tiêu đề khung). */
    public static JLabel iconLabel(Icons.Kind icon, String text, Font font, Color color) {
        JLabel l = new JLabel(text, Icons.of(icon, 16, color), SwingConstants.LEFT);
        l.setIconTextGap(8);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    /** Khung có thanh tiêu đề (dùng khi cần tiêu đề + nút bên phải). */
    public static Card titled(String title, Icons.Kind icon, JComponent right, Component body) {
        Card card = new Card(new BorderLayout(0, 10));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(icon == null ? caps(title) : iconLabel(icon, title.toUpperCase(java.util.Locale.of("vi")), Theme.CAPS, Theme.TEXT_DIM),
                BorderLayout.WEST);
        if (right != null) {
            head.add(right, BorderLayout.EAST);
        }
        card.add(head, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    /** Vạch cam bên trái để nhấn mạnh một khối. */
    public static void accentBar(Graphics g, int h) {
        g.setColor(Theme.ACCENT);
        g.fillRect(0, 0, 3, h);
    }

    public static Icon icon(Icons.Kind k, Color c) {
        return Icons.of(k, 16, c);
    }

    private Ui() {
    }
}
