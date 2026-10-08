package controller;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import dao.VehicleDao;
import model.Vehicle;
import model.VehicleSummary;

/**
 * Controller cho UC01 (Quản lý hồ sơ xe) và UC02 (Chọn xe cần chẩn đoán).
 * Giữ xe đang được chọn; các màn hình khác đăng ký listener để biết khi xe đổi.
 */
public class VehicleController {

    // Mã lỗi SQL Server: 2627/2601 = trùng khóa duy nhất, 547 = vi phạm khóa ngoại
    private static final int ERR_DUPLICATE_KEY = 2627;
    private static final int ERR_DUPLICATE_INDEX = 2601;
    private static final int ERR_FOREIGN_KEY = 547;

    private final VehicleDao dao;
    private final List<Consumer<List<Vehicle>>> listListeners = new ArrayList<>();
    private final List<Consumer<Vehicle>> selectionListeners = new ArrayList<>();
    private Vehicle selected;

    public VehicleController(VehicleDao dao) {
        this.dao = dao;
    }

    public void addListListener(Consumer<List<Vehicle>> l) {
        listListeners.add(l);
    }

    /** Listener nhận null khi không còn xe nào được chọn. */
    public void addSelectionListener(Consumer<Vehicle> l) {
        selectionListeners.add(l);
    }

    /** Xe đang chọn, hoặc null nếu chưa chọn. */
    public Vehicle selected() {
        return selected;
    }

    public void select(Vehicle v) {
        selected = v;
        selectionListeners.forEach(l -> l.accept(v));
    }

    public void reload(Consumer<String> onError) {
        run(dao::findAll, onError);
    }

    public void add(Vehicle v, Consumer<String> onError) {
        run(() -> {
            dao.insert(v);
            return dao.findAll();
        }, onError);
    }

    public void update(Vehicle v, Consumer<String> onError) {
        run(() -> {
            dao.update(v);
            return dao.findAll();
        }, onError);
    }

    public void delete(Vehicle v, Consumer<String> onError) {
        run(() -> {
            dao.delete(v.id());
            return dao.findAll();
        }, onError);
    }

    /** Đọc tóm tắt hồ sơ xe (số phiên, mã lỗi gần nhất, bảo dưỡng) trên luồng nền. */
    public void summary(Vehicle v, Consumer<VehicleSummary> onDone, Consumer<String> onError) {
        Async.run(() -> dao.summary(v.id()), onDone, t -> onError.accept(describe(t)));
    }

    /** Ghi số km mới cho xe nếu lớn hơn số đang lưu, rồi tải lại danh sách (thanh đầu hiện số km mới). */
    public void raiseOdometer(int vehicleId, int km) {
        run(() -> {
            dao.raiseOdometer(vehicleId, km);
            return dao.findAll();
        }, msg -> { });
    }

    /** Tải lại danh sách xe (ví dụ sau khi bảo dưỡng làm tăng số km). */
    public void refresh() {
        run(dao::findAll, msg -> { });
    }

    private void run(Callable<List<Vehicle>> job, Consumer<String> onError) {
        Async.run(job, this::publish, t -> onError.accept(describe(t)));
    }

    /** Gửi danh sách mới cho giao diện, đồng thời cập nhật hoặc bỏ chọn xe hiện tại. */
    private void publish(List<Vehicle> list) {
        listListeners.forEach(l -> l.accept(list));
        if (selected != null) {
            Vehicle fresh = list.stream().filter(x -> x.id() == selected.id()).findFirst().orElse(null);
            if (fresh == null || !fresh.equals(selected)) {
                select(fresh);
            }
        }
    }

    private static String describe(Throwable t) {
        if (t instanceof SQLException s) {
            switch (s.getErrorCode()) {
                case ERR_DUPLICATE_KEY, ERR_DUPLICATE_INDEX:
                    return s.getMessage() != null && s.getMessage().contains("UX_Xe_VIN")
                            ? "Số VIN này đã gắn với một xe khác."
                            : "Biển số này đã có trong danh sách.";
                case ERR_FOREIGN_KEY:
                    return "Xe đã có phiên chẩn đoán hoặc hồ sơ bảo dưỡng nên không thể xóa.";
                default:
                    break;
            }
        }
        return "Không thao tác được CSDL: " + Async.message(t);
    }
}
