package controller;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import dao.MaintenanceDao;
import model.MaintenanceInterval;
import model.MaintenanceRecord;
import model.Vehicle;

/**
 * Controller cho UC11 (Quản lý lịch sử bảo dưỡng của xe đang chọn) và chu kỳ bảo dưỡng dùng để nhắc hạn.
 * Khi đổi xe tự tải lại danh sách; mọi thao tác CSDL chạy trên luồng nền.
 */
public class MaintenanceController {

    // Mã lỗi SQL Server: 547 vi phạm khóa ngoại hoặc CHECK; 2627/2601 trùng khóa duy nhất
    private static final int ERR_CONSTRAINT = 547;
    private static final int ERR_DUPLICATE_KEY = 2627;
    private static final int ERR_DUPLICATE_INDEX = 2601;

    private final MaintenanceDao dao;
    private final VehicleController vehicles;
    private final List<BiConsumer<Vehicle, List<MaintenanceRecord>>> listListeners = new ArrayList<>();
    private final List<Consumer<List<MaintenanceInterval>>> intervalListeners = new ArrayList<>();
    private final List<Consumer<String>> errorListeners = new ArrayList<>();
    private List<MaintenanceInterval> intervals = List.of();

    public MaintenanceController(MaintenanceDao dao, VehicleController vehicles) {
        this.dao = dao;
        this.vehicles = vehicles;
        vehicles.addSelectionListener(v -> reload(this::fireError));
        reloadIntervals(this::fireError);
    }

    /** Listener nhận (xe đang chọn hoặc null, danh sách bảo dưỡng của xe đó). */
    public void addListListener(BiConsumer<Vehicle, List<MaintenanceRecord>> l) {
        listListeners.add(l);
    }

    /** Listener nhận danh sách chu kỳ bảo dưỡng mỗi khi tải lại. */
    public void addIntervalListener(Consumer<List<MaintenanceInterval>> l) {
        intervalListeners.add(l);
        l.accept(intervals);
    }

    /** Lỗi phát sinh khi tự tải lại (ví dụ sau khi đổi xe). */
    public void addErrorListener(Consumer<String> l) {
        errorListeners.add(l);
    }

    /** Xe đang chọn, hoặc null. */
    public Vehicle vehicle() {
        return vehicles.selected();
    }

    public List<MaintenanceInterval> intervals() {
        return intervals;
    }

    public void reload(Consumer<String> onError) {
        Vehicle v = vehicles.selected();
        if (v == null) {
            publish(null, List.of());
            return;
        }
        run(v, () -> dao.findByVehicle(v.id()), onError);
    }

    /** Thêm bản ghi cho xe đang chọn (vehicleId trong r được thay bằng xe đang chọn). */
    public void add(MaintenanceRecord r, Consumer<String> onError) {
        Vehicle v = vehicles.selected();
        if (v == null) {
            onError.accept("Hãy chọn xe ở tab Quản lý xe trước.");
            return;
        }
        run(v, () -> {
            dao.insert(new MaintenanceRecord(0, v.id(), r.date(), r.km(), r.item(), r.cost(), r.note()));
            return dao.findByVehicle(v.id());
        }, onError);
        raiseOdometer(v, r);
    }

    public void update(MaintenanceRecord r, Consumer<String> onError) {
        Vehicle v = vehicles.selected();
        if (v == null) {
            onError.accept("Hãy chọn xe ở tab Quản lý xe trước.");
            return;
        }
        run(v, () -> {
            dao.update(r);
            return dao.findByVehicle(v.id());
        }, onError);
        raiseOdometer(v, r);
    }

    public void delete(MaintenanceRecord r, Consumer<String> onError) {
        Vehicle v = vehicles.selected();
        if (v == null) {
            onError.accept("Hãy chọn xe ở tab Quản lý xe trước.");
            return;
        }
        run(v, () -> {
            dao.delete(r.id());
            return dao.findByVehicle(v.id());
        }, onError);
    }

    /** Lưu (thêm hoặc sửa) một chu kỳ rồi tải lại danh sách chu kỳ. */
    public void saveInterval(MaintenanceInterval iv, Consumer<String> onError) {
        Async.run(() -> {
            dao.saveInterval(iv);
            return dao.intervals();
        }, this::publishIntervals, t -> onError.accept(describe(t)));
    }

    public void deleteInterval(MaintenanceInterval iv, Consumer<String> onError) {
        Async.run(() -> {
            dao.deleteInterval(iv.id());
            return dao.intervals();
        }, this::publishIntervals, t -> onError.accept(describe(t)));
    }

    public void reloadIntervals(Consumer<String> onError) {
        Async.run(dao::intervals, this::publishIntervals, t -> onError.accept(describe(t)));
    }

    /** Lần bảo dưỡng có số km lớn hơn số km đang lưu của xe thì cập nhật số km hiện tại của xe. */
    private void raiseOdometer(Vehicle v, MaintenanceRecord r) {
        if (r.km() != null && (v.odometerKm() == null || r.km() > v.odometerKm())) {
            vehicles.raiseOdometer(v.id(), r.km());
        }
    }

    /** Chạy job nền; chỉ hiển thị kết quả nếu người dùng vẫn đang chọn đúng xe đó (tránh lẫn khi đổi xe nhanh). */
    private void run(Vehicle v, Callable<List<MaintenanceRecord>> job, Consumer<String> onError) {
        Async.run(job, list -> {
            Vehicle now = vehicles.selected();
            if (now != null && now.id() == v.id()) {
                publish(now, list);
            }
        }, t -> onError.accept(describe(t)));
    }

    private void publish(Vehicle v, List<MaintenanceRecord> list) {
        listListeners.forEach(l -> l.accept(v, list));
    }

    private void publishIntervals(List<MaintenanceInterval> list) {
        intervals = list;
        intervalListeners.forEach(l -> l.accept(list));
    }

    private void fireError(String msg) {
        errorListeners.forEach(l -> l.accept(msg));
    }

    private static String describe(Throwable t) {
        if (t instanceof SQLException s) {
            if (s.getErrorCode() == ERR_CONSTRAINT) {
                return "CSDL từ chối dữ liệu (số km, chi phí, chu kỳ không được âm; hạng mục không được trống).";
            }
            if (s.getErrorCode() == ERR_DUPLICATE_KEY || s.getErrorCode() == ERR_DUPLICATE_INDEX) {
                return "Hạng mục này đã có chu kỳ.";
            }
        }
        return "Không thao tác được CSDL: " + Async.message(t);
    }
}
