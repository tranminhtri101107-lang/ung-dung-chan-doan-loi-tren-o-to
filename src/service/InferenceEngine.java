package service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import model.Cause;
import model.Diagnosis;
import model.Rule;
import model.RuleBase;

/**
 * Bộ suy luận tiến (forward chaining) dựa trên luật, mục 1.3 và 2.5 của báo cáo.
 * Lớp này thuần tính toán: không đọc CSDL, không dính giao diện, nên kiểm thử độc lập được.
 *
 * Các bước: (1) duyệt mọi luật, giữ luật mà TẤT CẢ điều kiện đều đúng với dữ liệu hiện tại;
 * (2) gom các luật khớp theo nguyên nhân và gộp độ tin cậy theo công thức hệ số tin cậy của MYCIN:
 * c = c1 + c2 x (1 - c1), nên điểm luôn nằm trong (0, 1) và tăng khi có thêm chứng cứ;
 * (3) xếp hạng giảm dần theo điểm, điểm bằng nhau thì theo mã nguyên nhân tăng dần để kết quả ổn định.
 * Không luật nào khớp thì trả danh sách rỗng ("chưa đủ cơ sở để kết luận").
 */
public class InferenceEngine {

    private final RuleBase base;

    public InferenceEngine(RuleBase base) {
        this.base = base;
    }

    /** Công thức gộp hai hệ số tin cậy độc lập c1, c2 trong [0, 1]. */
    public static double combine(double c1, double c2) {
        return c1 + c2 * (1 - c1);
    }

    /**
     * @param storedDtcs mã lỗi đang lưu trong ECU
     * @param pidValues  giá trị vật lý mới nhất của các PID (khóa là mã PID, ví dụ 0x0C)
     */
    public List<Diagnosis> diagnose(Set<String> storedDtcs, Map<Integer, Double> pidValues) {
        Map<Integer, Double> score = new LinkedHashMap<>();
        Map<Integer, List<Rule>> fired = new LinkedHashMap<>();
        for (Rule r : base.rules()) {
            if (r.fires(storedDtcs, pidValues)) {
                score.merge(r.causeId(), r.confidence(), InferenceEngine::combine);
                fired.computeIfAbsent(r.causeId(), k -> new ArrayList<>()).add(r);
            }
        }
        List<Diagnosis> result = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : score.entrySet()) {
            Cause cause = base.causes().get(e.getKey());
            if (cause != null) {
                result.add(new Diagnosis(cause, e.getValue(), fired.get(e.getKey())));
            }
        }
        result.sort(Comparator.comparingDouble(Diagnosis::confidence).reversed()
                .thenComparingInt(d -> d.cause().id()));
        return result;
    }
}
