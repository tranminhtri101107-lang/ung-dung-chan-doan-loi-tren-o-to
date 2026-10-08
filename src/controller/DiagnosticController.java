package controller;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.Timer;

import dao.DtcDao;
import dao.RuleDao;
import dao.SessionDao;
import model.Diagnosis;
import model.Dtc;
import model.HistoryEntry;
import model.LiveData;
import model.RuleBase;
import model.Vehicle;
import service.DataSource;
import service.InferenceEngine;
import service.ScenarioCatalog;

/**
 * Controller: nhận thao tác từ View, gọi DataSource, rồi báo kết quả lại cho
 * View qua listener. View không biết gì về DataSource hay CSDL.
 * Đọc/xóa DTC chỉ chạy khi đã chọn xe; mỗi thao tác được lưu vào phiên chẩn đoán của xe đó.
 * Mọi callback chạy trên luồng giao diện nên View cập nhật an toàn.
 */
public class DiagnosticController {

    private static final int POLL_MS = 500;
    private static final String NEED_VEHICLE = "Hãy chọn xe ở tab Quản lý xe trước khi chẩn đoán.";
    private static final String NOT_IN_CATALOG = "Mã chưa có trong danh mục";

    private final DataSource source;
    private final VehicleController vehicles;
    private final SessionDao sessions;
    private final DtcDao dtcDao;
    private final RuleDao ruleDao;
    private final Timer pollTimer;
    private final List<Consumer<List<Diagnosis>>> analysisListeners = new ArrayList<>();
    private final List<Consumer<String>> infoListeners = new ArrayList<>();
    private final List<Consumer<LiveData>> liveListeners = new ArrayList<>();
    private final List<Consumer<List<Dtc>>> dtcListeners = new ArrayList<>();
    private final List<Consumer<HistoryEntry>> historyListeners = new ArrayList<>();
    private final List<Consumer<List<HistoryEntry>>> historyResetListeners = new ArrayList<>();
    private final List<Consumer<String>> errorListeners = new ArrayList<>();

    // Phiên chẩn đoán hiện tại (chỉ đổi trên luồng nền, trong ensureSession)
    private int sessionId;
    private int sessionVehicleId = -1;

    public DiagnosticController(DataSource source, VehicleController vehicles, SessionDao sessions,
            DtcDao dtcDao, RuleDao ruleDao) {
        this.source = source;
        this.vehicles = vehicles;
        this.sessions = sessions;
        this.dtcDao = dtcDao;
        this.ruleDao = ruleDao;
        this.pollTimer = new Timer(POLL_MS, e -> {
            LiveData data = source.readLiveData();
            liveListeners.forEach(l -> l.accept(data));
        });
    }

    public void addLiveListener(Consumer<LiveData> l) {
        liveListeners.add(l);
    }

    public void addDtcListener(Consumer<List<Dtc>> l) {
        dtcListeners.add(l);
    }

    /** Nhận từng dòng lịch sử mới phát sinh. */
    public void addHistoryListener(Consumer<HistoryEntry> l) {
        historyListeners.add(l);
    }

    /** Nhận cả danh sách lịch sử khi đổi xe. */
    public void addHistoryResetListener(Consumer<List<HistoryEntry>> l) {
        historyResetListeners.add(l);
    }

    public void addErrorListener(Consumer<String> l) {
        errorListeners.add(l);
    }

    /** Nhận kết quả phân tích nguyên nhân (UC07): danh sách đã xếp hạng, rỗng nếu chưa đủ cơ sở kết luận. */
    public void addAnalysisListener(Consumer<List<Diagnosis>> l) {
        analysisListeners.add(l);
    }

    /** Thông báo ngắn (không phải lỗi), ví dụ đã chọn kịch bản mô phỏng. */
    public void addInfoListener(Consumer<String> l) {
        infoListeners.add(l);
    }

    public String sourceName() {
        return source.name();
    }

    public boolean sourceConnected() {
        return source.isConnected();
    }

    /** Giá trị vật lý mới nhất của 8 PID (đọc từ bộ đệm của nguồn dữ liệu, không gửi yêu cầu mới). */
    public java.util.Map<Integer, Double> latestPids() {
        return source.readPids();
    }

    public void startLive() {
        pollTimer.start();
    }

    public void stopLive() {
        pollTimer.stop();
    }

    public void readDtcs() {
        Vehicle v = vehicles.selected();
        if (v == null) {
            fireError(NEED_VEHICLE);
            return;
        }
        // Hỏi xe có thể mất vài giây nên chạy nền; ECU chỉ trả về mã, mô tả và mức độ tra trong CSDL
        Async.runSerial(() -> {
            List<Dtc> raw = source.readDtcs();  // lỗi mạng/ECU: ném IOException, báo ở nhánh lỗi
            List<Dtc> shown = raw;
            HistoryEntry entry = null;
            String dbError = null;
            try {
                Map<String, Dtc> catalog = dtcDao.findByCodes(raw.stream().map(Dtc::code).toList());
                shown = raw.stream()
                        .map(d -> catalog.getOrDefault(d.code(), new Dtc(d.code(), NOT_IN_CATALOG, null)))
                        .toList();
                // Chỉ mã có trong danh mục mới ghi được vào phiên (ràng buộc khóa ngoại)
                List<String> known = raw.stream().map(Dtc::code).filter(catalog::containsKey).toList();
                String detail = "Tìm thấy " + raw.size() + " mã lỗi" + (known.size() < raw.size()
                        ? " (" + (raw.size() - known.size()) + " mã chưa có trong danh mục)" : "");
                sessions.record(ensureSession(v.id()), "DOC_DTC", detail, known);
                entry = new HistoryEntry(LocalDateTime.now(), SessionDao.actionLabel("DOC_DTC"), detail);
            } catch (SQLException e) {
                // CSDL lỗi: vẫn cho kỹ thuật viên thấy các mã đọc được từ xe
                dbError = "Không tra được danh mục hoặc lưu lịch sử: " + Async.message(e);
            }
            return new Outcome(shown, entry, dbError);
        }, outcome -> {
            dtcListeners.forEach(l -> l.accept(outcome.dtcs()));
            showOutcome(outcome);
        }, t -> fireError("Không đọc được DTC: " + Async.message(t)));
    }

    /**
     * UC07: phân tích nguyên nhân. Đọc mã lỗi và ảnh chụp giá trị PID hiện tại, chạy bộ suy luận với luật trong
     * CSDL, rồi lưu kết quả vào một phiên mới. Lỗi mạng/ECU hoặc không nạp được luật thì báo lỗi, không có kết quả.
     */
    public void analyze() {
        Vehicle v = vehicles.selected();
        if (v == null) {
            fireError(NEED_VEHICLE);
            return;
        }
        Async.runSerial(() -> {
            List<Dtc> raw = source.readDtcs();
            Map<Integer, Double> pids = source.readPids();
            RuleBase base = ruleDao.load();  // lỗi CSDL: ném SQLException, báo ở nhánh lỗi
            List<Diagnosis> result = new InferenceEngine(base)
                    .diagnose(raw.stream().map(Dtc::code).collect(java.util.stream.Collectors.toSet()), pids);
            String detail = result.isEmpty() ? "Chưa đủ cơ sở để kết luận"
                    : String.format("%d nguyên nhân khả dĩ; hạng 1: %s (%.2f)", result.size(),
                            result.get(0).cause().name(), result.get(0).confidence());
            List<Dtc> shown = raw;
            HistoryEntry entry = null;
            String dbError = null;
            try {
                Map<String, Dtc> catalog = dtcDao.findByCodes(raw.stream().map(Dtc::code).toList());
                shown = raw.stream()
                        .map(d -> catalog.getOrDefault(d.code(), new Dtc(d.code(), NOT_IN_CATALOG, null)))
                        .toList();
                List<String> known = raw.stream().map(Dtc::code).filter(catalog::containsKey).toList();
                sessions.saveAnalysis(newSession(v.id()), known, pids, result, detail);
                entry = new HistoryEntry(LocalDateTime.now(), SessionDao.actionLabel("PHAN_TICH"), detail);
            } catch (SQLException e) {
                dbError = "Không lưu được kết quả phân tích: " + Async.message(e);
            }
            return new AnalysisOutcome(new Outcome(shown, entry, dbError), result);
        }, outcome -> {
            dtcListeners.forEach(l -> l.accept(outcome.base().dtcs()));
            analysisListeners.forEach(l -> l.accept(outcome.diagnoses()));
            showOutcome(outcome.base());
        }, t -> fireError("Không phân tích được: " + Async.message(t)));
    }

    /** Chọn kịch bản lỗi trong ECU mô phỏng (tiêm lỗi). Chỉ nguồn Gateway hỗ trợ. */
    public void injectScenario(int id) {
        Async.runSerial(() -> {
            source.inject(id);
            return id;
        }, k -> infoListeners.forEach(l -> l.accept("Đã chọn kịch bản " + ScenarioCatalog.ALL.get(k).label()
                + ". Đợi vài giây cho dữ liệu ổn định rồi đọc DTC hoặc phân tích.")),
                t -> fireError("Không chọn được kịch bản: " + Async.message(t)));
    }

    private record AnalysisOutcome(Outcome base, List<Diagnosis> diagnoses) {
    }

    /** Kết quả một thao tác: DTC cần hiển thị, dòng lịch sử (nếu ghi được CSDL) hoặc lỗi CSDL. */
    private record Outcome(List<Dtc> dtcs, HistoryEntry entry, String dbError) {
    }

    private void showOutcome(Outcome outcome) {
        if (outcome.dbError() != null) {
            fireError(outcome.dbError());
        } else {
            historyListeners.forEach(l -> l.accept(outcome.entry()));
        }
    }

    public void clearDtcs() {
        Vehicle v = vehicles.selected();
        if (v == null) {
            fireError(NEED_VEHICLE);
            return;
        }
        Async.runSerial(() -> {
            source.clearDtcs();  // lỗi mạng/ECU: ném IOException, báo ở nhánh lỗi
            HistoryEntry entry = null;
            String dbError = null;
            String detail = "Đã gửi yêu cầu xóa mã lỗi";
            try {
                sessions.record(ensureSession(v.id()), "XOA_DTC", detail, List.of());
                entry = new HistoryEntry(LocalDateTime.now(), SessionDao.actionLabel("XOA_DTC"), detail);
            } catch (SQLException e) {
                dbError = "Không lưu được vào CSDL: " + Async.message(e);
            }
            return new Outcome(List.of(), entry, dbError);
        }, outcome -> {
            dtcListeners.forEach(l -> l.accept(outcome.dtcs()));
            showOutcome(outcome);
        }, t -> fireError("Không xóa được DTC: " + Async.message(t)));
    }

    /** Tải lịch sử của xe vừa chọn (UC08); null nghĩa là chưa chọn xe. */
    public void loadHistory(Vehicle v) {
        if (v == null) {
            historyResetListeners.forEach(l -> l.accept(List.of()));
            return;
        }
        Async.run(() -> sessions.historyOfVehicle(v.id()),
                list -> historyResetListeners.forEach(l -> l.accept(list)),
                t -> fireError("Không đọc được lịch sử: " + Async.message(t)));
    }

    /** Xe đang chọn, hoặc null. */
    public Vehicle currentVehicle() {
        return vehicles.selected();
    }

    /** Danh sách phiên chẩn đoán của xe đang chọn (tab Lịch sử), đọc trên luồng nền. */
    public void loadSessions(Consumer<List<model.SessionInfo>> onDone, Consumer<String> onError) {
        Vehicle v = vehicles.selected();
        if (v == null) {
            onDone.accept(List.of());
            return;
        }
        Async.run(() -> sessions.sessionsOfVehicle(v.id()), list -> {
            Vehicle now = vehicles.selected();
            if (now != null && now.id() == v.id()) {
                onDone.accept(list);
            }
        }, t -> onError.accept("Không đọc được lịch sử: " + Async.message(t)));
    }

    /** Chi tiết một phiên (mã lỗi kèm mô tả, ảnh chụp PID, kết quả phân tích, nhật ký). */
    public void loadDetail(model.SessionInfo info, Consumer<model.SessionDetail> onDone, Consumer<String> onError) {
        Async.run(() -> sessions.detail(info, dtcDao), onDone,
                t -> onError.accept("Không đọc được chi tiết phiên: " + Async.message(t)));
    }

    /** Mở phiên mới cho xe và coi đó là phiên hiện tại (mỗi lần phân tích cần phiên riêng). */
    private synchronized int newSession(int vehicleId) throws SQLException {
        sessionId = sessions.createSession(vehicleId);
        sessionVehicleId = vehicleId;
        return sessionId;
    }

    /** Dùng lại phiên của xe đang chẩn đoán, hoặc mở phiên mới khi đổi xe. */
    private synchronized int ensureSession(int vehicleId) throws SQLException {
        if (sessionVehicleId != vehicleId) {
            sessionId = sessions.createSession(vehicleId);
            sessionVehicleId = vehicleId;
        }
        return sessionId;
    }

    private void fireError(String msg) {
        errorListeners.forEach(l -> l.accept(msg));
    }
}
