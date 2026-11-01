package server;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DatabaseConfig {
    // Giữ host, port, tên database và thông tin đăng nhập MySQL; đây không phải tài khoản người chơi.
    private final Properties settings;

    public DatabaseConfig(Properties settings) {
        // Sao chép cấu hình để nơi gọi thay đổi Properties ban đầu không làm đổi kết nối đang sử dụng.
        this.settings = new Properties();
        this.settings.putAll(settings);
    }

    // ===== ĐỌC CẤU HÌNH DATABASE TỪ FILE VÀ BIẾN MÔI TRƯỜNG =====
    public static DatabaseConfig read() throws IOException {
        // Đọc file cấu hình UTF-8; có thể chọn file khác bằng -Dtreasure.db.config.
        Properties properties = new Properties();
        Path file = Path.of(System.getProperty("treasure.db.config", "config/database.properties"));
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
        }
        // Biến môi trường TREASURE_DB_* được ưu tiên hơn giá trị trong file cấu hình.
        for (String key : new String[] {"storage", "host", "port", "database", "user", "password", "sslMode"}) {
            String value = System.getenv("TREASURE_DB_" + key.toUpperCase(java.util.Locale.ROOT));
            if (value != null) { properties.setProperty(key, value); }
        }
        return new DatabaseConfig(properties);
    }

    // ===== CHỌN LƯU DỮ LIỆU BẰNG MYSQL HOẶC FILE TSV =====
    public static DataStore defaultStore() {
        // Chọn lớp lưu trữ MySQL hoặc TSV; nếu đã chọn MySQL mà lỗi thì không tự đổi sang TSV.
        try {
            DatabaseConfig config = read();
            String storage = config.settings.getProperty("storage", "tsv");
            if ("mysql".equalsIgnoreCase(storage)) { return new MySqlDataStore(config); }
            if ("tsv".equalsIgnoreCase(storage)) { return new ServerDataStore(Path.of("data", "server-data.tsv")); }
            throw new IOException("storage must be mysql or tsv");
        } catch (IOException e) { throw new IllegalStateException("Cannot read database configuration", e); }
    }

    // ===== KIỂM TRA TÊN DATABASE TRƯỚC KHI TẠO SQL =====
    public String database() {
        // Tên database là định danh trong SQL nên chỉ chấp nhận chữ, số và dấu gạch dưới trước khi ghép lệnh.
        String name = settings.getProperty("database", "treasure_rush");
        if (!name.matches("[A-Za-z0-9_]{1,64}")) { throw new IllegalArgumentException("Invalid database name"); }
        return name;
    }

    public DatabaseConfig withDatabase(String name) {
        // Tạo cấu hình bản sao với database khác, dùng cho các database kiểm thử riêng biệt.
        Properties copy = new Properties();
        copy.putAll(settings);
        copy.setProperty("database", name);
        return new DatabaseConfig(copy);
    }

    // ===== MỞ KẾT NỐI MYSQL BẰNG JDBC CONNECTOR/J =====
    public Connection connect(boolean selectDatabase) throws SQLException {
        // true: kết nối vào database đã chọn; false: kết nối MySQL trước để có thể chạy CREATE DATABASE.
        String host = settings.getProperty("host", "localhost");
        if (!host.matches("[A-Za-z0-9._-]+")) { throw new SQLException("Invalid MySQL host"); }
        int port;
        try { port = Integer.parseInt(settings.getProperty("port", "3306")); }
        catch (NumberFormatException e) { throw new SQLException("Invalid MySQL port", e); }
        if (port < 1 || port > 65535) { throw new SQLException("Invalid MySQL port"); }
        // Nạp JDBC driver Connector/J; thiếu JAR thì báo lỗi cụ thể thay vì tiếp tục chạy với dữ liệu rỗng.
        try { Class.forName("com.mysql.cj.jdbc.Driver"); }
        catch (ClassNotFoundException e) {
            throw new SQLException("MySQL Connector/J missing. Run setup-mysql.ps1, or put the driver in dist/lib.", e);
        }
        // Truyền user/password MySQL bằng Properties, không ghép mật khẩu vào chuỗi JDBC URL.
        Properties properties = new Properties();
        properties.setProperty("user", settings.getProperty("user", "root"));
        properties.setProperty("password", settings.getProperty("password", ""));
        properties.setProperty("characterEncoding", "UTF-8");
        properties.setProperty("connectionCollation", "utf8mb4_0900_bin");
        // sslMode áp dụng cho kết nối JDBC tới MySQL; không bổ sung TLS cho socket Client-Server của game.
        properties.setProperty("sslMode", settings.getProperty("sslMode", "PREFERRED"));
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "10000");
        // Mở một kết nối mới theo dạng jdbc:mysql://host:port/database; nơi gọi chịu trách nhiệm đóng nó.
        return DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/"
                + (selectDatabase ? database() : ""), properties);
    }
}
