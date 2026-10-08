package model;

/**
 * Hồ sơ một xe trong xưởng. id = 0 khi chưa lưu vào CSDL; các trường khác ngoài biển số có thể null.
 * vin: số khung 17 ký tự; odometerKm: số km hiện tại; owner, phone: chủ xe và số điện thoại liên hệ.
 */
public record Vehicle(int id, String plate, String brand, String model, Integer year, String note,
        String vin, Integer odometerKm, String owner, String phone) {

    /** Tạo xe chỉ với thông tin cơ bản (các trường mới để trống). */
    public Vehicle(int id, String plate, String brand, String model, Integer year, String note) {
        this(id, plate, brand, model, year, note, null, null, null, null);
    }

    /** Chữ hiển thị trên giao diện, ví dụ "51A-123.45 - Toyota Vios". */
    public String label() {
        String name = name();
        return name.isEmpty() ? plate : plate + " - " + name;
    }

    /** Hãng và dòng xe, ví dụ "Toyota Vios" (rỗng nếu chưa nhập). */
    public String name() {
        return ((brand == null ? "" : brand) + " " + (model == null ? "" : model)).trim();
    }

    /** Bản sao với số km mới (dùng khi bảo dưỡng ghi nhận số km lớn hơn). */
    public Vehicle withOdometer(Integer km) {
        return new Vehicle(id, plate, brand, model, year, note, vin, km, owner, phone);
    }
}
