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

#define CAN_ID_BRAKE_EMERGENCY 0x010
#define CAN_ID_BRAKE_NORMAL    0x110
#define NUM_SAMPLES_PER_TYPE 100  // số lần gửi mỗi loại - có thể chỉnh

int setup_can_socket(const char* ifname) {
    int sockfd = socket(PF_CAN, SOCK_RAW, CAN_RAW);
    if (sockfd < 0) { perror("Không thể tạo socket CAN"); return -1; }

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

void send_brake_frame(int sockfd, uint32_t can_id, uint8_t brake_level) {
    struct can_frame frame;
    memset(&frame, 0, sizeof(frame));
    frame.can_id = can_id;
    frame.can_dlc = 8;
    frame.data[0] = brake_level;

    auto now = std::chrono::high_resolution_clock::now();
    int64_t us = std::chrono::duration_cast<std::chrono::microseconds>(
                     now.time_since_epoch()).count();
    memcpy(&frame.data[1], &us, 7);

    write(sockfd, &frame, sizeof(frame));
}

int main() {
    int sockfd = setup_can_socket("vcan0");
    if (sockfd < 0) return 1;

    printf("=== Bắt đầu gửi tự động %d mẫu mỗi loại (phanh thường + phanh gấp), xen kẽ ngẫu nhiên ===\n", NUM_SAMPLES_PER_TYPE);

    // Random để trộn thứ tự 2 loại, tránh thiên vị theo thời gian hệ thống
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dist(0, 1);

    int count_normal = 0, count_emergency = 0;
    int total = NUM_SAMPLES_PER_TYPE * 2;

    for (int i = 0; i < total; ) {
        int choice = dist(gen);

        if (choice == 0 && count_normal < NUM_SAMPLES_PER_TYPE) {
            send_brake_frame(sockfd, CAN_ID_BRAKE_NORMAL, 30);
            count_normal++;
            i++;
        } else if (choice == 1 && count_emergency < NUM_SAMPLES_PER_TYPE) {
            send_brake_frame(sockfd, CAN_ID_BRAKE_EMERGENCY, 95);
            count_emergency++;
            i++;
        }

        // Khoảng nghỉ nhỏ giữa các lần gửi (50ms) để giống nhịp thực tế, tránh nghẽn
        std::this_thread::sleep_for(std::chrono::milliseconds(50));

        if (i % 20 == 0) printf("Đã gửi %d/%d...\n", i, total);
    }

    printf("=== Hoàn tất: %d mẫu phanh thường, %d mẫu phanh gấp ===\n", count_normal, count_emergency);

    close(sockfd);
    return 0;
}