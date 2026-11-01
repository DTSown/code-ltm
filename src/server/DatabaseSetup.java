package server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import model.MatchHistory;

/** Run with the server JAR and its Connector/J dependency on the classpath. */
public final class DatabaseSetup {
    // Công cụ thiết lập: không truyền tham số thì tạo/kiểm tra cấu trúc, truyền một đường dẫn thì nhập TSV cũ.
    // ===== THIẾT LẬP DATABASE VÀ NHẬP DỮ LIỆU TSV CŨ KHI ĐƯỢC YÊU CẦU =====
    public static void main(String[] args) {
        try {
            if (args.length > 1) { throw new IllegalArgumentException("Usage: DatabaseSetup [existing-data.tsv]"); }
            Map<String, Account> accounts = new LinkedHashMap<>();
            Map<String, MatchHistory> history = new LinkedHashMap<>();
            if (args.length == 1) {
                // Đọc TSV vào hai map trước; hàm importEmpty() phía dưới sẽ từ chối nếu MySQL đã có bản ghi.
                Path source = Path.of(args[0]);
                if (!Files.isRegularFile(source)) { throw new IllegalArgumentException("TSV source does not exist: " + source); }
                new ServerDataStore(source).load(accounts, history);
            }
            // Đọc cấu hình JDBC, tạo database nếu thiếu và cập nhật bảng/view theo phiên bản hiện tại.
            MySqlDataStore store = new MySqlDataStore(DatabaseConfig.read());
            store.createDatabase();
            if (args.length == 1) {
                store.importEmpty(accounts, history);
                System.out.println("Imported " + accounts.size() + " accounts and " + history.size() + " matches. Source TSV unchanged.");
            }
            // Đọc lại từ MySQL để xác nhận kết nối/dữ liệu và báo số tài khoản, số trận; không in mật khẩu.
            Map<String, Account> restored = new LinkedHashMap<>();
            Map<String, MatchHistory> matches = new LinkedHashMap<>();
            store.load(restored, matches);
            System.out.println("MySQL ready: " + restored.size() + " accounts, " + matches.size() + " matches.");
        } catch (Exception e) {
            System.err.println("Database setup failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
