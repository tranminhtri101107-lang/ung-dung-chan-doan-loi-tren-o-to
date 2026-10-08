// gateway.cpp - Cổng nối giữa ứng dụng Java (TCP) và ECU (CAN, vcan0).
//
// Biên dịch: g++ -std=c++17 -Wall -o gateway gateway.cpp
// Chạy:      ./gateway [giao_dien=vcan0] [cong_tcp=5000]
//
// Gateway đóng vai máy chẩn đoán (tester):
//   - Khi có client TCP, hỏi ECU định kỳ từng PID (Mode 01) và đẩy kết quả: LIVE <pid> <byte_thô> <us>
//   - Nhận "REQ 03" / "REQ 04" từ client, hỏi ECU rồi trả "RSP 03 ..." / "RSP 04 OK"
//   - Ghép các khung ISO-TP (First Frame + Consecutive Frame) và gửi Flow Control cho ECU
// Giao thức đầy đủ: docs/giao-thuc/protocol.md.
//
// Thiết kế: một luồng, vòng lặp select() chờ đồng thời socket CAN, socket lắng nghe và client TCP.
// Tại một thời điểm chỉ có một yêu cầu CAN đang chờ phản hồi (xem struct Pending).

#include <algorithm>
#include <array>
#include <chrono>
#include <csignal>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <string>
#include <vector>

#include <arpa/inet.h>
#include <linux/can.h>
#include <linux/can/raw.h>
#include <net/if.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/ioctl.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <unistd.h>

namespace {

constexpr canid_t ID_REQ_FUNCTIONAL = 0x7DF;  // gửi yêu cầu quảng bá
constexpr canid_t ID_REQ_PHYSICAL = 0x7E0;    // gửi flow control riêng cho ECU
constexpr canid_t ID_RESP = 0x7E8;            // ECU trả lời

constexpr int POLL_SPACING_MS = 50;   // khoảng cách giữa hai yêu cầu PID
constexpr int PID_TIMEOUT_MS = 300;   // quá hạn thì bỏ qua PID đó
constexpr int DTC_TIMEOUT_MS = 2000;  // đọc/xóa DTC có thể nhiều khung nên chờ lâu hơn
constexpr size_t MAX_QUEUE = 8;       // số lệnh REQ tối đa đang xếp hàng

// 8 PID cần hỏi định kỳ (giống ECU hỗ trợ)
const std::array<uint8_t, 8> POLL_PIDS = {0x0C, 0x0D, 0x05, 0x11, 0x04, 0x0F, 0x10, 0x42};

volatile std::sig_atomic_t g_stop = 0;
void on_signal(int) { g_stop = 1; }

using Clock = std::chrono::steady_clock;

long long now_us() {
    return std::chrono::duration_cast<std::chrono::microseconds>(
               std::chrono::system_clock::now().time_since_epoch())
        .count();
}

std::string hex2(uint8_t v) {
    char b[3];
    std::snprintf(b, sizeof b, "%02X", v);
    return b;
}

// Đổi 2 byte DTC thành chữ, ví dụ 03 00 -> "P0300"
std::string dtc_to_string(uint8_t b0, uint8_t b1) {
    static const char SYS[] = {'P', 'C', 'B', 'U'};
    char s[6];
    std::snprintf(s, sizeof s, "%c%d%X%X%X", SYS[b0 >> 6], (b0 >> 4) & 0x3, b0 & 0xF, b1 >> 4, b1 & 0xF);
    return s;
}

// ---------- Socket ----------
int open_can(const char* ifname) {
    int s = socket(PF_CAN, SOCK_RAW, CAN_RAW);
    if (s < 0) {
        std::perror("socket CAN");
        return -1;
    }
    struct ifreq ifr;
    std::memset(&ifr, 0, sizeof ifr);
    std::strncpy(ifr.ifr_name, ifname, IFNAMSIZ - 1);
    if (ioctl(s, SIOCGIFINDEX, &ifr) < 0) {
        std::fprintf(stderr, "Không tìm thấy giao diện %s (đã chạy setup_vcan.sh chưa?)\n", ifname);
        close(s);
        return -1;
    }
    struct sockaddr_can addr;
    std::memset(&addr, 0, sizeof addr);
    addr.can_family = AF_CAN;
    addr.can_ifindex = ifr.ifr_ifindex;
    if (bind(s, (struct sockaddr*)&addr, sizeof addr) < 0) {
        std::perror("bind CAN");
        close(s);
        return -1;
    }
    struct can_filter filter = {ID_RESP, CAN_SFF_MASK};  // chỉ nhận khung ECU trả lời
    setsockopt(s, SOL_CAN_RAW, CAN_RAW_FILTER, &filter, sizeof filter);
    return s;
}

int open_listener(int port) {
    int s = socket(AF_INET, SOCK_STREAM, 0);
    if (s < 0) {
        std::perror("socket TCP");
        return -1;
    }
    int one = 1;
    setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &one, sizeof one);
    struct sockaddr_in addr;
    std::memset(&addr, 0, sizeof addr);
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);  // nhận kết nối từ Windows qua mạng ảo VMware
    addr.sin_port = htons((uint16_t)port);
    if (bind(s, (struct sockaddr*)&addr, sizeof addr) < 0 || listen(s, 1) < 0) {
        std::perror("bind/listen TCP");
        close(s);
        return -1;
    }
    return s;
}

void send_can(int can, canid_t id, const std::vector<uint8_t>& bytes) {
    struct can_frame fr;
    std::memset(&fr, 0, sizeof fr);
    fr.can_id = id;
    fr.can_dlc = 8;
    std::memcpy(fr.data, bytes.data(), std::min<size_t>(bytes.size(), 8));
    if (write(can, &fr, sizeof fr) != (ssize_t)sizeof fr) std::perror("write CAN");
}

// ---------- Trạng thái ----------
enum class Op { None, Pid, ReadDtc, ClearDtc };

struct Pending {  // yêu cầu CAN đang chờ ECU trả lời
    Op op = Op::None;
    uint8_t pid = 0;
    Clock::time_point deadline;
};

struct Reassembly {  // đang ghép thông điệp ISO-TP nhiều khung
    bool active = false;
    size_t total = 0;
    uint8_t next_seq = 1;
    std::vector<uint8_t> data;
};

struct Gateway {
    int can = -1;
    int listen_fd = -1;
    int client = -1;
    std::string inbuf;                 // dữ liệu TCP chưa đủ một dòng
    std::deque<std::string> user_queue;  // lệnh REQ chờ gửi: "03" hoặc "04"
    Pending pending;
    Reassembly reasm;
    Clock::time_point next_poll = Clock::now();
    size_t poll_index = 0;

    void reset_session() {
        inbuf.clear();
        user_queue.clear();
        pending.op = Op::None;
        reasm.active = false;
    }

    void close_client() {
        if (client >= 0) close(client);
        client = -1;
        reset_session();
    }

    // Gửi một dòng cho client. Dòng LIVE rất nhiều nên không in ra màn hình.
    void send_line(const std::string& line) {
        if (client < 0) return;
        if (line.compare(0, 5, "LIVE ") != 0) std::printf("TCP <- %s\n", line.c_str());
        std::string out = line + "\n";
        size_t sent = 0;
        while (sent < out.size()) {
            ssize_t n = send(client, out.data() + sent, out.size() - sent, MSG_NOSIGNAL);
            if (n <= 0) {
                std::printf("-- Gửi cho client lỗi, đóng kết nối\n");
                close_client();
                return;
            }
            sent += (size_t)n;
        }
    }

    // Gửi yêu cầu OBD-II lên bus và ghi nhận đang chờ phản hồi
    void request(Op op, uint8_t pid = 0) {
        std::vector<uint8_t> f;
        int timeout_ms = PID_TIMEOUT_MS;
        switch (op) {
            case Op::Pid: f = {0x02, 0x01, pid}; break;
            case Op::ReadDtc: f = {0x01, 0x03}; timeout_ms = DTC_TIMEOUT_MS; break;
            case Op::ClearDtc: f = {0x01, 0x04}; timeout_ms = DTC_TIMEOUT_MS; break;
            default: return;
        }
        pending.op = op;
        pending.pid = pid;
        pending.deadline = Clock::now() + std::chrono::milliseconds(timeout_ms);
        reasm.active = false;
        send_can(can, ID_REQ_FUNCTIONAL, f);
    }

    // Xử lý thông điệp hoàn chỉnh từ ECU (đã ghép xong nếu nhiều khung)
    void handle_payload(const std::vector<uint8_t>& p) {
        if (p.empty()) return;
        if (p[0] == 0x7F) {  // phản hồi âm: 7F <mode> <mã lỗi>
            char b[40];
            std::snprintf(b, sizeof b, "ERR NEGATIVE %02X %02X", p.size() > 1 ? p[1] : 0, p.size() > 2 ? p[2] : 0);
            pending.op = Op::None;
            send_line(b);
            return;
        }
        std::string line;
        if (pending.op == Op::Pid && p[0] == 0x41 && p.size() >= 3 && p[1] == pending.pid) {
            line = "LIVE " + hex2(p[1]) + " ";
            for (size_t i = 2; i < p.size(); i++) line += hex2(p[i]);
            line += " " + std::to_string(now_us());
        } else if (pending.op == Op::ReadDtc && p[0] == 0x43 && p.size() >= 2) {
            size_t n = p[1];
            if (p.size() < 2 + 2 * n) {
                line = "ERR BAD_RESPONSE 03";
            } else {
                line = "RSP 03";
                for (size_t i = 0; i < n; i++) line += " " + dtc_to_string(p[2 + 2 * i], p[3 + 2 * i]);
            }
        } else if (pending.op == Op::ClearDtc && p[0] == 0x44) {
            line = "RSP 04 OK";
        } else {
            return;  // không khớp yêu cầu đang chờ (phản hồi muộn), bỏ qua
        }
        pending.op = Op::None;
        send_line(line);
    }

    // Một khung CAN từ ECU. PCI: 0x0L = khung đơn, 0x1_ = First Frame, 0x2_ = Consecutive Frame
    void on_can(const struct can_frame& fr) {
        if ((fr.can_id & CAN_SFF_MASK) != ID_RESP || pending.op == Op::None) return;
        uint8_t pci = fr.data[0];
        uint8_t type = pci >> 4;
        if (type == 0) {
            int len = pci & 0x0F;
            if (len < 1 || len > 7) return;
            handle_payload(std::vector<uint8_t>(fr.data + 1, fr.data + 1 + len));
        } else if (type == 1) {
            reasm.active = true;
            reasm.total = ((size_t)(pci & 0x0F) << 8) | fr.data[1];
            reasm.next_seq = 1;
            reasm.data.assign(fr.data + 2, fr.data + 8);
            send_can(can, ID_REQ_PHYSICAL, {0x30, 0x00, 0x00});  // Flow Control: gửi tiếp, không giới hạn khối
        } else if (type == 2 && reasm.active) {
            if ((pci & 0x0F) != (reasm.next_seq & 0x0F)) {
                std::printf("-- Sai số thứ tự khung (nhận %d, cần %d), bỏ gói\n", pci & 0x0F, reasm.next_seq & 0x0F);
                reasm.active = false;
                return;
            }
            size_t take = std::min<size_t>(7, reasm.total - reasm.data.size());
            reasm.data.insert(reasm.data.end(), fr.data + 1, fr.data + 1 + take);
            reasm.next_seq++;
            if (reasm.data.size() >= reasm.total) {
                reasm.active = false;
                handle_payload(reasm.data);
            }
        }
    }

    void handle_command(const std::string& line) {
        if (line.empty()) return;
        std::printf("TCP -> %s\n", line.c_str());
        if (line == "REQ 03" || line == "REQ 04") {
            if (user_queue.size() >= MAX_QUEUE) {
                send_line("ERR BUSY");
                return;
            }
            user_queue.push_back(line.substr(4));
        } else if (line == "PING") {
            send_line("PONG");
        } else {
            send_line("ERR BAD_COMMAND");
        }
    }

    void on_tcp_data() {
        char buf[512];
        ssize_t n = recv(client, buf, sizeof buf, 0);
        if (n <= 0) {
            std::printf("-- Client ngắt kết nối\n");
            close_client();
            return;
        }
        inbuf.append(buf, (size_t)n);
        size_t pos;
        while (client >= 0 && (pos = inbuf.find('\n')) != std::string::npos) {
            std::string line = inbuf.substr(0, pos);
            inbuf.erase(0, pos + 1);
            while (!line.empty() && (line.back() == '\r' || line.back() == ' ')) line.pop_back();
            handle_command(line);
        }
        if (inbuf.size() > 1024) inbuf.clear();  // chống dòng dài vô hạn
    }

    void on_accept() {
        int c = accept(listen_fd, nullptr, nullptr);
        if (c < 0) return;
        if (client >= 0) {
            std::printf("-- Client mới thay thế client cũ\n");
            close_client();
        }
        client = c;
        int one = 1;
        setsockopt(c, IPPROTO_TCP, TCP_NODELAY, &one, sizeof one);
        reset_session();
        std::printf("-- Client đã kết nối\n");
    }

    // Gọi đều đặn: xử lý quá hạn, rồi chọn yêu cầu tiếp theo (lệnh của người dùng được ưu tiên)
    void tick() {
        auto now = Clock::now();
        if (pending.op != Op::None && now >= pending.deadline) {
            if (pending.op == Op::ReadDtc) send_line("ERR TIMEOUT 03");
            else if (pending.op == Op::ClearDtc) send_line("ERR TIMEOUT 04");
            else std::printf("-- PID %02X không phản hồi\n", pending.pid);
            pending.op = Op::None;
            reasm.active = false;
        }
        if (client < 0 || pending.op != Op::None) return;
        if (!user_queue.empty()) {
            std::string cmd = user_queue.front();
            user_queue.pop_front();
            request(cmd == "03" ? Op::ReadDtc : Op::ClearDtc);
            return;
        }
        if (now >= next_poll) {
            request(Op::Pid, POLL_PIDS[poll_index]);
            poll_index = (poll_index + 1) % POLL_PIDS.size();
            next_poll = now + std::chrono::milliseconds(POLL_SPACING_MS);
        }
    }
};

}  // namespace

int main(int argc, char** argv) {
    const char* ifname = argc > 1 ? argv[1] : "vcan0";
    int port = argc > 2 ? std::atoi(argv[2]) : 5000;

    Gateway gw;
    gw.can = open_can(ifname);
    if (gw.can < 0) return 1;
    gw.listen_fd = open_listener(port);
    if (gw.listen_fd < 0) return 1;

    std::signal(SIGINT, on_signal);
    std::signal(SIGTERM, on_signal);
    std::signal(SIGPIPE, SIG_IGN);
    std::setvbuf(stdout, nullptr, _IOLBF, 0);  // xả log theo từng dòng, kể cả khi chuyển hướng ra file
    std::printf("Gateway: CAN %s <-> TCP cổng %d. Nhấn Ctrl+C để dừng.\n", ifname, port);

    while (!g_stop) {
        fd_set rf;
        FD_ZERO(&rf);
        FD_SET(gw.can, &rf);
        FD_SET(gw.listen_fd, &rf);
        int maxfd = std::max(gw.can, gw.listen_fd);
        if (gw.client >= 0) {
            FD_SET(gw.client, &rf);
            maxfd = std::max(maxfd, gw.client);
        }
        struct timeval tv = {0, 20000};  // 20 ms để tick() chạy đều dù không có dữ liệu
        int r = select(maxfd + 1, &rf, nullptr, nullptr, &tv);
        if (r > 0) {
            if (FD_ISSET(gw.can, &rf)) {
                struct can_frame fr;
                if (read(gw.can, &fr, sizeof fr) == (ssize_t)sizeof fr) gw.on_can(fr);
            }
            if (FD_ISSET(gw.listen_fd, &rf)) gw.on_accept();
            if (gw.client >= 0 && FD_ISSET(gw.client, &rf)) gw.on_tcp_data();
        }
        gw.tick();
    }
    gw.close_client();
    close(gw.listen_fd);
    close(gw.can);
    std::printf("Đã dừng.\n");
    return 0;
}
