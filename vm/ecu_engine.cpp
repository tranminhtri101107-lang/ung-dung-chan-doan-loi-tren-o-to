// ecu_engine.cpp - ECU giả lập (động cơ) trả lời yêu cầu chẩn đoán OBD-II trên SocketCAN.
//
// Biên dịch: g++ -std=c++17 -Wall -o ecu_engine ecu_engine.cpp
// Chạy:      ./ecu_engine [giao_dien=vcan0] [DTC ...]
//   ví dụ:   ./ecu_engine vcan0 P0300 P0171      (không ghi DTC thì dùng 2 mã mặc định; tối đa 2 mã)
//
// Hỗ trợ (chi tiết ở docs/giao-thuc/protocol.md):
//   Mode 01 - đọc dữ liệu sống, 8 PID: 04 05 0C 0D 0F 10 11 42
//   Mode 03 - đọc DTC, tối đa 2 DTC mỗi lần (phản hồi nằm gọn trong một khung đơn, không dùng ISO-TP)
//   Mode 04 - xóa DTC
//   Tiêm lỗi: khung điều khiển 0x6F0 [02 01 k] chọn kịch bản k (0 = xe khỏe, 1..8 = các kịch bản lỗi), ECU xác nhận ở 0x6F8
// Công thức mã hóa PID lấy từ docs/du-lieu/BANG-DTC-PID.xlsx (đang ở trạng thái "cần xác minh" với SAE J1979).

#include <algorithm>
#include <array>
#include <cctype>
#include <chrono>
#include <cmath>
#include <csignal>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <random>
#include <string>
#include <vector>

#include <linux/can.h>
#include <linux/can/raw.h>
#include <net/if.h>
#include <sys/ioctl.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <unistd.h>

namespace {

constexpr canid_t ID_REQ_FUNCTIONAL = 0x7DF;  // tester gửi yêu cầu quảng bá tới mọi ECU
constexpr canid_t ID_REQ_PHYSICAL = 0x7E0;    // tester gửi riêng cho ECU này
constexpr canid_t ID_RESP = 0x7E8;            // ECU số 1 trả lời
constexpr canid_t ID_CONTROL = 0x6F0;         // khung điều khiển tiêm lỗi (ngoài OBD-II): [02 01 k] = chọn kịch bản k
constexpr canid_t ID_CONTROL_ACK = 0x6F8;     // ECU xác nhận: [03 01 k trạng_thái], 0 = đã áp dụng, 1 = kịch bản không có
// Mode 03 trả 2 + 2*N byte; N <= 2 thì <= 6 byte, vừa một khung đơn (tối đa 7 byte dữ liệu)
constexpr size_t MAX_DTC = 2;

volatile std::sig_atomic_t g_stop = 0;
void on_signal(int) { g_stop = 1; }

using Clock = std::chrono::steady_clock;

// ---------- Kịch bản lỗi (tiêm lỗi) ----------
// Mỗi kịch bản gồm tối đa 2 DTC và các độ lệch của dữ liệu sống so với xe khỏe.
// Kịch bản 0 là xe khỏe. Nguyên nhân đúng của từng kịch bản (nhãn chuẩn) nằm ở docs/giao-thuc/KICH-BAN.md.
constexpr double NO_VALUE = -1e9;  // "không ghi đè"

struct Fault {
    double thr_lo = 5, thr_hi = 60;   // khoảng bướm ga: cầm chừng 5-9, có tải 20-70
    double rpm_add = 0;               // cộng thêm vào vòng tua đích
    double maf_mul = 1;               // nhân lưu lượng khí MAF
    double coolant_add = 0;           // cộng thêm vào nhiệt độ nước đọc được
    double coolant_fixed = NO_VALUE;  // ghi đè nhiệt độ nước (cảm biến hỏng)
    double speed_fixed = NO_VALUE;    // ghi đè tốc độ xe
    double volt_fixed = NO_VALUE;     // ghi đè điện áp
};

struct Scenario {
    const char* name;
    std::vector<std::string> dtcs;
    Fault fault;
};

const std::vector<Scenario>& scenarios() {
    static const std::vector<Scenario> list = [] {
        std::vector<Scenario> v;
        v.push_back({"Xe khoe", {}, Fault{}});
        Fault f1; f1.thr_lo = 5; f1.thr_hi = 9; f1.rpm_add = 500;
        v.push_back({"Ro ri chan khong", {"P0171"}, f1});
        Fault f2; f2.thr_lo = 5; f2.thr_hi = 9; f2.maf_mul = 0.55;
        v.push_back({"Cam bien MAF ban", {"P0171"}, f2});
        Fault f3; f3.thr_lo = 20; f3.thr_hi = 50;
        v.push_back({"Bom xang yeu", {"P0171"}, f3});
        v.push_back({"Bugi mon", {"P0300", "P0301"}, Fault{}});
        Fault f5; f5.thr_lo = 5; f5.thr_hi = 9; f5.coolant_add = 22;
        v.push_back({"Quat lam mat hong", {"P0217"}, f5});
        Fault f6; f6.coolant_fixed = 215;
        v.push_back({"Cam bien nhiet do nuoc ngan mach", {"P0117"}, f6});
        Fault f7; f7.thr_lo = 45; f7.thr_hi = 70; f7.speed_fixed = 0;
        v.push_back({"Mat tin hieu toc do xe", {"P0500"}, f7});
        Fault f8; f8.thr_lo = 22; f8.thr_hi = 50; f8.volt_fixed = 11.2;
        v.push_back({"May phat hong", {"P0562"}, f8});
        return v;
    }();
    return list;
}

// ---------- Mô phỏng trạng thái động cơ ----------
// Bước ngẫu nhiên nhỏ quanh giá trị hiện tại để số liệu thay đổi mượt như xe thật.
// Các giá trị công khai (throttle, rpm, ...) là giá trị đọc được qua OBD-II, đã tính độ lệch của kịch bản.
struct Sim {
    std::mt19937 rng{std::random_device{}()};
    double throttle = 8, rpm = 800, speed = 0, coolant = 88;
    double load = 20, iat = 30, maf = 3.0, voltage = 13.8;
    Fault fault;

    double noise(double sd) { return std::normal_distribution<double>(0.0, sd)(rng); }
    static double clamp(double v, double lo, double hi) { return std::max(lo, std::min(hi, v)); }

    void set_fault(const Fault& f) {
        fault = f;
        throttle = clamp(throttle, f.thr_lo, f.thr_hi);
    }

    void step() {
        double sd = clamp((fault.thr_hi - fault.thr_lo) / 12.0, 0.8, 4.0);
        throttle = clamp(throttle + noise(sd), fault.thr_lo, fault.thr_hi);
        // Cầm chừng (bướm ga 5%) quanh 750 vòng/phút, mỗi 1% bướm ga thêm khoảng 55 vòng/phút
        double target_rpm = 750 + 55 * std::max(0.0, throttle - 5) + fault.rpm_add;
        rpm = clamp(rpm + (target_rpm - rpm) * 0.25 + noise(40), 600, 6500);
        // Bướm ga dưới 10% là chưa chạy, trên đó tốc độ tăng dần
        double target_speed = std::max(0.0, throttle - 10) * 2.4;
        speed = clamp(speed + (target_speed - speed) * 0.08, 0, 220);
        coolant_state = clamp(coolant_state + (88 - coolant_state) * 0.01 + noise(0.3), 70, 118);
        load = clamp(15 + throttle * 0.8 + noise(1), 0, 100);
        iat = clamp(iat + noise(0.1), 20, 60);
        maf = clamp((2 + rpm / 1000.0 * throttle / 10.0 + noise(0.2)) * fault.maf_mul, 0, 655);
        voltage = clamp(13.8 + noise(0.05), 11, 15);
        // Ghi đè của kịch bản (cảm biến hỏng, đứt tín hiệu...)
        coolant = fault.coolant_fixed != NO_VALUE ? fault.coolant_fixed : coolant_state + fault.coolant_add;
        if (fault.speed_fixed != NO_VALUE) speed = fault.speed_fixed;
        if (fault.volt_fixed != NO_VALUE) voltage = fault.volt_fixed + noise(0.1);
    }

private:
    double coolant_state = 88;
};

// Đổi giá trị vật lý sang byte thô theo công thức của PID.
// Trả về số byte dữ liệu (0 nếu ECU không hỗ trợ PID này), ghi byte thô vào out[].
int encode_pid(uint8_t pid, const Sim& s, uint8_t* out) {
    auto u16 = [out](uint32_t raw) {
        out[0] = (raw >> 8) & 0xFF;
        out[1] = raw & 0xFF;
        return 2;
    };
    switch (pid) {
        case 0x04: out[0] = (uint8_t)std::lround(s.load * 255 / 100); return 1;     // tải = 100/255*A
        case 0x05: out[0] = (uint8_t)std::lround(s.coolant + 40); return 1;         // nhiệt độ = A-40
        case 0x0C: return u16((uint32_t)std::lround(s.rpm * 4));                    // rpm = (256A+B)/4
        case 0x0D: out[0] = (uint8_t)std::lround(s.speed); return 1;                // km/h = A
        case 0x0F: out[0] = (uint8_t)std::lround(s.iat + 40); return 1;             // nhiệt độ = A-40
        case 0x10: return u16((uint32_t)std::lround(s.maf * 100));                  // g/s = (256A+B)/100
        case 0x11: out[0] = (uint8_t)std::lround(s.throttle * 255 / 100); return 1; // % = 100/255*A
        case 0x42: return u16((uint32_t)std::lround(s.voltage * 1000));             // V = (256A+B)/1000
        default: return 0;
    }
}

// ---------- Mã hóa DTC thành 2 byte (ví dụ P0300 -> 03 00) ----------
// Bit 15-14: hệ (P=0, C=1, B=2, U=3); bit 13-12: chữ số thứ 2; mỗi ký tự sau là 4 bit (hex).
int hex_value(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    c = (char)std::toupper((unsigned char)c);
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

bool encode_dtc(const std::string& code, std::array<uint8_t, 2>& out) {
    if (code.size() != 5) return false;
    int sys;
    switch (std::toupper((unsigned char)code[0])) {
        case 'P': sys = 0; break;
        case 'C': sys = 1; break;
        case 'B': sys = 2; break;
        case 'U': sys = 3; break;
        default: return false;
    }
    int d = hex_value(code[1]), h2 = hex_value(code[2]), h3 = hex_value(code[3]), h4 = hex_value(code[4]);
    if (d < 0 || d > 3 || h2 < 0 || h3 < 0 || h4 < 0) return false;
    out[0] = (uint8_t)((sys << 6) | (d << 4) | h2);
    out[1] = (uint8_t)((h3 << 4) | h4);
    return true;
}

// ---------- Truy cập SocketCAN ----------
int open_can(const char* ifname) {
    int s = socket(PF_CAN, SOCK_RAW, CAN_RAW);
    if (s < 0) {
        std::perror("socket");
        return -1;
    }
    struct ifreq ifr;
    std::memset(&ifr, 0, sizeof ifr);
    std::strncpy(ifr.ifr_name, ifname, IFNAMSIZ - 1);
    if (ioctl(s, SIOCGIFINDEX, &ifr) < 0) {
        std::fprintf(stderr, "Không tìm thấy giao diện %s (đã chạy ./setup_vcan.sh chưa?)\n", ifname);
        close(s);
        return -1;
    }
    struct sockaddr_can addr;
    std::memset(&addr, 0, sizeof addr);
    addr.can_family = AF_CAN;
    addr.can_ifindex = ifr.ifr_ifindex;
    if (bind(s, (struct sockaddr*)&addr, sizeof addr) < 0) {
        std::perror("bind");
        close(s);
        return -1;
    }
    // Chỉ nhận khung gửi tới 0x7DF (quảng bá), 0x7E0 (riêng ECU này) và 0x6F0 (điều khiển tiêm lỗi)
    struct can_filter filters[3] = {{ID_REQ_FUNCTIONAL, CAN_SFF_MASK}, {ID_REQ_PHYSICAL, CAN_SFF_MASK},
                                    {ID_CONTROL, CAN_SFF_MASK}};
    setsockopt(s, SOL_CAN_RAW, CAN_RAW_FILTER, filters, sizeof filters);
    return s;
}

void print_frame(const char* dir, const struct can_frame& fr) {
    std::printf("%s %03X [%d]", dir, (unsigned)(fr.can_id & CAN_SFF_MASK), fr.can_dlc);
    for (int i = 0; i < fr.can_dlc && i < 8; i++) std::printf(" %02X", fr.data[i]);
    std::printf("\n");
    std::fflush(stdout);
}

// Gửi một khung 8 byte (phần dư điền 0)
void send_frame(int s, canid_t id, const std::vector<uint8_t>& bytes) {
    struct can_frame fr;
    std::memset(&fr, 0, sizeof fr);
    fr.can_id = id;
    fr.can_dlc = 8;
    std::memcpy(fr.data, bytes.data(), std::min<size_t>(bytes.size(), 8));
    if (write(s, &fr, sizeof fr) != (ssize_t)sizeof fr) std::perror("write");
    print_frame("TX", fr);
}

// ---------- Áp dụng một kịch bản lỗi: đặt lại danh sách DTC và độ lệch dữ liệu sống ----------
bool apply_scenario(size_t k, Sim& sim, std::vector<std::array<uint8_t, 2>>& dtcs) {
    if (k >= scenarios().size()) return false;
    const Scenario& sc = scenarios()[k];
    dtcs.clear();
    for (const auto& code : sc.dtcs) {
        std::array<uint8_t, 2> b{};
        if (!encode_dtc(code, b) || dtcs.size() >= MAX_DTC) return false;
        dtcs.push_back(b);
    }
    sim.set_fault(sc.fault);
    std::printf("-- Kich ban %zu: %s (%zu DTC)\n", k, sc.name, dtcs.size());
    std::fflush(stdout);
    return true;
}

// ---------- Xử lý một yêu cầu chẩn đoán ----------
void handle_request(int s, const struct can_frame& fr, Sim& sim, std::vector<std::array<uint8_t, 2>>& dtcs) {
    print_frame("RX", fr);
    if ((fr.can_id & CAN_SFF_MASK) == ID_CONTROL) {  // tiêm lỗi: [02 01 k]
        if (fr.data[0] == 2 && fr.data[1] == 0x01) {
            bool ok = apply_scenario(fr.data[2], sim, dtcs);
            if (!ok) std::printf("-- Kich ban %u khong hop le\n", fr.data[2]);
            send_frame(s, ID_CONTROL_ACK, {0x03, 0x01, fr.data[2], (uint8_t)(ok ? 0 : 1)});
        }
        return;
    }
    int len = fr.data[0];  // số byte hợp lệ phía sau byte độ dài
    if (len < 1 || len > 7) return;  // chỉ nhận yêu cầu một khung
    uint8_t mode = fr.data[1];

    switch (mode) {
        case 0x01: {  // dữ liệu sống: yêu cầu = [02 01 PID], phản hồi = [len 41 PID data...]
            if (len < 2) return;
            uint8_t pid = fr.data[2];
            uint8_t raw[2] = {0, 0};
            int n = encode_pid(pid, sim, raw);
            if (n == 0) {
                std::printf("-- PID %02X không hỗ trợ, bỏ qua\n", pid);
                return;
            }
            std::vector<uint8_t> f{(uint8_t)(2 + n), 0x41, pid};
            f.insert(f.end(), raw, raw + n);
            send_frame(s, ID_RESP, f);
            break;
        }
        case 0x03: {  // đọc DTC: phản hồi = [43 số_DTC (2 byte mỗi DTC)...]
            // Tối đa 2 DTC nên tối đa 6 byte (43, số DTC, 2 byte mỗi DTC): luôn nằm gọn trong một khung đơn
            std::vector<uint8_t> f{(uint8_t)(2 + 2 * dtcs.size()), 0x43, (uint8_t)dtcs.size()};
            for (const auto& d : dtcs) {
                f.push_back(d[0]);
                f.push_back(d[1]);
            }
            send_frame(s, ID_RESP, f);
            break;
        }
        case 0x04:  // xóa DTC: phản hồi dương = [01 44]
            dtcs.clear();
            send_frame(s, ID_RESP, {0x01, 0x44});
            std::printf("-- Đã xóa DTC\n");
            break;
        default:
            std::printf("-- Mode %02X chưa hỗ trợ, bỏ qua\n", mode);
    }
}

}  // namespace

int main(int argc, char** argv) {
    const char* ifname = argc > 1 ? argv[1] : "vcan0";

    std::vector<std::array<uint8_t, 2>> dtcs;
    std::vector<std::string> codes;
    for (int i = 2; i < argc; i++) codes.push_back(argv[i]);
    if (codes.empty()) codes = {"P0300", "P0171"};
    for (const auto& c : codes) {
        std::array<uint8_t, 2> b{};
        if (!encode_dtc(c, b)) {
            std::fprintf(stderr, "DTC không hợp lệ: %s\n", c.c_str());
            return 1;
        }
        dtcs.push_back(b);
    }

    if (dtcs.size() > MAX_DTC) {
        std::fprintf(stderr, "Tối đa %zu DTC mỗi lần đọc (không dùng ISO-TP), đã nhập %zu mã\n", MAX_DTC, dtcs.size());
        return 1;
    }

    int s = open_can(ifname);
    if (s < 0) return 1;
    std::signal(SIGINT, on_signal);
    std::signal(SIGTERM, on_signal);

    std::printf("ECU giả lập chạy trên %s, %zu DTC. Nhấn Ctrl+C để dừng.\n", ifname, dtcs.size());
    Sim sim;
    auto next_step = Clock::now();

    while (!g_stop) {
        fd_set rf;
        FD_ZERO(&rf);
        FD_SET(s, &rf);
        struct timeval tv = {0, 50000};  // 50 ms để vòng lặp cập nhật mô phỏng đều đặn
        int r = select(s + 1, &rf, nullptr, nullptr, &tv);
        if (r > 0) {
            struct can_frame fr;
            if (read(s, &fr, sizeof fr) == (ssize_t)sizeof fr) handle_request(s, fr, sim, dtcs);
        }
        if (Clock::now() >= next_step) {
            sim.step();
            next_step += std::chrono::milliseconds(100);
        }
    }
    close(s);
    std::printf("Đã dừng.\n");
    return 0;
}
