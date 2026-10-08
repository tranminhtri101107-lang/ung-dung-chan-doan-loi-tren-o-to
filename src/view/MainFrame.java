package view;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

import controller.CatalogController;
import controller.DiagnosticController;
import controller.MaintenanceController;
import controller.VehicleController;
import model.Vehicle;

/**
 * Cửa sổ chính kiểu máy chẩn đoán xưởng: thanh bên than chì (icon + vạch chỉ báo cam trượt theo mục chọn),
 * thanh đầu hiện biển số và thông tin xe đang chọn, vùng nội dung mờ dần khi chuyển trang.
 */
public class MainFrame extends JFrame {

    private static final NumberFormat NF = NumberFormat.getIntegerInstance(Locale.of("vi", "VN"));

    private final CardLayout cards = new CardLayout();
    private final FadePane content = new FadePane(cards);
    private final JLabel title = new JLabel();
    private final JLabel subtitle = new JLabel();
    private final Sidebar sidebar = new Sidebar();
    private final List<NavItem> items = new ArrayList<>();
    private final JPanel header = new JPanel(new BorderLayout(24, 0));

    public MainFrame(DiagnosticController controller, CatalogController catalogController,
            VehicleController vehicleController, MaintenanceController maintenanceController) {
        super("AutoDiag - Ứng dụng chẩn đoán lỗi ô tô");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 700));
        setSize(1320, 800);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

        add(sidebar, BorderLayout.WEST);
        sidebar.setStatus(controller::sourceName, controller::sourceConnected);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Theme.BG);
        main.add(buildHeader(vehicleController), BorderLayout.NORTH);
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(18, 24, 20, 24));
        main.add(content, BorderLayout.CENTER);
        add(main, BorderLayout.CENTER);

        addPage("vehicle", "Quản lý xe", "Hồ sơ xe của xưởng, chọn xe cần chẩn đoán", Icons.Kind.CAR,
                new VehiclePanel(vehicleController));
        addPage("live", "Dữ liệu sống", "OBD-II Mode 01: 8 PID cập nhật liên tục", Icons.Kind.GAUGE,
                new LiveDataPanel(controller));
        addPage("dtc", "Chẩn đoán lỗi", "Mode 03 đọc mã lỗi, Mode 04 xóa, phân tích nguyên nhân", Icons.Kind.SCAN,
                new DtcPanel(controller));
        addPage("history", "Lịch sử", "Các phiên chẩn đoán của xe đang chọn", Icons.Kind.HISTORY,
                new HistoryPanel(controller));
        addPage("maintenance", "Bảo dưỡng", "Lịch sử và nhắc bảo dưỡng theo xe", Icons.Kind.WRENCH,
                new MaintenancePanel(maintenanceController));
        addPage("catalog", "Tra cứu DTC", "Danh mục mã lỗi, ý nghĩa và nguyên nhân thường gặp", Icons.Kind.SEARCH,
                new CatalogPanel(catalogController));
        select(items.get(0), false);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                controller.startLive();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                controller.stopLive();
            }
        });
    }

    /** Chuyển tới trang theo khóa (ví dụ "vehicle"). */
    public void showPage(String key) {
        items.stream().filter(i -> i.key.equals(key)).findFirst().ifPresent(i -> select(i, true));
    }

    private JComponent buildHeader(VehicleController vehicles) {
        title.setFont(Theme.TITLE);
        title.setForeground(Theme.TEXT);
        subtitle.setFont(Theme.FONT);
        subtitle.setForeground(Theme.TEXT_DIM);
        // Đệm phải vài px: phông chữ khi vẽ có thể rộng hơn số đo một chút, tránh mất ký tự cuối
        title.setBorder(new EmptyBorder(0, 0, 0, 8));
        subtitle.setBorder(new EmptyBorder(0, 0, 0, 24));
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(title);
        left.add(subtitle);

        // Khối xe đang chọn: biển số kiểu biển xe + tên xe + VIN, số km
        PlateBadge plate = new PlateBadge(17f);
        plate.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        plate.setToolTipText("Bấm để mở Quản lý xe");
        plate.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showPage("vehicle");
            }
        });
        JLabel name = new JLabel(" ");
        name.setFont(Theme.FONT_BOLD);
        name.setForeground(Theme.TEXT);
        JLabel meta = Ui.caps(" ");
        name.setBorder(new EmptyBorder(0, 0, 0, 8));
        meta.setBorder(new EmptyBorder(0, 0, 0, 8));
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(name);
        info.add(meta);
        JPanel right = new JPanel(new BorderLayout(12, 0));
        right.setOpaque(false);
        right.add(info, BorderLayout.CENTER);
        right.add(plate, BorderLayout.EAST);
        vehicles.addSelectionListener(v -> {
            plate.setPlate(v == null ? null : v.plate());
            name.setText(v == null ? "Hãy chọn xe ở Quản lý xe" : vehicleLine(v));
            meta.setText(v == null ? " " : metaLine(v));
            header.revalidate();
            header.repaint();
        });
        name.setText("Hãy chọn xe ở Quản lý xe");

        header.setBackground(Theme.CARD);
        header.setBorder(new javax.swing.border.CompoundBorder(new MatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(14, 24, 14, 24)));
        header.add(left, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private static String vehicleLine(Vehicle v) {
        String n = v.name().isEmpty() ? "Chưa ghi hãng, dòng xe" : v.name();
        return v.year() == null ? n : n + " · " + v.year();
    }

    private static String metaLine(Vehicle v) {
        String vin = v.vin() == null ? "VIN: chưa nhập" : "VIN " + v.vin();
        String km = v.odometerKm() == null ? "" : "  ·  " + NF.format(v.odometerKm()) + " KM";
        return vin + km;
    }

    private void addPage(String key, String text, String sub, Icons.Kind icon, JComponent page) {
        content.add(page, key);
        NavItem item = new NavItem(key, text, sub, icon);
        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                select(item, true);
            }
        });
        items.add(item);
        sidebar.addItem(item);
    }

    private void select(NavItem item, boolean animate) {
        items.forEach(i -> i.setSelected(i == item));
        cards.show(content, item.key);
        title.setText(item.text);
        subtitle.setText(item.sub);
        header.revalidate();
        sidebar.moveIndicator(item, animate);
        if (animate) {
            content.fadeIn();
        }
    }

    /** Vùng nội dung: sau khi đổi trang thì phủ lớp nền mờ dần để tạo hiệu ứng chuyển trang. */
    private static class FadePane extends JPanel {
        private float veil;

        FadePane(CardLayout layout) {
            super(layout);
        }

        void fadeIn() {
            Anim.run(220, t -> {
                veil = (float) (1 - t);
                repaint();
            }, null);
        }

        @Override
        protected void paintChildren(Graphics g0) {
            super.paintChildren(g0);
            if (veil > 0.01f) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, veil));
                g.setColor(Theme.BG);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.dispose();
            }
        }
    }

    /** Một mục điều hướng: icon + tên, sáng nền khi rê chuột. */
    private static class NavItem extends JComponent {
        final String key;
        final String text;
        final String sub;
        final Icons.Kind icon;
        private boolean selected;
        private boolean hover;

        NavItem(String key, String text, String sub, Icons.Kind icon) {
            this.key = key;
            this.text = text;
            this.sub = sub;
            this.icon = icon;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(220, 44));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            setAlignmentX(LEFT_ALIGNMENT);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        void setSelected(boolean s) {
            selected = s;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (selected || hover) {
                g.setColor(selected ? Theme.SIDEBAR_HOVER : new Color(0x2A2C31));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            Color fg = selected ? Color.WHITE : Theme.SIDEBAR_TEXT;
            Icons.of(icon, 18, selected ? Theme.ACCENT : Theme.SIDEBAR_DIM).paintIcon(this, g, 22, (getHeight() - 18) / 2);
            g.setFont(selected ? Theme.FONT_BOLD : Theme.FONT);
            g.setColor(fg);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, 52, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    /** Thanh bên than chì: logo, các mục, vạch chỉ báo cam và khối trạng thái kết nối ở cuối. */
    private static class Sidebar extends JPanel {
        private final JPanel list = new JPanel();
        private int indicatorY = -100;
        private int indicatorH = 44;
        private Timer anim;
        private java.util.function.Supplier<String> sourceName = () -> "";
        private java.util.function.BooleanSupplier connected = () -> false;
        private float pulse;

        Sidebar() {
            super(new BorderLayout());
            setBackground(Theme.SIDEBAR);
            setPreferredSize(new Dimension(220, 0));

            JPanel brand = new JPanel() {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setColor(Theme.ACCENT);
                    g.fillRect(22, 26, 10, 22);
                    g.setColor(Color.WHITE);
                    g.setFont(Theme.display(Font.BOLD, 21));
                    g.drawString("AUTODIAG", 42, 46);
                    g.setColor(Theme.SIDEBAR_DIM);
                    g.setFont(Theme.CAPS);
                    g.drawString("OBD-II · CAN BUS · XƯỞNG DỊCH VỤ", 22, 68);
                    g.dispose();
                }
            };
            brand.setOpaque(false);
            brand.setPreferredSize(new Dimension(220, 92));

            list.setOpaque(false);
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            JLabel menu = new JLabel("CHỨC NĂNG");
            menu.setFont(Theme.CAPS);
            menu.setForeground(Theme.SIDEBAR_DIM);
            menu.setBorder(new EmptyBorder(10, 22, 8, 0));
            menu.setAlignmentX(LEFT_ALIGNMENT);
            list.add(menu);

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(brand, BorderLayout.NORTH);
            top.add(list, BorderLayout.CENTER);
            add(top, BorderLayout.NORTH);

            StatusBlock status = new StatusBlock();
            add(status, BorderLayout.SOUTH);
            // Cập nhật trạng thái mỗi 100 ms; khi mất kết nối thì chấm đỏ nhấp nháy
            new Timer(100, e -> {
                pulse = (pulse + 0.08f) % 1f;
                status.repaint();
            }).start();
        }

        void setStatus(java.util.function.Supplier<String> name, java.util.function.BooleanSupplier ok) {
            this.sourceName = name;
            this.connected = ok;
        }

        void addItem(NavItem item) {
            list.add(item);
            list.add(Box.createVerticalStrut(2));
        }

        void moveIndicator(NavItem item, boolean animate) {
            Runnable go = () -> {
                int target = javax.swing.SwingUtilities.convertPoint(item, 0, 0, this).y;
                indicatorH = item.getHeight();
                if (anim != null) {
                    anim.stop();
                }
                if (!animate || indicatorY < 0) {
                    indicatorY = target;
                    repaint();
                    return;
                }
                int from = indicatorY;
                anim = Anim.run(200, t -> {
                    indicatorY = (int) Anim.lerp(from, target, t);
                    repaint();
                }, null);
            };
            if (item.getHeight() == 0) {
                javax.swing.SwingUtilities.invokeLater(go); // chưa bố trí xong: đợi lần vẽ đầu
            } else {
                go.run();
            }
        }

        @Override
        protected void paintChildren(Graphics g) {
            super.paintChildren(g);
            if (indicatorY >= 0) {
                g.setColor(Theme.ACCENT);
                g.fillRect(0, indicatorY, 4, indicatorH);
            }
        }

        /** Khối trạng thái nguồn dữ liệu ở cuối thanh bên. */
        private class StatusBlock extends JComponent {
            StatusBlock() {
                setPreferredSize(new Dimension(220, 86));
            }

            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(new Color(0x34373D));
                g.drawLine(16, 0, getWidth() - 16, 0);
                boolean ok = connected.getAsBoolean();
                float a = ok ? 1f : (float) (0.35 + 0.65 * Math.abs(Math.sin(pulse * Math.PI)));
                Color dot = ok ? new Color(0x4CAF7A) : Theme.DANGER;
                g.setColor(new Color(dot.getRed(), dot.getGreen(), dot.getBlue(), Math.round(255 * a)));
                g.fillOval(22, 22, 9, 9);
                g.setFont(Theme.CAPS);
                g.setColor(Theme.SIDEBAR_TEXT);
                g.drawString(ok ? "ĐÃ KẾT NỐI" : "MẤT KẾT NỐI · ĐANG NỐI LẠI", 38, 31);
                g.setFont(Theme.SMALL);
                g.setColor(Theme.SIDEBAR_DIM);
                g.drawString(sourceName.get(), 22, 52);
                g.drawString("OBD-II qua CAN 11 bit (vcan0)", 22, 68);
                g.dispose();
            }
        }
    }
}
