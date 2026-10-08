package view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

import controller.MaintenanceController;
import model.MaintenanceInterval;

/** Hộp thoại xem và sửa chu kỳ bảo dưỡng theo hạng mục (bảng ChuKyBaoDuong). */
public class IntervalDialog extends JDialog {

    private static final NumberFormat NF = NumberFormat.getIntegerInstance(Locale.of("vi", "VN"));

    private final MaintenanceController controller;
    private final List<MaintenanceInterval> list = new ArrayList<>();
    private final DefaultTableModel model = new DefaultTableModel(new Object[] { "Hạng mục", "Chu kỳ km", "Chu kỳ tháng", "Nguồn" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = Ui.table(model);
    private final JTextField item = new JTextField();
    private final JTextField km = new JTextField();
    private final JTextField months = new JTextField();
    private final JTextField source = new JTextField();
    private final JLabel error = new JLabel(" ");
    private MaintenanceInterval editing;
    private boolean closed;   // sau khi đóng thì bỏ qua thông báo từ controller

    public IntervalDialog(Window owner, MaintenanceController controller) {
        super(owner, "Chu kỳ bảo dưỡng", ModalityType.APPLICATION_MODAL);
        this.controller = controller;

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Ui.widths(table, 0, 110, 110, 0);
        table.getSelectionModel().addListSelectionListener(e -> {
            int r = table.getSelectedRow();
            if (!e.getValueIsAdjusting() && r >= 0 && r < list.size()) {
                fill(list.get(r));
            }
        });

        JPanel fields = new JPanel(new GridLayout(1, 4, 10, 0));
        fields.setOpaque(false);
        fields.add(Ui.field("Hạng mục", item, null));
        fields.add(Ui.field("Chu kỳ km", km, null));
        fields.add(Ui.field("Chu kỳ tháng", months, null));
        fields.add(Ui.field("Nguồn", source, null));
        error.setForeground(Theme.DANGER);

        JButton save = Ui.primary("Lưu chu kỳ", Icons.Kind.SAVE);
        save.addActionListener(e -> save());
        JButton add = Ui.secondary("Hạng mục mới", Icons.Kind.PLUS);
        add.addActionListener(e -> fill(null));
        JButton delete = Ui.danger("Xóa", Icons.Kind.TRASH);
        delete.addActionListener(e -> delete());
        JButton close = Ui.secondary("Đóng", null);
        close.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setOpaque(false);
        buttons.add(Ui.row(8, save, add, delete), BorderLayout.WEST);
        buttons.add(close, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        south.add(fields, BorderLayout.NORTH);
        south.add(error, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);

        JLabel hint = Ui.status("Mốc nào đến trước (km hoặc tháng) thì tính đến hạn. Để trống một trong hai nếu không dùng.");
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        root.add(hint, BorderLayout.NORTH);
        Card tableCard = new Card(new BorderLayout());
        tableCard.add(Ui.scroll(table));
        root.add(tableCard, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(new Dimension(820, 520));
        setLocationRelativeTo(owner);

        controller.addIntervalListener(this::show);
        fill(null);
    }

    private void show(List<MaintenanceInterval> l) {
        if (closed) {
            return;
        }
        list.clear();
        list.addAll(l);
        model.setRowCount(0);
        for (MaintenanceInterval iv : l) {
            model.addRow(new Object[] { iv.item(), iv.km() == null ? "" : NF.format(iv.km()),
                    iv.months() == null ? "" : iv.months(), iv.source() == null ? "" : iv.source() });
        }
    }

    @Override
    public void dispose() {
        closed = true;
        super.dispose();
    }

    private void fill(MaintenanceInterval iv) {
        editing = iv;
        if (iv == null) {
            table.clearSelection();
        }
        item.setText(iv == null ? "" : iv.item());
        km.setText(iv == null || iv.km() == null ? "" : String.valueOf(iv.km()));
        months.setText(iv == null || iv.months() == null ? "" : String.valueOf(iv.months()));
        source.setText(iv == null || iv.source() == null ? "" : iv.source());
        error.setText(" ");
    }

    private void save() {
        String name = item.getText().trim();
        Integer k = positive(km.getText());
        Integer m = positive(months.getText());
        if (name.isEmpty()) {
            error.setText("Nhập tên hạng mục.");
            return;
        }
        if ((k == null && !km.getText().isBlank()) || (m == null && !months.getText().isBlank())) {
            error.setText("Chu kỳ phải là số nguyên dương.");
            return;
        }
        if (k == null && m == null) {
            error.setText("Cần ít nhất một chu kỳ (km hoặc tháng).");
            return;
        }
        String src = source.getText().isBlank() ? "Kỹ thuật viên tự đặt" : source.getText().trim();
        controller.saveInterval(new MaintenanceInterval(editing == null ? 0 : editing.id(), name, k, m, src), error::setText);
        fill(null);
    }

    private void delete() {
        if (editing == null) {
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, "Bỏ chu kỳ của \"" + editing.item() + "\"?\n(Hồ sơ bảo dưỡng không bị xóa.)",
                "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            controller.deleteInterval(editing, error::setText);
            fill(null);
        }
    }

    private static Integer positive(String s) {
        try {
            int v = Integer.parseInt(s.trim().replace(".", "").replace(",", ""));
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
