package model;

/** Một nguyên nhân khả dĩ của lỗi (bảng NguyenNhan), kèm gợi ý kiểm tra cho kỹ thuật viên. */
public record Cause(int id, String name, String hint) {
}
