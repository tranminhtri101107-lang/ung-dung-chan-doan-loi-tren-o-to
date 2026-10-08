% phan_tich_thuc_nghiem.m - Phân tích kết quả thực nghiệm chẩn đoán (mục 3.4 của báo cáo).
%
% Đầu vào : CSV do docs/bao-cao/tools/ChayThucNghiem.java ghi ra (mỗi dòng là một lần chạy một kịch bản).
% Đầu ra  : 3 hình PNG và các bảng tóm tắt (CSV + văn bản) để chèn báo cáo.
% Chạy    : matlab -batch "phan_tich_thuc_nghiem('<csv>', '<thu_muc_hinh>', '<thu_muc_tom_tat>')"
% Không dùng Statistics Toolbox (không có trong bản cài): khoảng tin cậy Wilson tự tính, histogram vẽ bằng bar.

function phan_tich_thuc_nghiem(csvFile, hinhDir, outDir)
    if nargin < 1, csvFile = fullfile('..', 'docs', 'bao-cao', 'du-lieu', 'ket_qua_thuc_nghiem.csv'); end
    if nargin < 2, hinhDir = fullfile('..', 'docs', 'bao-cao', 'hinh'); end
    if nargin < 3, outDir = fileparts(csvFile); end

    opts = detectImportOptions(csvFile, 'Encoding', 'UTF-8');
    opts = setvartype(opts, {'scenario', 'true_cause', 'dtcs', 'top1'}, 'string');
    T = readtable(csvFile, opts);
    soNguyenNhan = 17;                       % số nguyên nhân khả dĩ trong cơ sở tri thức
    chance1 = 1 / soNguyenNhan;              % đoán ngẫu nhiên hạng 1 trong mọi nguyên nhân
    chance3 = 3 / soNguyenNhan;              % đoán ngẫu nhiên nằm trong 3 hạng đầu

    loi = T(T.scenario_id > 0, :);           % các kịch bản có lỗi (kịch bản 0 là xe khỏe)
    khoe = T(T.scenario_id == 0, :);
    ids = unique(loi.scenario_id)';
    k = numel(ids);

    % ---- Thống kê theo từng kịch bản ----
    ten = strings(k, 1); n = zeros(k, 1);
    t1 = zeros(k, 1); t3 = zeros(k, 1); t1d = zeros(k, 1); t3d = zeros(k, 1);
    lo1 = zeros(k, 1); hi1 = zeros(k, 1); lo3 = zeros(k, 1); hi3 = zeros(k, 1); uv = zeros(k, 1); nuv = zeros(k, 1);
    for i = 1:k
        s = loi(loi.scenario_id == ids(i), :);
        ten(i) = s.scenario(1); n(i) = height(s);
        t1(i) = mean(s.top1_ok); t3(i) = mean(s.top3_ok);
        t1d(i) = mean(s.top1_dtc_only); t3d(i) = mean(s.top3_dtc_only);
        [lo1(i), hi1(i)] = wilson(sum(s.top1_ok), n(i));
        [lo3(i), hi3(i)] = wilson(sum(s.top3_ok), n(i));
        nuv(i) = mean(s.n_causes);            % số nguyên nhân ứng viên mà bộ luật đưa ra cho mã lỗi của kịch bản
        uv(i) = mean(1 ./ s.n_causes);        % đoán ngẫu nhiên hạng 1 trong các ứng viên đó
    end
    kb = table(ids', ten, n, t1, lo1, hi1, t3, lo3, hi3, t1d, t3d, nuv, uv, ...
        'VariableNames', {'id', 'ten', 'n', 'top1', 'top1_lo', 'top1_hi', 'top3', 'top3_lo', 'top3_hi', 'top1_dtc', 'top3_dtc', 'ung_vien', 'ngau_nhien_uv'});
    writetable(kb, fullfile(outDir, 'tom_tat_theo_kich_ban.csv'), 'Encoding', 'UTF-8');

    % ---- Thống kê toàn bộ ----
    N = height(loi);
    s1 = sum(loi.top1_ok); s3 = sum(loi.top3_ok);
    [a1, b1] = wilson(s1, N); [a3, b3] = wilson(s3, N);
    [d1lo, d1hi] = wilson(sum(loi.top1_dtc_only), N); [d3lo, d3hi] = wilson(sum(loi.top3_dtc_only), N);
    baoSai = mean(khoe.n_causes > 0);        % xe khỏe mà vẫn có kết luận (cảnh báo sai)

    % ---- Độ trễ ----
    lat = T.req03_ms; inf = T.infer_us; inj = T.inject_ms;
    tt = @(x) [mean(x), std(x), median(x), prctile_(x, 95), max(x), min(x)];
    L = array2table([tt(lat); tt(inj); tt(inf)], 'VariableNames', {'tb', 'dlc', 'trung_vi', 'p95', 'max', 'min'}, ...
        'RowNames', {'req03_ms', 'inject_ms', 'infer_us'});
    writetable(L, fullfile(outDir, 'tom_tat_do_tre.csv'), 'WriteRowNames', true, 'Encoding', 'UTF-8');
    nNgoaiLai = sum(lat > median(lat) + 3 * 1.4826 * mad_(lat));   % ngoại lai theo trung vị và MAD

    tong = table(N, s1, a1, b1, s3, a3, b3, sum(loi.top1_dtc_only), d1lo, d1hi, sum(loi.top3_dtc_only), d3lo, d3hi, ...
        chance1, chance3, height(khoe), sum(khoe.n_causes > 0), nNgoaiLai, height(T), mean(1 ./ loi.n_causes), ...
        'VariableNames', {'n', 'top1_dung', 'top1_lo', 'top1_hi', 'top3_dung', 'top3_lo', 'top3_hi', 'top1_dtc_dung', 'top1_dtc_lo', 'top1_dtc_hi', ...
        'top3_dtc_dung', 'top3_dtc_lo', 'top3_dtc_hi', 'ngau_nhien_1', 'ngau_nhien_3', 'khoe_n', 'khoe_bao_sai', 'ngoai_lai', 'tong_lan', 'ngau_nhien_uv'});
    writetable(tong, fullfile(outDir, 'tom_tat_tong.csv'), 'Encoding', 'UTF-8');

    % ---- Văn bản tóm tắt ----
    fid = fopen(fullfile(outDir, 'tom_tat_thuc_nghiem.txt'), 'w', 'n', 'UTF-8');
    fprintf(fid, 'Tổng số lần chạy: %d (kịch bản lỗi: %d, xe khỏe: %d)\n', height(T), N, height(khoe));
    fprintf(fid, 'Đoán ngẫu nhiên (%d nguyên nhân): hạng 1 = %.1f%%, top-3 = %.1f%%\n', soNguyenNhan, 100 * chance1, 100 * chance3);
    fprintf(fid, 'Đoán ngẫu nhiên hạng 1 trong ứng viên của mã lỗi (trung bình): %.1f%%\n', 100 * mean(1 ./ loi.n_causes));
    fprintf(fid, 'Hạng 1: %d/%d = %.1f%% (KTC 95%% %.1f-%.1f)\n', s1, N, 100 * s1 / N, 100 * a1, 100 * b1);
    fprintf(fid, 'Top-3 : %d/%d = %.1f%% (KTC 95%% %.1f-%.1f)\n', s3, N, 100 * s3 / N, 100 * a3, 100 * b3);
    fprintf(fid, 'Chỉ dùng mã lỗi (bỏ luật PID): hạng 1 = %.1f%% (KTC %.1f-%.1f), top-3 = %.1f%% (KTC %.1f-%.1f)\n', ...
        100 * mean(loi.top1_dtc_only), 100 * d1lo, 100 * d1hi, 100 * mean(loi.top3_dtc_only), 100 * d3lo, 100 * d3hi);
    fprintf(fid, 'Xe khỏe có kết luận sai: %.1f%% (%d/%d)\n', 100 * baoSai, sum(khoe.n_causes > 0), height(khoe));
    fprintf(fid, 'Khứ hồi REQ 03 -> RSP 03 (ms): TB %.3f, độ lệch chuẩn %.3f, trung vị %.3f, P95 %.3f, lớn nhất %.3f, nhỏ nhất %.3f; ngoại lai %d\n', L{'req03_ms', :}, nNgoaiLai);
    fprintf(fid, 'Khứ hồi INJECT (ms): TB %.3f, độ lệch chuẩn %.3f, trung vị %.3f, P95 %.3f\n', L{'inject_ms', 1:4});
    fprintf(fid, 'Thời gian suy luận (µs): TB %.1f, trung vị %.1f, P95 %.1f, lớn nhất %.1f\n', L{'infer_us', 1}, L{'infer_us', 3}, L{'infer_us', 4}, L{'infer_us', 5});
    fclose(fid);

    % ---- Hình 1: độ chính xác theo kịch bản ----
    f = figure('Visible', 'off', 'Position', [100 100 900 420], 'Color', 'w');
    x = 1:k; w = 0.38;
    b = bar(x, [t1, t3], 'grouped'); b(1).FaceColor = [0.18 0.45 0.78]; b(2).FaceColor = [0.93 0.62 0.20];
    hold on;
    errorbar(x - w / 2.2, t1, t1 - lo1, hi1 - t1, 'k', 'LineStyle', 'none', 'CapSize', 4);
    errorbar(x + w / 2.2, t3, t3 - lo3, hi3 - t3, 'k', 'LineStyle', 'none', 'CapSize', 4);
    h1 = yline(chance1, '--', 'Color', [0.18 0.45 0.78], 'LineWidth', 1.4);
    h3 = yline(chance3, '--', 'Color', [0.93 0.62 0.20], 'LineWidth', 1.4);
    hu = plot(x - w / 2.2, uv, 'kd', 'MarkerFaceColor', 'w', 'MarkerSize', 7);
    hold off;
    set(gca, 'XTick', x, 'XTickLabel', arrayfun(@(i) sprintf('KB%d', ids(i)), 1:k, 'UniformOutput', false), 'FontSize', 11);
    ylim([0 1.1]); yticks(0:0.2:1); yticklabels(compose('%d %%', 0:20:100)); ylabel('Tỉ lệ nguyên nhân đúng'); xlabel('Kịch bản');
    legend([b(1) b(2) h1 h3 hu], {'Hạng 1', 'Trong 3 hạng đầu', vi(sprintf('Đoán ngẫu nhiên hạng 1 trong 17 nguyên nhân (%.1f %%)', 100 * chance1)), ...
        vi(sprintf('Đoán ngẫu nhiên top-3 trong 17 nguyên nhân (%.1f %%)', 100 * chance3)), 'Đoán ngẫu nhiên hạng 1 trong ứng viên của mã lỗi'}, ...
        'Location', 'southoutside', 'Orientation', 'horizontal', 'NumColumns', 2);
    title(sprintf('Độ chính xác chẩn đoán theo kịch bản (%d lần mỗi kịch bản, khoảng tin cậy Wilson 95%%)', round(mean(n))), 'FontSize', 11);
    grid on; box on;
    exportgraphics(f, fullfile(hinhDir, 'm-do-chinh-xac.png'), 'Resolution', 200); close(f);

    % ---- Hình 2: có và không dùng dữ liệu sống (PID) ----
    f = figure('Visible', 'off', 'Position', [100 100 900 420], 'Color', 'w');
    b = bar(x, [t1d, t1], 'grouped'); b(1).FaceColor = [0.70 0.70 0.72]; b(2).FaceColor = [0.18 0.45 0.78];
    set(gca, 'XTick', x, 'XTickLabel', arrayfun(@(i) sprintf('KB%d', ids(i)), 1:k, 'UniformOutput', false), 'FontSize', 11);
    ylim([0 1.1]); yticks(0:0.2:1); yticklabels(compose('%d %%', 0:20:100)); ylabel('Tỉ lệ đúng ở hạng 1'); xlabel('Kịch bản');
    legend({'Chỉ dùng mã lỗi', 'Mã lỗi và dữ liệu sống (PID)'}, 'Location', 'southoutside', 'Orientation', 'horizontal');
    title('Đóng góp của dữ liệu sống vào độ chính xác hạng 1', 'FontSize', 11);
    grid on; box on;
    exportgraphics(f, fullfile(hinhDir, 'm-so-sanh-pid.png'), 'Resolution', 200); close(f);

    % ---- Hình 3: phân bố độ trễ khứ hồi REQ 03 ----
    f = figure('Visible', 'off', 'Position', [100 100 900 380], 'Color', 'w');
    edges = linspace(min(lat), min(max(lat), prctile_(lat, 99.5)), 41);
    cnt = histcounts(lat, edges);
    bar((edges(1:end-1) + edges(2:end)) / 2, cnt, 1, 'FaceColor', [0.18 0.45 0.78], 'EdgeColor', 'w');
    hold on;
    hm = xline(median(lat), '--k', 'LineWidth', 1.5);
    hp = xline(prctile_(lat, 95), '--r', 'LineWidth', 1.5);
    hold off;
    legend([hm hp], {vi(sprintf('Trung vị %.2f ms', median(lat))), vi(sprintf('P95 %.2f ms', prctile_(lat, 95)))}, 'Location', 'northeast');
    xlabel('Độ trễ khứ hồi REQ 03 → RSP 03 (ms)'); ylabel('Số lần đo');
    title(sprintf('Phân bố độ trễ đọc mã lỗi (%d lần đo)', numel(lat)), 'FontSize', 11);
    grid on; box on; set(gca, 'FontSize', 11);
    exportgraphics(f, fullfile(hinhDir, 'm-do-tre.png'), 'Resolution', 200); close(f);

    disp(fileread(fullfile(outDir, 'tom_tat_thuc_nghiem.txt')));
end

% Số thập phân kiểu Việt (dấu phẩy) cho nhãn trên hình
function s = vi(s)
    s = strrep(s, '.', ',');
end

% Khoảng tin cậy Wilson 95% cho tỉ lệ s/n
function [lo, hi] = wilson(s, n)
    z = 1.959964; p = s / n;
    c = (p + z^2 / (2 * n)) / (1 + z^2 / n);
    h = z * sqrt(p * (1 - p) / n + z^2 / (4 * n^2)) / (1 + z^2 / n);
    lo = max(0, c - h); hi = min(1, c + h);
end

% Phân vị theo nội suy tuyến tính (thay prctile của Statistics Toolbox)
function v = prctile_(x, p)
    x = sort(x(:)); n = numel(x);
    pos = 1 + (n - 1) * p / 100; lo = floor(pos); hi = ceil(pos);
    v = x(lo) + (pos - lo) * (x(hi) - x(lo));
end

% Độ lệch tuyệt đối trung vị
function m = mad_(x)
    m = median(abs(x - median(x)));
end
