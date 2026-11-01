package server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import model.Player;

public final class Account {
    // Account giữ thông tin xác thực và thống kê trong RAM; các trường final không đổi sau khi tạo.
    // Bản demo vẫn lưu mật khẩu rõ ở MySQL; sau chuyển đổi, salt/hash chỉ được tạo tạm trong RAM.
    private final String username;
    private final String salt;
    private final String passwordHash;
    private final int rank, wins, treasures, matches;

    // ===== KIỂM TRA VÀ KHỞI TẠO HỒ SƠ TÀI KHOẢN TRONG RAM =====
    public Account(String username, String salt, String passwordHash, int rank, int wins, int treasures, int matches) {
        if (username == null || username.trim().isEmpty() || salt == null || !salt.matches("[0-9a-f]{32}")
                || passwordHash == null || !passwordHash.matches("[0-9a-f]{64}")
                || rank < 0 || wins < 0 || treasures < 0 || matches < 0) {
            throw new IllegalArgumentException("Invalid account record");
        }
        this.username = username;
        this.salt = salt;
        this.passwordHash = passwordHash;
        this.rank = rank; this.wins = wins; this.treasures = treasures; this.matches = matches;
    }

    // ===== TẠO SALT VÀ HASH MẬT KHẨU CHO ĐỐI TƯỢNG ACCOUNT =====
    public static Account register(String username, String password) {
        // Mỗi tài khoản có salt ngẫu nhiên 16 byte; dạng hex dài 32 ký tự.
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String encodedSalt = HexFormat.of().formatHex(salt);
        // Tạo đối tượng Account với thống kê bằng 0; hàm này không tự chạy câu INSERT vào database.
        return new Account(username, encodedSalt, hash(encodedSalt, password), 0, 0, 0, 0);
    }

    // ===== BĂM MẬT KHẨU BẰNG SHA-256 KẾT HỢP SALT =====
    private static String hash(String salt, String password) {
        // Tính SHA-256 trên salt dạng byte nối với mật khẩu UTF-8, rồi trả kết quả hex 64 ký tự.
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(HexFormat.of().parseHex(salt));
            return HexFormat.of().formatHex(digest.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Java must support SHA-256", e);
        }
    }

    // ===== KIỂM TRA MẬT KHẨU KHI ĐĂNG NHẬP =====
    public boolean verifyPassword(String password) {
        // Băm mật khẩu vừa nhập bằng cùng salt rồi so sánh hai hash; không giải ngược hash cũ.
        return password != null && MessageDigest.isEqual(HexFormat.of().parseHex(passwordHash),
                HexFormat.of().parseHex(hash(salt, password)));
    }

    // ===== TẠO PLAYER CHO PHIÊN ĐĂNG NHẬP TỪ HỒ SƠ TÀI KHOẢN =====
    public Player createPlayer() {
        // Sao chép hồ sơ thành Player cho phiên đăng nhập; trạng thái online/vị trí thuộc phiên chơi.
        Player player = new Player();
        player.setUsername(username);
        player.setTotalRankPoint(rank); player.setWins(wins);
        player.setTotalTreasureCollected(treasures); player.setTotalMatches(matches);
        return player;
    }

    // ===== TẠO BẢN HỒ SƠ MỚI VỚI THỐNG KÊ SAU TRẬN =====
    public Account withStatistics(Player player) {
        // Account bất biến nên trả đối tượng mới có thống kê vừa cập nhật, giữ nguyên thông tin xác thực.
        if (!username.equals(player.getUsername())) { throw new IllegalArgumentException("Wrong player"); }
        return new Account(username, salt, passwordHash, player.getTotalRankPoint(), player.getWins(),
                player.getTotalTreasureCollected(), player.getTotalMatches());
    }

    public String getUsername() { return username; }
    public String getSalt() { return salt; }
    public String getPasswordHash() { return passwordHash; }
}
