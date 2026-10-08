#include <cstdio>
#include <cstring>
#include <unistd.h>
#include <chrono>

#include <sys/socket.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <linux/can.h>
#include <linux/can/raw.h>

#define CAN_ID_BRAKE_EMERGENCY 0x010
#define CAN_ID_BRAKE_NORMAL    0x110

// Hàm khởi tạo socket CAN (giống hệt bên Node Phanh)
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

// Xử lý phản ứng của động cơ dựa trên mức độ phanh nhận được
void react_to_brake(uint32_t can_id, uint8_t brake_level) {
    if (can_id == CAN_ID_BRAKE_EMERGENCY) {
        printf("  >> [Hộp điều khiển động cơ] PHÁT HIỆN PHANH GẤP (%d%%) -> CẮT NHIÊN LIỆU KHẨN CẤP!\n", brake_level);
    } else if (can_id == CAN_ID_BRAKE_NORMAL) {
        printf("  >> [Hộp điều khiển động cơ] Phanh bình thường (%d%%) -> Giảm ga từ từ\n", brake_level);
    }
}

int main() {
    int sockfd = setup_can_socket("vcan0");
    if (sockfd < 0) return 1;

    printf("=== Node Hộp điều khiển động cơ khởi động, đang lắng nghe vcan0... ===\n\n");

    struct can_frame frame;

    while (true) {
        int nbytes = read(sockfd, &frame, sizeof(frame));
        if (nbytes < 0) {
            perror("Lỗi đọc frame");
            continue;
        }

        // Chỉ xử lý frame liên quan tới phanh
        if (frame.can_id == CAN_ID_BRAKE_EMERGENCY || frame.can_id == CAN_ID_BRAKE_NORMAL) {
            uint8_t brake_level = frame.data[0];

            // Lấy lại timestamp gửi đã nhúng trong payload (byte 1-7)
            int64_t sent_us = 0;
            memcpy(&sent_us, &frame.data[1], 7);

            // Thời điểm nhận (bây giờ)
            auto now = std::chrono::high_resolution_clock::now();
            int64_t recv_us = std::chrono::duration_cast<std::chrono::microseconds>(
                                   now.time_since_epoch()).count();

            int64_t latency_us = recv_us - sent_us;

            printf("[Nhận] CAN ID=0x%03X, mức độ=%d%%, độ trễ=%lld us\n",
                   frame.can_id, brake_level, (long long)latency_us);

            react_to_brake(frame.can_id, brake_level);
        }
    }

    close(sockfd);
    return 0;
}