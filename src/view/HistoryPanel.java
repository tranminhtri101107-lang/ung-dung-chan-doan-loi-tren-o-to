package view;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

import controller.DiagnosticController;
import model.Dtc;
import model.HistoryEntry;
import model.SessionDetail;
import model.SessionInfo;
import model.Vehicle;
import service.PidDecoder;
import service.ReportExporter;

/**
 * Tab "Lịch sử" (UC08, UC09): danh sách phiên chẩn đoán của xe đang chọn (lọc theo thời gian, thao tác, mã lỗi),
 * đánh dấu mã lỗi tái diễn; bấm vào một phiên để xem chi tiết và xuất phiếu chẩn đoán.
 */
public class HistoryPanel extends JPanel {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String[] PERIODS = { "Mọi thời gian", "7 ngày qua", "30 ngày qua", "Năm nay" };
    private static final String[] ACTIONS = { "Mọi thao tác", "Đọc DTC", "Xóa DTC", "Phân tích" };

    private final DiagnosticController controller;
    private final List<SessionInfo> all = new ArrayList<>();
    private final List<SessionInfo> shown = new ArrayList<>();

    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final DefaultTableModel model = model("Thời gian", "Mã lỗi", "Nguyên nhân hạng 1", "Thao tác");
    private final JTable table = Ui.table(model);
    private final JComboBox<String> period = new JComboBox<>(PERIODS);
    private final JComboBox<String> action = new JComboBox<>(ACTIONS);
    private final JTextField dtcFilter = new JTextField(9);
    private final JLabel count = Ui.status(" ");
    private final JLabel recurring = new JLabel(" ");

    // Chi tiết phiên
    private final CardLayout detailCards = new CardLayout();
    private final JPanel detail = new JPanel(detailCards);
    private final JLabel detailTitle = new JLabel(" ");
    private final DefaultTableModel dtcModel = model("Mã", "Mô tả", "Mức độ");
    private final DefaultTableModel pidModel = model("PID", "Tham số", "Giá trị");
    private final DefaultTableModel causeModel = model("Hạng", "Nguyên nhân", "Điểm");
    private final DefaultTableModel logModel = model("Giờ", "Thao tác", "Chi tiết");
    private final JTable dtcTable = Ui.table(dtcModel);
    private final JButton export = Ui.primary("Xuất phiếu", Icons.Kind.EXPORT);
    private SessionDetail current;

    public HistoryPanel(DiagnosticController controller) {
        super(new BorderLayout());
        this.controller = controller;
        setOpaque(false);

        JPanel main = new JPanel(new BorderLayout(16, 0));
        main.setOpaque(false);
        main.add(buildList(), BorderLayout.CENTER);
        main.add(buildDetail(), BorderLayout.EAST);
        body.setOpaque(false);
        body.add(Ui.empty(Icons.Kind.HISTORY, "Chưa chọn xe", "Chọn một xe ở tab Quản lý xe để xem lịch sử chẩn đoán."), "empty");
        body.add(Ui.empty(Icons.Kind.HISTORY, "Xe này chưa có phiên chẩn đoán", "Đọc mã lỗi hoặc phân tích ở tab Chẩn đoán lỗi để tạo phiên."), "none");
        body.add(main, "main");
        add(body, BorderLayout.CENTER);
        cards.show(body, "empty");

        // Đổi xe hoặc có thao tác mới thì tải lại danh sách phiên
        controller.addHistoryResetListener(list -> reload());
        controller.addHistoryListener(e -> reload());
    }

    private static DefaultTableModel model(String... cols) {
        return new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    // ------------------------------------------------------------------ danh sách phiên
    private JComponent buildList() {
        dtcFilter.putClientProperty("JTextField.placeholderText", "Mã lỗi");
        dtcFilter.getDocument().addDocumentListener(new DocumentListener() {
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
        period.addActionListener(e -> applyFilter());
        action.addActionListener(e -> applyFilter());
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);
        bar.add(Ui.row(8, period, action, dtcFilter), BorderLayout.WEST);
        bar.add(count, BorderLayout.CENTER);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Ui.widths(table, 128, 112, 0, 150);
        table.getColumnModel().getColumn(1).setCellRenderer(Ui.mono());
        table.getSelectionModel().addListSelectionListener(e -> {
            int r = table.getSelectedRow();
            if (!e.getValueIsAdjusting() && r >= 0 && r < shown.size()) {
                openDetail(shown.get(r));
            }
        });

        recurring.setFont(Theme.MONO_BOLD);
        recurring.setForeground(Theme.DANGER);
        JPanel rec = new JPanel(new BorderLayout(10, 0));
        rec.setOpaque(false);
        rec.setBorder(new EmptyBorder(8, 0, 0, 0));
        rec.add(Ui.caps("Mã lỗi tái diễn (≥ 2 phiên)"), BorderLayout.WEST);
        rec.add(recurring, BorderLayout.CENTER);

        Card card = new Card(new BorderLayout());
        card.add(Ui.scroll(table), BorderLayout.CENTER);
        card.add(rec, BorderLayout.SOUTH);

        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        p.add(bar, BorderLayout.NORTH);
        p.add(card, BorderLayout.CENTER);
        return p;
    }

    private void reload() {
        Vehicle v = controller.currentVehicle();
        if (v == null) {
            cards.show(body, "empty");
            return;
        }
        controller.loadSessions(this::showSessions, msg -> Toast.show(this, msg, Toast.Kind.ERROR));
    }

    private void showSessions(List<SessionInfo> list) {
        int keep = current == null ? -1 : current.info().id();
        all.clear();
        all.addAll(list);
        cards.show(body, list.isEmpty() ? "none" : "main");
        // Mã lỗi xuất hiện ở từ 2 phiên trở lên của cùng xe
        Map<String, Integer> times = new LinkedHashMap<>();
        list.forEach(s -> s.dtcs().forEach(c -> times.merge(c, 1, Integer::sum)));
        StringBuilder rec = new StringBuilder();
        times.entrySet().stream().filter(e -> e.getValue() >= 2)
                .forEach(e -> rec.append(e.getKey()).append(" ×").append(e.getValue()).append("   "));
        recurring.setForeground(rec.length() == 0 ? Theme.OK : Theme.DANGER);
        recurring.setFont(rec.length() == 0 ? Theme.FONT_BOLD : Theme.MONO_BOLD);
        recurring.setText(rec.length() == 0 ? "Không có" : rec.toString().trim());
        applyFilter();
        // Giữ phiên đang xem nếu còn trong danh sách, nếu không thì mở phiên mới nhất
        int idx = 0;
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).id() == keep) {
                idx = i;
            }
        }
        if (!shown.isEmpty()) {
            table.setRowSelectionInterval(idx, idx);
        } else {
            detailCards.show(detail, "pick");
        }
    }

    private void applyFilter() {
        LocalDateTime from = switch (period.getSelectedIndex()) {
            case 1 -> LocalDateTime.now().minusDays(7);
            case 2 -> LocalDateTime.now().minusDays(30);
            case 3 -> LocalDate.now().withDayOfYear(1).atStartOfDay();
            default -> null;
        };
        String act = action.getSelectedIndex() == 0 ? null : (String) action.getSelectedItem();
        String code = dtcFilter.getText().trim().toUpperCase(Locale.ROOT);
        shown.clear();
        for (SessionInfo s : all) {
            boolean ok = (from == null || !s.start().isBefore(from))
                    && (act == null || s.actions().contains(act))
                    && (code.isEmpty() || s.dtcs().stream().anyMatch(c -> c.contains(code)));
            if (ok) {
                shown.add(s);
            }
        }
        model.setRowCount(0);
        for (SessionInfo s : shown) {
            String cause = s.topCause() == null ? "—"
                    : s.topCause() + String.format(Locale.of("vi", "VN"), " (%.2f)", s.topScore());
            model.addRow(new Object[] { DT.format(s.start()), s.dtcs().isEmpty() ? "Không có" : String.join(" ", s.dtcs()),
                    cause, String.join(", ", s.actions()) });
        }
        count.setText(shown.size() + " / " + all.size() + " phiên");
    }

    // ------------------------------------------------------------------ chi tiết phiên
    private JComponent buildDetail() {
        detailTitle.setFont(Theme.HEADING);
        detailTitle.setForeground(Theme.TEXT);
        export.addActionListener(e -> exportReport());
        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        head.add(detailTitle, BorderLayout.CENTER);
        head.add(export, BorderLayout.EAST);

        Ui.widths(dtcTable, 70, 0, 130);
        dtcTable.getColumnModel().getColumn(1).setCellRenderer(Ui.wrap());
        dtcTable.getColumnModel().getColumn(0).setCellRenderer(Ui.mono());
        dtcTable.getColumnModel().getColumn(2).setCellRenderer(new SeverityTag());
        JTable pidTable = Ui.table(pidModel);
        Ui.widths(pidTable, 56, 0, 120);
        pidTable.getColumnModel().getColumn(0).setCellRenderer(Ui.mono());
        pidTable.getColumnModel().getColumn(2).setCellRenderer(Ui.mono());
        JTable causeTable = Ui.table(causeModel);
        Ui.widths(causeTable, 56, 0, 60);
        causeTable.getColumnModel().getColumn(1).setCellRenderer(Ui.wrap());
        causeTable.getColumnModel().getColumn(2).setCellRenderer(Ui.mono());
        JTable logTable = Ui.table(logModel);
        Ui.widths(logTable, 70, 110, 0);
        logTable.getColumnModel().getColumn(2).setCellRenderer(Ui.wrap());

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.add(section("Mã lỗi đọc được", dtcTable));
        stack.add(section("Nguyên nhân khả dĩ", causeTable));
        stack.add(section("Dữ liệu sống lúc phân tích", pidTable));
        stack.add(section("Nhật ký thao tác", logTable));
        JScrollPane sp = new JScrollPane(stack);
        sp.setBorder(null);
        sp.getViewport().setBackground(Theme.CARD);
        sp.getVerticalScrollBar().setUnitIncrement(16);

        JPanel shownPane = new JPanel(new BorderLayout(0, 12));
        shownPane.setOpaque(false);
        shownPane.add(head, BorderLayout.NORTH);
        shownPane.add(sp, BorderLayout.CENTER);

        detail.setOpaque(false);
        detail.add(Ui.empty(Icons.Kind.HISTORY, "Chọn một phiên", "Bấm vào một phiên bên trái để xem chi tiết."), "pick");
        detail.add(shownPane, "show");
        Card card = new Card(new BorderLayout());
        card.add(detail);
        card.setPreferredSize(new Dimension(470, 0));
        return card;
    }

    /** Một khối: tiêu đề in hoa + bảng cao vừa đủ số dòng (không cuộn riêng). */
    private static JComponent section(String title, JTable t) {
        // Cao đúng bằng nội dung để các khối xếp sát nhau (BoxLayout mặc định kéo giãn đều)
        JPanel p = new JPanel(new BorderLayout(0, 6)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(0, 0, 14, 0));
        p.add(Ui.caps(title), BorderLayout.NORTH);
        JPanel tbl = new JPanel(new BorderLayout());
        tbl.add(t.getTableHeader(), BorderLayout.NORTH);
        tbl.add(t, BorderLayout.CENTER);
        p.add(tbl, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private void openDetail(SessionInfo s) {
        detailTitle.setText("Phiên #" + s.id() + " · " + DT.format(s.start()));
        controller.loadDetail(s, this::showDetail, msg -> Toast.show(this, msg, Toast.Kind.ERROR));
    }

    private void showDetail(SessionDetail d) {
        current = d;
        for (JTable t : new JTable[] { dtcTable }) {
            t.setRowHeight(34);
        }
        dtcModel.setRowCount(0);
        for (Dtc x : d.dtcs()) {
            dtcModel.addRow(new Object[] { x.code(), x.description() == null ? "Mã chưa có trong danh mục" : x.description(),
                    x.severity() });
        }
        if (d.dtcs().isEmpty()) {
            dtcModel.addRow(new Object[] { "—", "Không có mã lỗi", null });
        }
        causeModel.setRowCount(0);
        for (SessionDetail.RankedCause c : d.causes()) {
            causeModel.addRow(new Object[] { c.rank(), c.name(), String.format(Locale.of("vi", "VN"), "%.2f", c.score()) });
        }
        if (d.causes().isEmpty()) {
            causeModel.addRow(new Object[] { "—", "Phiên này chưa phân tích nguyên nhân", "" });
        }
        pidModel.setRowCount(0);
        for (Map.Entry<Integer, Double> e : d.pids().entrySet()) {
            pidModel.addRow(new Object[] { String.format("0x%02X", e.getKey()), PidDecoder.name(e.getKey()),
                    String.format(Locale.of("vi", "VN"), "%.2f %s", e.getValue(), PidDecoder.unit(e.getKey())) });
        }
        if (d.pids().isEmpty()) {
            pidModel.addRow(new Object[] { "—", "Không chụp PID (chỉ đọc hoặc xóa mã lỗi)", "" });
        }
        logModel.setRowCount(0);
        for (HistoryEntry e : d.log()) {
            logModel.addRow(new Object[] { TIME.format(e.time()), e.action(), e.detail() });
        }
        detailCards.show(detail, "show");
        detail.revalidate();
    }

    /** Lưu phiếu chẩn đoán HTML rồi mở bằng trình duyệt mặc định (từ đó in hoặc lưu PDF). */
    private void exportReport() {
        Vehicle v = controller.currentVehicle();
        if (current == null || v == null) {
            return;
        }
        JFileChooser fc = new JFileChooser(new File(System.getProperty("user.home"), "Documents"));
        fc.setDialogTitle("Lưu phiếu chẩn đoán");
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), ReportExporter.fileName(v, current)));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        try {
            Files.writeString(f.toPath(), ReportExporter.html(v, current, LocalDateTime.now()), StandardCharsets.UTF_8);
            Toast.show(this, "Đã lưu phiếu: " + f.getName(), Toast.Kind.OK);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(f.toURI());
            }
        } catch (Exception ex) {
            Toast.show(this, "Không lưu được phiếu: " + ex.getMessage(), Toast.Kind.ERROR);
        }
    }
}
