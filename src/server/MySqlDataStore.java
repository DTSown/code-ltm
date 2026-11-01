package server;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import model.GameResult;
import model.MatchHistory;
import model.Player;

/** MySQL 8.0+ storage. A single server process owns each database. */
public final class MySqlDataStore implements DataStore {
    // Lớp truy cập MySQL: thực hiện SQL; việc tính thắng/thua và xác thực nằm ở GameRoom/ServerManager/Account.
    private final DatabaseConfig config;

    public MySqlDataStore(DatabaseConfig config) { this.config = config; }

    // ===== TẠO DATABASE MYSQL NẾU CHƯA TỒN TẠI =====
    public void createDatabase() throws IOException {
        // Chưa chọn database khi kết nối vì database có thể chưa tồn tại; UTF-8 hỗ trợ tên/thông báo tiếng Việt.
        try (Connection connection = config.connect(false); Statement sql = connection.createStatement()) {
            sql.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + config.database()
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin");
        } catch (SQLException e) { throw failure("create database", e); }
        initialize();
    }

    // ===== TẠO / CẬP NHẬT CẤU TRÚC BẢNG TÀI KHOẢN VÀ BẢNG TRẬN =====
    @Override public void initialize() throws IOException {
        // Tạo bảng accounts nếu thiếu: username là khóa chính, CHECK ngăn thống kê âm, InnoDB hỗ trợ transaction.
        // Bản demo mới lưu mật khẩu rõ; các cột salt/hash của bản cũ chỉ giữ tạm trong thời gian chuyển đổi.
        try (Connection connection = config.connect(true); Statement sql = connection.createStatement()) {
            sql.executeUpdate("""
                CREATE TABLE IF NOT EXISTS accounts (
                    username VARCHAR(255) NOT NULL PRIMARY KEY,
                    password_plaintext LONGTEXT NULL COMMENT 'Demo only: original password',
                    rank_points INT NOT NULL CHECK (rank_points >= 0),
                    wins INT NOT NULL CHECK (wins >= 0),
                    total_treasures INT NOT NULL CHECK (total_treasures >= 0),
                    total_matches INT NOT NULL CHECK (total_matches >= 0)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin
                """);
            // Cập nhật cấu trúc cũ theo tình trạng thực tế, không xóa rồi tạo lại dữ liệu tài khoản.
            if (!hasColumn(connection, "password_plaintext")) {
                sql.executeUpdate("ALTER TABLE accounts ADD COLUMN password_plaintext LONGTEXT NULL "
                        + "COMMENT 'Demo only: original password' AFTER username");
            }
            // Đổi tên bảng trận cũ để giữ nguyên chi tiết; tên matches sẽ dành cho view dễ đọc.
            if (isBaseTable(connection, "matches") && !isBaseTable(connection, "match_details")) {
                sql.executeUpdate("RENAME TABLE matches TO match_details");
            }
            // ===== TẠO BẢNG LƯU ĐẦY ĐỦ CHI TIẾT TRẬN ĐẤU =====
            // room_id chống trùng trận; khóa ngoại yêu cầu cả hai người thuộc accounts; CHECK kiểm tra dữ liệu hợp lệ.
            sql.executeUpdate("""
                CREATE TABLE IF NOT EXISTS match_details (
                    room_id VARCHAR(128) NOT NULL PRIMARY KEY,
                    player1 VARCHAR(255) NOT NULL,
                    player2 VARCHAR(255) NOT NULL,
                    winner VARCHAR(4) NOT NULL CHECK (winner IN ('P1', 'P2', 'DRAW')),
                    score1 INT NOT NULL CHECK (score1 >= 0),
                    score2 INT NOT NULL CHECK (score2 >= 0),
                    collected1 INT NOT NULL CHECK (collected1 >= 0),
                    collected2 INT NOT NULL CHECK (collected2 >= 0),
                    rank_gain1 INT NOT NULL,
                    rank_gain2 INT NOT NULL,
                    reason VARCHAR(16) NOT NULL CHECK (reason IN ('TIME_UP', 'PLAYER_EXIT', 'DISCONNECT')),
                    ended_at_epoch_millis BIGINT NOT NULL CHECK (ended_at_epoch_millis >= 0),
                    duration_seconds INT NOT NULL CHECK (duration_seconds BETWEEN 0 AND 180),
                    FOREIGN KEY (player1) REFERENCES accounts(username),
                    FOREIGN KEY (player2) REFERENCES accounts(username),
                    INDEX history_player1 (player1, ended_at_epoch_millis),
                    INDEX history_player2 (player2, ended_at_epoch_millis),
                    CHECK (player1 <> player2)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin
                """);
            // ===== TẠO VIEW PHÒNG GỒM 5 CỘT DỄ ĐỌC =====
            // View không chép dữ liệu: lấy chi tiết trận và trình bày đúng 5 cột tên phòng/người/rank/rương/người thắng.
            // CASE đổi P1/P2 thành tên thật; CONCAT ghép thông tin; ROW_NUMBER đặt tên cho các phòng UUID cũ.
            sql.executeUpdate("""
                CREATE OR REPLACE VIEW matches AS
                SELECT
                    CASE WHEN room_id REGEXP '^ROOM-[1-9][0-9]*-[0-9a-fA-F-]{36}$'
                        THEN CONCAT('Phòng ', SUBSTRING_INDEX(SUBSTRING(room_id, 6), '-', 1))
                        ELSE CONCAT('Phòng cũ ', ROW_NUMBER() OVER (
                            PARTITION BY room_id REGEXP '^ROOM-[1-9][0-9]*-[0-9a-fA-F-]{36}$'
                            ORDER BY ended_at_epoch_millis, room_id)) END AS ten_phong,
                    CONCAT(player1, ' / ', player2) AS nguoi_tham_gia,
                    CONCAT(player1, ': +', rank_gain1, ' / ', player2, ': +', rank_gain2) AS diem_rank,
                    CONCAT(player1, ': ', collected1, ' / ', player2, ': ', collected2) AS ruong_da_an,
                    CASE winner WHEN 'P1' THEN player1 WHEN 'P2' THEN player2 ELSE 'Hòa' END AS nguoi_thang
                FROM match_details
                ORDER BY ended_at_epoch_millis DESC, room_id
                """);
            finishCredentialMigration(connection);
        } catch (SQLException e) { throw failure("initialize tables", e); }
    }

    // ===== ĐỌC TÀI KHOẢN VÀ LỊCH SỬ TỪ MYSQL VÀO RAM =====
    @Override public void load(Map<String, Account> accounts, Map<String, MatchHistory> history) throws IOException {
        // Đọc vào map tạm trước; chỉ công bố cho ServerManager khi cả tài khoản và lịch sử đã kiểm tra xong.
        Map<String, Account> loadedAccounts = new LinkedHashMap<>();
        Map<String, MatchHistory> loadedHistory = new LinkedHashMap<>();
        try (Connection connection = config.connect(true)) {
            boolean legacyCredentials = hasColumn(connection, "salt") && hasColumn(connection, "password_hash");
            String historyTable = isBaseTable(connection, "match_details") ? "match_details" : "matches";
            // Hai lần SELECT nằm trong cùng transaction đọc để thấy ảnh dữ liệu nhất quán của hai bảng.
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                try (Statement sql = connection.createStatement(); ResultSet rows = sql.executeQuery("SELECT * FROM accounts")) {
                    while (rows.next()) {
                        Account account;
                        if (legacyCredentials) {
                            // Trong giai đoạn chuyển đổi, Account tiếp tục xác thực bằng salt/hash gốc.
                            account = new Account(rows.getString("username"), rows.getString("salt"),
                                    rows.getString("password_hash"), rows.getInt("rank_points"), rows.getInt("wins"),
                                    rows.getInt("total_treasures"), rows.getInt("total_matches"));
                        } else {
                            // MySQL đã bỏ salt/hash: đọc mật khẩu rõ rồi tạo hash tạm trong RAM, không INSERT tài khoản mới.
                            String password = rows.getString("password_plaintext");
                            if (password == null) { throw new SQLException("An account has no saved password"); }
                            // Hashes exist only in memory after the legacy database columns are removed.
                            account = Account.register(rows.getString("username"), password);
                            Player player = account.createPlayer();
                            player.setTotalRankPoint(rows.getInt("rank_points")); player.setWins(rows.getInt("wins"));
                            player.setTotalTreasureCollected(rows.getInt("total_treasures"));
                            player.setTotalMatches(rows.getInt("total_matches")); account = account.withStatistics(player);
                        }
                        loadedAccounts.put(account.getUsername(), account);
                    }
                }
                // Chuyển từng dòng SQL thành GameResult/MatchHistory; từ chối lịch sử tham chiếu người không tồn tại.
                try (Statement sql = connection.createStatement(); ResultSet rows = sql.executeQuery("SELECT * FROM " + historyTable)) {
                    while (rows.next()) {
                        GameResult result = new GameResult(rows.getString("room_id"), rows.getString("player1"),
                                rows.getString("player2"), rows.getString("winner"), rows.getInt("score1"), rows.getInt("score2"),
                                rows.getInt("collected1"), rows.getInt("collected2"), rows.getInt("rank_gain1"),
                                rows.getInt("rank_gain2"), rows.getString("reason"));
                        if (!loadedAccounts.containsKey(result.getPlayer1Username())
                                || !loadedAccounts.containsKey(result.getPlayer2Username())) {
                            throw new IllegalArgumentException("Match references missing account");
                        }
                        loadedHistory.put(result.getRoomId(), new MatchHistory(result,
                                rows.getLong("ended_at_epoch_millis"), rows.getInt("duration_seconds")));
                    }
                }
                connection.commit();
            } catch (SQLException | IllegalArgumentException e) { rollback(connection, e); throw e; }
        } catch (SQLException | IllegalArgumentException e) { throw failure("load data", e); }
        // Chỉ sau khi toàn bộ phần đọc thành công mới đưa dữ liệu vào các map chính đang được Server sử dụng.
        accounts.putAll(loadedAccounts);
        history.putAll(loadedHistory);
    }

    // ===== LƯU THỐNG KÊ VÀ LỊCH SỬ SAU KHI DỮ LIỆU THAY ĐỔI =====
    @Override public void save(Map<String, Account> accounts, Map<String, MatchHistory> history) throws IOException {
        // Lưu thông thường sau thay đổi thống kê/kết thúc trận; không cần nhận mật khẩu mới.
        saveSnapshot(accounts, history, null, null);
    }

    // ===== LƯU ĐĂNG KÝ TÀI KHOẢN MỚI =====
    @Override public void saveRegistration(Map<String, Account> accounts, Map<String, MatchHistory> history,
            String username, String password) throws IOException {
        // Lưu tài khoản mới và mật khẩu rõ trong cùng transaction để chỉ báo thành công khi cả hai đã được ghi.
        saveSnapshot(accounts, history, username, password);
    }

    // ===== GHI DỮ LIỆU TRONG TRANSACTION: COMMIT HOẶC ROLLBACK =====
    private void saveSnapshot(Map<String, Account> accounts, Map<String, MatchHistory> history,
            String username, String password) throws IOException {
        // Tắt auto-commit: ghi tài khoản + lịch sử cùng nhau; thành công commit, lỗi rollback phần chưa xác nhận.
        try (Connection connection = config.connect(true)) {
            connection.setAutoCommit(false);
            try {
                write(connection, accounts, history, username);
                if (username != null) { writeDemoPassword(connection, username, password); }
                connection.commit();
                connection.setAutoCommit(true);
                // Kiểm tra dọn salt/hash sau khi đã commit, tránh lệnh ALTER tự xác nhận một transaction còn dang dở.
                finishCredentialMigration(connection);
            } catch (SQLException e) { rollback(connection, e); throw e; }
        } catch (SQLException e) {
            if (username != null) {
                throw new IOException("MySQL could not save registration (error " + e.getErrorCode() + ")");
            }
            throw failure("save data", e);
        }
    }

    // ===== BỔ SUNG MẬT KHẨU RÕ SAU KHI ĐĂNG NHẬP ĐÚNG =====
    @Override public void recordSuccessfulLogin(String username, String password) throws IOException {
        // ServerManager chỉ gọi sau khi mật khẩu đúng; ghi lại mật khẩu gốc rồi thử hoàn tất chuyển đổi cấu trúc cũ.
        try (Connection connection = config.connect(true)) {
            writeDemoPassword(connection, username, password);
            finishCredentialMigration(connection);
        } catch (SQLException e) {
            // Some JDBC errors include bound values; retain only the error code here.
            throw new IOException("MySQL could not save demo login data (error " + e.getErrorCode() + ")");
        }
    }

    // ===== CẬP NHẬT CỘT MẬT KHẨU RÕ BẰNG PREPAREDSTATEMENT =====
    private void writeDemoPassword(Connection connection, String username, String password) throws SQLException {
        // Dấu ? là tham số của PreparedStatement; giá trị mật khẩu/username được truyền riêng bằng setString().
        try (PreparedStatement sql = connection.prepareStatement(
                "UPDATE accounts SET password_plaintext=? WHERE username=?")) {
            sql.setString(1, password);
            sql.setString(2, username);
            if (sql.executeUpdate() != 1) { throw new SQLException("Account missing when saving demo password"); }
        }
    }

    /** Explicit migration only: refuses to overwrite any existing database records. */
    // ===== NHẬP TÀI KHOẢN VÀ LỊCH SỬ TSV VÀO DATABASE TRỐNG =====
    public void importEmpty(Map<String, Account> accounts, Map<String, MatchHistory> history) throws IOException {
        // Chỉ nhập TSV vào bảng trống; bản TSV cũ có hash nên phải giữ tạm thông tin này để còn đăng nhập được.
        try (Connection connection = config.connect(true)) {
            requireEmpty(connection);
            if (!accounts.isEmpty() && !hasColumn(connection, "salt")) {
                // Old TSV files contain hashes only; retain them until each account logs in.
                try (Statement sql = connection.createStatement()) {
                    sql.executeUpdate("ALTER TABLE accounts ADD COLUMN salt CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL, "
                            + "ADD COLUMN password_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL");
                }
            }
            // Nhập trong transaction với mức cô lập chặt; kiểm tra trống lại trước khi ghi để tránh nhập đè.
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);
            try {
                requireEmpty(connection);
                write(connection, accounts, history, null);
                connection.commit();
            } catch (SQLException e) { rollback(connection, e); throw e; }
        } catch (SQLException e) { throw failure("import TSV", e); }
    }

    // ===== THỰC THI SQL THÊM / CẬP NHẬT TÀI KHOẢN VÀ LƯU TRẬN =====
    private void write(Connection connection, Map<String, Account> accounts, Map<String, MatchHistory> history,
            String registeringUsername) throws SQLException {
        // Chọn câu INSERT phù hợp cấu trúc trước/sau khi bỏ salt/hash, không tự thêm lại cột đã xóa.
        boolean legacy = hasColumn(connection, "salt") && hasColumn(connection, "password_hash");
        if (!legacy) {
            // Ngoài tài khoản đang đăng ký, mọi tài khoản được lưu phải đã có mật khẩu rõ trong MySQL.
            Set<String> knownPasswords = new HashSet<>();
            try (Statement sql = connection.createStatement();
                    ResultSet rows = sql.executeQuery("SELECT username FROM accounts WHERE password_plaintext IS NOT NULL")) {
                while (rows.next()) { knownPasswords.add(rows.getString(1)); }
            }
            for (String username : accounts.keySet()) {
                if (!username.equals(registeringUsername) && !knownPasswords.contains(username)) {
                    throw new SQLException("Account without a known password; import legacy snapshots using importEmpty");
                }
            }
        }
        // ON DUPLICATE KEY UPDATE: username mới thì INSERT, đã có thì cập nhật thống kê từ dòng incoming.
        String accountSql = legacy ? """
                INSERT INTO accounts (username, salt, password_hash, rank_points, wins, total_treasures, total_matches)
                VALUES (?, ?, ?, ?, ?, ?, ?) AS incoming
                ON DUPLICATE KEY UPDATE salt=incoming.salt, password_hash=incoming.password_hash,
                    rank_points=incoming.rank_points, wins=incoming.wins,
                    total_treasures=incoming.total_treasures, total_matches=incoming.total_matches
                """ : """
                INSERT INTO accounts (username, rank_points, wins, total_treasures, total_matches)
                VALUES (?, ?, ?, ?, ?) AS incoming
                ON DUPLICATE KEY UPDATE rank_points=incoming.rank_points, wins=incoming.wins,
                    total_treasures=incoming.total_treasures, total_matches=incoming.total_matches
                """;
        // Gán tham số từng tài khoản; addBatch gom lệnh và executeBatch thực thi nhóm trong transaction của nơi gọi.
        try (PreparedStatement sql = connection.prepareStatement(accountSql)) {
            for (Account account : accounts.values()) {
                Player player = account.createPlayer();
                int field = 1;
                sql.setString(field++, account.getUsername());
                if (legacy) { sql.setString(field++, account.getSalt()); sql.setString(field++, account.getPasswordHash()); }
                sql.setInt(field++, player.getTotalRankPoint()); sql.setInt(field++, player.getWins());
                sql.setInt(field++, player.getTotalTreasureCollected()); sql.setInt(field, player.getTotalMatches()); sql.addBatch();
            }
            sql.executeBatch();
        }
        // Lịch sử bất biến: room_id đã có thì giữ bản ghi cũ, không cộng/lưu lại kết quả của một trận lần nữa.
        try (PreparedStatement sql = connection.prepareStatement("""
                INSERT INTO match_details (room_id, player1, player2, winner, score1, score2, collected1, collected2,
                    rank_gain1, rank_gain2, reason, ended_at_epoch_millis, duration_seconds)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) AS incoming
                ON DUPLICATE KEY UPDATE room_id=incoming.room_id
                """)) {
            for (MatchHistory match : history.values()) {
                GameResult result = match.getResult();
                sql.setString(1, result.getRoomId()); sql.setString(2, result.getPlayer1Username());
                sql.setString(3, result.getPlayer2Username()); sql.setString(4, result.getWinner());
                sql.setInt(5, result.getPlayer1Score()); sql.setInt(6, result.getPlayer2Score());
                sql.setInt(7, result.getPlayer1Collected()); sql.setInt(8, result.getPlayer2Collected());
                sql.setInt(9, result.getPlayer1RankGain()); sql.setInt(10, result.getPlayer2RankGain());
                sql.setString(11, result.getReason()); sql.setLong(12, match.getEndedAtEpochMillis());
                sql.setInt(13, match.getDurationSeconds()); sql.addBatch();
            }
            sql.executeBatch();
        }
    }

    private static boolean hasColumn(Connection connection, String name) throws SQLException {
        // Đọc metadata để kiểm tra cột tồn tại, phục vụ cập nhật cấu trúc mà không làm mất dữ liệu.
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "accounts", name)) {
            return columns.next();
        }
    }

    private static boolean isBaseTable(Connection connection, String name) throws SQLException {
        // information_schema chứa thông tin hệ thống; BASE TABLE giúp phân biệt bảng thật với view cùng tên.
        try (PreparedStatement sql = connection.prepareStatement("SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name=? AND table_type='BASE TABLE'")) {
            sql.setString(1, name);
            try (ResultSet rows = sql.executeQuery()) { return rows.next(); }
        }
    }

    private static void requireEmpty(Connection connection) throws SQLException {
        // Hai bảng phải có COUNT(*) bằng 0; chỉ cần một bảng đã có dữ liệu thì từ chối thao tác nhập TSV.
        for (String table : new String[] {"accounts", "match_details"}) {
            try (Statement sql = connection.createStatement(); ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM " + table)) {
                rows.next();
                if (rows.getLong(1) != 0) { throw new SQLException("Import requires empty accounts and match_details tables"); }
            }
        }
    }

    /** Drop old database credentials only when every original password is known and verified. */
    // ===== XÓA SALT / HASH KHI MỌI MẬT KHẨU CŨ ĐÃ ĐƯỢC XÁC NHẬN =====
    private static void finishCredentialMigration(Connection connection) {
        // Chỉ bỏ salt/hash khi tất cả mật khẩu rõ đều có và khớp hash gốc; một tài khoản chưa đủ thì giữ nguyên.
        try {
            if (!connection.getAutoCommit() || !hasColumn(connection, "salt") || !hasColumn(connection, "password_hash")) { return; }
            try (Statement sql = connection.createStatement(); ResultSet rows = sql.executeQuery(
                    "SELECT username, salt, password_hash, password_plaintext FROM accounts")) {
                while (rows.next()) {
                    String password = rows.getString("password_plaintext");
                    if (password == null) { return; }
                    Account legacy = new Account(rows.getString("username"), rows.getString("salt"),
                            rows.getString("password_hash"), 0, 0, 0, 0);
                    if (!legacy.verifyPassword(password)) { return; }
                }
            }
            try (Statement sql = connection.createStatement()) {
                // Đã kiểm tra đủ mật khẩu nên có thể xóa cả hai cột; việc xác thực sau restart dùng hash tạm trong RAM.
                sql.executeUpdate("ALTER TABLE accounts DROP COLUMN salt, DROP COLUMN password_hash");
            }
        } catch (SQLException | IllegalArgumentException e) {
            // Cleanup is retried on the next login/save/start; a committed login stays successful.
            System.err.println("Legacy credential cleanup is pending; no passwords were logged.");
        }
    }

    // ===== HOÀN TÁC TRANSACTION KHI GHI DỮ LIỆU LỖI =====
    private static void rollback(Connection connection, Exception cause) {
        // Hoàn tác các lệnh chưa commit; nếu rollback cũng lỗi thì gắn vào lỗi gốc để không mất nguyên nhân ban đầu.
        try { connection.rollback(); } catch (SQLException e) { cause.addSuppressed(e); }
    }

    private static IOException failure(String operation, Exception cause) {
        // Đưa lỗi JDBC về kiểu lỗi lưu trữ dùng chung với DataStore, kèm thao tác đang thực hiện.
        return new IOException("MySQL could not " + operation + ": " + cause.getMessage(), cause);
    }
}
