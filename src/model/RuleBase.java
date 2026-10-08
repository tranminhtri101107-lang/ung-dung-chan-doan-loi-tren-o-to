package model;

import java.util.List;
import java.util.Map;

/** Cơ sở tri thức nạp từ CSDL: các nguyên nhân (theo mã) và các luật. */
public record RuleBase(Map<Integer, Cause> causes, List<Rule> rules) {
}
