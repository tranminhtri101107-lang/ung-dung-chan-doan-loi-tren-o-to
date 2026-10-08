package dao;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Mở kết nối tới SQL Server. Thông tin kết nối đọc từ config.properties
 * (không ghi cứng trong code, và file đó không đưa lên git).
 * Mẫu cấu hình: config.example.properties.
 */
public class Database {

    private static final String CONFIG_FILE = "config.properties";

    public Connection getConnection() throws SQLException {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            p.load(in);
        } catch (IOException e) {
            throw new SQLException("Không đọc được " + CONFIG_FILE
                    + " (hãy sao chép từ config.example.properties và điền mật khẩu)", e);
        }
        String host = p.getProperty("db.host", "localhost");
        String port = p.getProperty("db.port", "1433");
        String name = p.getProperty("db.name", "ChanDoanXe");
        String user = p.getProperty("db.user");
        String password = p.getProperty("db.password");
        if (user == null || password == null || password.isBlank()) {
            throw new SQLException("Thiếu db.user hoặc db.password trong " + CONFIG_FILE);
        }
        // trustServerCertificate=true chỉ dùng cho SQL Server cục bộ khi phát triển
        String url = "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + name
                + ";encrypt=true;trustServerCertificate=true;loginTimeout=5";
        return DriverManager.getConnection(url, user, password);
    }
}
