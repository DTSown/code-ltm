package server;

import common.TrapEvent;
import model.GameState;
import model.GameResult;
import model.Player;
import model.Position;
import model.Treasure;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GameRoom {
    public static final String MOVE_STUNNED = "MOVE_STUNNED";
    private static final int STUN_MILLIS = 2000;
    private static final long STUN_NANOS = TimeUnit.MILLISECONDS.toNanos(STUN_MILLIS);
    private final String id;
    private boolean started;
    private Player player1;
    private Player player2;
    private GameState gameState;
    private final Random random;
    private final LongSupplier nanoTime;
    // Danh sách rương bẫy chỉ nằm ở Server; snapshot gửi Client vẫn chỉ có hình thức NORMAL/SILVER/GOLD.
    private final Set<Treasure> trapTreasures = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Player, Long> stunStartedAt = new IdentityHashMap<>();
    private final Queue<TrapEvent> pendingTrapEvents = new ArrayDeque<>();
    private long lastTrapEventId;
    private ScheduledFuture<?> timer;
    private GameResult result;

    public GameRoom(Player player1, Player player2) {
        this(player1, player2, UUID.randomUUID().toString());
    }

    GameRoom(Player player1, Player player2, long roomNumber) {
        this(player1, player2, "ROOM-" + roomNumber + "-" + UUID.randomUUID());
    }

    private GameRoom(Player player1, Player player2, String id) {
        this(player1, player2, id, System::nanoTime, new Random());
    }

    GameRoom(Player player1, Player player2, LongSupplier nanoTime, Random random) {
        this(player1, player2, UUID.randomUUID().toString(), nanoTime, random);
    }

    private GameRoom(Player player1, Player player2, String id, LongSupplier nanoTime, Random random) {
        this.id = id;
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.random = Objects.requireNonNull(random);
        this.player1 = player1;
        this.player2 = player2;
        this.gameState = new GameState();
        this.gameState.setPlayer1(player1);
        this.gameState.setPlayer2(player2);
        this.gameState.setStatus("WAITING");
    }

    public synchronized void startGame() {
        startGame(null);
    }

    // ===== BẮT ĐẦU TRẬN, SINH MAP MỚI VÀ CHỌN MỘT RƯƠNG BẪY NGẪU NHIÊN =====
    public synchronized void startGame(int[][] previousMap) {
        if (started || "FINISHED".equals(gameState.getStatus())) {
            return;
        }
        MapGenerator generator = new MapGenerator();
        // Vòng ngoài này phục vụ tái đấu: map đã sinh phải khác map trước; generate() có vòng kiểm tra hợp lệ riêng.
        for (int attempt = 0; attempt < 1000; attempt++) {
            model.GameState generated = generator.generate(id, player1, player2);
            if (previousMap == null || !java.util.Arrays.deepEquals(previousMap, generated.getMap())) {
                gameState = generated;
                clearTrapState();
                List<Treasure> treasures = gameState.getTreasures();
                trapTreasures.add(treasures.get(random.nextInt(treasures.size())));
                started = true;
                return;
            }
        }
        throw new IllegalStateException("Could not generate a different rematch map");
    }

    // ===== [PHẦN PHỤ TRÁCH: DI CHUYỂN + VA CHẠM] =====
    public synchronized String handleMove(String username, String direction) {
        // synchronized khóa theo từng phòng: tại một thời điểm chỉ một luồng được đổi vị trí.
        // Vì vậy hai client gửi MOVE đồng thời vẫn được Server xếp thứ tự xử lý rõ ràng.
        // Trận phải đang chạy và còn thời gian; người gửi phải thuộc phòng và đang ở trạng thái PLAYING.
        if (!started || !"RUNNING".equals(gameState.getStatus()) || gameState.getRemainingTime() <= 0) {
            return "Trận đấu chưa RUNNING";
        }
        Player moving;
        Player opponent;
        if (gameState.getPlayer1().getUsername().equals(username)) {
            moving = gameState.getPlayer1();
            opponent = gameState.getPlayer2();
        } else if (gameState.getPlayer2().getUsername().equals(username)) {
            moving = gameState.getPlayer2();
            opponent = gameState.getPlayer1();
        } else {
            return "Người chơi không thuộc phòng này";
        }
        if (moving.getStatus() != Player.Status.PLAYING) {
            return "Người chơi không đang PLAYING";
        }
        // Server tự kiểm tra thời gian choáng, nên Client sửa nút bấm vẫn không thể đi khi đang bị khóa.
        if (isStunned(moving)) {
            return MOVE_STUNNED;
        }
        int row = moving.getPosition().getRow();
        int col = moving.getPosition().getCol();
        if (direction == null) {
            return "Hướng di chuyển không hợp lệ";
        }
        // Chỉ tính tọa độ dự kiến ở biến cục bộ; chưa sửa Player khi chưa kiểm tra xong ô đích.
        switch (direction) {
            case "UP": row--; break;
            case "DOWN": row++; break;
            case "LEFT": col--; break;
            case "RIGHT": col++; break;
            default: return "Hướng di chuyển không hợp lệ";
        }
        // ===== CHẶN RA NGOÀI MAP, XUYÊN TƯỜNG HOẶC ĐI VÀO Ô ĐỐI THỦ =====
        if (row < 0 || row >= GameState.MAP_SIZE || col < 0 || col >= GameState.MAP_SIZE
                || gameState.getMap()[row][col] == 1
                || (opponent.getPosition().getRow() == row && opponent.getPosition().getCol() == col)) {
            return "Di chuyển không hợp lệ";
        }
        // Chỉ cập nhật vị trí thật sau khi hợp lệ; null là thành công, chuỗi khác null là lý do từ chối.
        moving.setPosition(new Position(row, col));
        return null;
    }

    // ===== [PHẦN PHỤ TRÁCH: NHẶT RƯƠNG + TĂNG ĐIỂM] =====
    public synchronized Treasure collectTreasure(Player player) {
        // Hàm dùng cùng khóa GameRoom với handleMove. ServerManager gọi hai hàm trong
        // một khối synchronized(room), nên MOVE -> nhặt -> cộng điểm -> hồi sinh rương
        // là một giao dịch nguyên tử; không có chuyện hai người cùng ăn một rương.
        // Kiểm tra lại trạng thái trận, đúng đối tượng Player của phòng và không bị choáng trước khi nhặt.
        if (!started || !"RUNNING".equals(gameState.getStatus()) || gameState.getRemainingTime() <= 0
                || (player != gameState.getPlayer1() && player != gameState.getPlayer2())
                || player.getStatus() != Player.Status.PLAYING || isStunned(player)) {
            return null;
        }
        List<Treasure> treasures = gameState.getTreasures();
        for (int i = 0; i < treasures.size(); i++) {
            Treasure treasure = treasures.get(i);
            // So sánh cả hàng lẫn cột: chỉ rương trùng chính xác vị trí mới được mở.
            if (samePosition(player.getPosition(), treasure.getPosition())) {
                // Server lấy mức điểm chuẩn từ loại rương, không nhận điểm tự khai từ Client.
                int value = Treasure.valueForType(treasure.getType());
                boolean trap = trapTreasures.contains(treasure);
                long eventId = trap ? Math.incrementExact(lastTrapEventId) : lastTrapEventId;
                // Tạo rương thay thế trước khi xóa rương cũ; xác suất rương thay thế là bẫy bằng 20%.
                Treasure replacement = createReplacementTreasure();
                boolean replacementIsTrap = random.nextDouble() < 0.20;
                // ===== XÓA RƯƠNG CŨ VÀ THÊM RƯƠNG MỚI TRONG CÙNG KHÓA PHÒNG =====
                // Số rương không giảm sau một lần mở thành công; lần xử lý tiếp theo không còn thấy rương cũ.
                treasures.remove(i);
                trapTreasures.remove(treasure);
                treasures.add(replacement);
                if (replacementIsTrap) {
                    trapTreasures.add(replacement);
                }
                // ===== NHÁNH RƯƠNG BẪY: TRỪ TỐI ĐA 10 ĐIỂM, CHOÁNG 2 GIÂY, KHÔNG CỘNG SỐ RƯƠNG =====
                if (trap) {
                    int score = Math.max(0, player.getMatchScore());
                    int pointsLost = Math.min(10, score);
                    player.setMatchScore(score - pointsLost);
                    stunStartedAt.put(player, nanoTime.getAsLong());
                    lastTrapEventId = eventId;
                    pendingTrapEvents.add(new TrapEvent(id, eventId, player.getUsername(),
                            pointsLost, player.getMatchScore(), STUN_MILLIS));
                    return null;
                }
                // ===== NHÁNH RƯƠNG THƯỞNG: CỘNG 10 / 20 / 30 ĐIỂM VÀ TĂNG SỐ RƯƠNG THÊM 1 =====
                player.setMatchScore(player.getMatchScore() + value);
                player.setCollectedTreasure(player.getCollectedTreasure() + 1);
                return treasure;
            }
        }
        return null;
    }

    public synchronized TrapEvent pollTrapEvent() {
        return pendingTrapEvents.poll();
    }

    // ===== KIỂM TRA CHOÁNG BẰNG ĐỒNG HỒ MONOTONIC CỦA SERVER =====
    private boolean isStunned(Player player) {
        Long start = stunStartedAt.get(player);
        if (start == null) {
            return false;
        }
        // Elapsed subtraction preserves nanosecond boundaries and nanoTime wraparound.
        if (nanoTime.getAsLong() - start < STUN_NANOS) {
            return true;
        }
        stunStartedAt.remove(player);
        return false;
    }

    private void clearTrapState() {
        trapTreasures.clear();
        stunStartedAt.clear();
        pendingTrapEvents.clear();
    }

    // ===== CHỌN Ô TRỐNG HỢP LỆ VÀ LOẠI RƯƠNG NGẪU NHIÊN ĐỂ HỒI SINH =====
    private Treasure createReplacementTreasure() {
        // Đánh dấu ô có người chơi hoặc rương; ô có tường sẽ bị loại khi lập danh sách available.
        boolean[][] occupied = new boolean[GameState.MAP_SIZE][GameState.MAP_SIZE];
        for (Player player : new Player[] { gameState.getPlayer1(), gameState.getPlayer2() }) {
            Position position = player.getPosition();
            occupied[position.getRow()][position.getCol()] = true;
        }
        for (Treasure treasure : gameState.getTreasures()) {
            Position position = treasure.getPosition();
            occupied[position.getRow()][position.getCol()] = true;
        }
        // Sampling from free cells avoids an unbounded random retry loop.
        // Liệt kê hữu hạn các ô hợp lệ rồi chọn ngẫu nhiên, tránh thử ngẫu nhiên lặp vô hạn.
        List<Position> available = new ArrayList<>();
        for (int row = 0; row < GameState.MAP_SIZE; row++) {
            for (int col = 0; col < GameState.MAP_SIZE; col++) {
                if (gameState.getMap()[row][col] == 0 && !occupied[row][col]) {
                    available.add(new Position(row, col));
                }
            }
        }
        if (available.isEmpty()) {
            throw new IllegalStateException("No free cell for treasure respawn");
        }
        // Loại rương, giá trị điểm và ID mới đều do Server quyết định; không đặt lên người chơi hay rương khác.
        String[] types = { Treasure.NORMAL, Treasure.SILVER, Treasure.GOLD };
        String type = types[random.nextInt(types.length)];
        Position position = available.get(random.nextInt(available.size()));
        return new Treasure("T-" + UUID.randomUUID(), position, Treasure.valueForType(type), type);
    }

    // ===== SO SÁNH HAI TỌA ĐỘ TRÊN LƯỚI BẰNG HÀNG VÀ CỘT =====
    private boolean samePosition(Position a, Position b) {
        return a.getRow() == b.getRow() && a.getCol() == b.getCol();
    }

    public synchronized void endGame() {
        cancelTimer();
        clearTrapState();
        started = false;
        gameState.setStatus("FINISHED");
    }

    public synchronized void startTimer(ScheduledExecutorService scheduler, Runnable tick) {
        if (started && "RUNNING".equals(gameState.getStatus()) && timer == null) {
            timer = scheduler.scheduleAtFixedRate(tick, 1, 1, TimeUnit.SECONDS);
        }
    }

    public synchronized void cancelTimer() {
        if (timer != null) {
            timer.cancel(false);
        }
    }

    public synchronized boolean isTimerRunning() {
        return timer != null && !timer.isDone() && !timer.isCancelled();
    }

    public synchronized GameResult finishGame() {
        return finishGame(null, GameResult.TIME_UP);
    }

    public synchronized GameResult finishGame(Player forfeiting, String reason) {
        if (result != null || !started || !"RUNNING".equals(gameState.getStatus())) {
            return null;
        }
        if (GameResult.TIME_UP.equals(reason)) {
            result = GameResult.fromState(gameState);
        } else {
            if (forfeiting != player1 && forfeiting != player2) {
                throw new IllegalArgumentException("Forfeiting player must belong to room");
            }
            boolean p1Wins = forfeiting == player2;
            result = new GameResult(id, player1.getUsername(), player2.getUsername(),
                    p1Wins ? GameResult.PLAYER1 : GameResult.PLAYER2,
                    player1.getMatchScore(), player2.getMatchScore(),
                    player1.getCollectedTreasure(), player2.getCollectedTreasure(),
                    p1Wins ? 3 : 0, p1Wins ? 0 : 3, reason);
        }
        player1.setTotalRankPoint(player1.getTotalRankPoint() + result.getPlayer1RankGain());
        player2.setTotalRankPoint(player2.getTotalRankPoint() + result.getPlayer2RankGain());
        player1.setTotalMatches(player1.getTotalMatches() + 1);
        player2.setTotalMatches(player2.getTotalMatches() + 1);
        player1.setTotalTreasureCollected(player1.getTotalTreasureCollected() + result.getPlayer1Collected());
        player2.setTotalTreasureCollected(player2.getTotalTreasureCollected() + result.getPlayer2Collected());
        if (GameResult.PLAYER1.equals(result.getWinner())) {
            player1.setWins(player1.getWins() + 1);
        } else if (GameResult.PLAYER2.equals(result.getWinner())) {
            player2.setWins(player2.getWins() + 1);
        }
        endGame();
        return result;
    }

    public synchronized GameResult getResult() {
        return result;
    }

    public Player getPlayer1() {
        return player1;
    }

    public String getId() {
        return id;
    }

    public boolean isStarted() {
        return started;
    }

    public void setPlayer1(Player player1) {
        this.player1 = player1;
    }

    public Player getPlayer2() {
        return player2;
    }

    public void setPlayer2(Player player2) {
        this.player2 = player2;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }
}
