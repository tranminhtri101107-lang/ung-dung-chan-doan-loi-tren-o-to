%% Đọc dữ liệu từ file log CAN bus
% Đảm bảo file can_log.csv nằm cùng thư mục với script này
data = readtable('can_log.csv');

% Tách dữ liệu theo 2 loại tín hiệu: phanh gấp và phanh bình thường
emergency_data = data(strcmp(data.signal_name, 'BRAKE_EMERGENCY'), :);
normal_data = data(strcmp(data.signal_name, 'BRAKE_NORMAL'), :);

% Lấy cột độ trễ (latency_us) của từng loại
latency_emergency = emergency_data.latency_us;
latency_normal = normal_data.latency_us;

%% Thống kê cơ bản
fprintf('=== THỐNG KÊ ĐỘ TRỄ (microsecond) ===\n\n');

fprintf('--- Phanh GẤP (n=%d mẫu) ---\n', length(latency_emergency));
fprintf('Trung bình: %.2f us\n', mean(latency_emergency));
fprintf('Độ lệch chuẩn: %.2f us\n', std(latency_emergency));
fprintf('Min: %d us | Max: %d us\n\n', min(latency_emergency), max(latency_emergency));

fprintf('--- Phanh BÌNH THƯỜNG (n=%d mẫu) ---\n', length(latency_normal));
fprintf('Trung bình: %.2f us\n', mean(latency_normal));
fprintf('Độ lệch chuẩn: %.2f us\n', std(latency_normal));
fprintf('Min: %d us | Max: %d us\n\n', min(latency_normal), max(latency_normal));

%% Vẽ biểu đồ so sánh - Boxplot (trực quan độ phân tán)
figure('Name', 'So sánh độ trễ CAN Bus');

subplot(1, 2, 1);
means = [mean(latency_emergency), mean(latency_normal)];
stds = [std(latency_emergency), std(latency_normal)];

bar(means, 'FaceColor', [0.3 0.6 0.9]);
hold on;
errorbar(1:2, means, stds, 'k', 'LineStyle', 'none', 'LineWidth', 1.5);
set(gca, 'XTickLabel', {'Phanh Gấp', 'Phanh Thường'});
ylabel('Độ trễ trung bình (microsecond)');
title('So sánh độ trễ: Trung bình ± Độ lệch chuẩn');
grid on;

%% Vẽ biểu đồ Histogram (phân bố)
subplot(1, 2, 2);
histogram(latency_emergency, 15, 'FaceColor', 'r', 'FaceAlpha', 0.5);
hold on;
histogram(latency_normal, 15, 'FaceColor', 'b', 'FaceAlpha', 0.5);
legend('Phanh Gấp', 'Phanh Thường');
xlabel('Độ trễ (microsecond)');
ylabel('Số lần xuất hiện');
title('Phân bố độ trễ');
grid on;

%% Lưu biểu đồ ra file ảnh để đưa vào báo cáo Word
saveas(gcf, 'bieu_do_so_sanh_do_tre.png');
fprintf('Đã lưu biểu đồ vào file bieu_do_so_sanh_do_tre.png\n');