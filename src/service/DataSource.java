package service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import model.Dtc;
import model.LiveData;

/**
 * Nguồn dữ liệu chẩn đoán. Có hai cài đặt:
 * MockDataSource (dữ liệu giả, để dựng giao diện) và GatewayDataSource (đọc từ Gateway C++ qua TCP).
 * Giao diện và controller không phải sửa khi đổi nguồn.
 */
public interface DataSource {

    /** Dữ liệu sống mới nhất (Mode 01). Không chặn: trả ngay giá trị đang có. */
    LiveData readLiveData();

    /**
     * Đọc danh sách DTC đang lưu (Mode 03). Chỉ cần mã lỗi; mô tả do ứng dụng tra trong CSDL.
     * Có thể chờ vài giây nên phải gọi trên luồng nền.
     */
    List<Dtc> readDtcs() throws IOException;

    /** Xóa DTC (Mode 04). Có thể chờ vài giây nên phải gọi trên luồng nền. */
    void clearDtcs() throws IOException;

    /** Giá trị vật lý mới nhất của các PID (khóa là mã PID, ví dụ 0x0C); PID chưa có dữ liệu thì vắng mặt. */
    Map<Integer, Double> readPids();

    /**
     * Tiêm lỗi: chọn kịch bản k trong ECU mô phỏng (0 = xe khỏe). Chỉ nguồn Gateway hỗ trợ.
     * Có thể chờ vài giây nên phải gọi trên luồng nền.
     */
    default void inject(int scenario) throws IOException {
        throw new IOException("Nguồn dữ liệu này không hỗ trợ tiêm lỗi");
    }

    /** Tên nguồn để hiển thị trên giao diện. */
    String name();

    /** Nguồn có đang sẵn sàng không (Gateway có thể mất kết nối). */
    default boolean isConnected() {
        return true;
    }
}
