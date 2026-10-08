package view;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

import controller.CatalogController;
import model.Dtc;
import model.DtcInfo;
import model.Severity;
import service.DtcDecoder;

/**
 * Tab "Tra cứu DTC" (UC10): lọc danh mục theo từ khóa, mức độ, nhóm hệ thống, trạng thái đối chiếu;
 * trang chi tiết giải nghĩa từng ký tự của mã, mô tả gốc và tiếng Việt, nguyên nhân thường gặp lấy từ bộ luật
 * và số lần mã này đã gặp trong xưởng.
 */
public class CatalogPanel extends JPanel {

    private static final String ALL_GROUPS = "Mọi nhóm hệ thống";
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CatalogController controller;
    private final List<Dtc> all = new ArrayList<>();
    private final List<Dtc> shown = new ArrayList<>();
    private final DefaultTableModel model = new DefaultTableModel(new Object[] { "Mã", "Mô tả", "Nhóm", "Mức độ" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = Ui.table(model);
    private final JTextField keyword = new JTextField(18);
    private final JToggleButton critical = chip("Nghiêm trọng", Theme.DANGER);
    private final JToggleButton warning = chip("Cảnh báo", Theme.WARN);
    private final JToggleButton info = chip("Thông tin", Theme.INFO);
    private final JComboBox<String> group = new JComboBox<>();
    private final JCheckBox verifiedOnly = new JCheckBox("Chỉ mã đã đối chiếu nguồn");
    private final JLabel count = Ui.status(" ");

    // Chi tiết
    private final CardLayout detailCards = new CardLayout();
    private final JPanel detail = new JPanel(detailCards);
    private final CodeStrip strip = new CodeStrip();
    private final JLabel descVi = new JLabel(" ");
    private final JLabel descEn = new JLabel(" ");
    private final JLabel sevTag = new JLabel(" ");
    private final JTextArea source = new JTextArea();
    private final JLabel seen = new JLabel(" ");
    private final DefaultTableModel ruleModel = new DefaultTableModel(new Object[] { "Luật", "Nguyên nhân khả dĩ", "Điểm", "Điều kiện thêm" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable ruleTable = Ui.table(ruleModel);
    private final List<DtcInfo.RuleRow> ruleRows = new ArrayList<>();
    private final JTextArea hint = new JTextArea();
    private JScrollPane sp;

    public CatalogPanel(CatalogController controller) {
        super(new BorderLayout(16, 0));
        this.controller = controller;
        setOpaque(false);
        add(buildList(), BorderLayout.CENTER);
        add(buildDetail(), BorderLayout.EAST);
        controller.search("", this::showAll, msg -> Ui.error(count, "Không đọc được CSDL: " + msg));
    }

    private static JToggleButton chip(String text, Color color) {
        JToggleButton b = new JToggleButton(text);
        b.setFont(Theme.FONT_BOLD);
        b.setForeground(color);
        b.setFocusPainted(false);
        b.putClientProperty("FlatLaf.style", "selectedBackground: #FBE3D3; selectedForeground: #1C1C1C");
        return b;
    }

    // ------------------------------------------------------------------ danh sách
    private JComponent buildList() {
        keyword.putClientProperty("JTextField.placeholderText", "Mã, mô tả tiếng Việt hoặc tiếng Anh...");
        keyword.putClientProperty("JTextField.leadingIcon", Icons.of(Icons.Kind.SEARCH, 16, Theme.TEXT_FAINT));
        keyword.putClientProperty("JTextField.showClearButton", true);
        keyword.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        for (JToggleButton b : new JToggleButton[] { critical, warning, info }) {
            b.addActionListener(e -> applyFilter());
        }
        group.addActionListener(e -> applyFilter());
        verifiedOnly.setOpaque(false);
        verifiedOnly.addActionListener(e -> applyFilter());

        JPanel row1 = new JPanel(new BorderLayout(10, 0));
        row1.setOpaque(false);
        row1.add(keyword, BorderLayout.CENTER);
        row1.add(count, BorderLayout.EAST);
        JPanel row2 = Ui.row(6, critical, warning, info, group, verifiedOnly);
        JPanel bar = new JPanel();
        bar.setOpaque(false);
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        row1.setAlignmentX(LEFT_ALIGNMENT);
        row2.setAlignmentX(LEFT_ALIGNMENT);
        bar.add(row1);
        bar.add(javax.swing.Box.createVerticalStrut(8));
        bar.add(row2);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Ui.widths(table, 76, 0, 140, 140);
        table.getColumnModel().getColumn(0).setCellRenderer(Ui.mono());
        table.getColumnModel().getColumn(3).setCellRenderer(new SeverityTag());
        table.getSelectionModel().addListSelectionListener(e -> {
            int r = table.getSelectedRow();
            if (!e.getValueIsAdjusting() && r >= 0 && r < shown.size()) {
                open(shown.get(r).code());
            }
        });
        Card card = new Card(new BorderLayout());
        card.add(Ui.scroll(table));

        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        p.add(bar, BorderLayout.NORTH);
        p.add(card, BorderLayout.CENTER);
        return p;
    }

    private void showAll(List<Dtc> list) {
        all.clear();
        all.addAll(list);
        Set<String> groups = new LinkedHashSet<>();
        groups.add(ALL_GROUPS);
        list.stream().map(Dtc::group).filter(g -> g != null && !g.isBlank()).sorted().forEach(groups::add);
        group.setModel(new DefaultComboBoxModel<>(groups.toArray(new String[0])));
        applyFilter();
        if (!shown.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
    }

    private void applyFilter() {
        String q = keyword.getText().trim().toLowerCase(Locale.of("vi"));
        boolean anySev = critical.isSelected() || warning.isSelected() || info.isSelected();
        Object g = group.getSelectedItem();
        shown.clear();
        for (Dtc d : all) {
            boolean text = q.isEmpty() || d.code().toLowerCase(Locale.ROOT).contains(q)
                    || (d.description() != null && d.description().toLowerCase(Locale.of("vi")).contains(q));
            boolean sev = !anySev || (d.severity() == Severity.CRITICAL && critical.isSelected())
                    || (d.severity() == Severity.WARNING && warning.isSelected())
                    || (d.severity() == Severity.INFO && info.isSelected());
            boolean grp = g == null || ALL_GROUPS.equals(g) || g.equals(d.group());
            boolean ver = !verifiedOnly.isSelected() || d.verified();
            if (text && sev && grp && ver) {
                shown.add(d);
            }
        }
        model.setRowCount(0);
        for (Dtc d : shown) {
            model.addRow(new Object[] { d.code(), d.description() == null ? "(chưa có mô tả)" : d.description(),
                    d.group() == null ? "" : d.group(), d.severity() });
        }
        count.setText(shown.size() + " / " + all.size() + " mã");
    }

    // ------------------------------------------------------------------ chi tiết
    private JComponent buildDetail() {
        descVi.setFont(Theme.HEADING);
        descVi.setForeground(Theme.TEXT);
        descEn.setFont(Theme.FONT);
        descEn.setForeground(Theme.TEXT_DIM);
        seen.setFont(Theme.FONT_BOLD);
        for (JTextArea a : new JTextArea[] { source, hint }) {
            a.setEditable(false);
            a.setLineWrap(true);
            a.setWrapStyleWord(true);
            a.setOpaque(false);
            a.setFont(Theme.FONT);
            a.setForeground(Theme.TEXT_DIM);
            a.setBorder(null);
        }
        hint.setForeground(Theme.TEXT);

        Ui.widths(ruleTable, 54, 0, 50, 0);
        ruleTable.getColumnModel().getColumn(1).setCellRenderer(Ui.wrap());
        ruleTable.getColumnModel().getColumn(3).setCellRenderer(Ui.wrap());
        ruleTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ruleTable.getColumnModel().getColumn(2).setCellRenderer(Ui.mono());
        ruleTable.setToolTipText("Bấm một luật để xem gợi ý kiểm tra");
        ruleTable.getSelectionModel().addListSelectionListener(e -> {
            int r = ruleTable.getSelectedRow();
            if (!e.getValueIsAdjusting() && r >= 0 && r < ruleRows.size()) {
                hint.setText(ruleRows.get(r).hint() == null ? "" : ruleRows.get(r).hint());
            }
        });

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        add(stack, strip);
        add(stack, gap(10));
        add(stack, descVi);
        add(stack, descEn);
        add(stack, gap(6));
        add(stack, sevTag);
        add(stack, gap(14));
        add(stack, Ui.caps("Nguyên nhân thường gặp (từ bộ luật chẩn đoán)"));
        add(stack, gap(6));
        JPanel tbl = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        tbl.add(ruleTable.getTableHeader(), BorderLayout.NORTH);
        tbl.add(ruleTable, BorderLayout.CENTER);
        add(stack, tbl);
        add(stack, gap(8));
        add(stack, Ui.caps("Gợi ý kiểm tra (luật đang chọn)"));
        add(stack, hint);
        add(stack, gap(14));
        add(stack, Ui.caps("Đã gặp trong xưởng"));
        add(stack, seen);
        add(stack, gap(14));
        add(stack, Ui.caps("Nguồn đối chiếu"));
        add(stack, source);

        sp = new JScrollPane(stack);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.getViewport().setBackground(Theme.CARD);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        detail.setOpaque(false);
        detail.add(Ui.empty(Icons.Kind.SEARCH, "Chọn một mã lỗi", "Bấm vào một mã bên trái để xem chi tiết."), "pick");
        detail.add(sp, "show");
        Card card = new Card(new BorderLayout());
        card.add(detail);
        card.setPreferredSize(new Dimension(500, 0));
        return card;
    }

    private static void add(JPanel stack, JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(c);
    }

    private static JComponent gap(int h) {
        JPanel g = new JPanel();
        g.setOpaque(false);
        g.setPreferredSize(new Dimension(0, h));
        g.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return g;
    }

    private void open(String code) {
        controller.info(code, this::show, msg -> Toast.show(this, msg, Toast.Kind.ERROR));
    }

    private void show(DtcInfo i) {
        if (i == null) {
            detailCards.show(detail, "pick");
            return;
        }
        Dtc d = i.dtc();
        strip.setCode(d.code());
        descVi.setText(d.description() == null ? "(chưa có mô tả tiếng Việt)" : d.description());
        descEn.setText(i.original() == null ? " " : i.original());
        sevTag.setIcon(new TagIcon(SeverityTag.labelOf(d.severity()), SeverityTag.colorOf(d.severity())));
        sevTag.setText(d.group() == null ? "" : "  " + d.group());
        sevTag.setFont(Theme.FONT);
        sevTag.setForeground(Theme.TEXT_DIM);
        ruleRows.clear();
        ruleRows.addAll(i.rules());
        ruleModel.setRowCount(0);
        ruleTable.setRowHeight(34);   // đặt lại chiều cao, bộ vẽ xuống dòng sẽ tăng theo nội dung
        for (DtcInfo.RuleRow r : i.rules()) {
            ruleModel.addRow(new Object[] { String.format("R%02d", r.ruleId()), r.cause(),
                    String.format(Locale.of("vi", "VN"), "%.2f", r.score()), r.extra().isEmpty() ? "chỉ cần mã lỗi" : r.extra() });
        }
        hint.setText(i.rules().isEmpty() ? "Chưa có luật nào cho mã này." : "Bấm một luật ở bảng trên để xem gợi ý kiểm tra.");
        seen.setForeground(i.sessions() == 0 ? Theme.TEXT_DIM : Theme.ACCENT_DARK);
        seen.setText(i.sessions() == 0 ? "Chưa gặp lần nào"
                : i.sessions() + " phiên trên " + i.vehicles() + " xe, gần nhất " + D.format(i.lastSeen()));
        source.setText((i.source() == null ? "Chưa ghi nguồn" : i.source())
                + (d.verified() ? "\nĐã đối chiếu ít nhất hai nguồn thứ cấp độc lập." : "\nChưa đối chiếu nguồn."));
        detailCards.show(detail, "show");
        detail.revalidate();
        // Mỗi lần mở mã mới thì cuộn về đầu trang chi tiết
        javax.swing.SwingUtilities.invokeLater(() -> sp.getVerticalScrollBar().setValue(0));
    }

    /** Dải giải nghĩa mã: mỗi phần (P, 0, 1, 71) là một ô có ký tự lớn và chú thích bên dưới. */
    private static class CodeStrip extends JComponent {
        private List<DtcDecoder.Part> parts = List.of();

        CodeStrip() {
            setPreferredSize(new Dimension(400, 112));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));
        }

        void setCode(String code) {
            parts = DtcDecoder.decode(code);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int n = parts.size();
            if (n == 0) {
                g.dispose();
                return;
            }
            int gapX = 6;
            int w = (getWidth() - gapX * (n - 1)) / n;
            for (int k = 0; k < n; k++) {
                DtcDecoder.Part p = parts.get(k);
                int x = k * (w + gapX);
                g.setColor(k == 2 ? Theme.HOVER : Theme.CARD_ALT);
                g.fillRect(x, 0, w, 48);
                g.setColor(k == 0 ? Theme.ACCENT : Theme.BORDER);
                g.setStroke(new BasicStroke(k == 0 ? 2f : 1f));
                g.drawRect(x, 0, w - 1, 47);
                g.setFont(Theme.display(Font.BOLD, 28));
                FontMetrics fm = g.getFontMetrics();
                g.setColor(Theme.TEXT);
                g.drawString(p.symbol(), x + (w - fm.stringWidth(p.symbol())) / 2, 35);
                g.setFont(Theme.CAPS);
                g.setColor(Theme.TEXT_DIM);
                g.drawString(p.role().toUpperCase(Locale.of("vi")), x + 2, 64);
                g.setFont(Theme.SMALL);
                g.setColor(Theme.TEXT);
                drawWrapped(g, p.meaning(), x + 2, 80, w - 4);
            }
            g.dispose();
        }

        private static void drawWrapped(Graphics2D g, String text, int x, int y, int width) {
            FontMetrics fm = g.getFontMetrics();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String test = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(test) > width && line.length() > 0) {
                    g.drawString(line.toString(), x, y);
                    y += fm.getHeight();
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(test);
                }
            }
            g.drawString(line.toString(), x, y);
        }
    }

    /** Nhãn tem dùng làm icon cho JLabel. */
    private record TagIcon(String label, Color color) implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            SeverityTag.paintTag((Graphics2D) g, label, color, x, y);
        }

        @Override
        public int getIconWidth() {
            return new JLabel().getFontMetrics(Theme.CAPS).stringWidth(label) + 14;
        }

        @Override
        public int getIconHeight() {
            return 20;
        }
    }
}
