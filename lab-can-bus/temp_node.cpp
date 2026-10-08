#include <cstdio>
#include <cstring>
#include <unistd.h>
#include <chrono>
#include <thread>
#include <random>

#include <sys/socket.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <linux/can.h>
#include <linux/can/raw.h>

// CAN ID mới - mức ưu tiên thấp nhất trong hệ thống (số lớn nhất)
#define CAN_ID_ENGINE_TEMP 0x200

// Hàm khởi tạo socket CAN (giống các node trước)
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

// Gửi 1 frame nhiệt độ động cơ (không cần đo độ trễ, chỉ tạo traffic nền)
void send_temp_frame(int sockfd, uint8_t temp_celsius) {
    struct can_frame frame;
    memset(&frame, 0, sizeof(frame));

    frame.can_id = CAN_ID_ENGINE_TEMP;
    frame.can_dlc = 8;
    frame.data[0] = temp_celsius; // byte 0: nhiệt độ (độ C)

    // Vẫn nhúng timestamp để đồng bộ format với các node khác (dù không dùng để tính trễ ở đây)
    auto now = std::chrono::high_resolution_clock::now();
    int64_t us = std::chrono::duration_cast<std::chrono::microseconds>(
                     now.time_since_epoch()).count();
    memcpy(&frame.data[1], &us, 7);

    write(sockfd, &frame, sizeof(frame));
}

int main() {
    int sockfd = setup_can_socket("vcan0");
    if (sockfd < 0) return 1;

    printf("=== Node Nhiệt độ động cơ khởi động - gửi liên tục mỗi 100ms ===\n");
    printf("Nhấn Ctrl+C để dừng.\n\n");

    // Random để mô phỏng nhiệt độ dao động nhẹ quanh mức bình thường (85-95 độ C)
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dist(85, 95);

    while (true) {
        uint8_t temp = dist(gen);
        send_temp_frame(sockfd, temp);
        printf("[Node Nhiệt độ] Gửi CAN ID=0x200, nhiệt độ=%d°C\n", temp);

        // Gửi liên tục mỗi 100ms - mô phỏng cảm biến thật luôn báo cáo định kỳ
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }

    close(sockfd);
    return 0;
}