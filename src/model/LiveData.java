package model;

/** Một mẫu dữ liệu sống (OBD-II Mode 01) tại một thời điểm. */
public record LiveData(int rpm, int speedKmh, int coolantC, int throttlePct) {
}
