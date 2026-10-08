package view;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import controller.DiagnosticController;
import model.LiveData;
import service.PidDecoder;

/** Tab "Dữ liệu sống": 4 đồng hồ, 2 biểu đồ cuộn và bảng đủ 8 PID kiểu máy chẩn đoán. */
public class LiveDataPanel extends JPanel {

    // PID, tên, đơn vị, số chữ số thập phân khi hiển thị
    private static final Object[][] PIDS = {
            { PidDecoder.PID_RPM, "Tốc độ quay động cơ", "vòng/phút", 0 },
            { PidDecoder.PID_SPEED, "Tốc độ xe", "km/h", 0 },
            { PidDecoder.PID_COOLANT, "Nhiệt độ nước làm mát", "°C", 0 },
            { PidDecoder.PID_INTAKE_TEMP, "Nhiệt độ khí nạp", "°C", 0 },
            { PidDecoder.PID_THROTTLE, "Vị trí bướm ga", "%", 1 },
            { PidDecoder.PID_LOAD, "Tải động cơ tính toán", "%", 1 },
            { PidDecoder.PID_MAF, "Lưu lượng khí nạp (MAF)", "g/s", 2 },
            { PidDecoder.PID_VOLTAGE, "Điện áp mô-đun điều khiển", "V", 2 },
    };

    private final GaugePanel rpm = new GaugePanel("Vòng tua", "x1000 v/ph", 0, 7000, 5000, 6000, 1000);
    private final GaugePanel speed = new GaugePanel("Tốc độ", "km/h", 0, 220, 140, 180, 20);
    private final GaugePanel coolant = new GaugePanel("Nước làm mát", "°C", 40, 130, 100, 110, 10);
    private final GaugePanel throttle = new GaugePanel("Bướm ga", "%", 0, 100, 80, 95, 10);
    private final TrendPanel rpmTrend = new TrendPanel("Vòng tua động cơ", "v/ph", 0, 7000, Theme.ACCENT);
    private final TrendPanel coolantTrend = new TrendPanel("Nhiệt độ nước làm mát", "°C", 40, 130, Theme.INFO);
    private final DefaultTableModel pidModel = new DefaultTableModel(new Object[] { "PID", "Tham số", "Giá trị", "Đơn vị" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final DiagnosticController controller;

    public LiveDataPanel(DiagnosticController controller) {
        super(new BorderLayout(0, 14));
        this.controller = controller;
        setOpaque(false);

        Card gauges = new Card(new GridLayout(1, 4, 8, 0));
        gauges.add(rpm);
        gauges.add(speed);
        gauges.add(coolant);
        gauges.add(throttle);

        JPanel trends = new JPanel(new GridLayout(2, 1, 0, 14));
        trends.setOpaque(false);
        Card t1 = new Card(new BorderLayout());
        t1.add(rpmTrend);
        Card t2 = new Card(new BorderLayout());
        t2.add(coolantTrend);
        trends.add(t1);
        trends.add(t2);

        for (Object[] p : PIDS) {
            pidModel.addRow(new Object[] { String.format("0x%02X", (Integer) p[0]), p[1], "-", p[2] });
        }
        JTable pids = Ui.table(pidModel);
        pids.setRowHeight(31);
        Ui.widths(pids, 64, 0, 90, 90);
        pids.getColumnModel().getColumn(0).setCellRenderer(Ui.mono());
        pids.getColumnModel().getColumn(2).setCellRenderer(Ui.mono());
        Card pidCard = Ui.titled("Bảng PID (Mode 01)", Icons.Kind.GAUGE, null, Ui.scroll(pids));
        pidCard.setPreferredSize(new java.awt.Dimension(470, 0));

        JPanel bottom = new JPanel(new BorderLayout(14, 0));
        bottom.setOpaque(false);
        bottom.add(trends, BorderLayout.CENTER);
        bottom.add(pidCard, BorderLayout.EAST);
        bottom.setPreferredSize(new java.awt.Dimension(0, 352));

        add(gauges, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        controller.addLiveListener(this::update);
    }

    private void update(LiveData d) {
        rpm.setValue(d.rpm());
        speed.setValue(d.speedKmh());
        coolant.setValue(d.coolantC());
        throttle.setValue(d.throttlePct());
        rpmTrend.addPoint(d.rpm());
        coolantTrend.addPoint(d.coolantC());
        Map<Integer, Double> values = controller.latestPids();
        for (int i = 0; i < PIDS.length; i++) {
            Double v = values.get((Integer) PIDS[i][0]);
            String text = v == null ? "-" : String.format("%." + PIDS[i][3] + "f", v).replace('.', ',');
            if (!text.equals(pidModel.getValueAt(i, 2))) {
                pidModel.setValueAt(text, i, 2);
            }
        }
    }
}
