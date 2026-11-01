import common.GameStateCodec;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import model.GameState;
import model.Player;
import model.Position;
import model.Treasure;
import server.GameRoom;
import server.MapGenerator;

public class MapGenerationTest {
    private static final int SIZE = GameState.MAP_SIZE;

    static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    static Player player(String name) {
        Player player = new Player();
        player.setUsername(name);
        return player;
    }

    private static int cell(Position position) {
        int row = position.getRow();
        int col = position.getCol();
        check(row >= 0 && row < SIZE && col >= 0 && col < SIZE, "Position bounds");
        return row * SIZE + col;
    }

    private static int manhattan(Position a, Position b) {
        return Math.abs(a.getRow() - b.getRow()) + Math.abs(a.getCol() - b.getCol());
    }

    private static int[][] distances(int[][] map, Position start) {
        int[][] distance = new int[SIZE][SIZE];
        for (int[] row : distance) {
            Arrays.fill(row, -1);
        }
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(cell(start));
        distance[start.getRow()][start.getCol()] = 0;
        int[][] directions = { { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 } };
        while (!queue.isEmpty()) {
            int next = queue.remove();
            int row = next / SIZE;
            int col = next % SIZE;
            for (int[] direction : directions) {
                int r = row + direction[0];
                int c = col + direction[1];
                if (r >= 0 && r < SIZE && c >= 0 && c < SIZE && map[r][c] == 0 && distance[r][c] < 0) {
                    distance[r][c] = distance[row][col] + 1;
                    queue.add(r * SIZE + c);
                }
            }
        }
        return distance;
    }

    static void validate(GameState state) {
        check(!state.getRoomId().isEmpty(), "Room id");
        check(state.getMap().length == SIZE, "Seven rows");
        Position p1 = state.getPlayer1().getPosition();
        Position p2 = state.getPlayer2().getPosition();
        check(cell(p1) != cell(p2) && manhattan(p1, p2) >= 5, "Spawn separation");
        int[][] d1 = distances(state.getMap(), p1);
        int[][] d2 = distances(state.getMap(), p2);
        int obstacles = 0;
        for (int row = 0; row < SIZE; row++) {
            check(state.getMap()[row].length == SIZE, "Seven columns");
            for (int col = 0; col < SIZE; col++) {
                int value = state.getMap()[row][col];
                check(value == 0 || value == 1, "Only road or obstacle");
                if (value == 1) {
                    obstacles++;
                } else {
                    check(d1[row][col] >= 0 && d2[row][col] >= 0, "No isolated road cell");
                }
            }
        }
        check(obstacles == 8, "Eight obstacles (16.3%)");
        Set<Integer> occupied = new HashSet<>();
        occupied.add(cell(p1));
        occupied.add(cell(p2));
        check(state.getMap()[p1.getRow()][p1.getCol()] == 0
                && state.getMap()[p2.getRow()][p2.getCol()] == 0, "Spawn on road");
        Set<String> ids = new HashSet<>();
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Integer> totals1 = new HashMap<>();
        Map<String, Integer> totals2 = new HashMap<>();
        check(state.getTreasures().size() == 5, "Five treasures");
        for (Treasure treasure : state.getTreasures()) {
            Position p = treasure.getPosition();
            check(occupied.add(cell(p)), "Treasure does not overlap another object");
            check(ids.add(treasure.getId()) && !treasure.getId().isEmpty(), "Unique treasure id");
            check(state.getMap()[p.getRow()][p.getCol()] == 0, "Treasure on road");
            check(d1[p.getRow()][p.getCol()] >= 0 && d2[p.getRow()][p.getCol()] >= 0, "Treasure reachable");
            check(manhattan(p, p1) >= 2 && manhattan(p, p2) >= 2, "Treasure not next to spawn");
            String type = treasure.getType();
            int expected = Treasure.NORMAL.equals(type) ? 10 : Treasure.SILVER.equals(type) ? 20 : 30;
            check(treasure.getValue() == expected, "Treasure value");
            counts.merge(type, 1, Integer::sum);
            totals1.merge(type, d1[p.getRow()][p.getCol()], Integer::sum);
            totals2.merge(type, d2[p.getRow()][p.getCol()], Integer::sum);
        }
        check(Integer.valueOf(2).equals(counts.get(Treasure.NORMAL))
                && Integer.valueOf(2).equals(counts.get(Treasure.SILVER))
                && Integer.valueOf(1).equals(counts.get(Treasure.GOLD)), "Treasure types");
        check(totals1.equals(totals2), "Equal shortest-path distance totals for every treasure type");
        for (Player player : new Player[] { state.getPlayer1(), state.getPlayer2() }) {
            check(player.getMatchScore() == 0 && player.getCollectedTreasure() == 0, "Initial scores and counts");
        }
        check(state.getRemainingTime() == 180 && "RUNNING".equals(state.getStatus()), "Time and status");
    }

    private static void expectInvalid(String[] parts, int index, String replacement) {
        String[] changed = parts.clone();
        changed[index] = replacement;
        try {
            GameStateCodec.decode(String.join(";", changed));
            throw new AssertionError("Malformed state accepted: field " + index);
        } catch (IllegalArgumentException expected) {
            // Malformed messages must fail without returning a partial state.
        }
    }

    public static void main(String[] args) {
        Set<String> differentMaps = new HashSet<>();
        for (int seed = 0; seed < 1000; seed++) {
            Player alex = player("Alex");
            alex.setMatchScore(90);
            alex.setCollectedTreasure(7);
            alex.setTotalRankPoint(123);
            alex.setWins(4);
            GameState state = new MapGenerator(new Random(seed)).generate("room-" + seed, alex, player("Nina"));
            validate(state);
            check(alex.getTotalRankPoint() == 123 && alex.getWins() == 4, "Keep lifetime ranking fields");
            String encoded = GameStateCodec.encode(state);
            GameState decoded = GameStateCodec.decode(encoded);
            validate(decoded);
            check(encoded.equals(GameStateCodec.encode(decoded)), "Codec preserves all wire fields");
            differentMaps.add(encoded.split(";", -1)[1]);
        }
        check(differentMaps.size() > 100, "Random map variety");
        System.out.println("PASS: 1000 seeds, connectivity, spawn, treasures, fairness, codec round-trip");

        GameRoom first = new GameRoom(player("A"), player("B"));
        GameRoom second = new GameRoom(player("C"), player("D"));
        first.startGame();
        second.startGame();
        GameState state = first.getGameState();
        check(state != second.getGameState() && state.getMap() != second.getGameState().getMap()
                && state.getTreasures() != second.getGameState().getTreasures(), "Separate room state");
        check(first.getId().equals(state.getRoomId()) && !first.getId().equals(second.getId()), "Room identity");
        first.startGame();
        check(state == first.getGameState(), "Duplicate start does not rerandomize the map");
        validate(state);

        state.getPlayer1().setUsername("Nguy\u1ec5n;A,|/%+");
        state.getPlayer2().setUsername("Nina + test");
        String payload = GameStateCodec.encode(state);
        GameState decoded = GameStateCodec.decode(payload);
        check(state.getPlayer1().getUsername().equals(decoded.getPlayer1().getUsername()), "UTF-8 and separators");
        check(state.getPlayer2().getUsername().equals(decoded.getPlayer2().getUsername()), "Spaces and plus sign");
        String[] parts = payload.split(";", -1);
        expectInvalid(parts, 1, "0");
        expectInvalid(parts, 1, parts[1].substring(1) + "2");
        expectInvalid(parts, 2, "Alex,7,0,0,0");
        expectInvalid(parts, 2, "%ZZ,0,0,0,0");
        expectInvalid(parts, 3, "Nina," + state.getPlayer1().getPosition().getRow() + ","
                + state.getPlayer1().getPosition().getCol() + ",0,0");
        expectInvalid(parts, 4, parts[4] + "/" + parts[4].split("/")[0]);
        expectInvalid(parts, 4, "T1,NORMAL,30,3,3");
        expectInvalid(parts, 4, "T1,NORMAL,10," + state.getPlayer1().getPosition().getRow()
                + "," + state.getPlayer1().getPosition().getCol());
        expectInvalid(parts, 5, "-1");
        expectInvalid(parts, 5, "abc");
        decoded.getPlayer1().setMatchScore(20);
        decoded.getPlayer1().setCollectedTreasure(1);
        decoded.getPlayer2().setMatchScore(10);
        decoded.getPlayer2().setCollectedTreasure(2);
        decoded.setRemainingTime(179);
        String nonzero = GameStateCodec.encode(decoded);
        check(nonzero.equals(GameStateCodec.encode(GameStateCodec.decode(nonzero))), "Nonzero scores and time retained");
        decoded.getTreasures().clear();
        check(GameStateCodec.decode(GameStateCodec.encode(decoded)).getTreasures().isEmpty(), "Empty treasure section");
        System.out.println("PASS: separate rooms, duplicate start, UTF-8, malformed payloads, scores/time fields");
        System.out.println("ALL MAP TESTS PASSED");
    }
}
