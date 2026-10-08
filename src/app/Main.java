package app;

import java.awt.Insets;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatLightLaf;

import controller.CatalogController;
import controller.DiagnosticController;
import controller.MaintenanceController;
import controller.VehicleController;
import dao.Database;
import dao.DtcDao;
import dao.MaintenanceDao;
import dao.RuleDao;
import dao.SessionDao;
import dao.VehicleDao;
import service.DataSource;
import service.GatewayDataSource;
import service.MockDataSource;
import view.MainFrame;
import view.Theme;

/** Điểm khởi động ứng dụng. */
public class Main {

    public static void main(String[] args) {
        setupLookAndFeel();
        SwingUtilities.invokeLater(Main::start);
    }

    /** Giao diện FlatLaf sáng, chỉnh theo phong cách "máy chẩn đoán xưởng" (dùng chung cho công cụ chụp ảnh). */
    public static void setupLookAndFeel() {
        FlatLightLaf.setup();
        // Phong cách "máy chẩn đoán xưởng": góc vuông, viền mảnh, màu nhấn cam tín hiệu
        UIManager.put("defaultFont", Theme.FONT);
        UIManager.put("Component.arc", 0);
        UIManager.put("Button.arc", 2);
        UIManager.put("TextComponent.arc", 0);
        UIManager.put("CheckBox.arc", 0);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.focusColor", Theme.ACCENT);
        UIManager.put("Component.focusedBorderColor", Theme.ACCENT);
        UIManager.put("Component.borderColor", Theme.BORDER);
        UIManager.put("Button.borderColor", Theme.BORDER);
        UIManager.put("Panel.background", Theme.BG);
        UIManager.put("TextField.margin", new Insets(5, 8, 5, 8));
        UIManager.put("Table.background", Theme.CARD);
        UIManager.put("List.selectionBackground", Theme.SELECT);
        UIManager.put("List.selectionForeground", Theme.TEXT);
        UIManager.put("ComboBox.selectionBackground", Theme.SELECT);
        UIManager.put("ComboBox.selectionForeground", Theme.TEXT);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 0);
        UIManager.put("ScrollBar.thumb", Theme.BORDER);
        UIManager.put("ScrollBar.track", Theme.CARD_ALT);
        UIManager.put("ToggleButton.selectedBackground", Theme.SIDEBAR);
        UIManager.put("ToggleButton.selectedForeground", java.awt.Color.WHITE);
    }

    /** Dựng các lớp và mở cửa sổ chính (gọi trên luồng giao diện); trả về cửa sổ để công cụ chụp ảnh điều khiển. */
    public static MainFrame start() {
        // Nguồn dữ liệu chọn trong config.properties: source=gateway (thật) hoặc mock (giả, mặc định)
        AppConfig config = new AppConfig();
        DataSource source = "gateway".equalsIgnoreCase(config.get("source", "mock"))
                ? new GatewayDataSource(config.get("gateway.host", "192.168.35.128"),
                        config.getInt("gateway.port", 5000))
                : new MockDataSource();
        Database db = new Database();
        VehicleDao vehicleDao = new VehicleDao(db);
        VehicleController vehicles = new VehicleController(vehicleDao);
        DtcDao dtcDao = new DtcDao(db);
        DiagnosticController controller = new DiagnosticController(source, vehicles,
                new SessionDao(db), dtcDao, new RuleDao(db));
        // Đổi xe thì tải lại lịch sử của xe đó
        vehicles.addSelectionListener(controller::loadHistory);
        CatalogController catalog = new CatalogController(dtcDao);
        MaintenanceController maintenance = new MaintenanceController(new MaintenanceDao(db), vehicles);
        MainFrame frame = new MainFrame(controller, catalog, vehicles, maintenance);
        frame.setVisible(true);
        return frame;
    }
}
