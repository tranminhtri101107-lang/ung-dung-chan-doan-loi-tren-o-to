package view;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import controller.DiagnosticController;
import model.Diagnosis;
import model.Dtc;
import service.ScenarioCatalog;

/**
 * Tab "Chẩn đoán lỗi": đọc/xóa DTC (Mode 03/04), phân tích nguyên nhân bằng bộ suy luận (UC07)
 * và chọn kịch bản lỗi trong ECU mô phỏng. Dải tiến trình hiện các bước của luồng chẩn đoán thật.
 */
public class DtcPanel extends JPanel {

    private static final String[] READ = { "Gửi 0x7DF [01 03]", "Chờ ECU trả lời 0x7E8", "Tra danh mục mã lỗi", "Ghi phiên chẩn đoán" };
    private static final String[] CLEAR = { "Gửi 0x7DF [01 04]", "Chờ ECU xác nhận [01 44]", "Ghi nhật ký" };
    private static final String[] ANALYZE = { "Mode 03: đọc mã lỗi", "Mode 01: chụp 8 PID", "Suy luận theo bộ luật", "Lưu kết quả phân tích" };
    private static final String[] INJECT = { "Gửi INJECT tới Gateway", "Khung điều khiển 0x6F0", "ECU xác nhận 0x6F8" };

    private enum Mode { NONE, READ, CLEAR, ANALYZE, INJECT }

    private final DefaultTableModel model = new DefaultTableModel(new Object[] { "Mã lỗi", "Mô tả", "Mức độ" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final DefaultTableModel causeModel = new DefaultTableModel(
            new Object[] { "Hạng", "Nguyên nhân khả dĩ", "Độ tin cậy", "Gợi ý kiểm tra" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = Ui.table(model);
    private final JTable causes = Ui.table(causeModel);
    private final JLabel dtcStatus = Ui.status("Chưa đọc mã lỗi");
    private final JLabel causeStatus = Ui.status("Chưa phân tích");
    private final ScanStrip strip = new ScanStrip();
    private Mode mode = Mode.NONE;

    public DtcPanel(DiagnosticController controller) {
        super(new BorderLayout(0, 14));
        setOpaque(false);

        JButton read = Ui.primary("Đọc mã lỗi (Mode 03)", Icons.Kind.SCAN);
        read.addActionListener(e -> {
            begin(Mode.READ, READ);
            controller.readDtcs();
        });
        JButton analyze = Ui.secondary("Phân tích nguyên nhân", Icons.Kind.SEARCH);
        analyze.addActionListener(e -> {
            begin(Mode.ANALYZE, ANALYZE);
            Ui.info(causeStatus, "Đang phân tích...");
            controller.analyze();
        });
        JButton clear = Ui.danger("Xóa mã lỗi (Mode 04)", Icons.Kind.CLEAR);
        clear.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(this,
                    "Xóa toàn bộ mã lỗi đang lưu trong ECU?\nMã lỗi bị xóa không khôi phục được.", "Xác nhận xóa mã lỗi",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok == JOptionPane.YES_OPTION) {
                begin(Mode.CLEAR, CLEAR);
                controller.clearDtcs();
            }
        });

        // Chọn kịch bản lỗi trong ECU mô phỏng (tiêm lỗi), phục vụ thử nghiệm và trình diễn
        JComboBox<ScenarioCatalog.Scenario> scenarios = new JComboBox<>(
                ScenarioCatalog.ALL.toArray(new ScenarioCatalog.Scenario[0]));
        scenarios.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list,
                        value instanceof ScenarioCatalog.Scenario s ? s.label() : value, index, selected, focus);
            }
        });
        JButton apply = Ui.secondary("Áp dụng", Icons.Kind.PLAY);
        apply.addActionListener(e -> {
            begin(Mode.INJECT, INJECT);
            controller.injectScenario(((ScenarioCatalog.Scenario) scenarios.getSelectedItem()).id());
        });
        JLabel scenarioLabel = Ui.caps("Kịch bản mô phỏng");
        scenarioLabel.setToolTipText("Tiêm lỗi vào ECU giả lập (chỉ nguồn Gateway hỗ trợ)");

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.add(Ui.row(8, read, analyze, clear), BorderLayout.WEST);
        toolbar.add(Ui.row(8, scenarioLabel, scenarios, apply), BorderLayout.EAST);
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(toolbar, BorderLayout.NORTH);
        top.add(strip, BorderLayout.CENTER);

        Ui.widths(table, 90, 0, 150);
        table.getColumnModel().getColumn(0).setCellRenderer(Ui.mono());
        table.getColumnModel().getColumn(1).setCellRenderer(Ui.wrap());
        table.getColumnModel().getColumn(2).setCellRenderer(new SeverityTag());
        Ui.widths(causes, 56, 300, 150, 0);
        causes.getColumnModel().getColumn(1).setCellRenderer(Ui.wrap());
        causes.getColumnModel().getColumn(2).setCellRenderer(new ConfidenceBar());
        causes.getColumnModel().getColumn(3).setCellRenderer(Ui.wrap());

        Card dtcCard = Ui.titled("Mã lỗi đang lưu trong ECU", Icons.Kind.WARN, dtcStatus, Ui.scroll(table));
        Card causeCard = Ui.titled("Nguyên nhân khả dĩ (xếp theo độ tin cậy)", Icons.Kind.SEARCH, causeStatus, Ui.scroll(causes));
        // Mã lỗi ở trên (thường ít dòng), nguyên nhân rộng toàn khung ở dưới để gợi ý kiểm tra dễ đọc
        dtcCard.setPreferredSize(new java.awt.Dimension(0, 215));
        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(dtcCard, BorderLayout.NORTH);
        center.add(causeCard, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        controller.addDtcListener(this::show);
        controller.addAnalysisListener(this::showCauses);
        controller.addInfoListener(msg -> {
            if (mode == Mode.INJECT) {
                end(true, msg);
            }
            Toast.show(this, msg, Toast.Kind.INFO);
        });
        controller.addErrorListener(msg -> {
            end(false, msg);
            if ("Đang phân tích...".equals(causeStatus.getText())) {
                Ui.info(causeStatus, "Chưa phân tích");
            }
        });
    }

    private void begin(Mode m, String[] steps) {
        mode = m;
        strip.start(steps);
    }

    private void end(boolean ok, String message) {
        strip.finish(ok, message);
        mode = Mode.NONE;
    }

    private void show(List<Dtc> dtcs) {
        table.setRowHeight(34);
        model.setRowCount(0);
        for (Dtc d : dtcs) {
            model.addRow(new Object[] { d.code(),
                    d.description() != null ? d.description() : "(chưa có mô tả, chưa tra nguồn)", d.severity() });
        }
        Ui.info(dtcStatus, dtcs.isEmpty() ? "Không có mã lỗi" : dtcs.size() + " mã lỗi");
        if (mode == Mode.READ) {
            end(true, dtcs.isEmpty() ? "ECU không lưu mã lỗi nào." : "Đọc được " + dtcs.size() + " mã lỗi: "
                    + String.join(", ", dtcs.stream().map(Dtc::code).toList()));
        } else if (mode == Mode.CLEAR) {
            end(true, "ECU đã xóa mã lỗi. Đọc lại để kiểm tra lỗi còn tái diễn hay không.");
        }
    }

    private void showCauses(List<Diagnosis> list) {
        causes.setRowHeight(34);
        causeModel.setRowCount(0);
        int rank = 1;
        for (Diagnosis d : list) {
            causeModel.addRow(new Object[] { rank++, d.cause().name(), d.confidence(),
                    d.cause().hint() == null ? "" : d.cause().hint() });
        }
        if (list.isEmpty()) {
            causeStatus.setForeground(Theme.WARN);
            causeStatus.setText("Chưa đủ cơ sở để kết luận (không luật nào khớp)");
        } else {
            Ui.info(causeStatus, list.size() + " nguyên nhân");
        }
        if (mode == Mode.ANALYZE) {
            end(true, list.isEmpty() ? "Không luật nào khớp với mã lỗi và dữ liệu sống hiện tại."
                    : "Hạng 1: " + list.get(0).cause().name()
                            + String.format(Locale.of("vi", "VN"), " (độ tin cậy %.2f)", list.get(0).confidence()));
        }
    }

    /** Ô độ tin cậy: thanh ngang (cam cho hạng 1, xám cho hạng sau) kèm số. */
    private static class ConfidenceBar extends DefaultTableCellRenderer {
        private double value;
        private boolean first;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, "", sel, false, r, c);
            value = v instanceof Double d ? d : 0;
            first = r == 0;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            int w = getWidth() - 62;
            int y = getHeight() / 2 - 5;
            g.setColor(Theme.RULE);
            g.fillRect(10, y, w, 10);
            g.setColor(first ? Theme.ACCENT : Theme.TEXT_FAINT);
            g.fillRect(10, y, (int) Math.round(w * Math.min(1, value)), 10);
            g.setColor(Theme.TEXT);
            g.setFont(Theme.MONO_BOLD);
            g.drawString(String.format(Locale.of("vi", "VN"), "%.2f", value), w + 16, y + 10);
            g.dispose();
        }
    }
}
