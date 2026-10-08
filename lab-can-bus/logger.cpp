#include <cstdio>
#include <cstring>
#include <unistd.h>
#include <chrono>
#include <fstream>
#include <ctime>

#include <sys/socket.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <linux/can.h>
#include <linux/can/raw.h>

#define CAN_ID_BRAKE_EMERGENCY 0x010
#define CAN_ID_BRAKE_NORMAL    0x110

// Hàm khởi tạo socket CAN (giống hệt 2 node trước)
int setup_can_socket(const char* ifname) {
    int sockfd = socket(PF_CAN, SOCK_RAW, CAN_RAW);
    if (sockfd < 0) {
        perror("Không thể tạo socket CAN");
        return -1;
    }

    struct ifreq ifr;
    strcpy(ifr.ifr_name, ifname);
    ioctl(sockfd, SIOCGIFINDEX, &ifr);

    struct sockaddr_can addr;
    memset(&addr, 0, sizeof(addr));
    addr.can_family = AF_CAN;
    addr.can_ifindex = ifr.ifr_ifindex;

    if (bind(sockfd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        perror("Không thể bind socket vào vcan0");
        close(sockfd);
        return -1;
    }

    return sockfd;
}

// Trả về chuỗi tên loại tín hiệu, để ghi vào CSV cho dễ đọc
const char* get_signal_name(uint32_t can_id) {
    if (can_id == CAN_ID_BRAKE_EMERGENCY) return "BRAKE_EMERGENCY";
    if (can_id == CAN_ID_BRAKE_NORMAL) return "BRAKE_NORMAL";
    return "UNKNOWN";
}

int main() {
    int sockfd = setup_can_socket("vcan0");
    if (sockfd < 0) return 1;

    // Mở file CSV để ghi (append - không xóa dữ liệu cũ nếu chạy lại)
    std::ofstream logfile("can_log.csv", std::ios::app);
    if (!logfile.is_open()) {
        fprintf(stderr, "Không thể mở file log\n");
        return 1;
    }

    // Ghi header nếu file mới tạo (kiểm tra file rỗng)
    logfile.seekp(0, std::ios::end);
    if (logfile.tellp() == 0) {
        logfile << "timestamp_recv_us,can_id,signal_name,brake_level,latency_us\n";
    }

    printf("=== Logger khởi động, đang ghi log vào can_log.csv ===\n");
    printf("Nhấn Ctrl+C để dừng.\n\n");

    struct can_frame frame;

    while (true) {
        int nbytes = read(sockfd, &frame, sizeof(frame));
        if (nbytes < 0) {
            perror("Lỗi đọc frame");
            continue;
        }

        if (frame.can_id == CAN_ID_BRAKE_EMERGENCY || frame.can_id == CAN_ID_BRAKE_NORMAL) {
            uint8_t brake_level = frame.data[0];

            int64_t sent_us = 0;
            memcpy(&sent_us, &frame.data[1], 7);

            auto now = std::chrono::high_resolution_clock::now();
            int64_t recv_us = std::chrono::duration_cast<std::chrono::microseconds>(
                                   now.time_since_epoch()).count();

            int64_t latency_us = recv_us - sent_us;

            // Ghi 1 dòng vào CSV: timestamp, CAN ID, tên tín hiệu, mức phanh, độ trễ
            logfile << recv_us << ","
                    << "0x" << std::hex << frame.can_id << std::dec << ","
                    << get_signal_name(frame.can_id) << ","
                    << (int)brake_level << ","
                    << latency_us << "\n";
            logfile.flush(); // ghi ngay xuống đĩa, tránh mất dữ liệu nếu dừng đột ngột

            printf("[Ghi log] %s | mức=%d%% | trễ=%lld us\n",
                   get_signal_name(frame.can_id), brake_level, (long long)latency_us);
        }
    }

    logfile.close();
    close(sockfd);
    return 0;
}