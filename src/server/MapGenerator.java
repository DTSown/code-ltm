package server;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import model.GameState;
import model.Player;
import model.Position;
import model.Treasure;

/**
 * [PHẦN PHỤ TRÁCH: BẢN ĐỒ]
 *
 * Server là nơi duy nhất sinh bản đồ. Client không tự sinh tường, vị trí xuất phát
 * hay rương; client chỉ nhận các dữ liệu này qua GAME_STATE để vẽ. Cách làm này
 * bảo đảm hai người chơi nhìn thấy cùng một bản đồ và không thể sửa map ở client.
 */
public class MapGenerator {
    // Bản đồ dùng 49 ô: số thứ tự cell = row * SIZE + col; CENTER = 24 tương ứng ô (3, 3).
    private static final int SIZE = GameState.MAP_SIZE;
    private static final int CENTER = SIZE * SIZE / 2;
    private final Random random;

    public MapGenerator() {
        this(new Random());
    }

    public MapGenerator(Random random) {
        this.random = random;
    }

    // ===== SINH BẢN ĐỒ NGẪU NHIÊN, VỊ TRÍ XUẤT PHÁT VÀ 5 RƯƠNG BAN ĐẦU =====
    public GameState generate(String roomId, Player player1, Player player2) {
        // Mỗi lượt dựng một bản đồ mới; không đạt điều kiện thì continue, tối đa 1000 lượt trong hàm này.
        for (int attempt = 0; attempt < 1000; attempt++) {
            // int mặc định bằng 0: ô đường đi. Giá trị 1 sẽ được đặt cho vật cản.
            int[][] map = new int[SIZE][SIZE];
            List<Integer> pairs = new ArrayList<>();
            // Chỉ lấy nửa đầu (0..23) làm đại diện; ô đối diện ở nửa còn lại, ô giữa không bị chọn làm tường.
            for (int cell = 0; cell < CENTER; cell++) {
                pairs.add(cell);
            }
            // Trộn danh sách giúp chọn ngẫu nhiên 4 cặp khác nhau, không đặt trùng vật cản.
            Collections.shuffle(pairs, random);
            // Four mirrored pairs give eight obstacles (8/49 = 16.3%).
            // ===== ĐẶT ĐÚNG 8 VẬT CẢN THÀNH 4 CẶP ĐỐI XỨNG QUA TÂM =====
            for (int i = 0; i < 4; i++) {
                int cell = pairs.get(i);
                map[cell / SIZE][cell % SIZE] = 1;
                int opposite = opposite(cell);
                map[opposite / SIZE][opposite % SIZE] = 1;
            }
            // BFS phải đi tới tất cả 41 ô trống; nếu bản đồ bị chia thành vùng kín thì thử lại.
            if (!isConnected(map)) {
                continue;
            }

            // ===== CHỌN HAI Ô XUẤT PHÁT ĐỐI XỨNG VÀ ĐỦ XA NHAU =====
            List<Integer> spawnCells = new ArrayList<>();
            for (int cell = 0; cell < SIZE * SIZE; cell++) {
                if (map[cell / SIZE][cell % SIZE] == 0 && distance(cell, opposite(cell)) >= 5) {
                    spawnCells.add(cell);
                }
            }
            if (spawnCells.isEmpty()) {
                continue;
            }
            int spawn1 = spawnCells.get(random.nextInt(spawnCells.size()));
            // Map đối xứng nên ô đối diện của một ô trống cũng trống; distance() tính số bước Manhattan.
            int spawn2 = opposite(spawn1);

            // ===== CHỌN VỊ TRÍ RƯƠNG TRÊN Ô TRỐNG, CÁCH MỖI NGƯỜI ÍT NHẤT 2 BƯỚC =====
            List<Integer> treasurePairs = new ArrayList<>();
            for (int cell : pairs) {
                if (map[cell / SIZE][cell % SIZE] == 0
                        && distance(cell, spawn1) >= 2 && distance(cell, spawn2) >= 2) {
                    treasurePairs.add(cell);
                }
            }
            if (treasurePairs.size() < 2) {
                continue;
            }
            Collections.shuffle(treasurePairs, random);
            // Hai rương NORMAL đối xứng, hai SILVER đối xứng và một GOLD tại tâm; tổng cộng 5 rương.
            List<Treasure> treasures = new ArrayList<>();
            addPair(treasures, treasurePairs.get(0), Treasure.NORMAL, 10);
            addPair(treasures, treasurePairs.get(1), Treasure.SILVER, 20);
            treasures.add(new Treasure("T5", position(CENTER), 30, Treasure.GOLD));

            // ===== ĐƯA NGƯỜI CHƠI VỀ ĐIỂM XUẤT PHÁT VÀ TẠO TRẠNG THÁI TRẬN CHO SERVER =====
            resetPlayer(player1, spawn1);
            resetPlayer(player2, spawn2);
            GameState state = new GameState();
            state.setRoomId(roomId);
            state.setMap(map);
            state.setPlayer1(player1);
            state.setPlayer2(player2);
            state.setTreasures(treasures);
            state.setRemainingTime(180);
            state.setStatus("RUNNING");
            return state;
        }
        // Hết giới hạn thử thì báo lỗi rõ ràng, không trả về một bản đồ sai điều kiện.
        throw new IllegalStateException("Could not generate a connected map");
    }

    // ===== RESET VỊ TRÍ, ĐIỂM VÀ RƯƠNG CỦA TRẬN MỚI =====
    private void resetPlayer(Player player, int spawn) {
        // Chỉ reset dữ liệu trong trận; điểm rank, số trận thắng và tổng rương trọn đời không đổi ở đây.
        player.setPosition(position(spawn));
        player.setMatchScore(0);
        player.setCollectedTreasure(0);
    }

    // ===== TẠO MỘT CẶP RƯƠNG CÙNG LOẠI Ở HAI Ô ĐỐI XỨNG =====
    private void addPair(List<Treasure> treasures, int cell, String type, int value) {
        treasures.add(new Treasure("T" + (treasures.size() + 1), position(cell), value, type));
        treasures.add(new Treasure("T" + (treasures.size() + 1), position(opposite(cell)), value, type));
    }

    // ===== TÌM Ô ĐỐI DIỆN QUA TÂM BẢN ĐỒ: cell + opposite = 48 =====
    private int opposite(int cell) {
        return SIZE * SIZE - 1 - cell;
    }

    // ===== ĐỔI SỐ THỨ TỰ Ô THÀNH TỌA ĐỘ HÀNG / CỘT =====
    private Position position(int cell) {
        return new Position(cell / SIZE, cell % SIZE);
    }

    // ===== TÍNH KHOẢNG CÁCH MANHATTAN: CHÊNH LỆCH HÀNG CỘNG CHÊNH LỆCH CỘT =====
    private int distance(int cell1, int cell2) {
        return Math.abs(cell1 / SIZE - cell2 / SIZE) + Math.abs(cell1 % SIZE - cell2 % SIZE);
    }

    // ===== DUYỆT BFS ĐỂ KIỂM TRA TOÀN BỘ Ô TRỐNG LIÊN THÔNG =====
    private boolean isConnected(int[][] map) {
        boolean[][] visited = new boolean[SIZE][SIZE];
        Queue<Integer> queue = new ArrayDeque<>();
        // Bắt đầu từ tâm (luôn là ô trống); queue giữ các ô chờ xét, visited tránh duyệt một ô nhiều lần.
        queue.add(CENTER);
        visited[CENTER / SIZE][CENTER % SIZE] = true;
        int reachable = 0;
        // Chỉ xét bốn hướng lên/xuống/trái/phải, đúng với cách người chơi được phép di chuyển.
        int[][] directions = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
        while (!queue.isEmpty()) {
            int cell = queue.remove();
            reachable++;
            for (int[] direction : directions) {
                int row = cell / SIZE + direction[0];
                int col = cell % SIZE + direction[1];
                if (row >= 0 && row < SIZE && col >= 0 && col < SIZE
                        && map[row][col] == 0 && !visited[row][col]) {
                    // Đánh dấu ngay khi đưa vào hàng đợi để các đường khác không thêm trùng cùng ô.
                    visited[row][col] = true;
                    queue.add(row * SIZE + col);
                }
            }
        }
        // generate() luôn đặt đúng 8 tường: đi được 49 - 8 = 41 ô thì mọi ô trống thuộc cùng một vùng.
        return reachable == SIZE * SIZE - 8;
    }
}
