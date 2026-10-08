#include <cstdio>
#include <cstring>
#include <unistd.h>
#include <chrono>
#include <string>
#include <iostream>

// Các header đặc thù của SocketCAN trên Linux
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <linux/can.h>
#include <linux/can/raw.h>

// ==== Định nghĩa CAN ID theo bảng thiết kế ====
// ID nhỏ hơn = ưu tiên cao hơn (đúng cơ chế arbitration của CAN bus)
#define CAN_ID_BRAKE_EMERGENCY 0x010  // Phanh gấp - ưu tiên cao nhất
#define CAN_ID_BRAKE_NORMAL    0x110  // Phanh bình thường - ưu tiên thấp hơn

// Hàm khởi tạo socket CAN, trả về file descriptor
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

// Hàm gửi 1 frame CAN với ID, mức độ phanh, và timestamp (để đo độ trễ ở phía nhận)
void send_brake_frame(int sockfd, uint32_t can_id, uint8_t brake_level) {
    struct can_frame frame;
    memset(&frame, 0, sizeof(frame));

    frame.can_id = can_id;
    frame.can_dlc = 8; // 8 byte payload

    // Byte 0: mức độ phanh (0-100%)
    frame.data[0] = brake_level;

    // Byte 1-7: nhúng timestamp (microsecond) để Node Động cơ tính độ trễ
    auto now = std::chrono::high_resolution_clock::now();
    int64_t us = std::chrono::duration_cast<std::chrono::microseconds>(
                     now.time_since_epoch()).count();
    memcpy(&frame.data[1], &us, 7); // chỉ lấy 7 byte thấp

    int nbytes = write(sockfd, &frame, sizeof(frame));
    if (nbytes != sizeof(frame)) {
        perror("Gửi frame thất bại");
    } else {
        printf("[Node Phanh] Gửi CAN ID=0x%03X, mức độ=%d%%\n", can_id, brake_level);
    }
}

int main() {
    int sockfd = setup_can_socket("vcan0");
    if (sockfd < 0) return 1;

    printf("=== Node Phanh khởi động ===\n");
    printf("Nhấn Enter để mô phỏng phanh BINH THUONG\n");
    printf("Go 'g' roi Enter de mo phong phanh GAP\n");
    printf("Go 'q' roi Enter de thoat\n\n");

    std::string input;
    while (true) {
        std::getline(std::cin, input);

        if (input == "q") {
            break;
        } else if (input == "g") {
            send_brake_frame(sockfd, CAN_ID_BRAKE_EMERGENCY, 95);
        } else {
            send_brake_frame(sockfd, CAN_ID_BRAKE_NORMAL, 30);
        }
    }

    close(sockfd);
    return 0;
}