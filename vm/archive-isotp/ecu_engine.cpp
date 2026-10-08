// ecu_engine.cpp - ECU giả lập (động cơ) trả lời yêu cầu chẩn đoán OBD-II trên SocketCAN.
//
// Biên dịch: g++ -std=c++17 -Wall -o ecu_engine ecu_engine.cpp
// Chạy:      ./ecu_engine [giao_dien=vcan0] [DTC ...]
//   ví dụ:   ./ecu_engine vcan0 P0300 P0171      (không ghi DTC thì dùng 4 mã mặc định)
//
// Hỗ trợ (chi tiết ở docs/protocol.md):
//   Mode 01 - đọc dữ liệu sống, 8 PID: 04 05 0C 0D 0F 10 11 42
//   Mode 03 - đọc DTC (nhiều DTC thì gửi nhiều khung theo ISO-TP: First Frame + Consecutive Frame)
//   Mode 04 - xóa DTC
// Công thức mã hóa PID lấy từ docs/BANG-DTC-PID.xlsx (đang ở trạng thái "cần xác minh" với SAE J1979).

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
constexpr canid_t ID_REQ_PHYSICAL = 0x7E0;    // tester gửi riêng cho ECU này (kể cả flow control)
constexpr canid_t ID_RESP = 0x7E8;            // ECU số 1 trả lời

volatile std::sig_atomic_t g_stop = 0;
void on_signal(int) { g_stop = 1; }

using Clock = std::chrono::steady_clock;

// ---------- Mô phỏng trạng thái động cơ ----------
// Bước ngẫu nhiên nhỏ quanh giá trị hiện tại để số liệu thay đổi mượt như xe thật.
struct Sim {
    std::mt19937 rng{std::random_device{}()};
    double throttle = 8, rpm = 800, speed = 0, coolant = 85;
    double load = 20, iat = 30, maf = 3.0, voltage = 13.8;

    double noise(double sd) { return std::normal_distribution<double>(0.0, sd)(rng); }
    static double clamp(double v, double lo, double hi) { return std::max(lo, std::min(hi, v)); }

    void step() {
        throttle = clamp(throttle + noise(4), 5, 90);
        double target_rpm = 800 + throttle * 55;
        rpm = clamp(rpm + (target_rpm - rpm) * 0.25 + noise(40), 700, 6500);
        speed = clamp(speed + (throttle * 1.6 - speed) * 0.08, 0, 220);
        coolant = clamp(coolant + noise(0.4) + (throttle - 40) * 0.003, 70, 118);
        load = clamp(15 + throttle * 0.8 + noise(1), 0, 100);
        iat = clamp(iat + noise(0.1), 20, 60);
        maf = clamp(2 + rpm / 1000.0 * throttle / 10.0 + noise(0.2), 0, 655);
        voltage = clamp(13.8 + noise(0.05), 11, 15);
    }
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
    // Chỉ nhận khung gửi tới 0x7DF (quảng bá) và 0x7E0 (riêng ECU này)
    struct can_filter filters[2] = {{ID_REQ_FUNCTIONAL, CAN_SFF_MASK}, {ID_REQ_PHYSICAL, CAN_SFF_MASK}};
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

// ---------- ISO-TP: gửi thông điệp dài hơn 7 byte ----------
// Chờ khung Flow Control (0x3x) của tester trên ID 0x7E0. Trả false nếu quá 1 giây hoặc tester báo tràn.
bool wait_flow_control(int s, int& block_size, int& stmin_us) {
    auto deadline = Clock::now() + std::chrono::seconds(1);
    while (Clock::now() < deadline) {
        fd_set rf;
        FD_ZERO(&rf);
        FD_SET(s, &rf);
        struct timeval tv = {0, 100000};
        if (select(s + 1, &rf, nullptr, nullptr, &tv) <= 0) continue;
        struct can_frame fr;
        if (read(s, &fr, sizeof fr) != (ssize_t)sizeof fr) continue;
        if ((fr.can_id & CAN_SFF_MASK) != ID_REQ_PHYSICAL || (fr.data[0] & 0xF0) != 0x30) continue;
        print_frame("RX", fr);
        int flow_status = fr.data[0] & 0x0F;  // 0 = tiếp tục gửi, 1 = chờ, 2 = tràn bộ đệm
        if (flow_status == 2) return false;
        if (flow_status == 1) {
            deadline = Clock::now() + std::chrono::seconds(1);
            continue;
        }
        block_size = fr.data[1];
        int st = fr.data[2];
        if (st <= 0x7F) stmin_us = st * 1000;                       // 0-127 ms
        else if (st >= 0xF1 && st <= 0xF9) stmin_us = (st - 0xF0) * 100;  // 100-900 us
        else stmin_us = 0;
        return true;
    }
    return false;
}

void send_isotp(int s, const std::vector<uint8_t>& payload) {
    if (payload.size() <= 7) {  // đủ một khung: byte đầu là độ dài
        std::vector<uint8_t> f{(uint8_t)payload.size()};
        f.insert(f.end(), payload.begin(), payload.end());
        send_frame(s, ID_RESP, f);
        return;
    }
    // First Frame: 0x1L LL + 6 byte dữ liệu đầu
    std::vector<uint8_t> ff{(uint8_t)(0x10 | ((payload.size() >> 8) & 0x0F)), (uint8_t)(payload.size() & 0xFF)};
    ff.insert(ff.end(), payload.begin(), payload.begin() + 6);
    send_frame(s, ID_RESP, ff);

    int block_size = 0, stmin_us = 0;
    if (!wait_flow_control(s, block_size, stmin_us)) {
        std::fprintf(stderr, "Không nhận được Flow Control, bỏ gửi phần còn lại\n");
        return;
    }
    // Consecutive Frame: 0x2N + 7 byte, N đếm vòng 1..15,0,1...
    size_t pos = 6;
    uint8_t seq = 1;
    int sent_in_block = 0;
    while (pos < payload.size()) {
        size_t n = std::min<size_t>(7, payload.size() - pos);
        std::vector<uint8_t> cf{(uint8_t)(0x20 | (seq & 0x0F))};
        cf.insert(cf.end(), payload.begin() + pos, payload.begin() + pos + n);
        send_frame(s, ID_RESP, cf);
        pos += n;
        seq = (seq + 1) & 0x0F;
        sent_in_block++;
        if (pos >= payload.size()) break;
        if (block_size > 0 && sent_in_block >= block_size) {  // hết một khối, chờ Flow Control mới
            if (!wait_flow_control(s, block_size, stmin_us)) return;
            sent_in_block = 0;
        }
        if (stmin_us > 0) usleep(stmin_us);
    }
}

// ---------- Xử lý một yêu cầu chẩn đoán ----------
void handle_request(int s, const struct can_frame& fr, Sim& sim, std::vector<std::array<uint8_t, 2>>& dtcs) {
    print_frame("RX", fr);
    int len = fr.data[0];  // số byte hợp lệ phía sau byte độ dài
    if (len < 1 || len > 7) return;  // chỉ nhận yêu cầu một khung (flow control đã xử lý riêng)
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
            std::vector<uint8_t> payload{0x43, (uint8_t)dtcs.size()};
            for (const auto& d : dtcs) {
                payload.push_back(d[0]);
                payload.push_back(d[1]);
            }
            send_isotp(s, payload);
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
    if (codes.empty()) codes = {"P0300", "P0171", "P0420", "P0117"};
    for (const auto& c : codes) {
        std::array<uint8_t, 2> b{};
        if (!encode_dtc(c, b)) {
            std::fprintf(stderr, "DTC không hợp lệ: %s\n", c.c_str());
            return 1;
        }
        dtcs.push_back(b);
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
