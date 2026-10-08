package view;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import controller.MaintenanceController;
import model.MaintenanceInterval;
import model.MaintenanceRecord;
import model.Vehicle;
import service.MaintenanceSchedule;
import service.MaintenanceSchedule.Reminder;
import service.MaintenanceSchedule.Status;

/**
 * Tab "Bảo dưỡng" (UC11): ô số liệu chi phí, nhắc bảo dưỡng theo chu kỳ (đến hạn / sắp đến / còn xa),
 * lịch sử có lọc theo năm và từ khóa, chi phí theo hạng mục; biểu mẫu thêm/sửa ở bên phải.
 */
public class MaintenancePanel extends JPanel {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d/M/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE_SHOW = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.of("vi", "VN"));
    private static final int MAX_KM = 9_999_999;
    private static final long MAX_COST = 999_999_999_999L;  // vừa DECIMAL(12,0)
    private static final String ALL_YEARS = "Tất cả các năm";
    // Gợi ý hạng mục thường gặp ngoài các hạng mục đã có chu kỳ (chỉ để chọn nhanh, vẫn nhập tự do được)
    private static final String[] COMMON = { "Thay lọc gió động cơ", "Thay nước làm mát", "Kiểm tra hệ thống phanh",
            "Thay ắc quy", "Đảo lốp, cân chỉnh độ chụm", "Bảo dưỡng định kỳ" };

    private final MaintenanceController controller;
    private final List<MaintenanceRecord> records = new ArrayList<>();
    private Vehicle vehicle;

    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);

    private final StatTile costYear = new StatTile("Chi phí năm nay");
    private final StatTile costAll = new StatTile("Tổng chi phí");
    private final StatTile count = new StatTile("Số lần bảo dưỡng");
    private final StatTile due = new StatTile("Hạng mục cần làm");

    private final DefaultTableModel remindModel = model("Hạng mục", "Lần gần nhất", "Hạn tiếp theo", "Còn lại", "Trạng thái");
    private final DefaultTableModel historyModel = model("Ngày", "Số km", "Hạng mục", "Chi phí (đ)", "Ghi chú");
    private final DefaultTableModel costModel = model("Hạng mục", "Số lần", "Chi phí (đ)", "Tỉ lệ");
    private final JTable historyTable = Ui.table(historyModel);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(historyModel);
    private final JComboBox<String> yearFilter = new JComboBox<>();
    private final JTextField search = new JTextField(16);
    private final JLabel historyCount = Ui.status(" ");

    private final JTextField date = new JTextField();
    private final JTextField km = new JTextField();
    private final JComboBox<String> item = new JComboBox<>();
    private final JTextField cost = new JTextField();
    private final JTextField note = new JTextField();
    private final JLabel dateErr = new JLabel();
    private final JLabel kmErr = new JLabel();
    private final JLabel itemErr = new JLabel();
    private final JLabel costErr = new JLabel();
    private final JLabel formTitle = Ui.caps("Ghi lần bảo dưỡng mới");
    private final JButton save = Ui.primary("Lưu", Icons.Kind.SAVE);
    private final JButton newBtn = Ui.secondary("Mới", Icons.Kind.PLUS);
    private final JButton delete = Ui.danger("Xóa", Icons.Kind.TRASH);
    private MaintenanceRecord editing;

    public MaintenancePanel(MaintenanceController controller) {
        super(new BorderLayout());
        this.controller = controller;
        setOpaque(false);

        JPanel main = new JPanel(new BorderLayout(16, 0));
        main.setOpaque(false);
        main.add(buildContent(), BorderLayout.CENTER);
        main.add(buildForm(), BorderLayout.EAST);
        body.setOpaque(false);
        body.add(Ui.empty(Icons.Kind.WRENCH, "Chưa chọn xe", "Chọn một xe ở tab Quản lý xe để xem và ghi bảo dưỡng."), "empty");
        body.add(main, "main");
        add(body, BorderLayout.CENTER);
        cards.show(body, "empty");

        controller.addListListener(this::showList);
        controller.addIntervalListener(list -> {
            refreshItems();
            refreshReminders();
        });
        controller.addErrorListener(msg -> Toast.show(this, msg, Toast.Kind.ERROR));
        controller.reload(msg -> Toast.show(this, msg, Toast.Kind.ERROR));
        startNew();
    }

    private static DefaultTableModel model(String... cols) {
        return new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    // ------------------------------------------------------------------ nội dung chính
    private JComponent buildContent() {
        JPanel stats = new JPanel(new GridLayout(1, 4, 12, 0));
        stats.setOpaque(false);
        stats.add(costYear);
        stats.add(costAll);
        stats.add(count);
        stats.add(due);

        // Nhắc bảo dưỡng
        JTable remind = Ui.table(remindModel);
        Ui.widths(remind, 0, 100, 175, 175, 128);
        remind.getColumnModel().getColumn(4).setCellRenderer(new StatusTag());
        JButton intervals = Ui.secondary("Chu kỳ bảo dưỡng", Icons.Kind.WRENCH);
        intervals.addActionListener(e -> new IntervalDialog(javax.swing.SwingUtilities.getWindowAncestor(this), controller).setVisible(true));
        JLabel rule = Ui.status("Hạn = lần gần nhất + chu kỳ, mốc km hoặc ngày nào đến trước tính trước.");
        rule.setToolTipText("Sắp đến: còn không quá 10 % chu kỳ km hoặc không quá 30 ngày.");
        rule.setFont(Theme.SMALL);
        JPanel remindTop = new JPanel(new BorderLayout(10, 0));
        remindTop.setOpaque(false);
        remindTop.add(rule, BorderLayout.CENTER);
        remindTop.add(intervals, BorderLayout.EAST);
        JPanel remindPane = new JPanel(new BorderLayout(0, 8));
        remindPane.setOpaque(false);
        remindPane.add(remindTop, BorderLayout.NORTH);
        remindPane.add(Ui.scroll(remind), BorderLayout.CENTER);

        // Lịch sử
        historyTable.setRowSorter(sorter);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Ui.widths(historyTable, 110, 100, 0, 130, 0);
        historyTable.getColumnModel().getColumn(1).setCellRenderer(right());
        historyTable.getColumnModel().getColumn(3).setCellRenderer(right());
        historyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                MaintenanceRecord r = rowRecord();
                if (r != null) {
                    edit(r);
                }
            }
        });
        yearFilter.addActionListener(e -> {
            filterHistory();
            refreshCosts();
        });
        search.putClientProperty("JTextField.placeholderText", "Tìm hạng mục, ghi chú...");
        search.putClientProperty("JTextField.leadingIcon", Icons.of(Icons.Kind.SEARCH, 16, Theme.TEXT_FAINT));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterHistory();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterHistory();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterHistory();
            }
        });
        JPanel histTop = new JPanel(new BorderLayout(10, 0));
        histTop.setOpaque(false);
        histTop.add(Ui.row(8, yearFilter, search), BorderLayout.WEST);
        histTop.add(historyCount, BorderLayout.CENTER);
        JPanel histPane = new JPanel(new BorderLayout(0, 8));
        histPane.setOpaque(false);
        histPane.add(histTop, BorderLayout.NORTH);
        histPane.add(Ui.scroll(historyTable), BorderLayout.CENTER);

        // Chi phí theo hạng mục
        JTable costTable = Ui.table(costModel);
        Ui.widths(costTable, 0, 90, 150, 230);
        costTable.getColumnModel().getColumn(1).setCellRenderer(right());
        costTable.getColumnModel().getColumn(2).setCellRenderer(right());
        costTable.getColumnModel().getColumn(3).setCellRenderer(new ShareBar());
        JLabel costNote = Ui.status("Theo năm đang chọn ở thẻ Lịch sử bảo dưỡng.");
        costNote.setFont(Theme.SMALL);
        JPanel costPane = new JPanel(new BorderLayout(0, 8));
        costPane.setOpaque(false);
        costPane.add(costNote, BorderLayout.NORTH);
        costPane.add(Ui.scroll(costTable), BorderLayout.CENTER);

        JTabbedPane tabs = new JTabbedPane();
        tabs.putClientProperty("JTabbedPane.tabType", "underlined");
        tabs.putClientProperty("JTabbedPane.underlineColor", Theme.ACCENT);
        tabs.putClientProperty("JTabbedPane.tabHeight", 36);
        tabs.setFont(Theme.FONT_BOLD);
        tabs.addTab("Nhắc bảo dưỡng", Icons.of(Icons.Kind.WARN, 14, Theme.TEXT_DIM), remindPane);
        tabs.addTab("Lịch sử bảo dưỡng", Icons.of(Icons.Kind.HISTORY, 14, Theme.TEXT_DIM), histPane);
        tabs.addTab("Chi phí theo hạng mục", Icons.of(Icons.Kind.EXPORT, 14, Theme.TEXT_DIM), costPane);
        Card tabCard = new Card(new BorderLayout());
        tabCard.add(tabs);

        JPanel p = new JPanel(new BorderLayout(0, 14));
        p.setOpaque(false);
        p.add(stats, BorderLayout.NORTH);
        p.add(tabCard, BorderLayout.CENTER);
        return p;
    }

    private static DefaultTableCellRenderer right() {
        DefaultTableCellRenderer r = Ui.padded();
        r.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        return r;
    }

    // ------------------------------------------------------------------ biểu mẫu
    private JComponent buildForm() {
        item.setEditable(true);
        date.putClientProperty("JTextField.placeholderText", "dd/mm/yyyy");
        km.putClientProperty("JTextField.placeholderText", "VD: 52.000");
        cost.putClientProperty("JTextField.placeholderText", "VD: 850.000");
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(Ui.field("Ngày bảo dưỡng *", date, dateErr));
        form.add(Ui.field("Số km lúc bảo dưỡng", km, kmErr));
        form.add(Ui.field("Hạng mục *", item, itemErr));
        form.add(Ui.field("Chi phí (đồng)", cost, costErr));
        form.add(Ui.field("Ghi chú", note, null));
        for (Component c : form.getComponents()) {
            ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        }

        save.addActionListener(e -> save());
        newBtn.addActionListener(e -> startNew());
        delete.addActionListener(e -> deleteRecord());

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(formTitle, BorderLayout.NORTH);
        top.add(form, BorderLayout.CENTER);

        Card card = new Card(new BorderLayout(0, 12));
        card.add(top, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new GridLayout(1, 3, 6, 0));
        buttons.setOpaque(false);
        buttons.add(save);
        buttons.add(newBtn);
        buttons.add(delete);
        card.add(buttons, BorderLayout.SOUTH);
        card.setPreferredSize(new Dimension(320, 0));
        return card;
    }

    private void startNew() {
        editing = null;
        historyTable.clearSelection();
        clearErrors();
        date.setText(DATE_SHOW.format(LocalDate.now()));
        km.setText(vehicle == null || vehicle.odometerKm() == null ? "" : String.valueOf(vehicle.odometerKm()));
        item.setSelectedItem("");
        cost.setText("");
        note.setText("");
        formTitle.setText("GHI LẦN BẢO DƯỠNG MỚI");
        delete.setEnabled(false);
    }

    private void edit(MaintenanceRecord r) {
        editing = r;
        clearErrors();
        date.setText(DATE_SHOW.format(r.date()));
        km.setText(r.km() == null ? "" : String.valueOf(r.km()));
        item.setSelectedItem(r.item());
        cost.setText(r.cost() == null ? "" : String.valueOf(r.cost()));
        note.setText(r.note() == null ? "" : r.note());
        formTitle.setText("SỬA LẦN BẢO DƯỠNG NGÀY " + DATE_SHOW.format(r.date()));
        delete.setEnabled(true);
    }

    private void save() {
        MaintenanceRecord r = readForm(editing == null ? 0 : editing.id());
        if (r == null) {
            return;
        }
        // Đồng hồ công-tơ-mét không chạy lùi: số km nhỏ hơn lần bảo dưỡng trước đó thường là nhập nhầm
        Integer before = MaintenanceSchedule.maxKmUntil(records, r.date(), r.id());
        if (r.km() != null && before != null && r.km() < before) {
            int ok = JOptionPane.showConfirmDialog(this, "Số km " + NUMBER.format(r.km())
                    + " nhỏ hơn số km đã ghi ở lần bảo dưỡng trước (" + NUMBER.format(before) + ").\nVẫn lưu?",
                    "Kiểm tra số km", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) {
                return;
            }
        }
        if (editing == null) {
            controller.add(r, msg -> Toast.show(this, msg, Toast.Kind.ERROR));
            Toast.show(this, "Đã ghi \"" + r.item() + "\" ngày " + DATE_SHOW.format(r.date()), Toast.Kind.OK);
            startNew();
        } else {
            controller.update(r, msg -> Toast.show(this, msg, Toast.Kind.ERROR));
            Toast.show(this, "Đã lưu thay đổi", Toast.Kind.OK);
        }
    }

    private void deleteRecord() {
        if (editing == null) {
            return;
        }
        MaintenanceRecord r = editing;
        int ok = JOptionPane.showConfirmDialog(this,
                "Xóa lần bảo dưỡng \"" + r.item() + "\" ngày " + DATE_SHOW.format(r.date()) + "?", "Xác nhận xóa",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok == JOptionPane.YES_OPTION) {
            controller.delete(r, msg -> Toast.show(this, msg, Toast.Kind.ERROR));
            Toast.show(this, "Đã xóa", Toast.Kind.OK);
            startNew();
        }
    }

    /** Đọc và kiểm tra biểu mẫu, báo lỗi dưới từng ô; trả null nếu có ô sai. */
    private MaintenanceRecord readForm(int id) {
        clearErrors();
        boolean ok = true;
        LocalDate d = null;
        try {
            d = LocalDate.parse(date.getText().trim(), DATE_FMT);
            if (d.isAfter(LocalDate.now())) {
                ok = err(dateErr, date, "Không được ở tương lai");
            }
        } catch (DateTimeParseException ex) {
            ok = err(dateErr, date, "Nhập dạng dd/mm/yyyy");
        }
        String it = item.getEditor().getItem() == null ? "" : item.getEditor().getItem().toString().trim();
        if (it.isEmpty()) {
            itemErr.setText("Bắt buộc nhập hạng mục");
            ok = false;
        } else if (it.length() > 100) {
            itemErr.setText("Tối đa 100 ký tự");
            ok = false;
        }
        Long kmValue = parseNumber(km.getText(), MAX_KM);
        if (kmValue == null && !km.getText().isBlank()) {
            ok = err(kmErr, km, "Số nguyên từ 0 đến " + NUMBER.format(MAX_KM));
        }
        Long costValue = parseNumber(cost.getText(), MAX_COST);
        if (costValue == null && !cost.getText().isBlank()) {
            ok = err(costErr, cost, "Số nguyên không âm (đồng)");
        }
        String n = note.getText().trim();
        if (n.length() > 300) {
            Toast.show(this, "Ghi chú tối đa 300 ký tự.", Toast.Kind.ERROR);
            ok = false;
        }
        if (vehicle == null || !ok) {
            return null;
        }
        return new MaintenanceRecord(id, vehicle.id(), d, kmValue == null ? null : Math.toIntExact(kmValue), it,
                costValue, n.isEmpty() ? null : n);
    }

    private static boolean err(JLabel label, JTextField f, String msg) {
        label.setText(msg);
        f.putClientProperty("JComponent.outline", "error");
        return false;
    }

    private void clearErrors() {
        for (JLabel l : new JLabel[] { dateErr, kmErr, itemErr, costErr }) {
            l.setText(" ");
        }
        for (JTextField f : new JTextField[] { date, km, cost }) {
            f.putClientProperty("JComponent.outline", null);
        }
    }

    /** Số nguyên không âm (cho phép dấu chấm, phẩy, khoảng trắng ngăn cách hàng nghìn); null nếu rỗng hoặc sai. */
    private static Long parseNumber(String s, long max) {
        String digits = s.replace(".", "").replace(",", "").replace(" ", "");
        if (!digits.matches("\\d{1,12}")) {
            return null;
        }
        long v = Long.parseLong(digits);
        return v > max ? null : v;
    }

    // ------------------------------------------------------------------ hiển thị
    private void showList(Vehicle v, List<MaintenanceRecord> list) {
        vehicle = v;
        records.clear();
        records.addAll(list);
        cards.show(body, v == null ? "empty" : "main");
        if (v == null) {
            return;
        }
        historyModel.setRowCount(0);
        for (MaintenanceRecord r : list) {
            historyModel.addRow(new Object[] { DATE_SHOW.format(r.date()), r.km() == null ? "" : NUMBER.format(r.km()),
                    r.item(), r.cost() == null ? "" : NUMBER.format(r.cost()), r.note() == null ? "" : r.note() });
        }
        // Danh sách năm để lọc (mới nhất trước)
        Object keepYear = yearFilter.getSelectedItem();
        Set<String> years = new LinkedHashSet<>();
        years.add(ALL_YEARS);
        list.stream().map(r -> String.valueOf(r.date().getYear())).forEach(years::add);
        yearFilter.setModel(new DefaultComboBoxModel<>(years.toArray(new String[0])));
        if (keepYear != null && years.contains(keepYear.toString())) {
            yearFilter.setSelectedItem(keepYear);
        }
        int thisYear = LocalDate.now().getYear();
        costYear.setNumber(MaintenanceSchedule.totalCost(list, thisYear), " đ", null);
        costYear.setSub("Năm " + thisYear);
        costAll.setNumber(MaintenanceSchedule.totalCost(list, null), " đ", null);
        costAll.setSub("Mọi năm");
        count.setNumber(list.size(), "", null);
        count.setSub(list.isEmpty() ? "Chưa có hồ sơ" : "Gần nhất " + DATE_SHOW.format(list.get(0).date()));
        refreshItems();
        refreshReminders();
        filterHistory();
        refreshCosts();
        if (editing == null) {
            startNew();
        }
    }

    private void refreshReminders() {
        remindModel.setRowCount(0);
        if (vehicle == null) {
            return;
        }
        List<Reminder> list = MaintenanceSchedule.reminders(controller.intervals(), records, vehicle.odometerKm(),
                LocalDate.now());
        int urgent = 0;
        for (Reminder r : list) {
            String last = r.last() == null ? "Chưa làm" : DATE_SHOW.format(r.last().date());
            String next = r.last() == null ? cycle(r.interval())
                    : join(r.dueKm() == null ? null : NUMBER.format(r.dueKm()) + " km",
                            r.dueDate() == null ? null : DATE_SHOW.format(r.dueDate()));
            String left = remaining(r.kmLeft(), r.daysLeft());
            remindModel.addRow(new Object[] { r.interval().item(), last, next, left, r.status() });
            if (r.status() == Status.OVERDUE || r.status() == Status.SOON) {
                urgent++;
            }
        }
        due.setNumber(urgent, "", urgent > 0 ? Theme.DANGER : Theme.OK);
        due.setSub(list.isEmpty() ? "Chưa có chu kỳ" : "Trong " + list.size() + " hạng mục");
    }

    /** "Còn 4.800 km / 120 ngày" hoặc "Quá 6.800 km / 89 ngày" khi đã qua hạn. */
    private static String remaining(Integer kmLeft, Long daysLeft) {
        boolean over = (kmLeft != null && kmLeft <= 0) || (daysLeft != null && daysLeft <= 0);
        String k = kmLeft == null ? null : NUMBER.format(Math.abs((long) kmLeft)) + " km";
        String d = daysLeft == null ? null : Math.abs(daysLeft) + " ngày";
        String text = join(k, d);
        return text.isEmpty() ? "" : (over ? "Quá " : "Còn ") + text;
    }

    private static String cycle(MaintenanceInterval iv) {
        return "Mỗi " + join(iv.km() == null ? null : NUMBER.format(iv.km()) + " km",
                iv.months() == null ? null : iv.months() + " tháng");
    }

    private static String join(String a, String b) {
        if (a == null) {
            return b == null ? "" : b;
        }
        return b == null ? a : a + " / " + b;
    }

    private Integer selectedYear() {
        Object y = yearFilter.getSelectedItem();
        return y == null || ALL_YEARS.equals(y) ? null : Integer.valueOf(y.toString());
    }

    private void filterHistory() {
        List<RowFilter<DefaultTableModel, Integer>> fs = new ArrayList<>();
        Integer y = selectedYear();
        if (y != null) {
            fs.add(RowFilter.regexFilter("/" + y + "$", 0));
        }
        String q = search.getText().trim();
        if (!q.isEmpty()) {
            fs.add(RowFilter.regexFilter("(?iu)" + Pattern.quote(q), 2, 4));
        }
        sorter.setRowFilter(fs.isEmpty() ? null : RowFilter.andFilter(fs));
        historyCount.setText(historyTable.getRowCount() + " / " + records.size() + " lần");
    }

    private void refreshCosts() {
        costModel.setRowCount(0);
        Integer y = selectedYear();
        Map<String, Long> byItem = MaintenanceSchedule.costByItem(records, y);
        long total = byItem.values().stream().mapToLong(Long::longValue).sum();
        for (Map.Entry<String, Long> e : byItem.entrySet()) {
            long times = records.stream().filter(r -> (y == null || r.date().getYear() == y)
                    && MaintenanceSchedule.sameItem(r.item(), e.getKey())).count();
            costModel.addRow(new Object[] { e.getKey(), times, NUMBER.format(e.getValue()),
                    total == 0 ? 0.0 : (double) e.getValue() / total });
        }
    }

    /** Danh sách gợi ý hạng mục: các hạng mục có chu kỳ trước, rồi các hạng mục thường gặp. */
    private void refreshItems() {
        Object keep = item.getEditor().getItem();
        Set<String> names = new LinkedHashSet<>();
        controller.intervals().forEach(iv -> names.add(iv.item()));
        names.addAll(List.of(COMMON));
        item.setModel(new DefaultComboBoxModel<>(names.toArray(new String[0])));
        item.setSelectedItem(keep == null ? "" : keep);
    }

    private MaintenanceRecord rowRecord() {
        int row = historyTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        int m = historyTable.convertRowIndexToModel(row);
        return m < records.size() ? records.get(m) : null;
    }

    /** Ô trạng thái nhắc: nhãn tem đỏ / hổ phách / xanh / xám. */
    private static class StatusTag extends DefaultTableCellRenderer {
        private Status status;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, "", sel, false, r, c);
            status = v instanceof Status s ? s : null;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (status != null) {
                java.awt.Color col = switch (status) {
                    case OVERDUE -> Theme.DANGER;
                    case SOON -> Theme.WARN;
                    case OK -> Theme.OK;
                    case NO_DATA -> Theme.TEXT_FAINT;
                };
                SeverityTag.paintTag((Graphics2D) g, status.label().toUpperCase(Locale.of("vi")), col, 10, (getHeight() - 20) / 2);
            }
        }
    }

    /** Ô tỉ lệ chi phí: thanh ngang cam kèm phần trăm. */
    private static class ShareBar extends DefaultTableCellRenderer {
        private double share;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, "", sel, false, r, c);
            share = v instanceof Double d ? d : 0;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            int w = getWidth() - 80;
            int y = getHeight() / 2 - 5;
            g.setColor(Theme.RULE);
            g.fillRect(10, y, w, 10);
            g.setColor(Theme.ACCENT);
            g.fillRect(10, y, (int) Math.round(w * share), 10);
            g.setColor(Theme.TEXT_DIM);
            g.setFont(Theme.SMALL);
            g.drawString(Math.round(share * 100) + " %", w + 18, y + 9);
            g.dispose();
        }
    }
}
