package service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import model.Dtc;
import model.LiveData;

/**
 * Dữ liệu giả để dựng và thử giao diện.
 * Giống ECU thật ở Mode 03: chỉ trả về MÃ lỗi; mô tả và mức độ do ứng dụng tra trong
 * danh mục DTC ở CSDL (DiagnosticController làm việc đó).
 */
public class MockDataSource implements DataSource {

    private final Random random = new Random();
    private double rpm = 800;
    private double speed = 0;
    private double coolant = 85;
    private double throttle = 8;
    private final List<Dtc> stored = new ArrayList<>();

    public MockDataSource() {
        resetDtcs();
    }

    private void resetDtcs() {
        stored.clear();
        for (String code : new String[] { "P0300", "P0171", "P0420", "P0117" }) {
            stored.add(new Dtc(code, null, null));
        }
    }

    @Override
    public LiveData readLiveData() {
        // Bước ngẫu nhiên nhỏ quanh giá trị hiện tại để đường biểu đồ trông mượt
        throttle = clamp(throttle + random.nextGaussian() * 4, 5, 90);
        double targetRpm = 800 + throttle * 55;
        rpm = clamp(rpm + (targetRpm - rpm) * 0.25 + random.nextGaussian() * 40, 700, 6500);
        double targetSpeed = throttle * 1.6;
        speed = clamp(speed + (targetSpeed - speed) * 0.08, 0, 220);
        coolant = clamp(coolant + random.nextGaussian() * 0.4 + (throttle - 40) * 0.003, 70, 118);
        return new LiveData((int) rpm, (int) speed, (int) coolant, (int) throttle);
    }

    @Override
    public Map<Integer, Double> readPids() {
        // Giá trị gần đúng theo trạng thái giả đang có (đủ để thử giao diện)
        Map<Integer, Double> m = new HashMap<>();
        m.put(PidDecoder.PID_LOAD, 15 + throttle * 0.8);
        m.put(PidDecoder.PID_COOLANT, coolant);
        m.put(PidDecoder.PID_RPM, rpm);
        m.put(PidDecoder.PID_SPEED, speed);
        m.put(PidDecoder.PID_INTAKE_TEMP, 30.0);
        m.put(PidDecoder.PID_MAF, 2 + rpm / 1000.0 * throttle / 10.0);
        m.put(PidDecoder.PID_THROTTLE, throttle);
        m.put(PidDecoder.PID_VOLTAGE, 13.8);
        return m;
    }

    @Override
    public List<Dtc> readDtcs() {
        return new ArrayList<>(stored);
    }

    @Override
    public void clearDtcs() {
        stored.clear();
    }

    @Override
    public String name() {
        return "Mô phỏng";
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
