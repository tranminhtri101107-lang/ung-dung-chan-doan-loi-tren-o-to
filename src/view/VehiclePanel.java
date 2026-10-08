package view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import controller.VehicleController;
import model.Vehicle;
import model.VehicleSummary;
import service.VehicleRules;

/**
 * Tab "Quản lý xe" (UC01 + UC02), bố cục danh sách - chi tiết: bên trái tìm kiếm và bảng xe,
 * bên phải thẻ xe (tóm tắt lịch sử) và biểu mẫu thêm/sửa có kiểm tra từng ô.
 */
public class VehiclePanel extends JPanel {

    private static final int MIN_YEAR = 1950;
    private static final int MAX_YEAR = 2100;
    private static final NumberFormat NF = NumberFormat.getIntegerInstance(Locale.of("vi", "VN"));
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final VehicleController controller;
    private final List<Vehicle> vehicles = new ArrayList<>();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] { "Biển số", "Xe", "Chủ xe", "Số km", "Trạng thái" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = Ui.table(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = new JTextField(22);
    private final JLabel count = Ui.status(" ");

    // Biểu mẫu
    private final JTextField plate = new JTextField();
    private final JTextField brand = new JTextField();
    private final JTextField carModel = new JTextField();
    private final JTextField year = new JTextField();
    private final JTextField vin = new JTextField();
    private final JTextField km = new JTextField();
    private final JTextField owner = new JTextField();
    private final JTextField phone = new JTextField();
    private final JTextField note = new JTextField();
    private final JLabel plateErr = new JLabel();
    private final JLabel yearErr = new JLabel();
    private final JLabel vinErr = new JLabel();
    private final JLabel kmErr = new JLabel();
    private final JLabel phoneErr = new JLabel();
    private final JLabel formTitle = Ui.caps("Xe mới");
    private final JButton save = Ui.primary("Lưu", Icons.Kind.SAVE);
    private final JButton delete = Ui.danger("Xóa", Icons.Kind.TRASH);
    private final JButton choose = Ui.secondary("Chọn để chẩn đoán", Icons.Kind.CHECK);

    // Thẻ tóm tắt
    private final PlateBadge badge = new PlateBadge(20f);
    private final JLabel carName = new JLabel(" ");
    private final StatTile sessions = new StatTile("Phiên chẩn đoán");
    private final StatTile maint = new StatTile("Lần bảo dưỡng");
    private final JLabel lastDtcs = new JLabel(" ");
    private final JPanel summaryBox = new JPanel(new BorderLayout(0, 10));

    private Vehicle editing;   // xe đang sửa; null = đang nhập xe mới
    private VehicleSummary summary = VehicleSummary.EMPTY;

    public VehiclePanel(VehicleController controller) {
        super(new BorderLayout(16, 0));
        this.controller = controller;
        setOpaque(false);

        add(buildList(), BorderLayout.CENTER);
        add(buildDetail(), BorderLayout.EAST);
        startNew();

        controller.addListListener(this::showList);
        controller.addSelectionListener(v -> refreshRows());
        controller.reload(msg -> Toast.show(this, msg, Toast.Kind.ERROR));
    }

    // ------------------------------------------------------------------ danh sách
    private JComponent buildList() {
        search.putClientProperty("JTextField.placeholderText", "Tìm biển số, hãng, dòng xe, chủ xe...");
        search.putClientProperty("JTextField.leadingIcon", Icons.of(Icons.Kind.SEARCH, 16, Theme.TEXT_FAINT));
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter();
            }
        });
        JButton add = Ui.primary("Thêm xe mới", Icons.Kind.PLUS);
        add.addActionListener(e -> startNew());

        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);
        bar.add(search, BorderLayout.WEST);
        bar.add(count, BorderLayout.CENTER);
        bar.add(add, BorderLayout.EAST);

        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Ui.widths(table, 120, 0, 170, 100, 130);
        table.getColumnModel().getColumn(0).setCellRenderer(Ui.mono());
        table.getColumnModel().getColumn(4).setCellRenderer(new ChosenRenderer());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Vehicle v = rowVehicle();
                if (v != null) {
                    edit(v);
                }
            }
        });
        // Bấm đúp vào một xe để chọn xe đó cho chẩn đoán
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && rowVehicle() != null) {
                    chooseForDiagnosis();
                }
            }
        });

        Card list = new Card(new BorderLayout());
        list.add(Ui.scroll(table), BorderLayout.CENTER);
        JLabel hint = Ui.status("Bấm đúp vào một xe để chọn xe đó cho chẩn đoán.");
        hint.setFont(Theme.SMALL);
        hint.setBorder(new EmptyBorder(8, 0, 0, 0));
        list.add(hint, BorderLayout.SOUTH);

        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        p.add(bar, BorderLayout.NORTH);
        p.add(list, BorderLayout.CENTER);
        return p;
    }

    private void filter() {
        String q = search.getText().trim();
        sorter.setRowFilter(q.isEmpty() ? null : RowFilter.regexFilter("(?iu)" + Pattern.quote(q), 0, 1, 2));
        count.setText(table.getRowCount() + " / " + vehicles.size() + " xe");
    }

    // ------------------------------------------------------------------ chi tiết
    private JComponent buildDetail() {
        carName.setFont(Theme.HEADING);
        carName.setForeground(Theme.TEXT);
        JPanel head = new JPanel(new BorderLayout(12, 0));
        head.setOpaque(false);
        head.add(badge, BorderLayout.WEST);
        head.add(carName, BorderLayout.CENTER);

        JPanel stats = new JPanel(new GridLayout(1, 2, 10, 0));
        stats.setOpaque(false);
        stats.add(sessions);
        stats.add(maint);
        lastDtcs.setFont(Theme.MONO_BOLD);
        lastDtcs.setForeground(Theme.DANGER);
        JPanel dtcRow = new JPanel(new BorderLayout(8, 0));
        dtcRow.setOpaque(false);
        dtcRow.add(Ui.caps("Mã lỗi lần gần nhất"), BorderLayout.WEST);
        dtcRow.add(lastDtcs, BorderLayout.CENTER);

        summaryBox.setOpaque(false);
        summaryBox.add(head, BorderLayout.NORTH);
        summaryBox.add(stats, BorderLayout.CENTER);
        summaryBox.add(dtcRow, BorderLayout.SOUTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(pair(Ui.field("Biển số *", plate, plateErr), Ui.field("Năm sản xuất", year, yearErr)));
        form.add(pair(Ui.field("Hãng", brand, null), Ui.field("Dòng xe", carModel, null)));
        form.add(gap());
        form.add(full(Ui.field("Số VIN (17 ký tự)", vin, vinErr)));
        form.add(pair(Ui.field("Số km hiện tại", km, kmErr), Ui.field("SĐT chủ xe", phone, phoneErr)));
        form.add(full(Ui.field("Chủ xe", owner, null)));
        form.add(gap());
        form.add(full(Ui.field("Ghi chú", note, null)));
        plate.putClientProperty("JTextField.placeholderText", "VD: 30F-256.58");
        vin.putClientProperty("JTextField.placeholderText", "VD: MR053REH105123456");
        phone.putClientProperty("JTextField.placeholderText", "VD: 0905123456");
        vin.setFont(Theme.MONO);
        plate.setFont(Theme.MONO_BOLD);

        save.addActionListener(e -> save());
        delete.addActionListener(e -> deleteVehicle());
        choose.addActionListener(e -> chooseForDiagnosis());
        JPanel actions = Ui.row(8, save, choose, delete);
        actions.setAlignmentX(LEFT_ALIGNMENT);

        JPanel formCard = new JPanel(new BorderLayout(0, 10));
        formCard.setOpaque(false);
        formCard.add(formTitle, BorderLayout.NORTH);
        formCard.add(form, BorderLayout.CENTER);
        formCard.add(actions, BorderLayout.SOUTH);

        Card card = new Card(new BorderLayout(0, 16));
        card.add(summaryBox, BorderLayout.NORTH);
        card.add(formCard, BorderLayout.CENTER);
        JScrollPane sp = new JScrollPane(card);
        sp.setBorder(null);
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.setPreferredSize(new Dimension(430, 0));
        return sp;
    }

    private static JPanel pair(JComponent a, JComponent b) {
        JPanel p = new JPanel(new GridLayout(1, 2, 10, 0));
        p.setOpaque(false);
        p.add(a);
        p.add(b);
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private static JPanel full(JComponent a) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(a);
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private static JComponent gap() {
        JPanel g = new JPanel();
        g.setOpaque(false);
        g.setPreferredSize(new Dimension(0, 4));
        g.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
        g.setAlignmentX(LEFT_ALIGNMENT);
        return g;
    }

    /** Chuyển biểu mẫu sang chế độ nhập xe mới. */
    private void startNew() {
        editing = null;
        table.clearSelection();
        for (JTextField f : new JTextField[] { plate, brand, carModel, year, vin, km, owner, phone, note }) {
            f.setText("");
        }
        clearErrors();
        formTitle.setText("THÔNG TIN XE MỚI");
        summaryBox.setVisible(false);
        delete.setEnabled(false);
        choose.setEnabled(false);
        plate.requestFocusInWindow();
    }

    private void edit(Vehicle v) {
        editing = v;
        plate.setText(v.plate());
        brand.setText(nz(v.brand()));
        carModel.setText(nz(v.model()));
        year.setText(v.year() == null ? "" : String.valueOf(v.year()));
        vin.setText(nz(v.vin()));
        km.setText(v.odometerKm() == null ? "" : String.valueOf(v.odometerKm()));
        owner.setText(nz(v.owner()));
        phone.setText(nz(v.phone()));
        note.setText(nz(v.note()));
        clearErrors();
        formTitle.setText("SỬA THÔNG TIN XE");
        delete.setEnabled(true);
        choose.setEnabled(true);
        badge.setPlate(v.plate());
        carName.setText(v.name().isEmpty() ? "Chưa ghi hãng, dòng xe" : v.name() + (v.year() == null ? "" : " · " + v.year()));
        summaryBox.setVisible(true);
        sessions.setText("…", null);
        maint.setText("…", null);
        lastDtcs.setText(" ");
        controller.summary(v, s -> {
            if (editing != null && editing.id() == v.id()) {
                showSummary(s);
            }
        }, msg -> lastDtcs.setText("Không đọc được: " + msg));
    }

    private void showSummary(VehicleSummary s) {
        summary = s;
        sessions.setNumber(s.sessions(), "", null);
        sessions.setSub(s.lastSession() == null ? "Chưa chẩn đoán lần nào" : "Gần nhất " + DT.format(s.lastSession()));
        maint.setNumber(s.maintenances(), "", null);
        maint.setSub(s.lastMaintenance() == null ? "Chưa có hồ sơ" : "Gần nhất " + D.format(s.lastMaintenance()));
        if (s.lastDtcs().isEmpty()) {
            lastDtcs.setForeground(Theme.OK);
            lastDtcs.setText(s.sessions() == 0 ? "—" : "Không có mã lỗi");
        } else {
            lastDtcs.setForeground(Theme.DANGER);
            lastDtcs.setText(String.join("  ", s.lastDtcs()));
        }
    }

    // ------------------------------------------------------------------ thao tác
    private void save() {
        Vehicle v = readForm(editing == null ? 0 : editing.id());
        if (v == null) {
            Toast.show(this, "Kiểm tra lại các ô báo đỏ.", Toast.Kind.ERROR);
            return;
        }
        if (editing == null) {
            controller.add(v, msg -> Toast.show(this, msg, Toast.Kind.ERROR));
            Toast.show(this, "Đã thêm xe " + v.plate(), Toast.Kind.OK);
            startNew();
        } else {
            controller.update(v, msg -> Toast.show(this, msg, Toast.Kind.ERROR));
            Toast.show(this, "Đã lưu thay đổi của xe " + v.plate(), Toast.Kind.OK);
        }
    }

    private void deleteVehicle() {
        if (editing == null) {
            return;
        }
        Vehicle v = editing;
        if (summary.sessions() > 0 || summary.maintenances() > 0) {
            JOptionPane.showMessageDialog(this, "Không thể xóa xe " + v.plate() + " vì xe đã có " + summary.sessions()
                    + " phiên chẩn đoán và " + summary.maintenances() + " lần bảo dưỡng.\n"
                    + "Hồ sơ được giữ lại để không mất lịch sử của xe.", "Không thể xóa", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, "Xóa xe " + v.label() + "?", "Xác nhận xóa",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok == JOptionPane.YES_OPTION) {
            controller.delete(v, msg -> Toast.show(this, msg, Toast.Kind.ERROR));
            Toast.show(this, "Đã xóa xe " + v.plate(), Toast.Kind.OK);
            startNew();
        }
    }

    private void chooseForDiagnosis() {
        Vehicle v = rowVehicle();
        if (v == null) {
            return;
        }
        controller.select(v);
        Toast.show(this, "Đang chẩn đoán xe " + v.plate(), Toast.Kind.OK);
    }

    /** Đọc và kiểm tra biểu mẫu; báo lỗi ngay dưới từng ô. Trả null nếu có ô sai. */
    private Vehicle readForm(int id) {
        clearErrors();
        boolean ok = true;
        String p = VehicleRules.normalizePlate(plate.getText());
        if (plate.getText().isBlank()) {
            ok = err(plateErr, plate, "Bắt buộc nhập biển số");
        } else if (p == null) {
            ok = err(plateErr, plate, "Sai định dạng, VD 30F-256.58 hoặc 29A-1234");
        }
        Integer y = null;
        if (!year.getText().isBlank()) {
            try {
                y = Integer.parseInt(year.getText().trim());
                if (y < MIN_YEAR || y > MAX_YEAR) {
                    ok = err(yearErr, year, "Từ " + MIN_YEAR + " đến " + MAX_YEAR);
                }
            } catch (NumberFormatException ex) {
                ok = err(yearErr, year, "Phải là số");
            }
        }
        String v = VehicleRules.cleanVin(vin.getText());
        if (v != null && !VehicleRules.validVin(v)) {
            ok = err(vinErr, vin, "17 ký tự chữ và số, không có I, O, Q");
        }
        Integer k = null;
        if (!km.getText().isBlank()) {
            try {
                k = Integer.parseInt(km.getText().trim().replace(".", "").replace(",", ""));
                if (k < 0) {
                    ok = err(kmErr, km, "Không được âm");
                }
            } catch (NumberFormatException ex) {
                ok = err(kmErr, km, "Phải là số nguyên");
            }
        }
        String ph = VehicleRules.cleanPhone(phone.getText());
        if (ph != null && !VehicleRules.validPhone(ph)) {
            ok = err(phoneErr, phone, "10 số, bắt đầu bằng 0");
        }
        if (!ok) {
            return null;
        }
        plate.setText(p);
        return new Vehicle(id, p, blankToNull(brand.getText()), blankToNull(carModel.getText()), y,
                blankToNull(note.getText()), v, k, blankToNull(owner.getText()), ph);
    }

    private static boolean err(JLabel label, JTextField field, String msg) {
        label.setText(msg);
        field.putClientProperty("JComponent.outline", "error");
        return false;
    }

    private void clearErrors() {
        for (JLabel l : new JLabel[] { plateErr, yearErr, vinErr, kmErr, phoneErr }) {
            l.setText(" ");
        }
        for (JTextField f : new JTextField[] { plate, year, vin, km, phone }) {
            f.putClientProperty("JComponent.outline", null);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Xe của dòng đang chọn trong bảng (đã đổi chỉ số qua bộ lọc), hoặc null. */
    private Vehicle rowVehicle() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        int m = table.convertRowIndexToModel(row);
        return m < vehicles.size() ? vehicles.get(m) : null;
    }

    private void showList(List<Vehicle> list) {
        int keepId = editing == null ? -1 : editing.id();
        vehicles.clear();
        vehicles.addAll(list);
        refreshRows();
        filter();
        // Giữ nguyên xe đang sửa sau khi danh sách tải lại
        for (int i = 0; i < vehicles.size(); i++) {
            if (vehicles.get(i).id() == keepId) {
                int view = table.convertRowIndexToView(i);
                if (view >= 0) {
                    table.setRowSelectionInterval(view, view);
                }
                break;
            }
        }
    }

    /** Vẽ lại các dòng, đánh dấu xe đang được chọn để chẩn đoán. */
    private void refreshRows() {
        Vehicle chosen = controller.selected();
        int keep = table.getSelectedRow();
        model.setRowCount(0);
        for (Vehicle v : vehicles) {
            boolean isChosen = chosen != null && chosen.id() == v.id();
            String car = v.name() + (v.year() == null ? "" : " · " + v.year());
            model.addRow(new Object[] { v.plate(), car, nz(v.owner()),
                    v.odometerKm() == null ? "" : NF.format(v.odometerKm()), isChosen ? "ĐANG CHẨN ĐOÁN" : "" });
        }
        if (keep >= 0 && keep < table.getRowCount()) {
            table.setRowSelectionInterval(keep, keep);
        }
    }

    /** Ô trạng thái: nhãn cam khi xe đang được chọn để chẩn đoán. */
    private static class ChosenRenderer extends javax.swing.table.DefaultTableCellRenderer {
        private boolean chosen;

        @Override
        public java.awt.Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, "", sel, false, r, c);
            chosen = v != null && !v.toString().isEmpty();
            return this;
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            if (chosen) {
                SeverityTag.paintTag((java.awt.Graphics2D) g, "ĐANG CHẨN ĐOÁN", Theme.ACCENT, 10, (getHeight() - 20) / 2);
            }
        }
    }

    // ------------------------------------------------------------------ tiện ích cho các tab khác
    static JPanel labeled(String text, JComponent field) {
        return Ui.field(text, field, null);
    }

    static JButton button(String text) {
        return Ui.secondary(text, null);
    }
}
