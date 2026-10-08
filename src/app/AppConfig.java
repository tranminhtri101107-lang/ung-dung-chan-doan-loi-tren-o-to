package app;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/** Đọc config.properties (cùng thư mục chạy). Thiếu file hoặc thiếu khóa thì dùng giá trị mặc định. */
public final class AppConfig {

    private static final String CONFIG_FILE = "config.properties";

    private final Properties props = new Properties();

    public AppConfig() {
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
        } catch (IOException e) {
            // chưa có file: dùng mặc định
        }
    }

    public String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue).trim();
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
