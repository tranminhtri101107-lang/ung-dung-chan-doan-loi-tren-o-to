package service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import model.Dtc;
import model.LiveData;

/**
 * Nguồn dữ liệu thật: nối TCP tới Gateway C++ trong máy ảo (giao thức ở docs/protocol.md).
 * Một luồng nền giữ kết nối (tự nối lại khi mất), đọc các dòng LIVE để cập nhật giá trị mới nhất
 * và chuyển các dòng RSP/ERR cho yêu cầu đang chờ.
 */
public class GatewayDataSource implements DataSource {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int RECONNECT_DELAY_MS = 2000;
    private static final int REPLY_TIMEOUT_MS = 4000;  // Gateway tự báo lỗi sau 2 giây nên chờ lâu hơn một chút

    private final String host;
    private final int port;
    private final Map<Integer, int[]> latest = new ConcurrentHashMap<>();  // PID -> byte thô mới nhất
    private final BlockingQueue<String> replies = new LinkedBlockingQueue<>();
    private volatile boolean connected;
    private volatile Writer out;

    public GatewayDataSource(String host, int port) {
        this.host = host;
        this.port = port;
        Thread worker = new Thread(this::connectionLoop, "gateway-client");
        worker.setDaemon(true);  // không chặn việc thoát ứng dụng
        worker.start();
    }

    /** Giữ kết nối: nối, đọc cho tới khi mất, đợi một chút rồi nối lại. */
    private void connectionLoop() {
        while (true) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                socket.setTcpNoDelay(true);
                out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
                connected = true;
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = in.readLine()) != null) {
                    handleLine(line);
                }
            } catch (IOException e) {
                // chưa nối được hoặc mất kết nối: sẽ thử lại bên dưới
            } finally {
                connected = false;
                out = null;
                latest.clear();  // dữ liệu cũ không còn đáng tin
            }
            try {
                Thread.sleep(RECONNECT_DELAY_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void handleLine(String line) {
        if (line.startsWith("LIVE ")) {
            String[] p = line.split(" ");
            if (p.length < 3) {
                return;
            }
            try {
                int pid = Integer.parseInt(p[1], 16);
                int[] bytes = new int[p[2].length() / 2];
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = Integer.parseInt(p[2].substring(2 * i, 2 * i + 2), 16);
                }
                latest.put(pid, bytes);
            } catch (NumberFormatException e) {
                // dòng hỏng, bỏ qua
            }
        } else if (line.startsWith("RSP ") || line.startsWith("ERR ")) {
            replies.offer(line);
        }
    }

    @Override
    public LiveData readLiveData() {
        return new LiveData(
                (int) Math.round(value(PidDecoder.PID_RPM)),
                (int) Math.round(value(PidDecoder.PID_SPEED)),
                (int) Math.round(value(PidDecoder.PID_COOLANT)),
                (int) Math.round(value(PidDecoder.PID_THROTTLE)));
    }

    /** Giá trị mới nhất của PID; 0 nếu chưa có dữ liệu. */
    private double value(int pid) {
        double v = PidDecoder.decode(pid, latest.get(pid));
        return Double.isNaN(v) ? 0 : v;
    }

    @Override
    public Map<Integer, Double> readPids() {
        Map<Integer, Double> result = new java.util.HashMap<>();
        for (int pid : new int[] { PidDecoder.PID_LOAD, PidDecoder.PID_COOLANT, PidDecoder.PID_RPM, PidDecoder.PID_SPEED,
                PidDecoder.PID_INTAKE_TEMP, PidDecoder.PID_MAF, PidDecoder.PID_THROTTLE, PidDecoder.PID_VOLTAGE }) {
            double v = PidDecoder.decode(pid, latest.get(pid));
            if (!Double.isNaN(v)) {
                result.put(pid, v);
            }
        }
        return result;
    }

    @Override
    public synchronized void inject(int scenario) throws IOException {
        String reply = request("INJECT " + scenario);
        if (!reply.equals("RSP INJECT OK " + scenario)) {
            throw new IOException(reply.startsWith("ERR BAD_SCENARIO") ? "ECU không có kịch bản " + scenario
                    : describeError(reply));
        }
    }

    @Override
    public synchronized List<Dtc> readDtcs() throws IOException {
        String reply = request("REQ 03");
        if (!reply.startsWith("RSP 03")) {
            throw new IOException(describeError(reply));
        }
        String[] parts = reply.split(" ");
        List<Dtc> list = new ArrayList<>();
        for (int i = 2; i < parts.length; i++) {
            list.add(new Dtc(parts[i], null, null));  // chỉ có mã; mô tả tra ở CSDL
        }
        return list;
    }

    @Override
    public synchronized void clearDtcs() throws IOException {
        String reply = request("REQ 04");
        if (!reply.equals("RSP 04 OK")) {
            throw new IOException(describeError(reply));
        }
    }

    /** Gửi một lệnh và chờ dòng phản hồi RSP/ERR. */
    private String request(String command) throws IOException {
        Writer w = out;
        if (!connected || w == null) {
            throw new IOException("Chưa kết nối được Gateway (" + host + ":" + port + ")");
        }
        replies.clear();  // bỏ phản hồi muộn của yêu cầu trước
        w.write(command + "\n");
        w.flush();
        try {
            String reply = replies.poll(REPLY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (reply == null) {
                throw new IOException("Gateway không trả lời");
            }
            return reply;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Bị ngắt khi chờ Gateway");
        }
    }

    private static String describeError(String reply) {
        if (reply.startsWith("ERR TIMEOUT")) {
            return "ECU không trả lời (quá thời gian)";
        }
        return "Gateway báo lỗi: " + reply;
    }

    @Override
    public String name() {
        return "Gateway " + host + ":" + port;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }
}
