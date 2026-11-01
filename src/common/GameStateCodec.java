package common;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import model.GameState;
import model.Player;
import model.Position;
import model.Treasure;

public final class GameStateCodec {
    private GameStateCodec() {
    }

    // [PHẦN PHỤ TRÁCH: ĐỊNH DẠNG ĐỒNG BỘ GAME_STATE]
    // roomId;49 map digits;p1;p2;treasure/treasure;remainingTime;status
    // Encode toàn bộ snapshot cần để client vẽ; không gửi cờ bí mật của rương bẫy.
    public static String encode(GameState state) {
        StringBuilder map = new StringBuilder();
        for (int[] row : state.getMap()) {
            for (int cell : row) {
                map.append(cell);
            }
        }
        StringBuilder treasures = new StringBuilder();
        for (Treasure treasure : state.getTreasures()) {
            if (treasures.length() > 0) {
                treasures.append('/');
            }
            treasures.append(treasure.getId()).append(',').append(treasure.getType()).append(',')
                    .append(treasure.getValue()).append(',').append(treasure.getPosition().getRow())
                    .append(',').append(treasure.getPosition().getCol());
        }
        return state.getRoomId() + ";" + map + ";" + encodePlayer(state.getPlayer1())
                + ";" + encodePlayer(state.getPlayer2()) + ";" + treasures
                + ";" + state.getRemainingTime() + ";" + state.getStatus();
    }

    private static String encodePlayer(Player player) {
        return URLEncoder.encode(player.getUsername(), StandardCharsets.UTF_8) + ","
                + player.getPosition().getRow() + "," + player.getPosition().getCol() + ","
                + player.getMatchScore() + "," + player.getCollectedTreasure();
    }

    public static GameState decode(String data) {
        // Client kiểm tra chặt số trường, map, tọa độ, ID và giá trị rương trước khi render.
        // Dữ liệu lỗi bị từ chối toàn bộ, không tạo một GameState nửa đúng nửa sai.
        if (data == null) {
            throw new IllegalArgumentException("Missing GAME_STATE");
        }
        String[] parts = data.split(";", -1);
        if (parts.length != 7 || parts[0].isEmpty() || parts[6].isEmpty()) {
            throw new IllegalArgumentException("GAME_STATE must contain seven fields");
        }
        int size = GameState.MAP_SIZE;
        if (!parts[1].matches("[01]{49}")) {
            throw new IllegalArgumentException("Map must contain 49 digits (0 or 1)");
        }
        int[][] map = new int[size][size];
        for (int i = 0; i < size * size; i++) {
            map[i / size][i % size] = parts[1].charAt(i) - '0';
        }
        Set<Integer> occupied = new HashSet<>();
        Player player1 = decodePlayer(parts[2], map, occupied);
        Player player2 = decodePlayer(parts[3], map, occupied);
        if (player1.getUsername().equals(player2.getUsername())) {
            throw new IllegalArgumentException("Players must have different usernames");
        }
        List<Treasure> treasures = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        if (!parts[4].isEmpty()) {
            for (String entry : parts[4].split("/", -1)) {
                String[] fields = entry.split(",", -1);
                if (fields.length != 5 || fields[0].isEmpty() || !ids.add(fields[0])) {
                    throw new IllegalArgumentException("Invalid or duplicate treasure id");
                }
                int value = nonnegative(fields[2]);
                int expectedValue = Treasure.valueForType(fields[1]);
                if (value != expectedValue) {
                    throw new IllegalArgumentException("Invalid treasure value");
                }
                Position position = decodePosition(fields[3], fields[4], map, occupied);
                treasures.add(new Treasure(fields[0], position, value, fields[1]));
            }
        }
        GameState state = new GameState();
        state.setRoomId(parts[0]);
        state.setMap(map);
        state.setPlayer1(player1);
        state.setPlayer2(player2);
        state.setTreasures(treasures);
        state.setRemainingTime(nonnegative(parts[5]));
        state.setStatus(parts[6]);
        return state;
    }

    private static Player decodePlayer(String data, int[][] map, Set<Integer> occupied) {
        String[] fields = data.split(",", -1);
        if (fields.length != 5) {
            throw new IllegalArgumentException("Invalid player fields");
        }
        Player player = new Player();
        player.setUsername(URLDecoder.decode(fields[0], StandardCharsets.UTF_8));
        if (player.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Missing player username");
        }
        player.setPosition(decodePosition(fields[1], fields[2], map, occupied));
        player.setMatchScore(nonnegative(fields[3]));
        player.setCollectedTreasure(nonnegative(fields[4]));
        player.setStatus(Player.Status.PLAYING);
        return player;
    }

    private static Position decodePosition(String rowData, String colData, int[][] map, Set<Integer> occupied) {
        int row = nonnegative(rowData);
        int col = nonnegative(colData);
        if (row >= GameState.MAP_SIZE || col >= GameState.MAP_SIZE || map[row][col] != 0
                || !occupied.add(row * GameState.MAP_SIZE + col)) {
            throw new IllegalArgumentException("Position is outside the map, blocked or occupied");
        }
        return new Position(row, col);
    }

    private static int nonnegative(String value) {
        int number = Integer.parseInt(value);
        if (number < 0) {
            throw new IllegalArgumentException("Negative game value");
        }
        return number;
    }
}
